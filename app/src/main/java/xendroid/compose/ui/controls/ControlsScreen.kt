package xendroid.compose.ui.controls

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.net.Inet4Address
import kotlinx.coroutines.launch
import xendroid.compose.R
import xendroid.compose.gamepad.ControlOptions
import xendroid.compose.gamepad.ControlOptionsStore
import xendroid.compose.gamepad.ControllerTestModel
import xendroid.compose.gamepad.GamepadConfigDto
import xendroid.compose.gamepad.GamepadController
import xendroid.compose.gamepad.GamepadGlobalsDto
import xendroid.compose.gamepad.GyroAim
import xendroid.compose.gamepad.GyroSensitivity
import xendroid.compose.gamepad.LayoutPresetsPanel
import xendroid.compose.gamepad.RumbleIntensity
import xendroid.compose.gamepad.SplitScreenMode
import xendroid.compose.gamepad.TestedDevice
import xendroid.compose.gamepad.splitScreenLabel
import xendroid.compose.settings.ConfigValueShape
import xendroid.compose.settings.Setting
import xendroid.compose.settings.SettingCatalog
import xendroid.compose.settings.SettingGroup
import xendroid.compose.settings.SettingLevelStore
import xendroid.compose.settings.SettingsSchema
import xendroid.compose.settings.SettingsViewModel
import xendroid.compose.ui.design.LocalXdToast
import xendroid.compose.ui.design.NoteTone
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdArea
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdButtonSize
import xendroid.compose.ui.design.XdCard
import xendroid.compose.ui.design.XdGroupHeader
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdKv
import xendroid.compose.ui.design.XdListRow
import xendroid.compose.ui.design.XdNote
import xendroid.compose.ui.design.XdSection
import xendroid.compose.ui.design.XdSectionedScreen
import xendroid.compose.ui.design.XdSegmented
import xendroid.compose.ui.design.XdSwitch
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.XdTwoColumns
import xendroid.compose.ui.design.focusRing
import xendroid.compose.ui.keymap.KeymapUiState
import xendroid.compose.ui.keymap.KeymapViewModel
import xendroid.compose.ui.rumbleLabel
import xendroid.compose.ui.settings.GlobalSettingsEditing
import xendroid.compose.ui.settings.XdControllerSettingRow
import xendroid.compose.ui.settings.XdSettingRow

/** Where the Controls area's tools are. */
class ControlsLinks(
    val onKeymap: () -> Unit = {},
    val onEditor: () -> Unit = {},
    val onTest: () -> Unit = {},
    val onPhone: () -> Unit = {},
)

object ControlsSections {
    const val OVERVIEW = "ov"
    const val STATUS = "input-status"
    const val TOUCH = "touch"
    const val PADS = "pads"
    const val MOTION = "motion"
    const val PHONES = "phones"
}

/**
 * The Controls area (lote 3): who plays as P1–P4, the touch controls' options and saved
 * layouts, each physical controller with its own vibration, the core's settings for
 * controllers, gyroscope and unbuffered input, phones as controllers, and the four tools
 * (key mapping, touch editor, controller test, this phone as a controller).
 */
