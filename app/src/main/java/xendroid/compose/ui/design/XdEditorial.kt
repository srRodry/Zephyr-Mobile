package xendroid.compose.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.graphics.Color
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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(196.dp).clip(EditorialCut).background(Color(0xFF237BFF))) {
            Box(Modifier.fillMaxWidth().height(188.dp).background(Color(0xFF1C2532)), contentAlignment = Alignment.Center) {
                AsyncImage(model, null,
                    modifier = if (smart) Modifier.size(104.dp) else Modifier.fillMaxWidth().height(188.dp),
                    contentScale = if (smart) ContentScale.Fit else ContentScale.Crop)
            }
        }
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(eyebrow, style = XdText.labelSm, color = Color(0xFF91BAFF))
            Text(title, style = XdText.body.copy(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold), color = Xd.colors.fg)
            Text(subtitle, style = XdText.small, color = Xd.colors.fg2)
            actions()
        }
    }
}

/** Light surface with its own text tokens; surrounding screens retain their existing theme. */
@Composable
fun XdSilverPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = Xd.colors.copy(s1 = Color(0xFFF5F7FA),
        s2 = Color(0xFFE8EEF6), s3 = Color(0xFFDEE6F0), s4 = Color(0xFFD0DDEB), fg = Color(0xFF15191F),
        fg2 = Color(0xFF354254), fg3 = Color(0xFF48576B), line = Color(0xFFD1D8E2),
        acc = Color(0xFF155ED2), onAcc = Color.White)
    CompositionLocalProvider(LocalXd provides colors) {
        Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.s1)
            .padding(horizontal = 16.dp, vertical = 8.dp), content = content)
    }
}


/** An editorial title in normal flow: it wraps with font scale instead of clipping in a fixed box. */
@Composable
fun XdEditorialHeading(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(42.dp, 4.dp).background(Color(0xFF237BFF)))
        Text(title, style = XdText.body.copy(fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold), color = Xd.colors.fg)
        Text(subtitle, style = XdText.body, color = Xd.colors.fg2)
    }
}
