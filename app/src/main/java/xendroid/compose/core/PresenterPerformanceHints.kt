package xendroid.compose.core

import android.content.Context
import android.os.Build
import android.os.PerformanceHintManager
import androidx.annotation.RequiresApi

/** ADPF only for the native presenter TID that supplied an observed work sample.
 * These are scheduling hints, not clocks/root or a promise to speed guest simulation. */
class PresenterPerformanceHints(context: Context) : AutoCloseable {
    private val helper = if (Build.VERSION.SDK_INT >= 31) Helper(context) else null
    var requested = false
    var status = if (helper == null) "Presenter ADPF · Android 12+ required" else "Presenter ADPF · Off"
        private set

    fun update(readWork: () -> LongArray, readTargetNs: () -> Long, foreground: Boolean) {
        if (Build.VERSION.SDK_INT < 31) { status = "Presenter ADPF · Android 12+ required"; return }
        val helper = helper
        if (!requested || !foreground) {
            helper?.close()
            status = when {
                helper == null -> "Presenter ADPF · unavailable"
                requested -> "Presenter ADPF · requested (suspended in menu/background)"
                else -> "Presenter ADPF · Off"
            }
            return
        }
        if (helper == null) { status = "Presenter ADPF · unavailable"; return }
        status = runCatching { helper.report(readWork(), readTargetNs()) }.getOrElse {
            helper.close(); "Presenter ADPF · unsupported by device"
        }
    }
    override fun close() { if (Build.VERSION.SDK_INT >= 31) helper?.close() }

    @RequiresApi(31)
    private class Helper(context: Context) : AutoCloseable {
        private val manager = context.getSystemService(PerformanceHintManager::class.java)
        private var session: PerformanceHintManager.Session? = null
        private var tid = 0
        private var sequence = -1L
        fun report(work: LongArray, target: Long): String {
            if (work.size != 3 || work[0] <= 0) return "Presenter ADPF · waiting for native thread"
            val nextTid = work[0].toInt()
            if (nextTid != tid || session == null) {
                close(); session = manager?.createHintSession(intArrayOf(nextTid), target.coerceAtLeast(1))
                tid = nextTid
            }
            val active = session ?: return "Presenter ADPF · unavailable"
            active.updateTargetWorkDuration(target.coerceAtLeast(1))
            if (work[2] != sequence && work[1] > 0) { active.reportActualWorkDuration(work[1]); sequence = work[2] }
            return "Presenter ADPF · active (native presentation only)"
        }
        override fun close() { session?.close(); session = null; tid = 0; sequence = -1 }
    }
}