@Composable
fun ControlsScreen(
    global: SettingsViewModel,
    keymap: KeymapViewModel,
    onBack: (() -> Unit)?,
    links: ControlsLinks,
    initialSection: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = LocalXdToast.current
    val layouts = remember { GamepadController(context.applicationContext) }
    val optionsStore = remember { ControlOptionsStore(context.applicationContext) }
    val cfg by layouts.config.collectAsStateWithLifecycle(initialValue = null)
    val options by optionsStore.options.collectAsStateWithLifecycle(initialValue = ControlOptions())
    val values by global.values.collectAsStateWithLifecycle()
    val ready by global.ready.collectAsStateWithLifecycle()
    val km by keymap.state.collectAsStateWithLifecycle()
    val devices = rememberInputDevices()
    val rumble = rememberPadRumble()

    // The core's config: a durable write when leaving, read again on return.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> global.flush()
                Lifecycle.Event.ON_RESUME -> global.onResume()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); global.flush() }
    }

    val editing = remember(global, values) { GlobalSettingsEditing(global, values) }
    val overlaySetting = SettingsSchema.byKey["HID|show_touch_overlay"]
    val overlayOn = overlaySetting?.let { ConfigValueShape.parseBool(editing.raw(it), true) } ?: true
    val globals = cfg?.globals ?: GamepadGlobalsDto()
    val touchShown = overlayOn && globals.enabled

    fun failed(e: Throwable) = toast.show(context.getString(R.string.xd_tg_save_failed, e.message ?: e.javaClass.simpleName))
    fun editLayouts(change: (GamepadConfigDto) -> GamepadConfigDto) {
        scope.launch { runCatching { layouts.update(change) }.onFailure(::failed) }
    }
    fun editGlobals(change: (GamepadGlobalsDto) -> GamepadGlobalsDto) = editLayouts { it.copy(globals = change(it.globals)) }
    fun editOptions(change: (ControlOptions) -> ControlOptions) {
        scope.launch { runCatching { optionsStore.update(change) }.onFailure(::failed) }
    }
    fun setOverlay(on: Boolean) {
        overlaySetting?.let { editing.set(it, ConfigValueShape.bool(on)) }
        if (on && !globals.enabled) editGlobals { it.copy(enabled = true) }
    }

    var section by rememberSaveable { mutableStateOf(initialSection ?: ControlsSections.OVERVIEW) }
    val groupControls = stringResource(R.string.xd_ctl_group_controls)
    val groupTools = stringResource(R.string.xd_ctl_group_tools)
    val pads = devices.pads
    val baseSections = listOf(
        XdSection(ControlsSections.OVERVIEW, stringResource(R.string.xd_ctl_sec_overview), XdIcons.gamepad, group = groupControls,
            heading = stringResource(R.string.xd_ctl_title)) {
            Overview(pads, rumble, options, globals, touchShown, km, cfg?.presets?.size ?: 0, onSection = { section = it }, links)
        },
        XdSection(ControlsSections.TOUCH, stringResource(R.string.xd_ctl_sec_touch), XdIcons.hand,
            heading = stringResource(R.string.xd_ctl_touch_title), lead = stringResource(R.string.xd_ctl_touch_lead)) {
            TouchOptionRows(globals, ::editGlobals, overlay = touchShown, onOverlay = ::setOverlay,
                camera = options.touchCamera, onCamera = { on -> editOptions { it.copy(touchCamera = on) } })
            Spacer(Modifier.size(14.dp))
            XdCard(title = stringResource(R.string.xd_tg_layouts), icon = XdIcons.layers, trailing = (cfg?.presets?.size ?: 0).toString()) {
                val current = cfg
                if (current != null) LayoutPresetsPanel(current, editScope = null, landscape = true, onEdit = ::editLayouts, onMessage = { toast.show(it) })
                Text(stringResource(R.string.xd_tg_layouts_note), style = XdText.note, color = Xd.colors.fg3)
            }
            Spacer(Modifier.size(12.dp))
            XdButton(stringResource(R.string.xd_ctl_tile_editor), links.onEditor, kind = XdButtonKind.PRIMARY, icon = XdIcons.move)
        },
        XdSection(ControlsSections.PADS, stringResource(R.string.xd_ctl_sec_pads), XdIcons.gamepad, badge = pads.size.takeIf { it > 0 }?.toString()) {
            Pads(devices, rumble, options.rumble, editing, links)
        },
        XdSection(ControlsSections.MOTION, stringResource(R.string.xd_ctl_sec_motion), XdIcons.vibrate) {
            Motion(options, ::editOptions)
        },
        XdSection(ControlsSections.PHONES, stringResource(R.string.xd_ctl_sec_phones), XdIcons.phone,
            heading = stringResource(R.string.xd_ctl_phones_title)) {
            Phones(links)
        },
        XdSection("go-keymap", stringResource(R.string.xd_ctl_tool_keymap), XdIcons.keyboard, group = groupTools, goTo = links.onKeymap),
        XdSection("go-editor", stringResource(R.string.xd_ctl_tool_editor), XdIcons.move, goTo = links.onEditor),
        XdSection("go-test", stringResource(R.string.xd_ctl_tool_test), XdIcons.gamepad, goTo = links.onTest),
        XdSection("go-phone", stringResource(R.string.xd_ctl_tool_phone), XdIcons.phone, goTo = links.onPhone),
    )
    // Keep the complete existing overview reachable behind the compact touch landing page.
    val sections = if (Xd.controller) baseSections else baseSections + XdSection(
        ControlsSections.STATUS, stringResource(R.string.zephyr_controls_status), XdIcons.info,
        heading = stringResource(R.string.zephyr_controls_status), content = baseSections.first().content)
    val padsText = if (pads.isEmpty()) stringResource(R.string.xd_ctl_no_pads) else pluralStringResource(R.plurals.xd_ctl_pads, pads.size, pads.size)
    XdSectionedScreen(
        title = stringResource(R.string.xd_ctl_title), sections = sections, selected = if (Xd.controller && sections.none { it.id == section }) ControlsSections.OVERVIEW else section, onSelect = { section = it },
        area = XdArea.CONTROLS, onBack = onBack, headIcon = XdIcons.gamepad,
        subtitle = padsText + " · " + stringResource(if (touchShown) R.string.xd_ctl_touch_on else R.string.xd_ctl_touch_off),
        touchFrame = { current, history ->
            val status = if (cfg == null || !ready) stringResource(R.string.xd_game_loading)
                else padsText + " · " + stringResource(if (touchShown) R.string.xd_ctl_touch_on else R.string.xd_ctl_touch_off)
            ControlsTouchFrame(sections, current, history, status, { section = it }, onBack)
        },
    )
}

