package xendroid.compose.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xendroid.compose.R
import xendroid.compose.settings.GameOverridesIndex
import xendroid.compose.settings.PinnedSettings
import xendroid.compose.settings.Setting
import xendroid.compose.settings.SettingCatalog
import xendroid.compose.settings.SettingLevelStore
import xendroid.compose.settings.SettingsSchema
import xendroid.compose.settings.SettingsViewModel
import xendroid.compose.ui.design.GameCover
import xendroid.compose.ui.design.LocalXdToast
import xendroid.compose.ui.design.NoteTone
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdArea
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdButtonSize
import xendroid.compose.ui.design.XdCard
import xendroid.compose.ui.design.XdEditorialHeading
import xendroid.compose.ui.design.XdSilverPanel
import xendroid.compose.ui.design.XdEmpty
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdKv
import xendroid.compose.ui.design.XdLink
import xendroid.compose.ui.design.XdListRow
import xendroid.compose.ui.design.XdNote
import xendroid.compose.ui.design.XdSearchField
import xendroid.compose.ui.design.XdSection
import xendroid.compose.ui.design.XdSectionedScreen
import xendroid.compose.ui.design.XdSheet
import xendroid.compose.ui.design.XdSingleScreen
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.XdToml
import xendroid.compose.ui.design.XdTwoColumns

/** Where Settings leads: the Drivers area, a game's settings, diagnostics and tests, About. */
class SettingsLinks(
    val onDrivers: (() -> Unit)? = null,
    /** A game's sheet at its settings, by Title ID; null when the library is not at hand. */
    val onGameSettings: ((String) -> Unit)? = null,
    val onDiagnostics: () -> Unit = {},
    val onCompare: () -> Unit = {},
    val onControllerTest: () -> Unit = {},
    val onAbout: () -> Unit = {},
    /** The library's name and art of a Title ID (null: not in the library). */
    val gameName: (String) -> String? = { null },
    val gameArt: (String) -> Any? = { null },
)

private object Ids {
    const val SUMMARY = "resumo"
    const val UI = "app:ui"
    const val LANGUAGE = "app:lang"
    const val DATA = "app:data"
    const val UPDATES = "app:upd"
    const val COMMUNITY = "app:comm"
    const val DIAGNOSTICS = "app:diag"
    const val ABOUT = "app:about"
}

