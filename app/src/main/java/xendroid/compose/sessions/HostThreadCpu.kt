package xendroid.compose.sessions

import java.io.File
import kotlinx.serialization.Serializable

/** A 5-second /proc stat snapshot at which this host CPU was observed. */
@Serializable
data class HostThreadProcessorObservation(val cpu: Int, val samples: Int)

/** CPU execution, relative to one core, not whole-device utilization or a bottleneck verdict. */
@Serializable
data class HostThreadCpuUsage(
    val tid: Int,
    val startTicks: Long,
    val name: String,
    val windows: Int,
    val meanPercentOneCore: Double,
    val peakPercentOneCore: Double,
    /** CPUs seen at sample instants, not residency, affinity or frequency. */
    val observedProcessors: List<Int> = emptyList(),
    /** Instantaneous CPU observations, not affinity, time residency or frequency. */
    val processorObservations: List<HostThreadProcessorObservation> = emptyList(),
)

@Serializable
data class HostThreadCpuReport(
    val version: Int = 1,
    val clockTicksPerSecond: Long? = null,
    val attempts: Int = 0,
    val unavailableReads: Int = 0,
    val unreadableThreads: Int = 0,
    val truncatedReads: Int = 0,
    val readTotalMs: Double = 0.0,
    val readMaxMs: Double = 0.0,
    val threads: List<HostThreadCpuUsage> = emptyList(),
)

data class HostThreadCpuStat(val tid: Int, val startTicks: Long, val name: String, val cpuTicks: Long, val processor: Int?) {
    companion object {
        /** /proc stat's comm can contain spaces and parentheses: split after the LAST ')'. */
        fun parse(text: String, expectedTid: Int): HostThreadCpuStat? {
            val open = text.indexOf('(')
            val close = text.lastIndexOf(')')
            if (open < 1 || close <= open || text.substring(0, open).trim().toIntOrNull() != expectedTid) return null
            val fields = text.substring(close + 1).trim().split(Regex("\\s+"))
            // After comm: state is field 3/index 0, utime 14/11, stime 15/12, starttime 22/19.
            fun nonnegative(index: Int) = fields.getOrNull(index)?.toLongOrNull()?.takeIf { it >= 0 }
            val user = nonnegative(11) ?: return null
            val system = nonnegative(12) ?: return null
            val start = nonnegative(19) ?: return null
            if (user > Long.MAX_VALUE - system) return null
            return HostThreadCpuStat(expectedTid, start,
                text.substring(open + 1, close).filter { it >= ' ' }.take(48), user + system,
                nonnegative(36)?.takeIf { it <= 255 }?.toInt())
        }
    }
}

data class HostThreadCpuSample(
    val atNs: Long,
    val ticksPerSecond: Long,
    val rows: List<HostThreadCpuStat>,
    val readNs: Long = 0,
    val unavailable: Boolean = false,
    val unreadable: Int = 0,
    val truncated: Boolean = false,
)

/** Reads only this process; caller runs on IO, at most once per five seconds. */
class HostThreadCpuReader(
    private val ticksPerSecond: Long,
    private val tasks: File = File("/proc/self/task"),
    private val nowNs: () -> Long = System::nanoTime,
) {
    fun read(): HostThreadCpuSample {
        val begin = nowNs()
        val dirs = runCatching { tasks.listFiles() }.getOrNull()
            ?.filter { it.name.toIntOrNull()?.let { n -> n > 0 } == true }
            ?.sortedBy { it.name.toInt() }
        var unreadable = 0
        val rows = dirs.orEmpty().take(MAX_THREADS).mapNotNull { dir ->
            val row = runCatching { HostThreadCpuStat.parse(File(dir, "stat").readText(), dir.name.toInt()) }.getOrNull()
            if (row == null) unreadable++
            row
        }
        val end = nowNs()
        return HostThreadCpuSample(begin + (end - begin).coerceAtLeast(0) / 2, ticksPerSecond, rows,
            (end - begin).coerceAtLeast(0), dirs == null || dirs.isEmpty() || ticksPerSecond <= 0,
            unreadable, (dirs?.size ?: 0) > MAX_THREADS)
    }
    companion object { const val MAX_THREADS = 128 }
}

