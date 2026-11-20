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
    val h = size.height
    moveTo(h * .3f, 0f)
    lineTo(size.width - h * .08f, 0f)
    quadraticBezierTo(size.width + h * .03f, 0f, size.width - h * .01f, h * .22f)
    lineTo(size.width - h * .16f, h * .68f)
    quadraticBezierTo(size.width - h * .23f, h, size.width - h * .52f, h)
    lineTo(h * .08f, h)
    quadraticBezierTo(-h * .03f, h, h * .01f, h * .79f)
    lineTo(h * .16f, h * .29f)
    quadraticBezierTo(h * .21f, h * .05f, h * .3f, 0f)
    close()
}

@Composable
fun ZephyrPortraitHeader() {
    Column(Modifier.fillMaxWidth().zephyrBackdrop(animated = false)
        .windowInsetsPadding(WindowInsets.safeDrawing.part(top = true, start = true, end = true))) {
        BladeIdentity(Modifier.fillMaxWidth())
        ZephyrChromeDivider()
    }
}

@Composable
fun ZephyrBottomBar(current: XdArea?, modifier: Modifier = Modifier) {
    val enlarged = LocalDensity.current.fontScale > 1.15f
    Row(modifier.fillMaxWidth().testTag("zephyr-navigation-portrait")
        .windowInsetsPadding(WindowInsets.safeDrawing.part(bottom = true, start = true, end = true))
        .padding(horizontal = 8.dp, vertical = 4.dp).zephyrSurface(ZephyrPanelShape)
        .then(if (enlarged) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
        .padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
        val itemWidth = if (enlarged) 104.dp else 64.dp
        fun RowScope.tabSize() = if (enlarged) Modifier.width(itemWidth) else Modifier.weight(1f)
        BladeTab(BladeGroup.GAMES, current, tabSize(), stacked = true)
        BladeTab(BladeGroup.CONTENT, current, tabSize(), stacked = true)
        ZephyrGuideAccess(if (enlarged) Modifier.width(itemWidth) else Modifier.width(56.dp))
        BladeTab(BladeGroup.PROFILES, current, tabSize(), stacked = true)
        BladeTab(BladeGroup.SYSTEM, current, tabSize(), stacked = true)
    }
}

@Composable
private fun ZephyrGuideAccess(modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.zephyr_game_guide)
    Column(modifier.heightIn(min = 52.dp).focusRing(ZephyrActionShape)
        .clickable(role = Role.Button) { open = true }.padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        ZephyrOrbitMark(30.dp)
        Text(label, style = XdText.small, color = Xd.colors.fg)
    }
    if (open) CGuide(onDismiss = { open = false })
}

/** Landscape areas stay scrollable even on short displays; no navigation target is clipped. */
@Composable
fun ZephyrBladeRail(current: XdArea?, modifier: Modifier = Modifier) {
    Column(modifier.width(128.dp).fillMaxHeight().zephyrBackdrop(animated = false).testTag("zephyr-navigation-landscape")
        .windowInsetsPadding(WindowInsets.safeDrawing.part(top = true, bottom = true, start = true))) {
        ZephyrGuideAccess(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BladeGroup.entries.forEach { group -> BladeTab(group, current, Modifier.fillMaxWidth(), showIcon = true) }
        }
        BladeProfile(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp))
    }
}

