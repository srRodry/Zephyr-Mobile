package xendroid.compose.sessions

import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/** Translation/optimization/assembly wall time, summed across compiler workers.
 * Not CPU execution time, frame stall duration or a measure of runtime JIT code cost.
 * Histogram bounds: <1, <4, <16.7, <33.3, <100, >=100 milliseconds. */
@Serializable
data class JitCompilationReport(
    val version: Int = 1,
    val translations: Long = 0,
    val failedTranslations: Long = 0,
    val summedWallMs: Double = 0.0,
    val timeHistogram: List<Long> = emptyList(),
)

/**
 * Performance summary of one run (C02), built from the host's one-second samples
 * and the core's per-frame counters. What it is and is not:
 *  - the FPS figures are the distribution of 1-second guest averages, NOT per-frame
 *    percentiles or "1% lows";
 *  - the frame-time histogram IS per guest frame (present-to-present, 1 ms buckets),
 *    but a gap over one second (pause, loading) restarts the timing and is left out;
 *  - pipeline creation time includes pipeline cache hits, so it is time the GPU thread
 *    spent creating pipelines, not only driver compiles;
 *  - present submissions are frames accepted by the compositor, NOT measured scanout;
 *  - seconds while the guest was paused or produced no new frame are left out;
 *  - audio underruns are blocks the output had to conceal because the emulator had not
 *    produced them in time; device xruns are counted separately, and both are absent
 *    (never zero) when no audio output ran.
 */