/** Main-thread accumulator. A missing read, pause, counter reset or reused TID establishes a fresh baseline. */
class HostThreadCpuProfiler {
    private data class Key(val tid: Int, val start: Long)
    private data class Total(var name: String, var ticks: Long = 0, var ns: Long = 0,
                             var windows: Int = 0, var peak: Double = 0.0,
                             val cpus: MutableSet<Int> = sortedSetOf(),
                             val cpuSnapshots: MutableMap<Int, Int> = sortedMapOf())
    private var previous: HostThreadCpuSample? = null
    private val totals = linkedMapOf<Key, Total>()
    private var attempts = 0
    private var unavailable = 0
    private var unreadable = 0
    private var truncated = 0
    private var readTotalNs = 0L
    private var readMaxNs = 0L
    private var tickHz: Long? = null

    fun resetBaseline() { previous = null }

    fun sample(now: HostThreadCpuSample) {
        attempts++
        unreadable += now.unreadable
        if (now.truncated) truncated++
        readTotalNs += now.readNs.coerceAtLeast(0)
        readMaxNs = maxOf(readMaxNs, now.readNs)
        if (now.unavailable || now.ticksPerSecond <= 0 || now.rows.isEmpty()) {
            unavailable++
            resetBaseline()
            return
        }
        // Never combine ticks measured at different frequencies into one mean.
        if (tickHz != null && tickHz != now.ticksPerSecond) { totals.clear(); resetBaseline() }
        tickHz = now.ticksPerSecond
        val before = previous
        previous = now
        if (before == null) return
        val wall = now.atNs - before.atNs
        if (wall <= 0 || wall > MAX_WINDOW_NS || before.ticksPerSecond != now.ticksPerSecond) return
        val old = before.rows.associateBy { Key(it.tid, it.startTicks) }
        for (row in now.rows) {
            val key = Key(row.tid, row.startTicks)
            val prev = old[key] ?: continue
            val delta = row.cpuTicks - prev.cpuTicks
            if (delta < 0) continue
            val percent = delta.toDouble() * 1e11 / now.ticksPerSecond / wall
            val total = totals[key] ?: if (totals.size < MAX_IDENTITIES) Total(row.name).also { totals[key] = it } else continue
            total.name = row.name
            total.ticks += delta
            total.ns += wall
            total.windows++
            total.peak = maxOf(total.peak, percent)
            row.processor?.let {
                total.cpus += it
                total.cpuSnapshots[it] = (total.cpuSnapshots[it] ?: 0) + 1
            }
        }
    }

    fun snapshot(): HostThreadCpuReport? {
        if (attempts == 0) return null
        val hz = tickHz
        val rows = if (hz == null) emptyList() else totals.map { (key, total) ->
            HostThreadCpuUsage(key.tid, key.start, total.name, total.windows,
                total.ticks.toDouble() * 1e11 / hz / total.ns, total.peak, total.cpus.toList(),
                total.cpuSnapshots.map { HostThreadProcessorObservation(it.key, it.value) })
        }.sortedByDescending { it.meanPercentOneCore }.take(MAX_REPORTED_THREADS)
        return HostThreadCpuReport(clockTicksPerSecond = hz, attempts = attempts, unavailableReads = unavailable,
            unreadableThreads = unreadable, truncatedReads = truncated,
            readTotalMs = readTotalNs / 1e6, readMaxMs = readMaxNs / 1e6, threads = rows)
    }

    companion object {
        const val MAX_WINDOW_NS = 15_000_000_000L
        const val MAX_IDENTITIES = 256
        const val MAX_REPORTED_THREADS = 32
    }
}
