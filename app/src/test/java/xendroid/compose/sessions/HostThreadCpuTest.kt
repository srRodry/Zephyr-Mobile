package xendroid.compose.sessions

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlinx.serialization.json.Json

class HostThreadCpuTest {
    @get:Rule val folder = TemporaryFolder()
    private fun stat(tid: Int = 42, start: Long = 900, ticks: Long = 10, name: String = "Guest CPU 0", cpu: Int = 7): String {
        val fields = MutableList(37) { "0" }
        fields[0] = "R"; fields[11] = ticks.toString(); fields[12] = "5"
        fields[19] = start.toString(); fields[36] = cpu.toString()
        return "$tid ($name) ${fields.joinToString(" ")}"
    }
    private fun row(ticks: Long, start: Long = 900, cpu: Int = 7) = HostThreadCpuStat(42, start, "Guest CPU 0", ticks, cpu)
    private fun sample(atSeconds: Long, vararg rows: HostThreadCpuStat) = HostThreadCpuSample(atSeconds * 1_000_000_000, 100, rows.toList())

    @Test fun parsesRealStatFieldPositionsWithSpacesAndClosingParentheses() {
        val s = HostThreadCpuStat.parse(stat(name = "worker ) with spaces"), 42)!!
        assertEquals("worker ) with spaces", s.name)
        assertEquals(15L, s.cpuTicks)
        assertEquals(900L, s.startTicks)
        assertEquals(7, s.processor)
        assertNull(HostThreadCpuStat.parse(stat(), 43))
        assertNull(HostThreadCpuStat.parse("42 (broken) R 0", 42))
        assertNull(HostThreadCpuStat.parse(stat(ticks = -1), 42))
    }

    @Test fun firstReadIsBaselineAndCpuIsRelativeToOneCore() {
        val p = HostThreadCpuProfiler()
        p.sample(sample(0, row(100)))
        assertTrue(p.snapshot()!!.threads.isEmpty())
        p.sample(sample(5, row(350))) // 250 ticks/100 Hz = 2.5 CPU seconds in 5 wall seconds.
        val t = p.snapshot()!!.threads.single()
        assertEquals(50.0, t.meanPercentOneCore, 1e-9)
        assertEquals(50.0, t.peakPercentOneCore, 1e-9)
        assertEquals(1, t.windows)
    }

    @Test fun meansAreWeightedByMeasuredTimeAndProcessorObservationsAreKept() {
        val p = HostThreadCpuProfiler()
        p.sample(sample(0, row(0)))
        p.sample(sample(5, row(500, cpu = 4)))
        p.sample(sample(15, row(1000, cpu = 7)))
        val t = p.snapshot()!!.threads.single()
        assertEquals(1000.0 / 100 / 15 * 100, t.meanPercentOneCore, 1e-9)
        assertEquals(100.0, t.peakPercentOneCore, 1e-9)
        assertEquals(listOf(4, 7), t.observedProcessors)
        assertEquals(listOf(HostThreadProcessorObservation(4, 1),
                            HostThreadProcessorObservation(7, 1)), t.processorObservations)
    }

    @Test fun tidReuseDoesNotBecomeAnEnormousCpuSpike() {
        val p = HostThreadCpuProfiler()
        p.sample(sample(0, row(100)))
        p.sample(sample(5, row(10000, start = 901)))
        assertTrue(p.snapshot()!!.threads.isEmpty())
        p.sample(sample(10, row(10250, start = 901)))
        assertEquals(50.0, p.snapshot()!!.threads.single().meanPercentOneCore, 1e-9)
    }

    @Test fun pauseUnavailableReadAndLongGapRequireFreshBaselines() {
        val p = HostThreadCpuProfiler()
        p.sample(sample(0, row(0))); p.resetBaseline()
        p.sample(sample(5, row(500)))
        assertTrue(p.snapshot()!!.threads.isEmpty())
        p.sample(HostThreadCpuSample(10_000_000_000, 100, emptyList(), unavailable = true))
        p.sample(sample(15, row(1000)))
        assertTrue(p.snapshot()!!.threads.isEmpty())
        p.sample(sample(40, row(3500)))
        assertTrue(p.snapshot()!!.threads.isEmpty())
        p.sample(sample(45, row(3750)))
        assertEquals(50.0, p.snapshot()!!.threads.single().meanPercentOneCore, 1e-9)
        assertEquals(1, p.snapshot()!!.unavailableReads)
    }

    @Test fun counterResetAndNonIncreasingTimeAreNotMeasurements() {
        val p = HostThreadCpuProfiler()
        p.sample(sample(0, row(1000)))
        p.sample(sample(5, row(1)))
        p.sample(sample(5, row(2)))
        assertTrue(p.snapshot()!!.threads.isEmpty())
        p.sample(sample(10, row(252)))
        assertEquals(50.0, p.snapshot()!!.threads.single().meanPercentOneCore, 1e-9)
    }

    @Test fun readerSkipsVanishedThreadsAndRecordsItsReadCost() {
        val tasks = folder.newFolder("task")
        File(tasks, "42").mkdir(); File(tasks, "42/stat").writeText(stat())
        File(tasks, "43").mkdir() // thread exited before its stat was read
        File(tasks, "not-a-tid").mkdir()
        var clock = 0L
        val sample = HostThreadCpuReader(100, tasks) { clock += 2_000_000; clock }.read()
        assertEquals(1, sample.rows.size)
        assertEquals(1, sample.unreadable)
        assertEquals(2_000_000L, sample.readNs)
        assertFalse(sample.unavailable)
        val p = HostThreadCpuProfiler(); p.sample(sample)
        assertEquals(2.0, p.snapshot()!!.readTotalMs, 1e-9)
        assertTrue(HostThreadCpuReader(100, File(tasks, "absent")).read().unavailable)
    }

    @Test fun readerAndReportAreBounded() {
        val tasks = folder.newFolder("many")
        for (tid in 1..130) { File(tasks, "$tid").mkdir(); File(tasks, "$tid/stat").writeText(stat(tid = tid)) }
        val s = HostThreadCpuReader(100, tasks).read()
        assertEquals(128, s.rows.size)
        assertTrue(s.truncated)
        val p = HostThreadCpuProfiler()
        p.sample(s.copy(atNs = 0))
        p.sample(s.copy(atNs = 5_000_000_000, rows = s.rows.map { it.copy(cpuTicks = it.cpuTicks + 100) }))
        assertEquals(32, p.snapshot()!!.threads.size)
    }

    @Test fun reportRoundTripsAndOldPerformanceReportsRemainReadable() {
        val p = HostThreadCpuProfiler()
        p.sample(sample(0, row(0))); p.sample(sample(5, row(250)))
        val acc = RunPerformanceAccumulator(); acc.hostThreads(p.snapshot())
        val json = Json { encodeDefaults = true }
        val perf = acc.snapshot()
        assertEquals(perf, json.decodeFromString(RunPerformance.serializer(), json.encodeToString(RunPerformance.serializer(), perf)))
        assertNull(json.decodeFromString(RunPerformance.serializer(), "{\"version\":1}").hostThreadCpu)
        assertNull(HostThreadCpuProfiler().snapshot())
    }
}
