package xendroid.compose.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xendroid.compose.core.ThermalWatch.Level

class ThermalWatchTest {
    @Test fun warnsBeforeTheDeviceThrottles() {
        val watch = ThermalWatch()
        assertNull(watch.sample(0.5f, 0))
        assertEquals(Level.NEAR_LIMIT, watch.sample(0.86f, 1))
        assertEquals(Level.THROTTLING, watch.sample(1.02f, 1))
        assertEquals(Level.NEAR_LIMIT, watch.sample(0.9f, 1))
        assertEquals(Level.OK, watch.sample(0.6f, 0))
    }

    @Test fun aReadingAroundTheLineDoesNotFlap() {
        val watch = ThermalWatch()
        watch.sample(0.86f, 0)
        assertNull(watch.sample(0.8f, 0))              // still above 0.7 once warned
        assertNull(watch.sample(0.72f, 0))
        assertEquals(Level.OK, watch.sample(0.69f, 0))
        assertNull(watch.sample(0.8f, 0))              // below 0.85 does not warn again
    }

    @Test fun theStatusAloneWhenThereIsNoHeadroom() {
        val watch = ThermalWatch()
        assertNull(watch.sample(null, 1))
        assertEquals(Level.NEAR_LIMIT, watch.sample(null, 2))
        assertEquals(Level.THROTTLING, watch.sample(Float.NaN, 3))
        assertEquals(Level.OK, watch.sample(null, 0))
        assertNull(watch.sample(-1f, -1))
    }

    @Test fun pausesAtCriticalAndRequiresRecoveryBelowSevere() {
        assertFalse(ThermalWatch.needsCoolingPause(3))
        assertTrue(ThermalWatch.needsCoolingPause(4))
        assertTrue(ThermalWatch.needsCoolingPause(6))
        assertFalse(ThermalWatch.mayResumeAfterCooling(-1))
        assertFalse(ThermalWatch.mayResumeAfterCooling(3))
        assertTrue(ThermalWatch.mayResumeAfterCooling(2))
    }
}
