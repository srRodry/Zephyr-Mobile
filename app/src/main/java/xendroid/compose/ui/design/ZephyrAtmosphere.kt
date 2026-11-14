package xendroid.compose.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xendroid.compose.R

/** Emerald current: cached vector geometry, no live blur, timers or particle loops. */
@Composable
fun Modifier.zephyrBackdrop(): Modifier {
    val c = Xd.colors
    return drawWithCache {
        val atmosphere = Brush.linearGradient(
            listOf(c.bg, Color(0xFF0B3027), c.bg),
            start = Offset(size.width, 0f), end = Offset(0f, size.height),
        )
        val current = Path().apply {
            moveTo(-size.width * .15f, size.height * .72f)
            cubicTo(size.width * .25f, size.height * .12f, size.width * .56f,
                size.height * 1.05f, size.width * 1.15f, size.height * .18f)
        }
        onDrawBehind {
            drawRect(atmosphere)
            drawPath(current, c.acc.copy(alpha = .035f), style = Stroke(36.dp.toPx()))
            drawPath(current, c.acc.copy(alpha = .12f), style = Stroke(1.dp.toPx()))
        }
    }
}

/** Solid readable glass illusion; the content behind a panel never bleeds into its text. */
@Composable
fun Modifier.zephyrSurface(shape: Shape = ZephyrPanelShape, emphasized: Boolean = false): Modifier {
    val c = Xd.colors
    val top = if (emphasized) c.solid(c.s3) else c.solid(c.s2)
    val bottom = c.solid(c.s1)
    return clip(shape)
        .background(Brush.linearGradient(listOf(top, bottom, bottom)))
        .border(1.dp, if (emphasized) c.acc.copy(alpha = .55f) else c.line, shape)
        .drawWithCache {
            val sheen = Brush.linearGradient(
                listOf(Color.White.copy(alpha = .045f), Color.Transparent),
                end = Offset(size.width * .7f, size.height * .5f),
            )
            onDrawBehind {
                drawRect(sheen)
                drawLine(c.acc.copy(alpha = if (emphasized) .7f else .22f),
                    Offset(24.dp.toPx(), 1.dp.toPx()),
                    Offset(size.width * .6f, 1.dp.toPx()), 1.dp.toPx())
            }
        }
}

/** An original Zephyr orbit around its own mark, never the Xbox identity. */
@Composable
fun ZephyrOrbitMark(size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val c = Xd.colors
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val inset = 2.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            for (start in listOf(-78f, 102f)) {
                drawArc(c.acc.copy(alpha = .7f), start, 128f, false,
                    Offset(inset, inset), arcSize, style = Stroke(1.5.dp.toPx()))
            }
        }
        Image(painterResource(R.drawable.zephyr_launcher_foreground), null, Modifier.size(size * .8f))
    }
}
