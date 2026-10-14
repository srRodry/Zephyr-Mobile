package xendroid.compose.sessions

import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xendroid.compose.compatibility.CompatStatus
import xendroid.compose.compatibility.CompatibilityReport

class RunReportTest {
    @Test fun jitCompileCountersAndTheirLimitsAreVisibleBeforeSharing() {
        val jit = JitCompilationReport(translations = 6, failedTranslations = 1,
            summedWallMs = 172.5, timeHistogram = List(6) { 1L })
        val report = RunReports.build(run.copy(performance = RunPerformance(jitCompilation = jit)), null, emptyList(), device, 0)
        val lines = RunReports.preview(report)
        assertTrue(lines.any { it.contains("6 translations, 1 failed") && it.contains("172.500 ms") })
        assertTrue(lines.any { it.contains("not guest execution or frame stall time") })
        assertTrue(lines.any { it.contains("<1/<4/<16.7/<33.3/<100/>=100 ms: 1/1/1/1/1/1") })
        assertEquals(jit, report.run.performance!!.jitCompilation)
    }

    @Test fun hostThreadNamesAreRedactedAndEveryReportedThreadIsPreviewed() {
        val threads = (1..12).map { tid ->
            HostThreadCpuUsage(tid, 99, if (tid == 1) "me@example.com" else "Guest CPU $tid", 2, 50.0, 60.0)
        }
        val cpu = HostThreadCpuReport(attempts = 3, threads = threads)
        val report = RunReports.build(run.copy(performance = RunPerformance(hostThreadCpu = cpu)), null, emptyList(), device, 0)
        assertEquals("[email]", report.run.performance!!.hostThreadCpu!!.threads.first().name)
        assertEquals("me@example.com", cpu.threads.first().name) // the private source is unchanged
        val lines = RunReports.preview(report)
        assertTrue(lines.any { it.contains("percentages relative to one core") })
        assertTrue(lines.any { it.contains("Guest CPU 12 (TID 12)") })
        assertTrue(lines.none { it.contains("me@example.com") })
    }

    @get:Rule val folder = TemporaryFolder()

    private val device = ReportDevice("Xiaomi", "POCO F7", "QTI SM8735", "15", 35, "77011a0c+local.abc-debug")
    private val run = SessionRun(
        runId = "0f8fad5b-d9cb-469f-a165-70867728950e", state = RunState.FAILED, launchSource = "library",
        gamePath = "/storage/emulated/0/Games/Forza Horizon (Pedro's copy).iso", buildVersion = "77011a0c+local.abc-debug",
        pid = 4242, startedAt = 1_000, titleId = "4D5309C9", runningAt = 2_000, lastSeenAt = 62_000, endedAt = 62_000,
        endReason = "fatal error: could not open /storage/emulated/0/Android/data/x/cache.bin",
        performance = RunPerformance(fpsHistogram = List(31) { if (it == 30) 60 else 0 }),
        profileXuid = "E03000002B7C4D1A",
    )
    private val events = RunEventLog(events = listOf(
        RunEvent(0, "boot", "run started"),
        RunEvent(5_000, "error", "pedro@example.com wrote to /sdcard/notes.txt"),
    ))
    private val notes = listOf(CompatibilityReport(CompatStatus.PLAYABLE, "ok, ask me at pedro@example.com", "b", "g", createdAt = 1))

    @Test fun theShareCopyKeepsTheFormatButNoPathsOrAddresses() {
        val report = RunReports.build(run, events, notes, device, now = 99)
        assertEquals("[game file].iso", report.run.gamePath)
        assertEquals("fatal error: could not open [storage-path]", report.run.endReason)
        assertEquals("[email] wrote to [storage-path]", report.events!!.events[1].detail)
        assertEquals("ok, ask me at [email]", report.compatibility.single().note)
        assertEquals("4D5309C9", report.run.titleId)
        assertEquals(null, report.run.profileXuid)                          // who played stays on the device
        // Unknown formats and content URIs keep no name either.
        assertEquals("[game file]", RunReports.build(run.copy(gamePath = "/x/game"), null, emptyList(), device, 0).run.gamePath)
        assertEquals("[game file].zar", RunReports.build(run.copy(
            gamePath = "content://com.android.externalstorage.documents/document/primary%3AGames%2FForza.ZAR"), null, emptyList(), device, 0)
            .run.gamePath)
    }