/**
 * Settings (docs/ui-redesign/bc, batch 1): everything that holds for every game. A summary of
 * what is away from the defaults and of the games with values of their own; the same groups of
 * settings as the game sheet, now global (each says how many games use another value); and the
 * app's own options (interface, language, data and backup, updates, community, diagnostics).
 */
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, links: SettingsLinks = SettingsLinks(), initialSection: String? = null) {
    val context = LocalContext.current
    val values by vm.values.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val gameValues by vm.gameValues.collectAsStateWithLifecycle()

    // Durable flush on pause; re-open on resume (the games' own values may have changed too).
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> vm.flush()
                Lifecycle.Event.ON_RESUME -> vm.onResume()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); vm.flush() }
    }

    if (!ready) {
        XdSingleScreen(stringResource(R.string.xd_set_title), area = XdArea.SETTINGS, onBack = onBack, headIcon = XdIcons.gear) {
            if (error != null) {
                XdNote(stringResource(R.string.set_config_failed), tone = NoteTone.ERROR)
                XdButton(stringResource(R.string.common_retry), vm::onResume, kind = XdButtonKind.PRIMARY)
            } else XdEmpty(stringResource(R.string.xd_game_loading))
        }
        return
    }
    error?.let {
        XdSheet(onDismiss = vm::clearError, title = null, actions = {
            XdButton(stringResource(R.string.common_close), vm::clearError, kind = XdButtonKind.GHOST)
            XdButton(stringResource(R.string.set_retry_save), { vm.clearError(); vm.flush() }, kind = XdButtonKind.PRIMARY)
        }) { XdNote(stringResource(R.string.set_config_failed), tone = NoteTone.ERROR) }
    }

    val overriding = remember(gameValues) { GameOverridesIndex.byKey(gameValues) }
    val editing = remember(vm, values, overriding) { GlobalSettingsEditing(vm, values, overriding) }
    val panel = remember { SettingsPanelState(SettingLevelStore.read(context)) }
    var pinned by remember { mutableStateOf(PinnedSettings.read(context)) }
    val onPinned: (List<String>) -> Unit = { pinned = it; PinnedSettings.write(context, it) }
    var section by rememberSaveable { mutableStateOf(initialSection ?: Ids.SUMMARY) }
    var overridingOf by remember { mutableStateOf<Setting?>(null) }
    var tomlOpen by remember { mutableStateOf(false) }

    val groupGeneral = stringResource(R.string.xd_set_group_general)
    val groupEmulation = stringResource(R.string.xd_set_group_emulation)
    val groupApp = stringResource(R.string.xd_set_group_app)
    val jump: (xendroid.compose.settings.SettingGroup) -> Unit = { section = "set:${it.name}" }
    val panelFor: @Composable (xendroid.compose.settings.SettingGroup?) -> Unit = { group ->
        XdSettingsPanel(editing, panel, pinned, onPinned, group = group, onToml = { tomlOpen = true },
            onOpenDrivers = links.onDrivers, onShowOverriding = { overridingOf = it }, onJump = jump)
    }
    val sections = buildList {
        add(XdSection(Ids.SUMMARY, stringResource(R.string.xd_set_summary), XdIcons.home, group = groupGeneral,
            heading = if (Xd.controller) stringResource(R.string.xd_set_summary) else null) {
            if (!Xd.controller && panel.query.isBlank()) {
                XdEditorialHeading(stringResource(R.string.zephyr_ui2_settings_title), stringResource(R.string.xd_set_subtitle))
            }
            // Round 2: the Summary searches every tab, the results by tab.
            XdSearchField(panel.query, { panel.query = it }, stringResource(R.string.xd_set_search_everywhere),
                Modifier.fillMaxWidth().widthIn(max = 560.dp).padding(bottom = 4.dp))
            if (panel.query.isBlank()) Summary(vm, editing, gameValues, links, onToml = { tomlOpen = true }, onSection = { section = it }, panelLevel = panel)
            else XdSettingsPanel(editing, panel, pinned, onPinned, onOpenDrivers = links.onDrivers, onShowOverriding = { overridingOf = it },
                onJump = jump, header = false)
        })
        for (g in SettingCatalog.groups) {
            val changed = SettingCatalog.settings(g).count { editing.changed(it) }
            add(XdSection("set:${g.name}", groupTitle(g), groupIcon(g), group = groupEmulation, badge = changed.takeIf { it > 0 }?.toString()) {
                panelFor(g)
            })
        }
        add(XdSection(Ids.UI, stringResource(R.string.xd_set_interface), XdIcons.monitor, group = groupApp,
            lead = stringResource(R.string.xd_set_interface_lead)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InputModeOption()
                SettingLevelOption(panel.level) { panel.level = it }
                MenuButtonsOption()
                SessionAdviceOption()
                UiScaleOptions()
            }
        })
        add(XdSection(Ids.LANGUAGE, stringResource(R.string.xd_set_language), XdIcons.globe) {
            AppLanguageOption()
            XdLink(stringResource(R.string.xd_set_language_console), { section = "set:${xendroid.compose.settings.SettingGroup.SYSTEM.name}" })
        })
        add(XdSection(Ids.DATA, stringResource(R.string.xd_set_data), XdIcons.save) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                XdDataBundleCard(beforeImport = vm::flushForTransfer, afterImport = { vm.onResume() })
                XdUserDataCard()
            }
        })
        if (xendroid.compose.updater.updateRepository() != null) add(XdSection(Ids.UPDATES, stringResource(R.string.xd_set_updates), XdIcons.download) {
            UpdatesBody()
        })
        add(XdSection(Ids.COMMUNITY, stringResource(R.string.xd_set_community), XdIcons.share) { CommunityBody() })
        add(XdSection(Ids.DIAGNOSTICS, stringResource(R.string.xd_set_diagnostics), XdIcons.bug) {
            DiagnosticsBody(links, onLogLevel = { section = "set:${xendroid.compose.settings.SettingGroup.DEBUG.name}" })
        })
        add(XdSection(Ids.ABOUT, stringResource(R.string.xd_guide_about), XdIcons.info) {
            XdCard {
                XdListRow("${stringResource(R.string.app_name)} ${xendroid.compose.BuildConfig.VERSION_NAME}", icon = XdIcons.info,
                    subtitle = xendroid.compose.core.EmulatorRuntime.gpuDeviceName ?: "", divider = false) {
                    XdButton(stringResource(R.string.xd_set_about_open), links.onAbout, size = XdButtonSize.SM)
                }
            }
        })
    }

    XdSectionedScreen(
        title = stringResource(R.string.xd_set_title),
        sections = sections,
        selected = section,
        onSelect = { section = it },
        area = XdArea.SETTINGS,
        subtitle = stringResource(R.string.xd_set_subtitle),
        onBack = onBack,
        headIcon = XdIcons.gear,
    )

    overridingOf?.let { s -> OverridingSheet(s, gameValues, links) { overridingOf = null } }
    if (tomlOpen) GlobalTomlSheet(editing) { tomlOpen = false }
}

