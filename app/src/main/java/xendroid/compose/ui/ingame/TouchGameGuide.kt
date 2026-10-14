package xendroid.compose.ui.ingame

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xendroid.compose.R
import xendroid.compose.ui.design.*

/** Static geometry only: no capture, blur, animation or new interaction with the guest. */
private val guideBlade = GenericShape { size, _ ->
    val cut = minOf(size.width * 0.12f, size.height * 0.15f)
    moveTo(0f, 0f)
    lineTo(size.width - cut, 0f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

/** A touch landing page over the existing game surface; tools and quit confirmation stay separate. */
@Composable
internal fun TouchGameGuide(
    model: InGameMenuModel, portrait: Boolean, modifier: Modifier,
    onResume: () -> Unit, onControls: () -> Unit, onSettings: () -> Unit, onQuit: () -> Unit,
) {
    val c = Xd.colors
    val shape = if (portrait) RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp) else guideBlade
    Column(modifier.testTag("game-guide-root").clip(shape).background(c.solid(c.sheet))
        .windowInsetsPadding(WindowInsets.safeDrawing.part(top = !portrait, bottom = true, start = true, end = portrait))
        .verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = if (portrait) 16.dp else 44.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(painterResource(R.drawable.zephyr_launcher_foreground), null, Modifier.size(40.dp))
            Text(stringResource(R.string.zephyr_game_guide), style = XdText.h2, color = c.fg)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(model.gameName ?: stringResource(R.string.app_name), style = XdText.label, color = c.fg,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(if (model.paused) R.string.xd_menu_paused else R.string.xd_menu_running),
                style = XdText.small, color = c.fg2)
        }
        GuideEntry(stringResource(R.string.zephyr_guide_resume), XdIcons.play, "game-guide-resume", onResume, primary = true)
        GuideEntry(stringResource(R.string.menu_tab_controls), XdIcons.gamepad, "game-guide-controls", onControls)
        GuideEntry(stringResource(R.string.zephyr_guide_settings), XdIcons.gear, "game-guide-settings", onSettings)
        GuideEntry(stringResource(R.string.menu_exit_game), XdIcons.exit, "game-guide-quit", onQuit, danger = true)
    }
}

@Composable
private fun GuideEntry(
    label: String, icon: ImageVector, tag: String, onClick: () -> Unit,
    primary: Boolean = false, danger: Boolean = false,
) {
    val c = Xd.colors
    val shape = RoundedCornerShape(4.dp)
    val fg = if (primary) c.onAction else if (danger) c.dangerText else c.fg
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag).focusRing(shape).clip(shape)
        .background(if (primary) c.action else c.s1)
        .border(1.dp, if (primary) c.acc else c.line, shape)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = fg)
        Text(label, Modifier.weight(1f), style = XdText.label.copy(fontSize = 16.sp, lineHeight = 21.sp), color = fg)
    }
}
