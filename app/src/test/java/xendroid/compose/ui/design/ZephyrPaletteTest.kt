package xendroid.compose.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Readability guards for the shared palette, independent of illustrative cover artwork. */
class ZephyrPaletteTest {
    private fun contrast(a: Color, b: Color): Double {
        val x = a.luminance().toDouble()
        val y = b.luminance().toDouble()
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }
    private fun readable(text: Color, surface: Color) {
        val ratio = contrast(text, surface)
        assertTrue("Text contrast $ratio must be at least 4.5:1", ratio >= 4.5)
    }

    @Test fun touchTextAndHighlightRemainReadableAcrossOpaqueSurfaces() {
        val c = XdColors.Touch
        for (surface in listOf(c.bg, c.s1, c.s2, c.s3, c.s4, c.sheet)) {
            for (text in listOf(c.fg, c.fg2, c.fg3, c.acc)) readable(text, surface)
        }
    }

    @Test fun touchActionsAndLimeSelectionsHaveReadableLabels() {
        val c = XdColors.Touch
        readable(c.onAction, c.action)
        readable(c.onAcc, c.acc)
        assertTrue("Action and focus must have separate roles", c.action != c.acc)
    }

    @Test fun controllerBaseLabelsRemainReadableOnCompositedSurfaces() {
        val c = XdColors.Controller
        for (surface in listOf(c.bg, c.s1, c.s2, c.s3, c.s4, c.sheet)) {
            val solid = c.solid(surface)
            readable(c.fg, solid)
            readable(c.acc, solid)
        }
        readable(c.onAction, c.action)
        readable(c.onAcc, c.acc)
    }
}
