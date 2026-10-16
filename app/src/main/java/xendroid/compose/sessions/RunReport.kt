package xendroid.compose.sessions

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xendroid.compose.compatibility.CompatibilityReport
import xendroid.compose.core.LogRedactor

/** Device facts shown to the user before a run report is shared: model and versions, no identifiers. */
@Serializable
data class ReportDevice(
    val manufacturer: String,
    val model: String,
    val soc: String? = null,
    val android: String,
    val sdk: Int,
    val app: String,
)

/**
 * One run, packaged for sharing (C06, first version): the run record and its
 * performance summary, the flight recorder, the user's compatibility results for the
 * title and the device. Built only when the user asks, shown before it leaves through
 * the Android share sheet (the user picks the destination every time), never uploaded
 * by the app. Raw logs are not included: Diagnostics shares those, redacted.
 */
@Serializable
data class RunReport(
    val version: Int = 1,
    val createdAt: Long,
    val device: ReportDevice,
    val run: SessionRun,
    val events: RunEventLog? = null,
    val compatibility: List<CompatibilityReport> = emptyList(),
)

object RunReports {
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    private val knownFormats = setOf("iso", "zar", "xex", "xcp")

    /**
     * The share copy. The game path becomes its format only (the Title ID already names
     * the game; folders and file names stay on the device); free text (end reason,
     * event details, the user's notes) goes through the log redactor.
     */
    fun build(run: SessionRun, events: RunEventLog?, compatibility: List<CompatibilityReport>,
              device: ReportDevice, now: Long): RunReport {
        val format = run.gamePath.substringAfterLast('/').substringAfterLast('.', "").lowercase()
            .takeIf { it in knownFormats }
        return RunReport(
            createdAt = now,
            device = device,
            run = run.copy(
                gamePath = "[game file]" + (format?.let { ".$it" } ?: ""),
                profileXuid = null,
                endReason = run.endReason?.let(LogRedactor::redact),
                changedSettings = run.changedSettings?.map(LogRedactor::redact),
                performance = run.performance?.let { perf ->
                    perf.copy(hostThreadCpu = perf.hostThreadCpu?.let { cpu ->
                        cpu.copy(threads = cpu.threads.map { it.copy(name = LogRedactor.redact(it.name)) })
                    })
                },
                nativeBacktrace = run.nativeBacktrace?.let { crash ->
                    crash.copy(thread = crash.thread?.let(LogRedactor::redact), cause = crash.cause?.let(LogRedactor::redact),
                        abortMessage = crash.abortMessage?.let(LogRedactor::redact))
                },
            ),
            events = events?.copy(events = events.events.map { it.copy(detail = LogRedactor.redact(it.detail)) }),
            compatibility = compatibility.map { it.copy(note = LogRedactor.redact(it.note)) },
        )
    }

    /** What the user reviews before sharing: everything in the report except the raw numbers. */
    fun preview(report: RunReport): List<String> = buildList {
        val run = report.run
        add("Device: ${report.device.manufacturer} ${report.device.model}" +
            (report.device.soc?.let { " · $it" } ?: "") + " · Android ${report.device.android} (API ${report.device.sdk})")
        add("App: ${report.device.app}")
        add("Game: Title ID ${run.titleId ?: "unknown"} · ${run.gamePath} · started from ${run.launchSource}")
        add("Run: ${describeRun(run)}")
        run.nativeBacktrace?.let { crash ->
            add("Native crash: ${describeNativeCrash(crash)}")
            crash.abortMessage?.let { add("  abort: $it") }
            crash.frames.forEach { add("  $it") }
        }
        run.driver?.let { add("Driver: ${it.label}") }
        run.performance?.let { perf ->
            add("Performance: ${perf.sampledSeconds} s sampled" +
                (perf.fpsPercentile(0.5)?.let { ", median $it FPS" } ?: ""))
            describeFrameTimes(perf)?.let { add("Frame time: $it") }
            describeAudio(perf)?.let { add("Audio: $it") }
            describeFrameGeneration(perf)?.let { add("Frame generation: $it") }
            perf.jitCompilation?.let { jit ->
                add("Guest JIT compilation: ${jit.translations} translations, ${jit.failedTranslations} failed; " +
                    "%.3f ms summed wall time across workers (not guest execution or frame stall time)".format(
                        java.util.Locale.ROOT, jit.summedWallMs))
                add("  Compile-time buckets <1/<4/<16.7/<33.3/<100/>=100 ms: ${jit.timeHistogram.joinToString("/")}")
            }
            perf.hostThreadCpu?.let { cpu ->
                add("Host thread CPU: ${cpu.attempts} probes, ${cpu.unavailableReads} unavailable; percentages relative to one core")
                cpu.threads.forEach { thread ->
                    add("  ${thread.name} (TID ${thread.tid}): " +
                        "%.1f%% mean, %.1f%% peak, %d windows".format(java.util.Locale.ROOT,
                            thread.meanPercentOneCore, thread.peakPercentOneCore, thread.windows))
                    // Show only the main guest dispatcher's sampled CPU placement.
                    // CPU numbers are observations at discrete reads, not
                    // affinity masks, residence percentages or CPU frequencies.
                    if (thread.name == "Guest CPU 0" && thread.processorObservations.isNotEmpty()) {
                        add("    Host CPUs sampled (not residency): " +
                            thread.processorObservations.joinToString(", ") {
                                "${it.cpu} × ${it.samples}"
                            })
                    }
                }
                add("  Read cost: %.2f ms total, %.2f ms maximum; 5-second samples, not per-frame timings".format(
                    java.util.Locale.ROOT, cpu.readTotalMs, cpu.readMaxMs))
            }
        }
        run.changedSettings?.let { settings ->
            // The core's own "(more settings changed, not listed)" is not a setting.
            val count = settings.count { !it.startsWith("(") }
            add(if (count == 0) "Settings: all at the core's defaults" else "Settings changed from defaults: $count")
            settings.forEach { add("  $it") }
        }
        add("Timeline: ${report.events?.events?.size ?: 0} events")
        add("Your compatibility results: ${report.compatibility.size}" +
            if (report.compatibility.any { it.note.isNotBlank() }) " (with your notes)" else "")
    }

    /** ZIP with the report as JSON and the timeline as text; written to a temp file, then renamed. */
    fun write(destination: File, report: RunReport): File {
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, ".${destination.name}.tmp")
        try {
            ZipOutputStream(temporary.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("run-report.json"))
                zip.write(json.encodeToString(report).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("timeline.txt"))
                val text = buildString {
                    preview(report).forEach { appendLine(it) }
                    appendLine()
                    report.events?.let { log ->
                        if (log.dropped > 0) appendLine("(${log.dropped} earlier events were not kept)")
                        log.events.forEach { appendLine(describeEvent(it)) }
                    }
                }
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            if (!temporary.renameTo(destination)) error("Could not finalize the run report")
            return destination
        } finally {
            temporary.delete()
        }
    }
}
