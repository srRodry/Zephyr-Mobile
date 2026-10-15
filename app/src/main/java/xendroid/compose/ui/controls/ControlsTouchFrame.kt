package xendroid.compose.ui.controls

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import xendroid.compose.R
import xendroid.compose.ui.design.*

/** Navigation presentation only. Tool destinations invoke the original callbacks directly. */
private val controlDestinations = listOf(
    "go-keymap" to R.string.zephyr_controls_mapping_note,
    ControlsSections.TOUCH to R.string.zephyr_controls_touch_note,
    "go-editor" to R.string.zephyr_controls_editor_note,
    ControlsSections.PADS to R.string.zephyr_controls_pads_note,
    ControlsSections.MOTION to R.string.zephyr_controls_motion_note,
    "go-test" to R.string.zephyr_controls_test_note,
    ControlsSections.PHONES to R.string.zephyr_controls_phones_note,
    "go-phone" to R.string.zephyr_controls_phone_note,
    ControlsSections.STATUS to R.string.zephyr_controls_status_note,
)

@Composable
internal fun ControlsTouchFrame(
    sections: List<XdSection>, current: XdSection?, history: XdSectionHistory,
    status: String, onSelect: (String) -> Unit, onBack: (() -> Unit)?,
) {
    val c = Xd.colors
    val focus = LocalFocusManager.current
    val home = current?.id == ControlsSections.OVERVIEW
    val open: (XdSection) -> Unit = { section ->
        focus.clearFocus()
        section.goTo?.invoke() ?: onSelect(section.id)
    }
    val back: () -> Unit = {
        focus.clearFocus()
        when {
            home -> onBack?.invoke()
            history.previous != null -> history.back()
            else -> history.pick(ControlsSections.OVERVIEW)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg).testTag("controls-touch-frame")) {
        val compact = maxHeight > maxWidth || maxWidth < 840.dp
        val body: @Composable (Modifier) -> Unit = { modifier ->
            CompositionLocalProvider(LocalXdButtonShape provides RoundedCornerShape(4.dp)) {
                Column(modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing.part(
                    top = !compact, bottom = true, start = compact, end = true))) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!home || onBack != null) XdIconButton(XdIcons.back, stringResource(R.string.xd_back), back,
                            Modifier.testTag("controls-hub-back"))
                        Text(if (home) stringResource(R.string.xd_ctl_title) else current?.title.orEmpty(),
                            Modifier.weight(1f).padding(vertical = 12.dp), style = XdText.h2, color = c.fg)
                        if (!home) XdIconButton(XdIcons.home, stringResource(R.string.xd_ctl_sec_overview),
                            { focus.clearFocus(); history.pick(ControlsSections.OVERVIEW) }, Modifier.testTag("controls-hub-home-button"))
                    }
                    val scroll = rememberSaveable(current?.id, saver = ScrollState.Saver) { ScrollState(0) }
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).testTag("controls-hub-scroll")
                        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (home) {
                            Text(stringResource(R.string.zephyr_controls_intro), style = XdText.body, color = c.fg2)
                            Text(status, Modifier.testTag("controls-hub-status"), style = XdText.labelSm, color = c.acc)
                            ControlsHubRows(sections, open)
                        } else if (current != null) Column(Modifier.fillMaxWidth().testTag("controls-hub-content:${current.id}"),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            current.heading?.takeIf { it != current.title }?.let { Text(it, style = XdText.h2, color = c.fg) }
                            current.lead?.let { Text(it, style = XdText.bodySm, color = c.fg2) }
                            current.content?.invoke(this)
                        }
                    }
                }
            }
        }
        if (compact) Column(Modifier.fillMaxSize()) { BTopAreaBar(XdArea.CONTROLS); body(Modifier.weight(1f)) }
        else Row(Modifier.fillMaxSize()) { BRail(XdArea.CONTROLS); body(Modifier.weight(1f)) }
    }
}

@Composable
private fun ControlsHubRows(sections: List<XdSection>, open: (XdSection) -> Unit) {
    val c = Xd.colors
    val available = controlDestinations.mapNotNull { (id, note) ->
        sections.firstOrNull { it.id == id && (it.goTo != null || it.content != null) }?.let { it to note }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("controls-hub-home")) {
        val columns = if (maxWidth >= 640.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            available.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (section, note) ->
                        Row(Modifier.weight(1f).heightIn(min = 88.dp).testTag("controls-hub-destination:${section.id}")
                            .focusRing(ZephyrPanelShape).clip(ZephyrPanelShape).background(c.s1).border(1.dp, c.line2, ZephyrPanelShape)
                            .clickable(role = Role.Button, onClick = { open(section) }).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(section.icon, null, Modifier.size(24.dp), tint = c.fg2)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(section.title, style = XdText.label, color = c.fg)
                                Text(stringResource(note), style = XdText.bodySm, color = c.fg2)
                            }
                            section.badge?.let { Text(it, style = XdText.monoNum, color = c.acc) }
                            Icon(XdIcons.chevR, null, Modifier.size(16.dp), tint = c.fg2)
                        }
                    }
                    if (columns == 2 && row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