@Serializable
data class RunPerformance(
    val version: Int = 1,
    /** Sampled running seconds by rounded guest FPS; index = FPS, the last bucket also holds anything above it. */
    val fpsHistogram: List<Int> = emptyList(),
    /** Seconds in which the guest was paused, in the background or produced no frame. */
    val idleSeconds: Int = 0,
    val presentSubmissions: Long = 0,
    val syntheticSubmissions: Long = 0,
    val frameGenerationSeconds: Int = 0,
    val batteryStartC: Float? = null,
    val batteryMaxC: Float? = null,
    val batteryEndC: Float? = null,
    /** Guest frames of this run by frame time: index = whole milliseconds, the last bucket
     *  holds everything from its index up (native GuestFrameTimeHistogram deltas). */
    val frameTimeHistogramMs: List<Long> = emptyList(),
    /** Graphics pipelines created during this run (driver compiles and pipeline cache hits)
     *  and the time spent creating them; null in records from before it was measured. */
    val pipelineCreations: Long? = null,
    val pipelineCreationMs: Long? = null,
    /** Seconds from the game process starting to the first guest frames (1 s resolution). */
    val firstFrameSeconds: Int? = null,
    /** Audio output of this run: backend ("AAudio" / "OpenSL ES"), blocks the device played
     *  after the guest's first one, blocks concealed because the emulator was late (heard as
     *  dropouts or crackle) and the stream's own xruns (AAudio only). Null when no audio
     *  output ran or it was not measured. */
    val audioBackend: String? = null,
    val audioBlocks: Long? = null,
    val audioConcealedBlocks: Long? = null,
    val audioDeviceXruns: Long? = null,
    /** Frame generation (F08); empty/null in runs without it or from before it was measured.
     *  GPU time per timed generation pass in 0.25 ms buckets (the last one holds 16 ms and
     *  more; LSFG: the source frame's processing plus its first generated frame), passes that
     *  could not be timed, synthetic outputs skipped because their display slot had passed,
     *  and guest frames the generation thread replaced by a newer one before showing them. */
    val generationGpuHistogram: List<Long> = emptyList(),
    val generationGpuUntimed: Long? = null,
    val lateSyntheticSkips: Long? = null,
    val replacedGuestFrames: Long? = null,
    /** Synthetic output slots the schedule offered (F02): late skips + painted; of the painted
     *  ones, [syntheticSubmissions] became synthetic frames on screen. */
    val syntheticSlots: Long? = null,
    /** Pacing of the sampled seconds (C07): FPS limits (0 = unlimited) and display refresh
     *  rates (rounded Hz) in the order first seen, more than one = it changed during the run;
     *  and whether the guest vblank was capped at 50/60 Hz (the config the run booted with,
     *  null = not known). Empty/null in older records. */
    val fpsLimits: List<Int> = emptyList(),
    val displayHz: List<Int> = emptyList(),
    val guestRefreshCap: Boolean? = null,
    /** Diagnostic test builds only; CPU time per host thread, not GPU execution time. */
    val hostThreadCpu: HostThreadCpuReport? = null,
    val jitCompilation: JitCompilationReport? = null,
) {
    val sampledSeconds: Int get() = fpsHistogram.sum()
    val frames: Long get() = frameTimeHistogramMs.sum()
    val timedGenerations: Long get() = generationGpuHistogram.sum()

    /**
     * Upper bound (ms) that [fraction] of the timed generation passes stayed under;
     * [GENERATION_GPU_OPEN_MS] means "that long or longer".
     */
    fun generationGpuUpperMs(fraction: Double): Double? {
        val total = timedGenerations
        if (total == 0L) return null
        val wanted = ceil(fraction.coerceIn(0.0, 1.0) * total).toLong().coerceAtLeast(1)
        var seen = 0L
        var bucket = 0
        while (bucket < generationGpuHistogram.lastIndex) {
            seen += generationGpuHistogram[bucket]
            if (seen >= wanted) break
            bucket++
        }
        val upper = (bucket + 1) * GENERATION_GPU_BUCKET_MS
        return if (bucket >= GENERATION_GPU_OPEN_BUCKET) GENERATION_GPU_OPEN_MS else upper
    }

    /**
     * Upper bound (ms) that [fraction] of this run's guest frames stayed under:
     * frameTimeUpperMs(0.99) = 40 means 99% of frames took less than 40 ms.
     * [FRAME_TIME_OPEN_BUCKET] means "that long or longer" (the open last bucket).
     */
    fun frameTimeUpperMs(fraction: Double): Int? {
        val total = frames
        if (total == 0L) return null
        val wanted = ceil(fraction.coerceIn(0.0, 1.0) * total).toLong().coerceAtLeast(1)
        var seen = 0L
        var ms = 0
        while (ms < frameTimeHistogramMs.lastIndex) {
            seen += frameTimeHistogramMs[ms]
            if (seen >= wanted) break
            ms++
        }
        return if (ms >= FRAME_TIME_OPEN_BUCKET) FRAME_TIME_OPEN_BUCKET else ms + 1
    }

    /** The FPS that [fraction] of the sampled seconds stay at or below (0.5 = median). */
    fun fpsPercentile(fraction: Double): Int? {
        val total = sampledSeconds
        if (total == 0) return null
        val wanted = ceil(fraction.coerceIn(0.0, 1.0) * total).toInt().coerceAtLeast(1)
        var seen = 0
        fpsHistogram.forEachIndexed { fps, count ->
            seen += count
            if (seen >= wanted) return fps
        }
        return fpsHistogram.lastIndex
    }

    /** Compositor submissions per sampled second (guest + synthetic frames). */
    val presentRate: Double? get() = sampledSeconds.takeIf { it > 0 }?.let { presentSubmissions.toDouble() / it }

    companion object {
        /** Index of the native histogram's open "this long or longer" bucket. */
        const val FRAME_TIME_OPEN_BUCKET = 250
        /** Native generation GPU histogram (presentation_runtime.h kGenerationGpuBuckets). */
        const val GENERATION_GPU_BUCKET_MS = 0.25
        const val GENERATION_GPU_OPEN_BUCKET = 64
        const val GENERATION_GPU_OPEN_MS = 16.0
    }
}

/**
 * "120 synthetic frames over 30 s (of 150 slots: 12 skipped late, 18 painted without a
 * generated frame) · GPU per generation pass: median under 3.25 ms, 95th under 4.50 ms, 99th
 * under 6.00 ms (118 timed, 2 not timed) · 2 guest frames replaced before being shown", or
 * null in a run without frame generation.
 */