/** Who plays as which player: connected controllers in the order the game takes them. */
private fun playerOf(pads: List<TestedDevice>): List<TestedDevice?> {
    val slots = ControllerTestModel.playerSlots(pads)
    return List(4) { i -> pads.firstOrNull { slots[it.id] == i } }
}

@Composable
private fun ColumnScope.Overview(
    pads: List<TestedDevice>,
    rumble: PadRumbleState,
    options: ControlOptions,
    globals: GamepadGlobalsDto,
    touchShown: Boolean,
    km: KeymapUiState,
    layoutCount: Int,
    onSection: (String) -> Unit,
    links: ControlsLinks,
) {
    val c = Xd.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        XdCard(title = stringResource(R.string.xd_ctl_who), icon = XdIcons.gamepad, trailing = stringResource(R.string.xd_ctl_who_order)) {
            val players = playerOf(pads)
            BoxWithConstraints {
                val columns = if (maxWidth >= 560.dp) 4 else 2
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    players.withIndex().chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (i, pad) -> Slot(i, pad, rumble, options.rumble, Modifier.weight(1f)) }
                        }
                    }
                }
            }
            XdNote(stringResource(if (globals.hideWithController) R.string.xd_ctl_touch_p1_hide else R.string.xd_ctl_touch_p1_keep), tone = NoteTone.INFO)
        }

        val kmChanged = km.changed.size
        val kmText = pluralStringResource(R.plurals.xd_ctl_keys_changed, kmChanged, kmChanged) +
            if (km.shared.isNotEmpty()) " · " + pluralStringResource(R.plurals.xd_ctl_keys_shared, km.shared.size, km.shared.size) else ""
        val editorText = listOf(
            stringResource(if (touchShown) R.string.xd_ctl_on_screen else R.string.xd_ctl_hidden),
            stringResource(R.string.xd_ctl_opacity_value, percent(globals.opacity)),
            pluralStringResource(R.plurals.xd_ctl_layouts, layoutCount, layoutCount),
        ).joinToString(" · ")
        val testText = pluralStringResource(R.plurals.xd_ctl_connected, pads.size, pads.size) + " · " + stringResource(R.string.xd_ctl_test_sub)
        val tiles = listOf(
            Tile(XdIcons.keyboard, stringResource(R.string.lib_menu_keymap), kmText, links.onKeymap),
            Tile(XdIcons.move, stringResource(R.string.xd_ctl_tile_editor), editorText, links.onEditor),
            Tile(XdIcons.gamepad, stringResource(R.string.lib_menu_test_controllers), testText, links.onTest),
            Tile(XdIcons.phone, stringResource(R.string.lib_menu_phone_controller), stringResource(R.string.xd_ctl_phone_sub), links.onPhone),
        )
        if (c.controller) Column {
            tiles.forEach { t ->
                XdListRow(t.title, subtitle = t.sub, icon = t.icon, onClick = t.onClick) {
                    Icon(XdIcons.chevR, null, Modifier.size(18.dp), tint = c.fg3)
                }
            }
        } else BoxWithConstraints {
            val columns = if (maxWidth >= 640.dp) 4 else 2
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tiles.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { TileBox(it, Modifier.weight(1f)) } }
                }
            }
        }

        BoxWithConstraints {
            XdTwoColumns(wide = maxWidth >= 560.dp, left = {
                XdCard(Modifier.fillMaxWidth(), title = stringResource(R.string.xd_ctl_card_screen), icon = XdIcons.hand) {
                    val hide = globals.autoHideSeconds.toInt()
                    XdKv(listOf(
                        stringResource(R.string.xd_ctl_touch_title) to stringResource(if (touchShown) R.string.xd_on else R.string.xd_off),
                        stringResource(R.string.xd_tg_autohide) to if (hide <= 0) stringResource(R.string.xd_off) else stringResource(R.string.xd_tg_seconds, hide),
                        stringResource(R.string.ge_split) to stringResource(splitScreenLabel(SplitScreenMode.parse(globals.splitScreen))),
                        stringResource(R.string.xd_tg_camera) to stringResource(if (options.touchCamera) R.string.xd_on else R.string.xd_off),
                    ))
                    XdButton(stringResource(R.string.xd_ctl_touch_settings), { onSection(ControlsSections.TOUCH) }, size = XdButtonSize.SM)
                }
            }, right = {
                XdCard(Modifier.fillMaxWidth(), title = stringResource(R.string.xd_ctl_card_motion), icon = XdIcons.vibrate) {
                    val own = pads.mapNotNull { pad -> rumble.own(pad.descriptor)?.let { pad.name to it } }
                    XdKv(listOf(stringResource(R.string.xd_ctl_kv_rumble) to rumbleLabel(options.rumble)) +
                        own.map { (name, intensity) -> name to stringResource(R.string.xd_ctl_kv_own, rumbleLabel(intensity)) } +
                        listOf(
                            stringResource(R.string.xd_mo_gyro_aim) to aimLabel(options.gyroAim),
                            stringResource(R.string.xd_mo_unbuffered) to stringResource(if (options.unbufferedInput) R.string.xd_on else R.string.xd_off),
                        ))
                    XdButton(stringResource(R.string.xd_open), { onSection(ControlsSections.MOTION) }, size = XdButtonSize.SM)
                }
            })
        }
    }
}

