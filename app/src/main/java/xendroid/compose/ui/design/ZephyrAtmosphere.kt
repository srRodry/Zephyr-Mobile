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
            listOf(Color(0xFF19513C), c.bg, Color(0xFF0B2D27)),
            start = Offset(size.width, 0f), end = Offset(0f, size.height),
        )
        val current = Path().apply {
            moveTo(-size.width * .15f, size.height * .72f)
            cubicTo(size.width * .25f, size.height * .12f, size.width * .56f,
                size.height * 1.05f, size.width * 1.15f, size.height * .18f)
        }
        val halo = Brush.radialGradient(
            listOf(c.acc.copy(alpha = .22f), Color.Transparent),
            center = Offset(size.width * .78f, size.height * .16f),
            radius = size.maxDimension * .7f,
        )
        onDrawBehind {
            drawRect(atmosphere)
            drawRect(halo)
            drawPath(current, c.acc.copy(alpha = .045f), style = Stroke(64.dp.toPx()))
            drawPath(current, c.acc.copy(alpha = .28f), style = Stroke(1.dp.toPx()))
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
        .background(Brush.linearGradient(listOf(top, bottom, Color(0xFF09271E))))
        .border(1.dp, if (emphasized) c.acc.copy(alpha = .55f) else c.line, shape)
        .drawWithCache {
            val sheen = Brush.linearGradient(
                listOf(Color.White.copy(alpha = .17f), Color.Transparent),
                end = Offset(size.width * .7f, size.height * .5f),
            )
            val reflection = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width * .78f, 0f)
                quadraticBezierTo(size.width * .48f, size.height * .24f, 0f, size.height * .58f)
                close()
            }
            val rim = Brush.horizontalGradient(listOf(Color.Transparent,
                c.acc.copy(alpha = if (emphasized) .85f else .4f), Color.Transparent))
            onDrawBehind {
                drawRect(sheen)
                drawPath(reflection, Color.White.copy(alpha = .035f))
                drawLine(rim, Offset(0f, size.height - 1.dp.toPx()),
                    Offset(size.width, size.height - 1.dp.toPx()), 1.dp.toPx())
                drawLine(c.acc.copy(alpha = if (emphasized) .7f else .22f),
                    Offset(24.dp.toPx(), 1.dp.toPx()),
                    Offset(size.width * .6f, 1.dp.toPx()), 1.dp.toPx())
            }
        }
}

/** Shared Anel geometry, without a second decorative ring or runtime animation. */
@Composable
fun ZephyrOrbitMark(size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.zephyr_brand_mark), null, modifier.size(size))
}