fun describeFrameGeneration(perf: RunPerformance): String? {
    val untimed = perf.generationGpuUntimed ?: 0L
    val late = perf.lateSyntheticSkips ?: 0L
    val replaced = perf.replacedGuestFrames ?: 0L
    val slots = perf.syntheticSlots ?: 0L
    val timed = perf.timedGenerations
    if (perf.frameGenerationSeconds == 0 && perf.syntheticSubmissions == 0L && timed == 0L && untimed == 0L &&
        late == 0L && replaced == 0L && slots == 0L) return null
    var head = "${perf.syntheticSubmissions} synthetic frames over ${perf.frameGenerationSeconds} s"
    if (slots > 0) {
        // Painted slots that did not reach the screen as a synthetic frame: warm-up, fallback
        // to the real frame, a failed presentation or a surface that was not paintable.
        val ungenerated = (slots - late - perf.syntheticSubmissions).coerceAtLeast(0)
        val detail = listOfNotNull(late.takeIf { it > 0 }?.let { "$it skipped late" },
            ungenerated.takeIf { it > 0 }?.let { "$it painted without a generated frame" })
        head += " (of $slots slots" + (if (detail.isEmpty()) "" else ": " + detail.joinToString(", ")) + ")"
    }
    val parts = mutableListOf(head)
    fun bound(fraction: Double): String {
        val ms = perf.generationGpuUpperMs(fraction) ?: return "?"
        return if (ms >= RunPerformance.GENERATION_GPU_OPEN_MS) "%.0f ms or more".format(java.util.Locale.ROOT, ms)
        else "under %.2f ms".format(java.util.Locale.ROOT, ms)
    }
    when {
        timed > 0 -> parts += "GPU per generation pass: median ${bound(0.5)}, 95th ${bound(0.95)}, 99th ${bound(0.99)} " +
            "($timed timed" + (if (untimed > 0) ", $untimed not timed" else "") + ")"
        untimed > 0 -> parts += "GPU time not measured ($untimed passes could not be timed)"
    }
    if (late > 0 && slots == 0L) parts += "$late late outputs skipped"
    if (replaced > 0) parts += "$replaced guest frames replaced before being shown"
    return parts.joinToString(" · ")
}

/**
 * Turns the per-second growth of a cumulative counter into flight recorder bursts: a
 * second in which [amount][sample] grew by [threshold] or more starts or extends a
 * burst, and [quietSeconds] seconds in a row below it end it (so sporadic spikes make
 * one burst, not one pair of events each). [events][sample] is summed alongside:
 * pipelines next to creation time, blocks next to concealed audio blocks. Feed it
 * once per second with the totals.
 */
class BurstTracker(private val threshold: Long, private val quietSeconds: Int = 1) {
    sealed interface Burst {
        data object Started : Burst
        /** [seconds] counts only the busy seconds; [events] covers the whole burst. */
        data class Ended(val seconds: Int, val amount: Long, val events: Long) : Burst
    }

    private var lastAmount: Long? = null
    private var lastEvents: Long? = null
    private var busySeconds = 0
    private var quiet = 0
    private var amount = 0L
    private var events = 0L

    fun sample(amountTotal: Long, eventsTotal: Long): Burst? {
        val previousAmount = lastAmount
        val previousEvents = lastEvents
        lastAmount = amountTotal
        lastEvents = eventsTotal
        if (previousAmount == null || previousEvents == null) return null
        val amountDelta = (amountTotal - previousAmount).coerceAtLeast(0)
        val eventsDelta = (eventsTotal - previousEvents).coerceAtLeast(0)
        if (amountDelta >= threshold) {
            busySeconds++
            quiet = 0
            amount += amountDelta
            events += eventsDelta
            return if (busySeconds == 1) Burst.Started else null
        }
        if (busySeconds == 0) return null
        events += eventsDelta
        if (++quiet < quietSeconds) return null
        return Burst.Ended(busySeconds, amount, events).also { busySeconds = 0; quiet = 0; amount = 0; events = 0 }
    }
}

