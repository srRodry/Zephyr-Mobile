package xendroid.compose.sessions

import org.junit.Assert.*
import org.junit.Test

class RunPerformanceTest {
    private fun jitStats(count: Long, ns: Long, failed: Long, vararg histogram: Long): LongArray =
        longArrayOf(10, 20_000_000, 0, count, ns, failed, *histogram)

    @Test fun jitCompilationUsesOptionalNativeSuffixAndNanosecondUnits() {
        val acc = RunPerformanceAccumulator()
        acc.compiles(longArrayOf(10, 20_000_000, 0)) // older library
        assertNull(acc.snapshot().jitCompilation)
        val baseline = jitStats(100, 200_000_000, 2, 0, 100, 0, 0, 0, 0)
        acc.compiles(baseline)
        baseline[3] = 9999 // caller mutation cannot alter baseline
        acc.compiles(jitStats(106, 372_500_000, 3, 1, 101, 1, 1, 1, 1))
        val report = acc.snapshot().jitCompilation!!
        assertEquals(6L, report.translations)
        assertEquals(1L, report.failedTranslations)
        assertEquals(172.5, report.summedWallMs, 0.000001)
        assertEquals(List(6) { 1L }, report.timeHistogram)
        assertEquals(0L, acc.snapshot().pipelineCreations) // legacy prefix remains independent
    }

    @Test fun jitProcessResetKeepsPreviousSegmentWithoutFabricatedSpikes() {
        val acc = RunPerformanceAccumulator()
        acc.compiles(jitStats(100, 200_000_000, 2, 0, 100, 0, 0, 0, 0))
        acc.compiles(jitStats(106, 372_500_000, 3, 1, 101, 1, 1, 1, 1))
        acc.compiles(jitStats(1, 5_000_000, 0, 0, 0, 1, 0, 0, 0))
        acc.compiles(jitStats(2, 15_000_000, 0, 0, 0, 2, 0, 0, 0))
        val report = acc.snapshot().jitCompilation!!
        assertEquals(7L, report.translations)
        assertEquals(182.5, report.summedWallMs, 0.000001)
        assertEquals(listOf(1L, 1L, 2L, 1L, 1L, 1L), report.timeHistogram)
    }

    @Test fun malformedJitCountersStayAbsentAndOldReportsRemainReadable() {
        val acc = RunPerformanceAccumulator()
        acc.compiles(jitStats(2, 1, 3, 2, 0, 0, 0, 0, 0)) // failures exceed translations
        acc.compiles(jitStats(2, 1, 0, 1, 0, 0, 0, 0, 0)) // incoherent histogram
        acc.compiles(jitStats(2, -1, 0, 2, 0, 0, 0, 0, 0))
        assertNull(acc.snapshot().jitCompilation)
        val json = kotlinx.serialization.json.Json
        val old = json.decodeFromString(RunPerformance.serializer(), "{}")
        assertNull(old.jitCompilation)
        val report = old.copy(jitCompilation = JitCompilationReport(translations = 1, summedWallMs = 3.5, timeHistogram = listOf(0,1,0,0,0,0)))
        assertEquals(report, json.decodeFromString(RunPerformance.serializer(), json.encodeToString(RunPerformance.serializer(), report)))
    }

