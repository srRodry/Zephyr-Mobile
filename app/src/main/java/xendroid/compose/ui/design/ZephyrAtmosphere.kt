package xendroid.compose.ui.design

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.cos
import kotlin.math.sin
import xendroid.compose.R

/** Cache the light gradients and read the motion only during drawing. */
@Composable
fun Modifier.zephyrBackdrop(animated: Boolean = true): Modifier {
    val c = Xd.colors
    val phase = if (animated) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
        if (lifecycle.value.isAtLeast(Lifecycle.State.RESUMED)) {
            rememberInfiniteTransition(label = "Zephyr atmosphere").animateFloat(
                initialValue = 0f,
                targetValue = 6.2831855f,
                animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
                label = "Aurora drift",
            )
        } else null
    } else null
    return drawWithCache {
        val base = Brush.verticalGradient(listOf(c.bg, c.dyn, c.bg))
        val cyan = Brush.radialGradient(
            listOf(c.acc.copy(alpha = .34f), c.acc.copy(alpha = .12f), Color.Transparent),
            center = Offset(size.width * .75f, size.height * .28f),
            radius = size.maxDimension.coerceAtLeast(1f) * .44f,
        )
        val violet = Brush.radialGradient(
            listOf(Color(0xFF7656E9).copy(alpha = .30f), Color.Transparent),
            center = Offset(size.width * .12f, size.height * .47f),
            radius = size.maxDimension.coerceAtLeast(1f) * .46f,
        )
        val textShade = Brush.verticalGradient(
            listOf(c.bg.copy(alpha = .20f), Color.Transparent, c.bg.copy(alpha = .48f)),
        )
        onDrawBehind {
            val angle = phase?.value ?: 0f
            val x = cos(angle)
            val y = sin(angle)
            drawRect(base)
            translate(left = x * size.width * .21f, top = y * size.height * .19f) {
                drawRect(cyan, topLeft = Offset(-size.width, -size.height), size = size * 3f)
            }
            translate(left = -y * size.width * .22f, top = x * size.height * .18f) {
                drawRect(violet, topLeft = Offset(-size.width, -size.height), size = size * 3f)
            }
            drawRect(textShade)
        }
    }
}

/** Shared surface for cards, navigation and settings. */
@Composable
fun Modifier.zephyrSurface(shape: Shape = ZephyrPanelShape, emphasized: Boolean = false): Modifier {
    val c = Xd.colors
    val top = if (emphasized) c.s3 else c.s2
    val edge = if (emphasized) c.acc.copy(alpha = .47f) else c.line2.copy(alpha = .56f)
    return clip(shape)
        .background(Brush.verticalGradient(listOf(top, c.s1)))
        .border(1.dp, Brush.verticalGradient(listOf(edge, c.line)), shape)
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