/** "Resumo": what is away from the defaults, the games with their own values, interface and driver. */
@Composable
private fun Summary(
    vm: SettingsViewModel,
    editing: SettingsEditing,
    gameValues: Map<String, Map<String, String>>,
    links: SettingsLinks,
    onToml: () -> Unit,
    onSection: (String) -> Unit,
    panelLevel: SettingsPanelState,
) {
    val c = Xd.colors
    val context = LocalContext.current
    val changed = SettingsSchema.allSettings.filter { it !is Setting.Action || it.name == "vulkan_lib_path" }.filter { editing.changed(it) }
    var lastDriver by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        lastDriver = withContext(Dispatchers.IO) {
            runCatching { xendroid.compose.sessions.SessionRuns.store().runs().firstOrNull { it.driver != null }?.driver?.label }.getOrNull()
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth > 620.dp
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!c.controller) XdSilverPanel {
                XdListRow(stringResource(R.string.xd_set_interface), icon = XdIcons.monitor,
                    onClick = { onSection(Ids.UI) })
                XdListRow(stringResource(R.string.xd_set_data), icon = XdIcons.save,
                    onClick = { onSection(Ids.DATA) })
                XdListRow(stringResource(R.string.xd_set_diagnostics), icon = XdIcons.bug, divider = false,
                    onClick = { onSection(Ids.DIAGNOSTICS) })
            }
            XdCard(title = stringResource(R.string.xd_set_summary_changed), icon = XdIcons.sliders, trailing = changed.size.toString()) {
                if (changed.isEmpty()) Text(stringResource(R.string.xd_set_summary_all_default), style = XdText.note, color = c.fg3)
                changed.forEachIndexed { i, s ->
                    XdListRow(settingTitle(s), icon = XdIcons.globe, divider = i < changed.lastIndex, actionsBelow = !wide,
                        subtitle = stringResource(R.string.xd_set_summary_value, valueLabel(s, editing.raw(s)), valueLabel(s, editing.defaultRaw(s)))) {
                        XdButton(stringResource(R.string.xd_set_summary_default), { editing.reset(s) }, size = XdButtonSize.SM,
                            kind = XdButtonKind.GHOST, icon = XdIcons.reset)
                    }
                }
                XdButton(stringResource(R.string.xd_set_toml_global), onToml, size = XdButtonSize.SM, icon = XdIcons.code)
            }
            XdCard(title = stringResource(R.string.xd_set_summary_games), icon = XdIcons.gamepad, trailing = gameValues.size.toString()) {
                if (gameValues.isEmpty()) Text(stringResource(R.string.xd_set_summary_no_games), style = XdText.note, color = c.fg3)
                gameValues.entries.forEachIndexed { i, (title, own) ->
                    val names = own.keys.mapNotNull { SettingsSchema.byKey[it] }.map { settingTitle(it) }
                    XdListRow(links.gameName(title) ?: title, divider = i < gameValues.size - 1,
                        lead = { GameThumb(links.gameArt(title), links.gameName(title) ?: title) },
                        subtitle = names.take(4).joinToString(" · ") + if (names.size > 4) " · +${names.size - 4}" else "") {
                        if (links.onGameSettings != null && links.gameName(title) != null) {
                            XdButton(stringResource(R.string.xd_data_open), { links.onGameSettings.invoke(title) }, size = XdButtonSize.SM, kind = XdButtonKind.GHOST)
                        }
                    }
                }
            }
            XdTwoColumns(wide,
                left = {
                    XdCard(title = stringResource(R.string.xd_set_interface), icon = XdIcons.monitor) {
                        InputModeOption()
                        SettingLevelOption(panelLevel.level) { panelLevel.level = it }
                    }
                },
                right = {
                    XdCard(title = stringResource(R.string.xd_set_driver), icon = XdIcons.chip) {
                        val s = SettingsSchema.byKey["Vulkan|vulkan_lib_path"]
                        XdKv(listOfNotNull(
                            s?.let { stringResource(R.string.xd_set_driver_all) to driverName(context, editing.raw(it)) },
                            stringResource(R.string.xd_set_driver_last) to (lastDriver ?: "—"),
                        ))
                        if (links.onDrivers != null) XdButton(stringResource(R.string.xd_drv_manage), links.onDrivers, size = XdButtonSize.SM, icon = XdIcons.chip)
                    }
                },
            )
        }
    }
}

@Composable
private fun GameThumb(art: Any?, name: String) {
    Box(Modifier.width(30.dp)) { GameCover(art ?: R.drawable.zephyr_app_icon, name, smart = true, radius = 4.dp) }
}