    @Test fun onlyRunningSecondsWithNewFramesBecomeFpsSamples() {
        val acc = RunPerformanceAccumulator()
        acc.sample(running = true, guestFps = 30.0, presentCount = 100, generatedCount = 0, frameGenerationActive = false)
        // First call only establishes the counters.
        assertEquals(0, acc.snapshot().sampledSeconds)
        acc.sample(true, 29.6, presentCount = 130, generatedCount = 0, frameGenerationActive = false)
        acc.sample(true, 30.0, presentCount = 160, generatedCount = 0, frameGenerationActive = false)
        // Paused: the stale average must not count.
        acc.sample(false, 30.0, presentCount = 160, generatedCount = 0, frameGenerationActive = false)
        // Running but no new frame (stalled guest or surface gone): idle too.
        acc.sample(true, 30.0, presentCount = 160, generatedCount = 0, frameGenerationActive = false)
        acc.sample(true, 24.4, presentCount = 184, generatedCount = 0, frameGenerationActive = false)
        val perf = acc.snapshot()
        assertEquals(3, perf.sampledSeconds)
        assertEquals(2, perf.idleSeconds)
        assertEquals(84L, perf.presentSubmissions)
        assertEquals(28.0, perf.presentRate!!, 0.001)
        assertEquals(24, perf.fpsPercentile(0.05))
        assertEquals(30, perf.fpsPercentile(0.5))
        assertEquals(31, perf.fpsHistogram.size)   // trailing empty buckets dropped
    }

    @Test fun pacingIsWhatTheCountedSecondsRanWith() {
        val acc = RunPerformanceAccumulator()
        assertTrue(acc.snapshot().fpsLimits.isEmpty())
        assertNull(acc.snapshot().guestRefreshCap)
        acc.sample(true, 30.0, presentCount = 0, generatedCount = 0, frameGenerationActive = false, fpsLimit = 60, displayHz = 119.88f)
        acc.sample(true, 30.0, presentCount = 30, generatedCount = 0, frameGenerationActive = false, fpsLimit = 60, displayHz = 119.88f)
        // Changed in the menu while paused: recorded only once a counted second ran with it.
        acc.sample(false, 30.0, presentCount = 30, generatedCount = 0, frameGenerationActive = false, fpsLimit = 30, displayHz = 60f)
        acc.sample(true, 30.0, presentCount = 60, generatedCount = 0, frameGenerationActive = false, fpsLimit = 0, displayHz = 120f)
        acc.guestRefreshCap(true)
        val perf = acc.snapshot()
        assertEquals(listOf(60, 0), perf.fpsLimits)
        assertEquals(listOf(120), perf.displayHz)
        assertEquals(true, perf.guestRefreshCap)
    }

    @Test fun guestFramesDecideIdleSecondsWhenKnown() {
        val acc = RunPerformanceAccumulator()
        acc.sample(true, 30.0, presentCount = 0, generatedCount = 0, frameGenerationActive = false, guestFrames = 0)
        acc.sample(true, 30.0, presentCount = 30, generatedCount = 0, frameGenerationActive = false, guestFrames = 30)
        // The host kept submitting (repaints) but the guest produced nothing: its stale 30 FPS must not count.
        acc.sample(true, 30.0, presentCount = 60, generatedCount = 0, frameGenerationActive = false, guestFrames = 30)
        acc.sample(true, 28.0, presentCount = 88, generatedCount = 0, frameGenerationActive = false, guestFrames = 58)
        val perf = acc.snapshot()
        assertEquals(2, perf.sampledSeconds)
        assertEquals(1, perf.idleSeconds)
        assertEquals(58L, perf.presentSubmissions)
    }

    @Test fun frameGenerationAndCounterResetsAreAccounted() {
        val acc = RunPerformanceAccumulator(maxFps = 120)
        acc.sample(true, 30.0, 0, 0, false)
        acc.sample(true, 30.0, 60, 30, true)
        acc.sample(true, 500.0, 120, 60, true)        // clamped into the last bucket
        acc.sample(true, 30.0, 10, 5, false)          // counter went backwards: no negative deltas
        val perf = acc.snapshot()
        assertEquals(60L, perf.syntheticSubmissions)
        assertEquals(2, perf.frameGenerationSeconds)
        assertEquals(120, perf.fpsHistogram.lastIndex)
        assertEquals(2, perf.sampledSeconds)
        assertEquals(1, perf.idleSeconds)
    }