/** "AAudio: 32 of 8000 blocks concealed (0.4%), 2 device xruns", or null without audio output. */
fun describeAudio(perf: RunPerformance): String? {
    val backend = perf.audioBackend ?: return null
    val blocks = perf.audioBlocks ?: return null
    val concealed = perf.audioConcealedBlocks ?: return null
    if (blocks == 0L) return "$backend: no guest audio played"
    val share = "%.1f".format(java.util.Locale.ROOT, 100.0 * concealed / blocks)
    val underruns = if (concealed == 0L) "no underruns in $blocks blocks" else "$concealed of $blocks blocks concealed ($share%)"
    return "$backend: $underruns" + (perf.audioDeviceXruns?.let { ", $it device xruns" } ?: "")
}

/** "99th percentile under 34 ms, 99.9th 250 ms or more (N frames)", or null without frames. */
fun describeFrameTimes(perf: RunPerformance): String? {
    val p99 = perf.frameTimeUpperMs(0.99) ?: return null
    val p999 = perf.frameTimeUpperMs(0.999) ?: return null
    fun bound(ms: Int) =
        if (ms >= RunPerformance.FRAME_TIME_OPEN_BUCKET) "$ms ms or more" else "under $ms ms"
    return "99th percentile ${bound(p99)}, 99.9th ${bound(p999)} (${perf.frames} frames)"
}

/** Collects [RunPerformance] in the host process; one [sample] call per second. Not thread-safe. */
class RunPerformanceAccumulator(private val maxFps: Int = 240) {
    private val histogram = IntArray(maxFps + 1)
    private var idle = 0
    private var lastPresents: Long? = null
    private var lastGenerated: Long? = null
    private var lastGuestFrames: Long? = null
    private var presents = 0L
    private var synthetic = 0L
    private var fgSeconds = 0
    private var batteryStart: Float? = null
    private var batteryMax: Float? = null
    private var batteryEnd: Float? = null
    private var frameBaseline: LongArray? = null
    private var frameLatest: LongArray? = null
    private var compileBaseline: LongArray? = null
    private var compileLatest: LongArray? = null
    private val jitCompiles = JitCompilationAccumulator()
    private var firstFrameSeconds: Int? = null
    private var audioBaseline: LongArray? = null
    private var audioLatest: LongArray? = null
    private var generationBaseline: LongArray? = null
    private var generationLatest: LongArray? = null
    private var fgCountersBaseline: LongArray? = null
    private var fgCountersLatest: LongArray? = null
    private val fpsLimits = LinkedHashSet<Int>()
    private val displayHz = LinkedHashSet<Int>()
    private var guestRefreshCap: Boolean? = null
    private var hostThreadCpu: HostThreadCpuReport? = null

    fun hostThreads(report: HostThreadCpuReport?) { hostThreadCpu = report }

    /** The guest vblank cap the run booted with (C07). */
    fun guestRefreshCap(capped: Boolean) { guestRefreshCap = capped }

    /**
     * Cumulative native frame-generation figures: the GPU histogram with the untimed count
     * as its last element (Emulator.frame_generation_gpu_histogram), late synthetic skips and
     * replaced guest frames (presentation state). The first call is the run's baseline.
     */
    fun frameGeneration(gpuHistogram: LongArray?, lateSkips: Long, replacedGuestFrames: Long, syntheticSlots: Long = 0) {
        if (gpuHistogram != null && gpuHistogram.size >= 2) {
            val baseline = generationBaseline
            if (baseline == null || baseline.size != gpuHistogram.size) generationBaseline = gpuHistogram.copyOf()
            generationLatest = gpuHistogram.copyOf()
        }
        val counters = longArrayOf(lateSkips, replacedGuestFrames, syntheticSlots)
        if (fgCountersBaseline == null) fgCountersBaseline = counters
        fgCountersLatest = counters
    }

    private fun generationDeltas(): List<Long>? {
        val latest = generationLatest ?: return null
        val baseline = generationBaseline ?: return null
        return latest.indices.map { (latest[it] - baseline[it]).coerceAtLeast(0) }
    }

