package xendroid.compose.sessions

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import xendroid.compose.Application

/** Android side of [SessionRunStore]: where runs live and how a dead run's process ended. */
object SessionRuns {
    fun store(): SessionRunStore = SessionRunStore(File(Application.get_internal_data_dir(), "session-runs"))

    /** Model and versions for a shared run report; no serials or other identifiers. */
    fun reportDevice(appVersion: String): ReportDevice = ReportDevice(
        manufacturer = Build.MANUFACTURER.orEmpty().take(64),
        model = Build.MODEL.orEmpty().take(64),
        soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim().take(64).ifBlank { null } else null,
        android = Build.VERSION.RELEASE.orEmpty().take(16),
        sdk = Build.VERSION.SDK_INT,
        app = appVersion.take(128),
    )

    /** Writes a run report into the shared cache directory (FileProvider "shared-logs/"). */
    fun writeReport(context: Context, report: RunReport): File {
        val dir = File(context.cacheDir, "shared-logs").apply { mkdirs() }
        dir.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 86_400_000L }?.forEach { it.delete() }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss-SSS", java.util.Locale.US).format(java.util.Date())
        return RunReports.write(File(dir, "zephyr-run-$stamp.zip"), report)
    }

    /** Battery temperature from the sticky broadcast; null when the platform does not report it. */
    fun batteryCelsius(context: Context): Float? = runCatching {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
    }.getOrNull()

    /**
     * Liveness of this app's own :emu processes, and the platform's exit reason for a
     * dead one (API 30+). A pid that is not a live :emu process counts as dead even if
     * the number was reused by another process.
     */
    fun fates(context: Context): (Int) -> ProcessFate {
        val manager = context.getSystemService(ActivityManager::class.java)
        val alive = runCatching {
            manager.runningAppProcesses.orEmpty().filter { it.processName.endsWith(":emu") }.map { it.pid }.toSet()
        }.getOrDefault(emptySet())
        val exits: Map<Int, ApplicationExitInfo> = if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                manager.getHistoricalProcessExitReasons(null, 0, 32)
                    .filter { it.processName.endsWith(":emu") }.associateBy { it.pid }
            }.getOrDefault(emptyMap())
        } else emptyMap()
        return { pid ->
            if (pid in alive) ProcessFate(alive = true)
            else exits[pid]?.let { describe(it) } ?: ProcessFate(alive = false)
        }
    }

    private fun describe(info: ApplicationExitInfo): ProcessFate {
        if (Build.VERSION.SDK_INT < 30) return ProcessFate(alive = false)
        val (crashed, reason) = when (info.reason) {
            ApplicationExitInfo.REASON_CRASH -> true to "Java crash"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> true to "native crash"
            ApplicationExitInfo.REASON_ANR -> true to "not responding (ANR)"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> true to "initialization failure"
            ApplicationExitInfo.REASON_LOW_MEMORY -> false to "killed by the system for memory"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> false to "killed for excessive resource use"
            ApplicationExitInfo.REASON_USER_REQUESTED, ApplicationExitInfo.REASON_USER_STOPPED -> false to "stopped by the user"
            ApplicationExitInfo.REASON_SIGNALED -> false to "killed (signal ${info.status})"
            else -> false to "process ended (reason ${info.reason})"
        }
        // From API 31 a native crash comes with its tombstone; only the crashing thread is kept.
        val backtrace = if (Build.VERSION.SDK_INT >= 31 && info.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) {
            runCatching { info.traceInputStream?.use { Tombstones.parse(Tombstones.read(it)) } }.getOrNull()
        } else null
        return ProcessFate(alive = false, crashed = crashed, reason = reason, backtrace = backtrace)
    }
}