    @Test fun frameTimesAreThisRunsDeltasAndPercentilesAreUpperBounds() {
        val acc = RunPerformanceAccumulator()
        // Native counts are process-wide: whatever was there at the first read is the baseline.
        val counts = LongArray(251)
        counts[16] = 500
        acc.frameTimes(counts)
        counts[16] += 980      // 980 frames of 16.x ms
        counts[33] += 15       // 15 frames of 33.x ms
        counts[49] += 4        // 4 frames of 49.x ms
        counts[250] += 1       // one frame of 250 ms or longer
        acc.frameTimes(counts)
        counts[16] += 1_000    // the accumulator keeps copies: the caller reusing its array changes nothing
        val perf = acc.snapshot()
        assertEquals(1_000L, perf.frames)
        assertEquals(251, perf.frameTimeHistogramMs.size)
        assertEquals(17, perf.frameTimeUpperMs(0.5))
        assertEquals(17, perf.frameTimeUpperMs(0.98))
        assertEquals(34, perf.frameTimeUpperMs(0.99))     // 980 + 15 = 995 >= 990
        assertEquals(50, perf.frameTimeUpperMs(0.999))    // 999 frames under 50 ms
        assertEquals(RunPerformance.FRAME_TIME_OPEN_BUCKET, perf.frameTimeUpperMs(1.0))
        assertEquals("99th percentile under 34 ms, 99.9th under 50 ms (1000 frames)", describeFrameTimes(perf))
    }

    @Test fun frameTimesWithoutFramesOrReadsAreAbsent() {
        val acc = RunPerformanceAccumulator()
        assertTrue(acc.snapshot().frameTimeHistogramMs.isEmpty())
        acc.frameTimes(null)
        acc.frameTimes(LongArray(0))
        val counts = LongArray(251).also { it[20] = 7 }
        acc.frameTimes(counts)
        acc.frameTimes(counts)                            // nothing new since the baseline
        val perf = acc.snapshot()
        assertTrue(perf.frameTimeHistogramMs.isEmpty())   // trailing zeros trimmed
        assertNull(perf.frameTimeUpperMs(0.99))
        assertNull(describeFrameTimes(perf))
        // Mostly long frames: the open bucket is reported as such, never as "under".
        val slow = RunPerformance(frameTimeHistogramMs = List(251) { if (it == 250) 9L else if (it == 40) 1L else 0L })
        assertEquals(41, slow.frameTimeUpperMs(0.1))
        assertEquals(250, slow.frameTimeUpperMs(0.99))
        assertEquals("99th percentile 250 ms or more, 99.9th 250 ms or more (10 frames)", describeFrameTimes(slow))
    }