    private fun fgCounterDelta(index: Int): Long? {
        val latest = fgCountersLatest ?: return null
        val baseline = fgCountersBaseline ?: return null
        return (latest[index] - baseline[index]).coerceAtLeast(0)
    }

    /** Time to the first guest frames; only the first call counts. */
    fun firstFrame(seconds: Int) {
        if (firstFrameSeconds == null && seconds >= 0) firstFrameSeconds = seconds
    }

    /** Cumulative native {backend, blocks, concealed, device xruns}; the first call is the baseline. */
    fun audio(stats: LongArray?) {
        if (stats == null || stats.size < 4) return
        if (audioBaseline == null) audioBaseline = stats.copyOf(4)
        audioLatest = stats.copyOf(4)
    }

    private fun audioDelta(index: Int): Long? {
        val latest = audioLatest ?: return null
        val baseline = audioBaseline ?: return null
        if (latest[0] == 0L) return null       // no audio output ran
        return (latest[index] - baseline[index]).coerceAtLeast(0)
    }

    /** Cumulative native {pipeline creations, ns spent}; the first call is the run's baseline. */
    fun compiles(stats: LongArray?) {
        if (stats == null || stats.size < 2) return
        if (compileBaseline == null) compileBaseline = stats.copyOf(2)
        compileLatest = stats.copyOf(2)
        jitCompiles.sample(stats)
    }

    /** Cumulative native frame-time counts; the first call is the run's baseline. */
    fun frameTimes(counts: LongArray?) {
        if (counts == null || counts.isEmpty()) return
        val baseline = frameBaseline
        if (baseline == null || baseline.size != counts.size) frameBaseline = counts.copyOf()
        frameLatest = counts.copyOf()
    }

    /**
     * [running]: guest not paused and the app in the foreground. [presentCount],
     * [generatedCount] and [guestFrames] are process-wide monotonic counters; the first
     * call only establishes them. The guest FPS keeps its last value while no guest
     * frame arrives, so a second without a new guest frame ([guestFrames]; host
     * submissions when it is unknown) is idle whatever [guestFps] says.
     */
    fun sample(running: Boolean, guestFps: Double, presentCount: Long, generatedCount: Long, frameGenerationActive: Boolean,
               guestFrames: Long? = null, fpsLimit: Int? = null, displayHz: Float? = null) {
        val previousPresents = lastPresents
        val previousGenerated = lastGenerated
        val previousFrames = lastGuestFrames
        lastPresents = presentCount
        lastGenerated = generatedCount
        lastGuestFrames = guestFrames
        if (previousPresents == null || previousGenerated == null) return
        val presentDelta = (presentCount - previousPresents).coerceAtLeast(0)
        val generatedDelta = (generatedCount - previousGenerated).coerceAtLeast(0)
        val newFrames = if (guestFrames != null && previousFrames != null) guestFrames - previousFrames > 0 else presentDelta > 0
        if (!running || !newFrames || !guestFps.isFinite()) {
            idle++
            return
        }
        histogram[guestFps.roundToInt().coerceIn(0, maxFps)]++
        // The pacing of counted seconds only: a limit changed in the menu while paused is
        // recorded once it applies.
        if (fpsLimit != null && fpsLimit >= 0 && fpsLimits.size < MAX_PACING_VALUES) fpsLimits += fpsLimit
        if (displayHz != null && displayHz.isFinite() && displayHz > 0f && this.displayHz.size < MAX_PACING_VALUES) {
            this.displayHz += displayHz.roundToInt()
        }
        presents += presentDelta
        synthetic += generatedDelta
        if (frameGenerationActive) fgSeconds++
    }

    fun battery(celsius: Float?) {
        if (celsius == null || !celsius.isFinite()) return
        if (batteryStart == null) batteryStart = celsius
        batteryMax = maxOf(batteryMax ?: celsius, celsius)
        batteryEnd = celsius
    }

