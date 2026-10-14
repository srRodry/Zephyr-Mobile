package xendroid.compose.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val EditorialCut = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height * .86f)
    lineTo(size.width * .72f, size.height)
    lineTo(0f, size.height)
    close()
}

/** Static artwork; a small embedded icon stays contained instead of being enlarged as a cover. */
@Composable
fun XdEditorialHero(
    model: Any,
    smart: Boolean,
    title: String,
    subtitle: String,
    eyebrow: String,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit,
) {
    if (smart) {
        val c = Xd.colors
        val shape = RoundedCornerShape(16.dp)
        Column(modifier.fillMaxWidth().clip(shape).background(c.s1).border(1.dp, c.line2, shape).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(4.dp, 64.dp).background(Xd.colors.acc))
                AsyncImage(model, null, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(eyebrow, style = XdText.labelSm, color = Xd.colors.acc)
                    Text(title, style = XdText.body.copy(fontSize = 24.sp, lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold), color = c.fg)
                    Text(subtitle, style = XdText.small, color = c.fg2)
                }
            }
            actions()
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(196.dp).clip(EditorialCut).background(Xd.colors.acc)) {
                Box(Modifier.fillMaxWidth().height(188.dp).background(Xd.colors.s1)) {
                    AsyncImage(model, null, Modifier.fillMaxWidth().height(188.dp), contentScale = ContentScale.Crop)
                }
            }
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(eyebrow, style = XdText.labelSm, color = Xd.colors.acc)
                Text(title, style = XdText.body.copy(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold), color = Xd.colors.fg)
                Text(subtitle, style = XdText.small, color = Xd.colors.fg2)
                actions()
            }
        }
    }
}

/** Feature panel using the shared green tokens; legacy function name retained for callers. */
@Composable
fun XdSilverPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = Xd.colors
    CompositionLocalProvider(LocalXd provides colors) {
        Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.s1)
            .padding(horizontal = 16.dp, vertical = 8.dp), content = content)
    }
}


/** An editorial title in normal flow: it wraps with font scale instead of clipping in a fixed box. */
@Composable
fun XdEditorialHeading(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(42.dp, 4.dp).background(Xd.colors.acc))
        Text(title, style = XdText.body.copy(fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold), color = Xd.colors.fg)
        Text(subtitle, style = XdText.body, color = Xd.colors.fg2)
    }
}

