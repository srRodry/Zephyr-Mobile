package xendroid.compose.ui.design

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xendroid.compose.R

/** Slow dashboard motion reads its phase in draw, leaving composition and layout alone. */
@Composable
fun Modifier.zephyrBackdrop(animated: Boolean = true): Modifier {
    val c = Xd.colors
    val drift = if (animated) {
        rememberInfiniteTransition(label = "Zephyr atmosphere").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(12000), RepeatMode.Reverse),
            label = "Ribbon drift",
        )
    } else null
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
            listOf(c.acc.copy(alpha = .18f), Color.Transparent),
            center = Offset(size.width * .78f, size.height * .16f),
            radius = size.maxDimension * .7f,
        )
        onDrawBehind {
            drawRect(atmosphere)
            val phase = drift?.value ?: 0f
            drawRect(halo, alpha = .78f + phase * .22f)
            translate(top = (phase - .5f) * minOf(size.height * .07f, 36.dp.toPx())) {
                drawPath(current, c.acc.copy(alpha = .045f), style = Stroke(64.dp.toPx()))
                drawPath(current, c.acc.copy(alpha = .26f), style = Stroke(1.dp.toPx()))
            }
            drawLine(c.chrome.copy(alpha = .085f), Offset(size.width * .08f, 0f),
                Offset(size.width * .42f, 0f), .75.dp.toPx())
        }
    }
}

/** Solid readable glass illusion; the content behind a panel never bleeds into its text. */
@Composable
fun Modifier.zephyrSurface(shape: Shape = ZephyrPanelShape, emphasized: Boolean = false): Modifier {
    val c = Xd.colors
    val top = lerp(if (emphasized) c.solid(c.s3) else c.solid(c.s2), c.chrome, .025f)
    val bottom = c.solid(c.s1)
    return clip(shape)
        .background(Brush.linearGradient(listOf(top, bottom, Color(0xFF09271E))))
        .border(1.dp, Brush.linearGradient(listOf(
            c.chrome.copy(alpha = if (emphasized) .48f else .28f),
            if (emphasized) c.acc.copy(alpha = .42f) else c.line,
            c.line, c.chromeShade.copy(alpha = .24f),
        )), shape)
        .drawWithCache {
            val sheen = Brush.linearGradient(
                listOf(c.chrome.copy(alpha = .19f), Color.Transparent),
                end = Offset(size.width * .7f, size.height * .5f),
            )
            val reflection = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width * .78f, 0f)
                quadraticBezierTo(size.width * .48f, size.height * .24f, 0f, size.height * .58f)
                close()
            }
            val rim = Brush.horizontalGradient(listOf(Color.Transparent,
                c.chrome.copy(alpha = if (emphasized) .52f else .30f), Color.Transparent))
            onDrawBehind {
                drawRect(sheen)
                drawPath(reflection, c.chrome.copy(alpha = .048f))
                drawLine(rim, Offset(0f, size.height - 1.dp.toPx()),
                    Offset(size.width, size.height - 1.dp.toPx()), 1.dp.toPx())
                drawLine(c.chrome.copy(alpha = if (emphasized) .6f else .32f),
                    Offset(24.dp.toPx(), 1.dp.toPx()),
                    Offset(size.width * .6f, 1.dp.toPx()), 1.dp.toPx())
            }
        }
}

/** Shared Zephyr-Mobile emblem, without a second decorative ring or runtime animation. */
@Composable
fun ZephyrOrbitMark(size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.zephyr_brand_mark), null, modifier.size(size))
}

/** Fine silver separator shared by touch and controller navigation. */
@Composable
fun ZephyrChromeDivider(modifier: Modifier = Modifier) {
    val c = Xd.colors
    Box(modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(
        listOf(c.line, c.chrome.copy(alpha = .38f), c.line),
    )))
}
