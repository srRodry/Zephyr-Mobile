package xendroid.compose.ui.library

import kotlin.math.floor
import kotlin.math.roundToInt

/** Position is unbounded during a gesture; only lookup wraps into the actual library. */
internal fun circularCoverIndex(position: Float, count: Int): Int {
    require(count > 0)
    return Math.floorMod(position.roundToInt(), count)
}

/** The closest occurrence of a title on either side of the current circular position. */
internal fun nearestCoverPosition(position: Float, target: Int, count: Int): Float {
    require(count > 0 && target in 0 until count)
    val cycle = floor((position - target) / count + .5f)
    return target + cycle * count
}
