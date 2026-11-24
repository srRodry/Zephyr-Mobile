package xendroid.compose.core

import kotlin.math.abs
import kotlin.math.roundToInt

/** Lowest mode with an even cadence. Allow fractional panel rates such as 59.94 Hz. */
internal fun cadenceRefreshRate(fps: Int, rates: List<Float>, batterySaver: Boolean): Float? {
    if (fps <= 0) return null
    return rates.filter { hz ->
        if (!hz.isFinite() || hz <= 0 || (batterySaver && hz > 60.1f)) return@filter false
        val slots = (hz / fps).roundToInt()
        slots >= 1 && abs(hz - slots.toFloat() * fps) <= 0.15f
    }.minOrNull()
}

/** Rate of accepted host present submissions, never a measurement of display scanout. */
fun presentSubmissionRate(previous: Long, current: Long, elapsedNs: Long): Double? {
    if (previous < 0 || current < previous || elapsedNs <= 0) return null
    return (current - previous).toDouble() * 1_000_000_000.0 / elapsedNs
}

/**
 * The temporary FPS cap applied while frame generation runs (guest cap x multiplier
 * must fit the display). Restoring it never undoes a limit the user picked in the
 * meantime: only the exact automatic value is reverted. 0 means unlimited.
 */
class GenerationCap {
    private var before: Int? = null
    private var automatic: Int? = null
    val active: Boolean get() = before != null

    /** The limit to apply now, or null to keep the current one. */
    fun prepare(current: Int, displayHz: Float, multiplier: Int): Int? {
        val max = (displayHz / multiplier.coerceAtLeast(2)).toInt().coerceAtLeast(1)
        val base = before ?: current
        val capped = if (base == 0) max else base.coerceAtMost(max)
        if (capped == current) return null
        if (before == null) before = current
        automatic = capped
        return capped
    }

    /** 15d: caps at exactly [cap] (a target's game rate); the limit before any automatic cap
     *  is kept the same way as [prepare]'s. */
    fun prepareExact(current: Int, cap: Int): Int? {
        if (cap == current) return null
        if (before == null) before = current
        automatic = cap
        return cap
    }

    /** The player's own limit: the one before the automatic cap, if one is applied. */
    fun playerLimit(current: Int): Int = before ?: current

    /** The limit to put back, or null when the user changed it after the automatic cap. */
    fun restore(current: Int): Int? {
        val result = before?.takeIf { automatic == current }
        forget()
        return result
    }

    /** An explicit user choice supersedes the automatic cap. */
    fun forget() { before = null; automatic = null }
}