/** The games with their own value of [s]: the global value does not hold for them. */
@Composable
private fun OverridingSheet(s: Setting, gameValues: Map<String, Map<String, String>>, links: SettingsLinks, onDismiss: () -> Unit) {
    val games = gameValues.filterValues { s.key in it }
    XdSheet(onDismiss = onDismiss, title = settingTitle(s), subtitle = stringResource(R.string.xd_set_overriding_sub),
        actions = { XdButton(stringResource(R.string.xd_done), onDismiss, kind = XdButtonKind.PRIMARY) }) {
        Column {
            games.entries.forEachIndexed { i, (title, own) ->
                XdListRow(links.gameName(title) ?: title, divider = i < games.size - 1,
                    lead = { GameThumb(links.gameArt(title), links.gameName(title) ?: title) },
                    subtitle = stringResource(R.string.xd_set_overriding_uses, valueLabel(s, own.getValue(s.key)))) {
                    if (links.onGameSettings != null && links.gameName(title) != null) {
                        XdButton(stringResource(R.string.xd_set_overriding_open), { onDismiss(); links.onGameSettings.invoke(title) },
                            size = XdButtonSize.SM, kind = XdButtonKind.GHOST)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalTomlSheet(editing: SettingsEditing, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val toast = LocalXdToast.current
    val text = tomlPreview(editing, listOf(stringResource(R.string.xd_toml_global_header)), stringResource(R.string.xd_toml_none))
    XdSheet(onDismiss = onDismiss, title = stringResource(R.string.xd_toml_global_title), subtitle = stringResource(R.string.xd_toml_global_sub),
        wide = true, actions = {
            XdButton(stringResource(R.string.xd_copy), {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("TOML", text))
                toast.show(context.getString(R.string.xd_copied))
            }, icon = XdIcons.copy)
            XdButton(stringResource(R.string.xd_done), onDismiss, kind = XdButtonKind.PRIMARY)
        }) { XdToml(text) }
}

@Composable
private fun UpdatesBody() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Lote 6: what the check finds shows here, with its steps (no dialog).
    val update = xendroid.compose.updater.rememberUpdateState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        XdUpdateChannelOption()
        XdCard(title = stringResource(R.string.xd_set_version), icon = XdIcons.download) {
            val last = xendroid.compose.updater.lastUpdateCheck(context)
            XdKv(listOf(
                stringResource(R.string.xd_set_version_installed) to xendroid.compose.BuildConfig.VERSION_NAME,
                stringResource(R.string.xd_set_version_checked) to (last?.let {
                    android.text.format.DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()
                } ?: stringResource(R.string.xd_lib_never)),
            ))
            XdButton(stringResource(if (update.checking) R.string.xd_up_checking else R.string.lib_menu_updates), { update.check(context, scope) },
                size = XdButtonSize.SM, icon = XdIcons.refresh, enabled = !update.checking && update.stage == null)
        }
        xendroid.compose.updater.UpdatePanel(update)
    }
}

@Composable
private fun CommunityBody() {
    val c = Xd.colors
    val on = xendroid.compose.community.CommunityConfigs.baseUrl(xendroid.compose.BuildConfig.COMMUNITY_URL) != null
    XdCard(title = stringResource(R.string.xd_set_community_title), icon = XdIcons.share) {
        Text(stringResource(if (on) R.string.xd_set_community_on else R.string.xd_set_community_off), style = XdText.bodySm, color = c.fg)
        Text(stringResource(R.string.xd_set_community_how), style = XdText.note, color = c.fg3)
        Text(stringResource(R.string.xd_set_community_privacy), style = XdText.note, color = c.fg3)
    }
}

@Composable
private fun DiagnosticsBody(links: SettingsLinks, onLogLevel: () -> Unit) {
    XdCard {
        XdListRow(stringResource(R.string.xd_guide_diagnostics), icon = XdIcons.bug, subtitle = stringResource(R.string.xd_set_diag_sub)) {
            XdButton(stringResource(R.string.xd_data_open), links.onDiagnostics, size = XdButtonSize.SM)
        }
        XdListRow(stringResource(R.string.xd_guide_compare), icon = XdIcons.ab, subtitle = stringResource(R.string.xd_set_compare_sub)) {
            XdButton(stringResource(R.string.xd_data_open), links.onCompare, size = XdButtonSize.SM, kind = XdButtonKind.GHOST)
        }
        XdListRow(stringResource(R.string.xd_set_pad_test), icon = XdIcons.gamepad, subtitle = stringResource(R.string.xd_set_pad_test_sub)) {
            XdButton(stringResource(R.string.xd_data_open), links.onControllerTest, size = XdButtonSize.SM, kind = XdButtonKind.GHOST)
        }
        XdListRow(stringResource(R.string.set_export_logs), icon = XdIcons.download, subtitle = stringResource(R.string.set_logs_note)) {
            ExportLogsButton()
        }
        XdListRow(stringResource(R.string.xd_set_log_level), icon = XdIcons.sliders, subtitle = stringResource(R.string.xd_set_log_level_sub), divider = false) {
            XdButton(stringResource(R.string.xd_data_open), onLogLevel, size = XdButtonSize.SM, kind = XdButtonKind.GHOST)
        }
    }
}