    @Test fun oldRecordsWithoutFrameTimesStillDecode() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString(RunPerformance.serializer(),
            """{"version":1,"fpsHistogram":[0,2],"idleSeconds":3,"presentSubmissions":4}""")
        assertTrue(old.frameTimeHistogramMs.isEmpty())
        assertEquals(2, old.sampledSeconds)
        assertNull(old.audioBlocks)
        assertNull(old.pipelineCreations)
    }

    @Test fun pipelineCreationsAreThisRunsDeltas() {
        val acc = RunPerformanceAccumulator()
        assertNull(acc.snapshot().pipelineCreations)                  // never read: unknown, not zero
        acc.compiles(longArrayOf(40, 900_000_000, 0))                 // created before this run's first read
        acc.compiles(longArrayOf(52, 2_150_000_000, 1))
        acc.compiles(null)
        val perf = acc.snapshot()
        assertEquals(12L, perf.pipelineCreations)
        assertEquals(1_250L, perf.pipelineCreationMs)
    }

    @Test fun firstFrameTimeIsKeptOnce() {
        val acc = RunPerformanceAccumulator()
        assertNull(acc.snapshot().firstFrameSeconds)
        acc.firstFrame(-1)
        acc.firstFrame(42)
        acc.firstFrame(90)
        assertEquals(42, acc.snapshot().firstFrameSeconds)
    }

    @Test fun compileBurstsStartOnABusySecondAndEndOnAQuietOne() {
        val ms = 1_000_000L
        val tracker = BurstTracker(threshold = 100 * ms)
        assertNull(tracker.sample(0, 0))                               // baseline
        assertNull(tracker.sample(20 * ms, 3))                         // 20 ms: cache hits, not a burst
        assertEquals(BurstTracker.Burst.Started, tracker.sample(420 * ms, 40))
        assertNull(tracker.sample(1_020 * ms, 90))                     // still busy
        assertEquals(BurstTracker.Burst.Ended(seconds = 2, amount = 1_000 * ms, events = 88),
            tracker.sample(1_030 * ms, 91))
        assertNull(tracker.sample(1_030 * ms, 91))
        // A counter that goes backwards (new process) never makes a negative burst.
        assertNull(tracker.sample(0, 0))
    }

    @Test fun sporadicSpikesStayOneBurstUntilEnoughQuietSeconds() {
        val tracker = BurstTracker(threshold = 1, quietSeconds = 3)
        assertNull(tracker.sample(0, 0))
        assertEquals(BurstTracker.Burst.Started, tracker.sample(2, 100))
        assertNull(tracker.sample(2, 200))                             // one quiet second
        assertNull(tracker.sample(3, 300))                             // busy again: same burst
        assertNull(tracker.sample(3, 400))
        assertNull(tracker.sample(3, 500))
        assertEquals(BurstTracker.Burst.Ended(seconds = 2, amount = 3, events = 600), tracker.sample(3, 600))
    }

    @Test fun audioIsThisRunsDeltasAndAbsentWithoutOutput() {
        val acc = RunPerformanceAccumulator()
        acc.audio(longArrayOf(0, 0, 0, 0))                             // no output yet at the first read
        assertNull(describeAudio(acc.snapshot()))
        acc.audio(longArrayOf(1, 8_000, 32, 2))
        val perf = acc.snapshot()
        assertEquals("AAudio", perf.audioBackend)
        assertEquals(8_000L, perf.audioBlocks)
        assertEquals(32L, perf.audioConcealedBlocks)
        assertEquals(2L, perf.audioDeviceXruns)
        assertEquals("AAudio: 32 of 8000 blocks concealed (0.4%), 2 device xruns", describeAudio(perf))

        val opensl = RunPerformanceAccumulator()
        opensl.audio(longArrayOf(2, 100, 5, 0))                        // process-wide counts before this run
        opensl.audio(longArrayOf(2, 600, 5, 0))
        val sl = opensl.snapshot()
        assertNull(sl.audioDeviceXruns)                                // OpenSL ES has none: absent, not zero
        assertEquals("OpenSL ES: no underruns in 500 blocks", describeAudio(sl))
        assertNull(RunPerformanceAccumulator().snapshot().audioBlocks)
    }

    @Test fun frameGenerationGpuTimesAreThisRunsDeltasWithUpperBounds() {
        val acc = RunPerformanceAccumulator()
        // Earlier runs of this process already counted some passes: only this run's count.
        val before = LongArray(66).also { it[10] = 5; it[65] = 1 }
        acc.frameGeneration(before, lateSkips = 2, replacedGuestFrames = 7)
        acc.sample(true, 30.0, presentCount = 0, generatedCount = 0, frameGenerationActive = false, guestFrames = 0)
        acc.sample(true, 30.0, presentCount = 60, generatedCount = 30, frameGenerationActive = true, guestFrames = 30)
        // +100 timed passes (80 at 2.50-2.75 ms, 15 at 3.25-3.50 ms, 5 at 16 ms or more), +3 untimed.
        val after = before.copyOf().also { it[10] += 80; it[13] += 15; it[64] += 5; it[65] += 3 }
        acc.frameGeneration(after, lateSkips = 6, replacedGuestFrames = 9)
        val perf = acc.snapshot()
        assertEquals(100L, perf.timedGenerations)
        assertEquals(3L, perf.generationGpuUntimed)
        assertEquals(4L, perf.lateSyntheticSkips)
        assertEquals(2L, perf.replacedGuestFrames)
        assertEquals(2.75, perf.generationGpuUpperMs(0.5)!!, 1e-9)
        assertEquals(3.5, perf.generationGpuUpperMs(0.95)!!, 1e-9)
        assertEquals(RunPerformance.GENERATION_GPU_OPEN_MS, perf.generationGpuUpperMs(0.99)!!, 1e-9)
        assertEquals("30 synthetic frames over 1 s · GPU per generation pass: median under 2.75 ms, 95th under 3.50 ms, " +
            "99th 16 ms or more (100 timed, 3 not timed) · 4 late outputs skipped · 2 guest frames replaced before being shown",
            describeFrameGeneration(perf))
    }

    @Test fun syntheticSlotsSeparateWhatWasOfferedSkippedAndShown() {
        val acc = RunPerformanceAccumulator()
        acc.frameGeneration(LongArray(66), lateSkips = 1, replacedGuestFrames = 0, syntheticSlots = 10)
        acc.sample(true, 30.0, presentCount = 0, generatedCount = 0, frameGenerationActive = false, guestFrames = 0)
        acc.sample(true, 30.0, presentCount = 60, generatedCount = 30, frameGenerationActive = true, guestFrames = 30)
        acc.frameGeneration(LongArray(66).also { it[8] = 30 }, lateSkips = 5, replacedGuestFrames = 0, syntheticSlots = 160)
        val perf = acc.snapshot()
        assertEquals(150L, perf.syntheticSlots)
        // 150 offered = 4 skipped late + 146 painted, of which 30 reached the screen as synthetic.
        assertEquals("30 synthetic frames over 1 s (of 150 slots: 4 skipped late, 116 painted without a generated frame) · " +
            "GPU per generation pass: median under 2.25 ms, 95th under 2.25 ms, 99th under 2.25 ms (30 timed)",
            describeFrameGeneration(perf))
    }

    @Test fun frameGenerationWithoutTimingsSaysSoAndARunWithoutItShowsNothing() {
        val untimed = RunPerformanceAccumulator()
        untimed.frameGeneration(LongArray(66), 0, 0)
        untimed.frameGeneration(LongArray(66).also { it[65] = 40 }, 0, 0)
        assertEquals("0 synthetic frames over 0 s · GPU time not measured (40 passes could not be timed)",
            describeFrameGeneration(untimed.snapshot()))

        val none = RunPerformanceAccumulator()
        none.frameGeneration(LongArray(66), 0, 0)
        none.sample(true, 30.0, presentCount = 0, generatedCount = 0, frameGenerationActive = false, guestFrames = 0)
        none.sample(true, 30.0, presentCount = 30, generatedCount = 0, frameGenerationActive = false, guestFrames = 30)
        none.frameGeneration(LongArray(66), 0, 0)
        val perf = none.snapshot()
        assertTrue(perf.generationGpuHistogram.isEmpty())
        assertNull(perf.generationGpuUntimed)
        assertNull(perf.lateSyntheticSkips)
        assertNull(perf.replacedGuestFrames)
        assertNull(perf.generationGpuUpperMs(0.5))
        assertNull(describeFrameGeneration(perf))
    }

    @Test fun batteryKeepsStartMaxAndEnd() {
        val acc = RunPerformanceAccumulator()
        acc.battery(null)
        acc.battery(31.5f); acc.battery(38.0f); acc.battery(Float.NaN); acc.battery(36.2f)
        val perf = acc.snapshot()
        assertEquals(31.5f, perf.batteryStartC)
        assertEquals(38.0f, perf.batteryMaxC)
        assertEquals(36.2f, perf.batteryEndC)
        assertNull(perf.fpsPercentile(0.5))
        assertNull(perf.presentRate)
    }
}
