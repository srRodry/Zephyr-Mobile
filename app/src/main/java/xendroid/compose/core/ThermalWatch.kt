package xendroid.compose.core

/**
 * 15j (Bannerlator `445d6655`): a warning before the phone slows itself down for heat, anchored
 * to the device's own limits: Android's thermal headroom (1.0 = the point where this device
 * throttles severely) and its thermal status. Advisory, like F04: it never changes a setting;
 * the run's timeline gets each change and the player one notice per rise. Hysteresis keeps a
 * reading that wobbles around the line from flapping. Pure, tested on the JVM.
 */
class ThermalWatch(private val enterAt: Float = 0.85f, private val leaveBelow: Float = 0.7f) {
    enum class Level { OK, NEAR_LIMIT, THROTTLING }

    var level: Level = Level.OK
        private set

    /** [headroom]: PowerManager.getThermalHeadroom (null when the device has none); [status]:
     *  PowerManager.THERMAL_STATUS_* (-1 unknown). The new level when it changed, else null. */
    fun sample(headroom: Float?, status: Int): Level? {
        val h = headroom?.takeIf { it.isFinite() && it >= 0f }
        val next = when {
            status >= SEVERE || (h != null && h >= 1f) -> Level.THROTTLING
            status == MODERATE || (h != null && h >= enterAt) -> Level.NEAR_LIMIT
            level != Level.OK && h != null && h >= leaveBelow -> Level.NEAR_LIMIT
            else -> Level.OK
        }
        if (next == level) return null
        level = next
        return next
    }

    companion object {
        private const val MODERATE = 2   // PowerManager.THERMAL_STATUS_MODERATE
        private const val SEVERE = 3     // PowerManager.THERMAL_STATUS_SEVERE
        private const val CRITICAL = 4   // PowerManager.THERMAL_STATUS_CRITICAL
        // Some devices return NaN when headroom is polled faster than this.
        const val HEADROOM_INTERVAL_MS = 10_000L
        fun needsCoolingPause(status: Int) = status >= CRITICAL
        fun mayResumeAfterCooling(status: Int) = status in 0 until SEVERE
        /** One notice per this long at most, even when the level rises again. */
        const val NOTICE_EVERY_MS = 5 * 60_000L
    }
}
