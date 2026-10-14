package xendroid.compose.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xendroid.compose.R

/** Presentation groups only: all existing navigator destinations retain their identity. */
private enum class BladeGroup { GAMES, CONTENT, PROFILES, SYSTEM }

private fun groupOf(area: XdArea?): BladeGroup? = when (area) {
    XdArea.GAMES, XdArea.COLLECTIONS -> BladeGroup.GAMES
    XdArea.CONTENT -> BladeGroup.CONTENT
    XdArea.PROFILES -> BladeGroup.PROFILES
    XdArea.CONTROLS, XdArea.DRIVERS, XdArea.SETTINGS -> BladeGroup.SYSTEM
    null -> null
}

private val bladeShape = GenericShape { size, _ ->
    val cut = (size.height * 0.14f).coerceAtMost(size.width / 8f)
    moveTo(cut, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width - cut, size.height)
    lineTo(0f, size.height)
    close()
}
private val bladeBlue = Color(0xFF155ED2) // White text: contrast 5.89:1.

/** Portrait navigation belongs above the viewport; its parent supplies bottom safe insets. */
@Composable
fun BTopAreaBar(current: XdArea?, modifier: Modifier = Modifier) {
    val c = Xd.colors
    Column(modifier.fillMaxWidth().background(c.bg)
        .windowInsetsPadding(WindowInsets.safeDrawing.part(top = true, start = true, end = true))) {
        BladeIdentity(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            BladeGroup.entries.forEach { group ->
                BladeTab(group, current, Modifier.weight(1f))
            }
        }
        HorizontalDivider(color = c.line)
    }
}

/** Landscape areas stay scrollable even on short displays; no navigation target is clipped. */
@Composable
fun ZephyrBladeRail(current: XdArea?, modifier: Modifier = Modifier) {
    val c = Xd.colors
    Column(modifier.width(144.dp).fillMaxHeight().background(c.bg)
        .windowInsetsPadding(WindowInsets.safeDrawing.part(top = true, bottom = true, start = true))) {
        Image(painterResource(R.drawable.zephyr_launcher_foreground), stringResource(R.string.app_name),
            Modifier.padding(8.dp).size(40.dp))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BladeGroup.entries.forEach { group -> BladeTab(group, current, Modifier.fillMaxWidth()) }
        }
        BladeProfile(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp))
    }
}

@Composable
private fun BladeIdentity(modifier: Modifier) {
    Row(modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(painterResource(R.drawable.zephyr_launcher_foreground), null, Modifier.size(30.dp))
        Text(stringResource(R.string.app_name), Modifier.weight(1f), color = Xd.colors.fg,
            style = XdText.label.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BladeProfile(Modifier.widthIn(max = 150.dp))
    }
}

@Composable
private fun BladeProfile(modifier: Modifier) {
    val nav = LocalXdNavigator.current
    val shape = RoundedCornerShape(4.dp)
    Row(modifier.heightIn(min = 48.dp).focusRing(shape).clip(shape)
        .clickable(role = Role.Button) { nav.area(XdArea.PROFILES) }.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(XdIcons.user, stringResource(R.string.xd_area_profiles), Modifier.size(18.dp), tint = Xd.colors.fg2)
        Text(nav.gamertag ?: stringResource(R.string.xd_guide_no_profile), color = Xd.colors.fg2,
            style = XdText.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BladeTab(group: BladeGroup, current: XdArea?, modifier: Modifier) {
    val c = Xd.colors
    val nav = LocalXdNavigator.current
    val picked = groupOf(current) == group
    var expanded by remember(current) { mutableStateOf(false) }
    val label = stringResource(when (group) {
        BladeGroup.GAMES -> R.string.xd_area_games
        BladeGroup.CONTENT -> R.string.xd_area_content
        BladeGroup.PROFILES -> R.string.xd_area_profiles
        BladeGroup.SYSTEM -> R.string.zephyr_ui2_system
    })
    Box(modifier) {
        Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).focusRing(bladeShape).clip(bladeShape)
            .background(if (picked) bladeBlue else c.s1)
            .clickable(role = if (group == BladeGroup.SYSTEM || group == BladeGroup.GAMES) Role.Button else Role.Tab) {
                when (group) {
                    BladeGroup.GAMES, BladeGroup.SYSTEM -> expanded = true
                    BladeGroup.CONTENT -> nav.area(XdArea.CONTENT)
                    BladeGroup.PROFILES -> nav.area(XdArea.PROFILES)
                }
            }.semantics { selected = picked }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), color = if (picked) Color.White else c.fg2,
                    style = XdText.label.copy(fontSize = 13.sp, lineHeight = 17.sp,
                        fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium))
                if (group == BladeGroup.GAMES || group == BladeGroup.SYSTEM) {
                    Icon(XdIcons.chevD, null, Modifier.size(12.dp), tint = if (picked) Color.White else c.fg2)
                }
            }
            // A geometric selection mark accompanies color; the complete shape is the touch target.
            if (picked) Box(Modifier.padding(top = 5.dp).width(20.dp).height(2.dp).background(Color.White))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(c.s1)) {
            val areas = when (group) {
                BladeGroup.GAMES -> listOf(XdArea.GAMES, XdArea.COLLECTIONS)
                BladeGroup.SYSTEM -> listOf(XdArea.SETTINGS, XdArea.CONTROLS, XdArea.DRIVERS)
                else -> emptyList()
            }
            areas.forEach { area ->
                DropdownMenuItem(
                    text = { Text(stringResource(area.label), color = c.fg) },
                    leadingIcon = { Icon(area.icon, null, tint = c.fg2) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { selected = current == area },
                    onClick = { expanded = false; nav.area(area) },
                )
            }
            if (group == BladeGroup.SYSTEM) {
                HorizontalDivider(color = c.line)
                listOf(
                    Triple(XdShortcut.DIAGNOSTICS, R.string.xd_guide_diagnostics, XdIcons.bug),
                    Triple(XdShortcut.COMPARE, R.string.xd_guide_compare, XdIcons.ab),
                    Triple(XdShortcut.ABOUT, R.string.xd_guide_about, XdIcons.info),
                ).forEach { (shortcut, text, icon) ->
                    DropdownMenuItem(text = { Text(stringResource(text), color = c.fg) },
                        leadingIcon = { Icon(icon, null, tint = c.fg2) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        onClick = { expanded = false; nav.shortcut(shortcut) })
                }
            }
        }
    }
}