@Composable
private fun BladeIdentity(modifier: Modifier) {
    Row(modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ZephyrOrbitMark(42.dp)
        Text(stringResource(R.string.app_name), Modifier.weight(1f), color = Xd.colors.chrome,
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
        Icon(XdIcons.user, stringResource(R.string.xd_area_profiles), Modifier.size(18.dp), tint = Xd.colors.chrome)
        Text(nav.gamertag ?: stringResource(R.string.xd_guide_no_profile), color = Xd.colors.fg2,
            style = XdText.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BladeTab(group: BladeGroup, current: XdArea?, modifier: Modifier, showIcon: Boolean = false, stacked: Boolean = false) {
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
    val icon = when (group) {
        BladeGroup.GAMES -> XdIcons.gamepad
        BladeGroup.CONTENT -> XdIcons.layers
        BladeGroup.PROFILES -> XdIcons.user
        BladeGroup.SYSTEM -> XdIcons.gear
    }
    val open: () -> Unit = {
        when (group) {
            BladeGroup.GAMES, BladeGroup.SYSTEM -> expanded = true
            BladeGroup.CONTENT -> nav.area(XdArea.CONTENT)
            BladeGroup.PROFILES -> nav.area(XdArea.PROFILES)
        }
    }
    Box(modifier) {
        if (stacked) {
            Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).focusRing(ZephyrActionShape)
                .clip(ZephyrActionShape).background(if (picked) c.acc.copy(alpha = .14f) else Color.Transparent)
                .clickable(role = Role.Tab, onClick = open).semantics { selected = picked }
                .padding(horizontal = 2.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                val tint = if (picked) c.acc else c.fg2
                Icon(icon, null, Modifier.size(20.dp), tint = tint)
                Text(label, style = XdText.small.copy(fontSize = 10.sp, lineHeight = 13.sp),
                    color = if (picked) c.fg else c.fg2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).focusRing(bladeShape).zephyrSurface(bladeShape, emphasized = picked)
            .background(Brush.verticalGradient(if (picked)
                listOf(Color(0xFF63E855).copy(alpha = .62f), Color(0xFF08642D).copy(alpha = .4f),
                    Color(0xFF25A94F).copy(alpha = .38f)) else listOf(Color.Transparent, Color.Transparent)))
            .clickable(role = if (group == BladeGroup.SYSTEM || group == BladeGroup.GAMES) Role.Button else Role.Tab,
                onClick = open).semantics { selected = picked }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showIcon) {
                    Icon(icon, null, Modifier.size(17.dp), tint = if (picked) c.acc else c.chrome)
                    Spacer(Modifier.width(7.dp))
                }
                Text(label, Modifier.weight(1f), color = if (picked) c.fg else c.fg2,
                    style = XdText.label.copy(fontSize = 13.sp, lineHeight = 17.sp,
                        fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium),
                    maxLines = 2)
                if (group == BladeGroup.GAMES || group == BladeGroup.SYSTEM) {
                    Icon(XdIcons.chevD, null, Modifier.size(12.dp), tint = if (picked) c.onAction else c.fg2)
                }
            }
            // A geometric selection mark accompanies color; the complete shape is the touch target.
            if (picked) Box(Modifier.padding(top = 5.dp).width(26.dp).height(2.dp).background(Brush.horizontalGradient(listOf(c.chrome, c.acc))))
        }
        if (group == BladeGroup.SYSTEM && expanded) {
            ZephyrSystemHub { expanded = false }
        }
        DropdownMenu(expanded && group == BladeGroup.GAMES, onDismissRequest = { expanded = false }, modifier = Modifier.background(c.s1)) {
            listOf(XdArea.GAMES, XdArea.COLLECTIONS).forEach { area ->
                DropdownMenuItem(
                    text = { Text(stringResource(area.label), color = c.fg) },
                    leadingIcon = { Icon(area.icon, null, tint = c.fg2) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { selected = current == area },
                    onClick = { expanded = false; nav.area(area) },
                )
            }
        }
    }
}

@Composable
private fun ZephyrSystemHub(onDismiss: () -> Unit) {
    val nav = LocalXdNavigator.current
    val destinations = listOf(
        Triple(XdIcons.gear, R.string.xd_guide_settings) { nav.area(XdArea.SETTINGS) },
        Triple(XdIcons.gamepad, R.string.xd_area_controls) { nav.area(XdArea.CONTROLS) },
        Triple(XdIcons.chip, R.string.xd_area_drivers) { nav.area(XdArea.DRIVERS) },
        Triple(XdIcons.bug, R.string.xd_guide_diagnostics) { nav.shortcut(XdShortcut.DIAGNOSTICS) },
        Triple(XdIcons.ab, R.string.xd_guide_compare) { nav.shortcut(XdShortcut.COMPARE) },
        Triple(XdIcons.info, R.string.xd_guide_about) { nav.shortcut(XdShortcut.ABOUT) },
    )
    XdSheet(onDismiss = onDismiss, title = stringResource(R.string.zephyr_ui2_system), wide = true) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 620.dp) 3 else if (maxWidth >= 290.dp) 2 else 1
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in destinations.chunked(columns)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((icon, title, route) in row) {
                        Column(Modifier.weight(1f).heightIn(min = 88.dp).focusRing(ZephyrPanelShape)
                            .zephyrSurface().clickable(role = Role.Button) { onDismiss(); route() }.padding(9.dp),
                            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon, null, Modifier.size(22.dp), tint = Xd.colors.acc)
                            Text(stringResource(title), style = XdText.labelSm, color = Xd.colors.fg,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
