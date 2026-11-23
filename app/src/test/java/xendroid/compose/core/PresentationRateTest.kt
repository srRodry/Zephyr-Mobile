package xendroid.compose.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresentationRateTest {
    @Test fun matchesCappedCadenceWithTheLowestCompatiblePanelMode() {
        val rates = listOf(144f, 120f, 90f, 59.94f)
        assertEquals(59.94f, cadenceRefreshRate(30, rates, batterySaver = false))
        assertEquals(120f, cadenceRefreshRate(40, rates, batterySaver = false))
        assertNull(cadenceRefreshRate(40, rates, batterySaver = true))
        assertNull(cadenceRefreshRate(0, rates, batterySaver = false))
        assertNull(cadenceRefreshRate(60, listOf(50f, 90f), batterySaver = false))
    }

    @Test fun countsAcceptedPresentSubmissionsOverElapsedTime() {
        assertEquals(30.0, presentSubmissionRate(120, 132, 400_000_000L)!!, 0.0001)
        assertEquals(0.0, presentSubmissionRate(132, 132, 400_000_000L)!!, 0.0001)
    }

    @Test fun rejectsCounterResetAndInvalidClockSamples() {
        assertNull(presentSubmissionRate(132, 2, 400_000_000L))
        assertNull(presentSubmissionRate(0, 2, 0))
        assertNull(presentSubmissionRate(-1, 2, 400_000_000L))
    }

    @Test fun generationCapIsRestoredOnlyWhileTheAutomaticValueIsStillSet() {
        val cap = GenerationCap()
        assertEquals(30, cap.prepare(current = 60, displayHz = 60f, multiplier = 2))
        // Raising the multiplier lowers the cap from the ORIGINAL limit, not the capped one.
        assertEquals(20, cap.prepare(current = 30, displayHz = 60f, multiplier = 3))
        assertEquals(60, cap.restore(current = 20))
        assertNull(cap.restore(current = 60))
        assertEquals(false, cap.active)
    }

    @Test fun generationCapNeverUndoesAManualChange() {
        val cap = GenerationCap()
        assertEquals(60, cap.prepare(current = 0, displayHz = 120f, multiplier = 2))
        assertNull("user picked 45 while FG ran", cap.restore(current = 45))
        assertNull(cap.prepare(current = 30, displayHz = 60f, multiplier = 2))
        assertEquals(false, cap.active)
    }
}
