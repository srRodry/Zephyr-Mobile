package xendroid.compose.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class CircularCoverflowTest {
    @Test fun lookupCrossesBothSeamsAndManyRevolutions() {
        for (count in listOf(1, 2, 3, 14, 100)) {
            for (step in -1000..1000) {
                assertEquals(Math.floorMod(step, count), circularCoverIndex(step.toFloat(), count))
            }
        }
    }

    @Test fun titleSelectionUsesNearestOccurrenceIncludingAcrossTheSeam() {
        assertEquals(3f, nearestCoverPosition(2.8f, 0, 3), .001f)
        assertEquals(-1f, nearestCoverPosition(.1f, 2, 3), .001f)
        assertEquals(1002f, nearestCoverPosition(1001.8f, 0, 3), .001f)
        for (count in 1..20) for (target in 0 until count) {
            val position = -34.3f
            val nearest = nearestCoverPosition(position, target, count)
            assertEquals(target, circularCoverIndex(nearest, count))
            org.junit.Assert.assertTrue(kotlin.math.abs(nearest - position) <= count / 2f + .001f)
        }
    }
}