@Composable
private fun Slot(index: Int, pad: TestedDevice?, rumble: PadRumbleState, default: RumbleIntensity, modifier: Modifier) {
    val c = Xd.colors
    val shape = RoundedCornerShape(12.dp)
    val touchP1 = pad == null && index == 0
    Column(
        modifier.clip(shape)
            .then(if (pad != null || touchP1) Modifier.background(c.s2) else Modifier.border(1.dp, c.line2, shape))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("P${index + 1}", style = XdText.monoNum, color = if (pad != null || touchP1) c.acc else c.fg3)
        val name = when {
            pad != null -> pad.name
            touchP1 -> stringResource(R.string.xd_ctl_slot_touch)
            else -> stringResource(R.string.xd_ctl_slot_free)
        }
        Text(name, style = XdText.label, color = if (pad != null || touchP1) c.fg else c.fg3, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val sub = when {
            pad != null && !pad.canVibrate -> stringResource(R.string.xd_ctl_slot_no_motor)
            pad != null -> stringResource(R.string.xd_ctl_slot_rumble, rumbleLabel(rumble.own(pad.descriptor) ?: default).lowercase())
            touchP1 -> stringResource(R.string.xd_ctl_slot_touch_sub)
            else -> stringResource(R.string.xd_ctl_slot_other)
        }
        Text(sub, style = XdText.small, color = c.fg3, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private class Tile(val icon: ImageVector, val title: String, val sub: String, val onClick: () -> Unit)

@Composable
private fun TileBox(t: Tile, modifier: Modifier) {
    val c = Xd.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.focusRing(shape).clip(shape).background(c.s1).clickable(role = Role.Button, onClick = t.onClick)
            .heightIn(min = 118.dp).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(t.icon, null, Modifier.size(24.dp), tint = c.acc)
        Text(t.title, style = XdText.label, color = c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(t.sub, style = XdText.small, color = c.fg3, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun aimLabel(aim: GyroAim): String = stringResource(when (aim) {
    GyroAim.ALWAYS -> R.string.xd_mo_aim_always
    GyroAim.WHILE_LT -> R.string.xd_mo_aim_lt
    GyroAim.WHILE_LB -> R.string.xd_mo_aim_lb
})

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Pads(devices: InputDevicesNow, rumble: PadRumbleState, default: RumbleIntensity, editing: GlobalSettingsEditing, links: ControlsLinks) {
    val context = LocalContext.current
    val c = Xd.colors
    val toast = LocalXdToast.current
    val slots = ControllerTestModel.playerSlots(devices.pads)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (devices.pads.isEmpty()) XdNote(stringResource(R.string.ct_none), tone = NoteTone.INFO)
        devices.pads.forEach { pad ->
            XdCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(XdIcons.gamepad, null, Modifier.size(18.dp), tint = c.fg3)
                    Text(slots[pad.id]?.let { "P${it + 1}" } ?: "—", style = XdText.monoNum, color = c.acc)
                    Text(pad.name, style = XdText.label, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                XdKv(listOf(
                    stringResource(R.string.xd_pad_id) to "%04X:%04X · %s".format(pad.vendor, pad.product, pad.sources.joinToString(", ")),
                    stringResource(R.string.xd_pad_gyro) to stringResource(if (pad.hasGyro) R.string.xd_pad_gyro_yes else R.string.xd_pad_gyro_no),
                ))
                val own = rumble.own(pad.descriptor)
                val effective = own ?: default
                if (pad.canVibrate) {
                    OptionRow(stringResource(R.string.xd_pad_rumble), null, stack = true) {
                        XdSegmented(listOf<Pair<RumbleIntensity?, String>>(null to stringResource(R.string.xd_pad_rumble_default, rumbleLabel(default))) +
                            RumbleIntensity.entries.map { it to rumbleLabel(it) }, own, { intensity ->
                            rumble.set(pad.descriptor, intensity)
                            toast.show(context.getString(R.string.xd_pad_rumble_set, pad.name,
                                (intensity?.let { rumbleText(context, it) } ?: context.getString(R.string.ct_rumble_default))))
                        })
                    }
                } else XdNote(stringResource(R.string.ct_no_motor))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    XdButton(stringResource(R.string.xd_pad_test), links.onTest, size = XdButtonSize.SM, icon = XdIcons.gamepad)
                    if (pad.canVibrate) XdButton(stringResource(R.string.ct_vibrate), { InputDevices.vibrate(pad.id, effective) },
                        kind = XdButtonKind.GHOST, size = XdButtonSize.SM, icon = XdIcons.vibrate, enabled = effective != RumbleIntensity.OFF)
                }
            }
        }
        if (devices.keyboards.isNotEmpty()) XdCard(Modifier.fillMaxWidth(), title = stringResource(R.string.xd_pad_others), icon = XdIcons.keyboard) {
            devices.keyboards.forEachIndexed { i, k ->
                XdListRow(k.name, subtitle = stringResource(R.string.xd_pad_keyboard_sub), icon = XdIcons.keyboard, divider = i < devices.keyboards.lastIndex) {
                    XdButton(stringResource(R.string.xd_pad_keymap), links.onKeymap, kind = XdButtonKind.GHOST, size = XdButtonSize.SM)
                }
            }
        }
        XdCard(Modifier.fillMaxWidth(), title = stringResource(R.string.xd_pad_core), icon = XdIcons.sliders, trailing = stringResource(R.string.xd_pad_core_scope)) {
            val level = remember { SettingLevelStore.read(context) }
            val core = SettingCatalog.settings(SettingGroup.INPUT, level).filter { it.key != "HID|show_touch_overlay" }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                core.forEach { s: Setting ->
                    if (c.controller) XdControllerSettingRow(s, editing)
                    else XdSettingRow(s, editing, showDescription = true, pinned = false, onPin = null)
                }
            }
            XdNote(stringResource(R.string.xd_pad_core_note))
        }
    }
}

private fun rumbleText(context: Context, intensity: RumbleIntensity): String = context.getString(when (intensity) {
    RumbleIntensity.OFF -> R.string.rumble_off
    RumbleIntensity.LOW -> R.string.rumble_low
    RumbleIntensity.MEDIUM -> R.string.rumble_medium
    RumbleIntensity.HIGH -> R.string.rumble_high
}).lowercase()

@Composable
private fun Motion(options: ControlOptions, edit: ((ControlOptions) -> ControlOptions) -> Unit) {
    val context = LocalContext.current
    val hasGyro = remember {
        runCatching { context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null }.getOrDefault(false)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        XdGroupHeader(stringResource(R.string.xd_mo_rumble_group), modifier = Modifier.padding(top = 0.dp))
        OptionRow(stringResource(R.string.xd_mo_rumble), stringResource(R.string.xd_mo_rumble_desc), stack = true) {
            XdSegmented(RumbleIntensity.entries.map { it to rumbleLabel(it) }, options.rumble, { r -> edit { it.copy(rumble = r) } })
        }
        XdGroupHeader(stringResource(R.string.xd_mo_gyro_group))
        if (!hasGyro) XdNote(stringResource(R.string.xd_mo_no_gyro), tone = NoteTone.INFO)
        OptionRow(stringResource(R.string.xd_mo_gyro_camera), stringResource(R.string.xd_mo_gyro_camera_desc)) {
            XdSwitch(options.gyroCamera, { on -> edit { it.copy(gyroCamera = on) } })
        }
        OptionRow(stringResource(R.string.xd_mo_gyro_aim), stringResource(R.string.xd_mo_gyro_aim_desc), stack = true) {
            XdSegmented(GyroAim.entries.map { it to aimLabel(it) }, options.gyroAim, { a -> edit { it.copy(gyroAim = a) } })
        }
        OptionRow(stringResource(R.string.xd_mo_gyro_sens), stringResource(R.string.xd_mo_gyro_sens_desc), stack = true) {
            XdSegmented(listOf(GyroSensitivity.LOW to stringResource(R.string.xd_mo_low), GyroSensitivity.NORMAL to stringResource(R.string.xd_mo_normal),
                GyroSensitivity.HIGH to stringResource(R.string.xd_mo_high)), options.gyroSensitivity, { g -> edit { it.copy(gyroSensitivity = g) } })
        }
        if (hasGyro) XdNote(stringResource(R.string.xd_mo_calibrate))
        XdGroupHeader(stringResource(R.string.xd_mo_input_group))
        OptionRow(stringResource(R.string.xd_mo_unbuffered), stringResource(R.string.xd_mo_unbuffered_desc)) {
            XdSwitch(options.unbufferedInput, { on -> edit { it.copy(unbufferedInput = on) } }, enabled = Build.VERSION.SDK_INT >= 30)
        }
        XdNote(stringResource(R.string.xd_mo_note), modifier = Modifier.padding(top = 8.dp), tone = NoteTone.INFO)
    }
}

/** This phone's Wi-Fi address, or null when it is not on Wi-Fi. */
private fun wifiAddress(context: Context): String? = runCatching {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
    val network = cm.activeNetwork ?: return null
    val caps = cm.getNetworkCapabilities(network) ?: return null
    if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
    cm.getLinkProperties(network)?.linkAddresses?.map { it.address }
        ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
}.getOrNull()

@Composable
private fun Phones(links: ControlsLinks) {
    val context = LocalContext.current
    val c = Xd.colors
    val address = remember { wifiAddress(context) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoxWithConstraints {
            XdTwoColumns(wide = maxWidth >= 560.dp, left = {
                XdCard(Modifier.fillMaxWidth(), title = stringResource(R.string.xd_ph_this), icon = XdIcons.phone) {
                    Text(stringResource(R.string.xd_ph_this_text), style = XdText.bodySm, color = c.fg2)
                    XdButton(stringResource(R.string.lib_menu_phone_controller), links.onPhone, kind = XdButtonKind.PRIMARY, size = XdButtonSize.SM)
                }
            }, right = {
                XdCard(Modifier.fillMaxWidth(), title = stringResource(R.string.xd_ph_here), icon = XdIcons.wifi) {
                    Text(stringResource(R.string.xd_ph_here_text), style = XdText.bodySm, color = c.fg2)
                    XdKv(listOf(stringResource(R.string.xd_ph_network) to
                        (address?.let { stringResource(R.string.xd_ph_net_wifi, it) } ?: stringResource(R.string.xd_ph_net_other))))
                }
            })
        }
        XdNote(stringResource(R.string.xd_ph_note), modifier = Modifier.widthIn(max = 720.dp))
    }
}