    @Test fun thePreviewNamesEverythingTheReportCarries() {
        val lines = RunReports.preview(RunReports.build(run, events, notes, device, now = 99))
        assertTrue(lines.first().startsWith("Device: Xiaomi POCO F7 · QTI SM8735 · Android 15 (API 35)"))
        assertTrue(lines.any { it.startsWith("Game: Title ID 4D5309C9 · [game file].iso") })
        assertTrue(lines.any { it == "Timeline: 2 events" })
        assertTrue(lines.any { it == "Your compatibility results: 1 (with your notes)" })
        assertTrue(lines.none { "Pedro" in it || "/storage" in it })
        assertTrue(lines.none { it.startsWith("Settings") })               // an older run: not recorded
    }

    @Test fun aNativeCrashShowsItsBacktraceWithTheFreeTextRedacted() {
        val crash = NativeBacktrace(thread = "GPU Commands (tid 4321)", signal = "SIGABRT (SI_TKILL)",
            abortMessage = "could not open /storage/emulated/0/Games/save.bin",
            frames = listOf("#00 pc 000000000004f5c8  libc.so (abort+168)", "#01 pc 00000000001a2b3c  libe.so (_ZN2xe3FooEv+40)"))
        val report = RunReports.build(run.copy(nativeBacktrace = crash), events, notes, device, now = 99)
        assertEquals("could not open [storage-path]", report.run.nativeBacktrace!!.abortMessage)
        val lines = RunReports.preview(report)
        val head = lines.indexOf("Native crash: GPU Commands (tid 4321) · SIGABRT (SI_TKILL)")
        assertTrue(head > 0)
        assertEquals("  abort: could not open [storage-path]", lines[head + 1])
        assertEquals("  #00 pc 000000000004f5c8  libc.so (abort+168)", lines[head + 2])
        assertTrue(RunReports.preview(RunReports.build(run, events, notes, device, 99)).none { it.startsWith("Native crash") })
    }

    @Test fun theBootsChangedSettingsAreListedAndRedacted() {
        val settings = listOf("GPU|framerate_limit = 30 (default 60) · this game",
            "Network|api_address = \"10.0.0.7:36000\" (default \"127.0.0.1:36000\")",
            "(more settings changed, not listed)")
        val report = RunReports.build(run.copy(changedSettings = settings), events, notes, device, now = 99)
        assertEquals("Network|api_address = \"[ip]:36000\" (default \"[ip]:36000\")", report.run.changedSettings!![1])
        val lines = RunReports.preview(report)
        val head = lines.indexOf("Settings changed from defaults: 2")
        assertTrue(head > 0)
        assertEquals("  GPU|framerate_limit = 30 (default 60) · this game", lines[head + 1])
        assertEquals("  (more settings changed, not listed)", lines[head + 3])
        assertTrue(RunReports.preview(RunReports.build(run.copy(changedSettings = emptyList()), null, emptyList(), device, 0))
            .contains("Settings: all at the core's defaults"))
    }

    @Test fun theZipHoldsTheJsonAndTheTimeline() {
        val report = RunReports.build(run, events, notes, device, now = 99)
        val zip = RunReports.write(folder.root.resolve("share/run.zip"), report)
        ZipFile(zip).use { file ->
            assertEquals(setOf("run-report.json", "timeline.txt"), file.entries().asSequence().map { it.name }.toSet())
            val text = file.getInputStream(file.getEntry("run-report.json")).readBytes().toString(Charsets.UTF_8)
            assertEquals(report, Json.decodeFromString(RunReport.serializer(), text))
            val timeline = file.getInputStream(file.getEntry("timeline.txt")).readBytes().toString(Charsets.UTF_8)
            assertTrue(timeline.contains("0:05 error · [email] wrote to [storage-path]"))
        }
        assertFalse(folder.root.resolve("share/.run.zip.tmp").exists())
    }
}