    fun snapshot(): RunPerformance {
        val generation = generationDeltas()
        val late = fgCounterDelta(0)
        val replaced = fgCounterDelta(1)
        val slots = fgCounterDelta(2)
        // A run that never generated keeps these empty rather than a row of zeros.
        val generated = fgSeconds > 0 || synthetic > 0 || (generation?.any { it > 0 } ?: false) ||
            (late ?: 0) > 0 || (replaced ?: 0) > 0 || (slots ?: 0) > 0
        return base().copy(
            generationGpuHistogram = if (generated) generation?.dropLast(1)?.dropLastWhile { it == 0L }.orEmpty() else emptyList(),
            generationGpuUntimed = if (generated) generation?.lastOrNull() else null,
            lateSyntheticSkips = if (generated) late else null,
            replacedGuestFrames = if (generated) replaced else null,
            syntheticSlots = if (generated) slots else null,
        )
    }

    private fun base(): RunPerformance = RunPerformance(
        fpsHistogram = histogram.toList().dropLastWhile { it == 0 },
        idleSeconds = idle,
        presentSubmissions = presents,
        syntheticSubmissions = synthetic,
        frameGenerationSeconds = fgSeconds,
        batteryStartC = batteryStart,
        batteryMaxC = batteryMax,
        batteryEndC = batteryEnd,
        frameTimeHistogramMs = frameDeltas(),
        pipelineCreations = compileDelta(0),
        pipelineCreationMs = compileDelta(1)?.let { it / 1_000_000 },
        firstFrameSeconds = firstFrameSeconds,
        audioBackend = when (audioLatest?.get(0)) { 1L -> "AAudio"; 2L -> "OpenSL ES"; else -> null },
        audioBlocks = audioDelta(1),
        audioConcealedBlocks = audioDelta(2),
        // OpenSL ES reports no xruns: absent, not zero.
        audioDeviceXruns = audioDelta(3)?.takeIf { audioLatest?.get(0) == 1L },
        fpsLimits = fpsLimits.toList(),
        displayHz = displayHz.toList(),
        guestRefreshCap = guestRefreshCap,
        hostThreadCpu = hostThreadCpu,
        jitCompilation = jitCompiles.snapshot(),
    )

    private fun compileDelta(index: Int): Long? {
        val latest = compileLatest ?: return null
        val baseline = compileBaseline ?: return null
        return (latest[index] - baseline[index]).coerceAtLeast(0)
    }

    private fun frameDeltas(): List<Long> {
        val latest = frameLatest ?: return emptyList()
        val baseline = frameBaseline ?: return emptyList()
        return latest.indices.map { (latest[it] - baseline[it]).coerceAtLeast(0) }.dropLastWhile { it == 0L }
    }

    private companion object {
        /** Distinct FPS limits / refresh rates kept per run: more says "it kept changing". */
        const val MAX_PACING_VALUES = 8
    }
}

/** Optional suffix of native shader_compile_stats; a coherent process snapshot.
 * First reading establishes baseline. A process reset renews it and keeps already
 * collected deltas. Missing/legacy snapshots never invent zero JIT timings. */
internal class JitCompilationAccumulator {
    private var previous: LongArray? = null
    private val collected = LongArray(9)
    fun sample(stats: LongArray) {
        if (stats.size < 12) return
        val now = stats.copyOfRange(3, 12)
        if (now.any { it < 0 } || now[2] > now[0]) return
        val histogramTotal = try { now.drop(3).fold(0L) { sum, count -> Math.addExact(sum, count) } } catch (_: ArithmeticException) { return }
        if (histogramTotal != now[0]) return
        val before = previous
        previous = now
        if (before == null || now.indices.any { now[it] < before[it] }) return
        val deltas = LongArray(9) { now[it] - before[it] }
        // Reject an impossible/wrapped aggregate without partially mutating it.
        val next = try { LongArray(9) { Math.addExact(collected[it], deltas[it]) } }
                   catch (_: ArithmeticException) { return }
        next.copyInto(collected)
    }
    fun snapshot(): JitCompilationReport? = previous?.let {
        JitCompilationReport(translations = collected[0], failedTranslations = collected[2],
            summedWallMs = collected[1] / 1_000_000.0, timeHistogram = collected.drop(3))
    }
}
