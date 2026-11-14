package xendroid.compose.ui.design

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
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
    val bend = size.height * .24f
    moveTo(bend, 0f)
    lineTo(size.width - bend * .45f, 0f)
    quadraticBezierTo(size.width, 0f, size.width, bend * .7f)
    lineTo(size.width, size.height - bend)
    quadraticBezierTo(size.width - bend * .7f, size.height - bend * .55f,
        size.width - bend * 1.8f, size.height)
    lineTo(bend * .45f, size.height)
    quadraticBezierTo(0f, size.height, 0f, size.height - bend * .7f)
    lineTo(0f, bend)
    quadraticBezierTo(bend * .6f, bend * .7f, bend, 0f)
    close()
}

/** Portrait navigation belongs above the viewport; its parent supplies bottom safe insets. */
@Composable
fun BTopAreaBar(current: XdArea?, modifier: Modifier = Modifier) {
    val c = Xd.colors
    val enlargedText = LocalDensity.current.fontScale > 1.15f
    Column(modifier.fillMaxWidth().zephyrBackdrop().testTag("zephyr-navigation-portrait")
        .windowInsetsPadding(WindowInsets.safeDrawing.part(top = true, start = true, end = true))) {
        BladeIdentity(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().then(if (enlargedText) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BladeGroup.entries.forEach { group ->
                BladeTab(group, current, if (enlargedText) Modifier.widthIn(min = 132.dp) else Modifier.weight(1f))
            }
        }
        HorizontalDivider(color = c.line)
    }
}

/** Landscape areas stay scrollable even on short displays; no navigation target is clipped. */
@Composable
fun ZephyrBladeRail(current: XdArea?, modifier: Modifier = Modifier) {
    val c = Xd.colors
    Column(modifier.width(128.dp).fillMaxHeight().zephyrBackdrop().testTag("zephyr-navigation-landscape")
        .windowInsetsPadding(WindowInsets.safeDrawing.part(top = true, bottom = true, start = true))) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.zephyr_launcher_foreground), stringResource(R.string.app_name), Modifier.size(44.dp))
            Text("ZEPHYR", style = XdText.eyebrow, color = c.fg, modifier = Modifier.padding(top = 6.dp))
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BladeGroup.entries.forEach { group -> BladeTab(group, current, Modifier.fillMaxWidth()) }
        }
        BladeProfile(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp))
    }
}

@Composable
private fun BladeIdentity(modifier: Modifier) {
    Row(modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ZephyrOrbitMark(42.dp)
        Text(stringResource(R.string.app_name), Modifier.weight(1f), color = Xd.colors.fg,
            style = XdText.h1.copy(fontSize = 21.sp, lineHeight = 25.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BladeProfile(Modifier.widthIn(max = 150.dp))
    }
}

@Composable
private fun BladeProfile(modifier: Modifier) {
    val nav = LocalXdNavigator.current
    val shape = ZephyrActionShape
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
            .background(Brush.verticalGradient(if (picked)
                listOf(Color(0xFF206748), Color(0xFF103D2E)) else listOf(c.s2, c.s1)))
            .border(1.dp, if (picked) c.acc.copy(alpha = .75f) else c.line.copy(alpha = .55f), bladeShape)
            .clickable(role = if (group == BladeGroup.SYSTEM || group == BladeGroup.GAMES) Role.Button else Role.Tab) {
                when (group) {
                    BladeGroup.GAMES, BladeGroup.SYSTEM -> expanded = true
                    BladeGroup.CONTENT -> nav.area(XdArea.CONTENT)
                    BladeGroup.PROFILES -> nav.area(XdArea.PROFILES)
                }
            }.semantics { selected = picked }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), color = if (picked) c.onAction else c.fg2,
                    style = XdText.label.copy(fontSize = 13.sp, lineHeight = 17.sp,
                        fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (group == BladeGroup.GAMES || group == BladeGroup.SYSTEM) {
                    Icon(XdIcons.chevD, null, Modifier.size(12.dp), tint = if (picked) c.onAction else c.fg2)
                }
            }
            // A geometric selection mark accompanies color; the complete shape is the touch target.
            if (picked) Box(Modifier.padding(top = 5.dp).width(26.dp).height(2.dp).background(c.acc))
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
