package xendroid.compose.ui.game

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import xendroid.compose.R
import xendroid.compose.data.Game
import xendroid.compose.data.GameFormat
import xendroid.compose.patches.GamePatchesViewModel
import xendroid.compose.settings.GameSettingsViewModel
import xendroid.compose.settings.PinnedSettings
import xendroid.compose.settings.SettingCatalog
import xendroid.compose.settings.SettingLevelStore
import xendroid.compose.settings.SettingValue
import xendroid.compose.settings.SettingsSchema
import xendroid.compose.settings.SettingsViewModel
import xendroid.compose.ui.compress.GameCompressViewModel
import xendroid.compose.ui.design.GameCover
import xendroid.compose.ui.design.LocalSwapConfirm
import xendroid.compose.ui.design.LocalXdToast
import xendroid.compose.ui.design.PadButton
import xendroid.compose.ui.design.WithCoverColors
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdArea
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdButtonSize
import xendroid.compose.ui.design.XdEmpty
import xendroid.compose.ui.design.XdHint
import xendroid.compose.ui.design.XdIconButton
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdMenuItem
import xendroid.compose.ui.design.XdSection
import xendroid.compose.ui.design.XdSectionedScreen
import xendroid.compose.ui.design.XdSegmented
import xendroid.compose.ui.design.XdSheet
import xendroid.compose.ui.design.XdSheetOption
import xendroid.compose.ui.design.XdToml
import xendroid.compose.ui.design.rememberCoverColors
import xendroid.compose.ui.library.CoverArt
import xendroid.compose.ui.library.GameActionDialogs
import xendroid.compose.ui.library.GameActions
import xendroid.compose.ui.library.GameLibraryViewModel
import xendroid.compose.ui.library.LibraryData
import xendroid.compose.ui.library.LibrarySort
import xendroid.compose.ui.library.formatLabel
import xendroid.compose.ui.library.rememberGameActions
import xendroid.compose.ui.settings.CommunityConfigsCard
import xendroid.compose.ui.settings.CommunityShareDialog
import xendroid.compose.ui.settings.GameSettingsEditing
import xendroid.compose.ui.settings.GlobalSettingsEditing
import xendroid.compose.ui.settings.ProfilePreviewDialog
import xendroid.compose.ui.settings.RecommendedProfilesCard
import xendroid.compose.ui.settings.SettingsEditing
import xendroid.compose.ui.settings.SettingsPanelState
import xendroid.compose.ui.settings.SettingsPresets
import xendroid.compose.ui.settings.XdQuickSettings
import xendroid.compose.ui.settings.XdSettingsPanel
import xendroid.compose.ui.settings.groupIcon
import xendroid.compose.ui.settings.groupTitle
import xendroid.compose.ui.settings.profileMessageText
import xendroid.compose.ui.settings.tomlPreview

/** Where the game sheet leads outside itself. */
class GameScreenLinks(
    val onBack: () -> Unit,
    val onPatches: () -> Unit = {},
    val onContent: () -> Unit = {},
    val onSaves: () -> Unit = {},
    val onDiagnostics: () -> Unit = {},
    val onCompare: () -> Unit = {},
    val onDrivers: (() -> Unit)? = null,
    val onInstallFromDisc: (String) -> Unit = {},
)

/** Touch section ids of the sheet; the controller menu has its own (see [controllerSection]). */
object GameSections {
    const val OVERVIEW = "ov"
    const val SETTINGS = "settings"
    const val PERF = "perf"
    const val CONTENT = "cont"
    const val DATA = "data"
    fun group(g: xendroid.compose.settings.SettingGroup) = "set:${g.name}"
}

private fun controllerSection(id: String): String = when {
    id == GameSections.OVERVIEW -> "play"
    id == GameSections.SETTINGS || id.startsWith("set:") -> "set"
    else -> id
}

private fun touchSection(id: String): String = when (id) {
    "play", "launch", "quick", "compat" -> GameSections.OVERVIEW
    "set" -> GameSections.SETTINGS
    else -> id
}

/**
 * The game sheet as a screen (docs/ui-redesign/bc, Base): Play and "Start with…" always at hand,
 * and sections for the overview, each group of this game's settings (with the scope switch to the
 * global values), performance, patches and content, saves and data. In controller mode the big
 * menu: Play, Start with…, Quick settings, All settings, Performance, Patches and content, Saves
 * and data, Compatibility.
 *
 * [settings] and [patches] are null when the game has no readable Title ID (they are keyed by it).
 */
@Composable
fun GameScreen(
    game: Game,
    library: GameLibraryViewModel,
    compressVm: GameCompressViewModel,
    settings: GameSettingsViewModel?,
    global: SettingsViewModel?,
    patches: GamePatchesViewModel?,
    links: GameScreenLinks,
    initialSection: String? = null,
) {
    val context = LocalContext.current
    val toast = LocalXdToast.current
    val actions = rememberGameActions(library, compressVm, links.onInstallFromDisc)
    val details by library.details.collectAsStateWithLifecycle()
    val favorites by library.favorites.collectAsStateWithLifecycle()
    val activity by library.activity.collectAsStateWithLifecycle()
    val compat by library.compat.collectAsStateWithLifecycle()
    val collections by library.collections.collectAsStateWithLifecycle()
    val coverRevision by library.coverRevision.collectAsStateWithLifecycle()
    val activeProfile by library.activeProfile.collectAsStateWithLifecycle()
    val info = details?.takeIf { it.identityKey == game.identityKey }
    val data = remember(game, favorites, activity, compat, collections) {
        LibraryData(listOf(game), favorites, activity, compat, collections, LibrarySort.NAME_ASC)
    }
    val art = remember(game.identityKey, coverRevision) { CoverArt(library.iconFileOrFallback(game), !library.hasCustomCover(game)) }

    val noOverrides = remember { MutableStateFlow(emptyMap<String, String>()) }
    val overrides by (settings?.overrides ?: noOverrides).collectAsStateWithLifecycle()
    val falseFlow = remember { MutableStateFlow(false) }
    val settingsReady by (settings?.ready ?: falseFlow).collectAsStateWithLifecycle()
    val zero = remember { MutableStateFlow(0) }
    val revision by (settings?.inheritedRevision ?: zero).collectAsStateWithLifecycle()
    val noValues = remember { MutableStateFlow(emptyMap<String, SettingValue>()) }
    val globalValues by (global?.values ?: noValues).collectAsStateWithLifecycle()
    val globalReady by (global?.ready ?: falseFlow).collectAsStateWithLifecycle()
    val noPatches = remember { MutableStateFlow<GamePatchesViewModel.UiState>(GamePatchesViewModel.UiState.Empty) }
    val patchState by (patches?.state ?: noPatches).collectAsStateWithLifecycle()

    val panel = remember { SettingsPanelState(SettingLevelStore.read(context)) }
    LaunchedEffect(panel) { snapshotFlow { panel.level }.drop(1).collect { SettingLevelStore.write(context, it) } }
    var pinned by remember { mutableStateOf(PinnedSettings.read(context)) }
    val onPinned: (List<String>) -> Unit = { pinned = it; PinnedSettings.write(context, it) }

    val gameEditing: SettingsEditing? = settings?.takeIf { settingsReady }?.let { vm ->
        remember(vm, overrides, revision) { GameSettingsEditing(vm, overrides) }
    }
    val globalEditing: SettingsEditing? = global?.takeIf { globalReady }?.let { vm ->
        remember(vm, globalValues) { GlobalSettingsEditing(vm, globalValues) }
    }
    val editing = if (panel.editGlobal && globalEditing != null) globalEditing else gameEditing

    // Durable writes on pause (both scopes), fresh reads on resume; the last run may be new.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, settings, global) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> { settings?.flush(); global?.flush() }
                Lifecycle.Event.ON_RESUME -> { settings?.onResume(); global?.onResume(); library.loadDetails(game) }
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); settings?.flush(); global?.flush() }
    }
    LaunchedEffect(game.identityKey) { library.loadDetails(game) }

    var section by rememberSaveable { mutableStateOf(initialSection ?: GameSections.OVERVIEW) }
    val controller = Xd.controller
    val shown = if (controller) controllerSection(section) else touchSection(section)
    val launchState = remember(game.identityKey) { LaunchFormState() }
    var launchOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var presetsOpen by remember { mutableStateOf(false) }
    var tomlOpen by remember { mutableStateOf(false) }
    val patchCounts = (patchState as? GamePatchesViewModel.UiState.Loaded)?.let { s ->
        s.files.sumOf { f -> f.entries.count { it.isEnabled } } to s.files.sumOf { it.entries.size }
    } ?: info?.patchesTotal?.let { (info.patchesEnabled ?: 0) to it }
    val play: () -> Unit = { actions.play(game) }
    val playWith: () -> Unit = {
        actions.play(game, launchState.options(overrides) { key -> SettingsSchema.byKey[key]?.let { settings?.inheritedRaw(it) } })
    }
    val resetAll: () -> Unit = {
        val vm = settings
        if (vm != null) {
            val before = vm.resetAll()
            toast.show(context.getString(R.string.xd_set_reset_all_done, game.name), context.getString(R.string.xd_undo)) { vm.restore(before) }
        }
    }
    val scopeSwitch: (@Composable () -> Unit)? = if (globalEditing == null || gameEditing == null) null else ({
        XdSegmented(
            listOf(false to stringResource(R.string.xd_set_scope_game, overrides.size), true to stringResource(R.string.xd_set_scope_global)),
            panel.editGlobal,
            { toGlobal ->
                if (panel.editGlobal && !toGlobal) { global?.flush(); settings?.reloadInherited() }
                panel.editGlobal = toGlobal
            },
            accent = true,
        )
    })
    val settingsPanel: @Composable (xendroid.compose.settings.SettingGroup?) -> Unit = { group ->
        val e = editing
        if (e == null) NoSettings(settings == null)
        else XdSettingsPanel(
            e, panel, pinned, onPinned, group = group, gameName = game.name,
            onPresets = if (!panel.editGlobal) ({ presetsOpen = true }) else null,
            onToml = { tomlOpen = true }, onResetAll = resetAll, onOpenDrivers = links.onDrivers,
            scopeSwitch = scopeSwitch, onJump = if (controller) null else ({ g -> section = GameSections.group(g) }),
        )
    }
    val quick: @Composable () -> Unit = {
        val e = gameEditing
        if (e == null) NoSettings(settings == null) else XdQuickSettings(e, pinned, onOpenDrivers = links.onDrivers)
    }
    val cards = GameCards(game, data, info, art, actions, library, patches, patchState, links, activeProfile,
        onSection = { section = it }, quick = quick, changedCount = overrides.size)

    val subtitle = listOfNotNull(game.titleId, game.mediaId?.let { stringResource(R.string.xd_game_media, it) }, formatLabel(game.format))
        .joinToString(" · ")
    val favorite = data.favorite(game)
    val cover: @Composable () -> Unit = { GameCover(art.model, game.name, art.smart, radius = 6.dp) }
    val colors = rememberCoverColors(art.model)

    val sections: List<XdSection> = if (controller) {
        listOf(
            XdSection("play", stringResource(R.string.lib_play), XdIcons.play, heading = null, play = true) {
                cards.Hero(onPlay = play)
            },
            XdSection("launch", stringResource(R.string.xd_game_sec_launch), XdIcons.sliders,
                lead = stringResource(R.string.xd_launch_sub, game.name)) {
                LaunchForm(launchState, library, settings, overrides, patchCounts?.first)
                XdButton(stringResource(R.string.xd_launch_play_with), playWith, kind = XdButtonKind.PRIMARY, size = XdButtonSize.LG,
                    icon = XdIcons.play, enabled = !actions.preparing, modifier = Modifier.padding(top = 12.dp))
            },
            XdSection("quick", stringResource(R.string.xd_game_sec_quick), XdIcons.pin, lead = stringResource(R.string.xd_game_quick_lead)) { quick() },
            XdSection("set", stringResource(R.string.xd_game_sec_all), XdIcons.gear, badge = overrides.size.takeIf { it > 0 }?.toString()) {
                settingsPanel(null)
            },
            XdSection("perf", stringResource(R.string.xd_game_sec_perf), XdIcons.chart) { cards.Performance() },
            XdSection("cont", stringResource(R.string.xd_game_sec_content), XdIcons.patch,
                badge = patchCounts?.let { (on, total) -> "$on/$total" }) { cards.Content() },
            XdSection("data", stringResource(R.string.xd_game_sec_data), XdIcons.save) { cards.Data() },
            XdSection("compat", stringResource(R.string.xd_game_sec_compat), XdIcons.shield) { cards.Compatibility() },
        )
    } else {
        val groupGame = stringResource(R.string.xd_game_group_game)
        val groupSettings = stringResource(R.string.xd_game_group_settings)
        val groupMore = stringResource(R.string.xd_game_group_more)
        buildList {
            add(XdSection(GameSections.OVERVIEW, stringResource(R.string.xd_game_sec_overview), XdIcons.home, group = groupGame, heading = null) {
                cards.Overview(play, { launchOpen = true })
            })
            // Round 2: "All settings" (the overview's link) lists every group, each a tap from its tab.
            add(XdSection(GameSections.SETTINGS, stringResource(R.string.xd_game_sec_all), XdIcons.gear, group = groupSettings,
                badge = overrides.size.takeIf { it > 0 }?.toString()) { settingsPanel(null) })
            for (g in SettingCatalog.groups) {
                val own = overrides.keys.count { key -> SettingsSchema.byKey[key]?.let { SettingCatalog.meta(it).group == g } == true }
                add(XdSection(GameSections.group(g), groupTitle(g), groupIcon(g), group = groupSettings,
                    badge = own.takeIf { it > 0 }?.toString()) { settingsPanel(g) })
            }
            add(XdSection(GameSections.PERF, stringResource(R.string.xd_game_sec_perf), XdIcons.chart, group = groupMore) { cards.Performance() })
            add(XdSection(GameSections.CONTENT, stringResource(R.string.xd_game_sec_content), XdIcons.patch, group = groupMore) { cards.Content() })
            add(XdSection(GameSections.DATA, stringResource(R.string.xd_game_sec_data), XdIcons.save, group = groupMore) { cards.Data() })
        }
    }

    val swap = LocalSwapConfirm.current
    WithCoverColors(colors?.dyn, colors?.accent) {
        XdSectionedScreen(
            title = if (!controller && shown == GameSections.OVERVIEW) stringResource(R.string.xd_lib_sheet) else game.name,
            sections = sections,
            selected = shown,
            onSelect = { id -> if (controller && id == "play" && shown == "play") play() else section = id },
            area = XdArea.GAMES,
            subtitle = subtitle,
            subtitleMono = true,
            onBack = links.onBack,
            lead = if ((controller && shown == "play") || (!controller && shown == GameSections.OVERVIEW)) null else cover,
            art = if (!controller && shown == GameSections.OVERVIEW) null else art.model,
            actions = if (controller) null else ({
                XdIconButton(if (favorite) XdIcons.starFilled else XdIcons.star, stringResource(R.string.lib_favorites),
                    { actions.toggleFavorite(game) }, on = favorite)
                XdIconButton(XdIcons.more, stringResource(R.string.lib_more), { moreOpen = true })
                if (shown != GameSections.OVERVIEW) {
                    XdButton(stringResource(R.string.xd_game_sec_launch), { launchOpen = true }, enabled = !actions.preparing)
                    XdButton(stringResource(R.string.lib_play), play, kind = XdButtonKind.PRIMARY, icon = XdIcons.play, enabled = !actions.preparing)
                }
            }),
            hints = if (!controller) null else listOf(
                XdHint(if (swap) "B" else "A", stringResource(R.string.xd_hint_select)),
                XdHint(if (swap) "A" else "B", stringResource(R.string.xd_hint_back), links.onBack),
                XdHint("Y", stringResource(if (favorite) R.string.xd_lib_unfavorite else R.string.xd_lib_favorite)) { actions.toggleFavorite(game) },
                XdHint("LB/RB", stringResource(R.string.xd_hint_sections)),
                XdHint("≡", stringResource(R.string.xd_hint_menu)),
            ),
            onPad = { b -> if (b == PadButton.Y) { actions.toggleFavorite(game); true } else false },
        )
    }

    if (launchOpen) LaunchSheet(game.name, launchState, library, settings, overrides, patchCounts?.first,
        onPlay = { options -> launchOpen = false; actions.play(game, options) }, onDismiss = { launchOpen = false })
    if (moreOpen) MoreSheet(game, actions, library, onDismiss = { moreOpen = false })
    if (presetsOpen && gameEditing != null && settings != null) PresetsSheet(game.name, gameEditing, settings) { presetsOpen = false }
    if (tomlOpen) editing?.let { e -> TomlSheet(e) { tomlOpen = false } }
    settings?.let { SettingsDialogs(it) }
    GameActionDialogs(actions)
}

@Composable
private fun NoSettings(noTitle: Boolean) {
    XdEmpty(stringResource(if (noTitle) R.string.xd_game_no_title else R.string.xd_game_loading))
}

/** ⋮ of the sheet: cover, collections, shortcut, compression, rating. */
@Composable
private fun MoreSheet(game: Game, actions: GameActions, library: GameLibraryViewModel, onDismiss: () -> Unit) {
    val custom = remember(game.identityKey) { library.hasCustomCover(game) }
    XdSheet(onDismiss = onDismiss, title = game.name) {
        Column {
            if (xendroid.compose.data.CoverStore.normalize(game.titleId) != null) {
                XdMenuItem(stringResource(R.string.lib_change_cover), { onDismiss(); actions.pickCover(game) }, icon = XdIcons.image,
                    subtitle = stringResource(R.string.lib_change_cover_note))
                if (custom) XdMenuItem(stringResource(R.string.lib_own_icon), { onDismiss(); actions.ownIcon(game) }, icon = XdIcons.reset)
            }
            XdMenuItem(stringResource(R.string.lib_collections), { onDismiss(); actions.editCollections(game) }, icon = XdIcons.layers)
            if (actions.canShortcut) XdMenuItem(stringResource(R.string.lib_shortcut), { onDismiss(); actions.shortcut(game) }, icon = XdIcons.link)
            if (game.format == GameFormat.ISO) XdMenuItem(stringResource(R.string.lib_compress), { onDismiss(); actions.compress(game) }, icon = XdIcons.zip)
            XdMenuItem(stringResource(R.string.xd_game_rate), { onDismiss(); actions.rate(game) }, icon = XdIcons.shield)
        }
    }
}

/** Presets written on this game together (with undo), the app's recommended profiles and the community's. */
@Composable
private fun PresetsSheet(gameName: String, editing: SettingsEditing, vm: GameSettingsViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val toast = LocalXdToast.current
    val profiles by vm.profilesState.collectAsStateWithLifecycle()
    val community by vm.communityState.collectAsStateWithLifecycle()
    XdSheet(onDismiss = onDismiss, title = stringResource(R.string.xd_set_presets), subtitle = stringResource(R.string.xd_presets_sub), wide = true,
        actions = { XdButton(stringResource(R.string.xd_done), onDismiss, kind = XdButtonKind.PRIMARY) }) {
        for (preset in SettingsPresets.all) {
            val changes = SettingsPresets.changes(preset, editing)
            XdSheetOption(stringResource(preset.title),
                subtitle = stringResource(preset.description) + " " + pluralStringResource(R.plurals.xd_preset_changes, changes, changes)) {
                XdButton(stringResource(if (changes > 0) R.string.xd_preset_apply else R.string.xd_preset_already), {
                    val before = vm.overrides.value
                    preset.values.forEach { (key, value) -> SettingsSchema.byKey[key]?.let { editing.set(it, value) } }
                    toast.show(context.getString(R.string.xd_preset_applied, context.getString(preset.title), gameName),
                        context.getString(R.string.xd_undo)) { vm.restore(before) }
                }, size = XdButtonSize.SM, kind = if (changes > 0) XdButtonKind.SECONDARY else XdButtonKind.GHOST, enabled = changes > 0)
            }
        }
        if (!profiles.isEmpty) RecommendedProfilesCard(profiles, vm::previewProfile, vm::previewRestore)
        else XdSheetOption(stringResource(R.string.xd_preset_recommended), subtitle = stringResource(R.string.xd_preset_recommended_none))
        val communityNow = community
        if (communityNow != null) CommunityConfigsCard(communityNow, canApply = profiles.applied == null, onSearch = vm::searchCommunity,
            onPreview = vm::previewProfile, onVote = vm::voteCommunity, onDelete = vm::deleteShared, onShare = vm::prepareShare)
        else XdSheetOption(stringResource(R.string.xd_preset_community), subtitle = stringResource(R.string.xd_preset_community_off))
    }
}

/** The dialogs of the recommended and community profiles (their previews and results). */
@Composable
private fun SettingsDialogs(vm: GameSettingsViewModel) {
    val preview by vm.profilePreview.collectAsStateWithLifecycle()
    val message by vm.profileMessage.collectAsStateWithLifecycle()
    val community by vm.communityState.collectAsStateWithLifecycle()
    val share by vm.shareStart.collectAsStateWithLifecycle()
    preview?.let { ProfilePreviewDialog(it, onConfirm = vm::confirmPreview, onDismiss = vm::dismissPreview) }
    val communityNow = community
    if (share != null && communityNow != null) {
        CommunityShareDialog(communityNow.server, draftOf = vm::draftShare, onShare = vm::share, onDismiss = vm::dismissShare)
    }
    message?.let { m ->
        XdSheet(onDismiss = vm::clearProfileMessage, title = null,
            actions = { XdButton(stringResource(R.string.common_ok), vm::clearProfileMessage, kind = XdButtonKind.PRIMARY) }) {
            androidx.compose.material3.Text(profileMessageText(m), style = xendroid.compose.ui.design.XdText.body, color = Xd.colors.fg2)
        }
    }
}

/** What the game's file (or the global config) holds away from the defaults, to read or copy. */
@Composable
private fun TomlSheet(editing: SettingsEditing, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val toast = LocalXdToast.current
    val text = tomlPreview(editing,
        listOf(stringResource(if (editing.forGame) R.string.xd_toml_game_header else R.string.xd_toml_global_header)),
        stringResource(R.string.xd_toml_none))
    XdSheet(onDismiss = onDismiss,
        title = stringResource(if (editing.forGame) R.string.xd_toml_game_title else R.string.xd_toml_global_title),
        subtitle = stringResource(if (editing.forGame) R.string.xd_toml_game_sub else R.string.xd_toml_global_sub), wide = true,
        actions = {
            XdButton(stringResource(R.string.xd_copy), {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("TOML", text))
                toast.show(context.getString(R.string.xd_copied))
            }, icon = XdIcons.copy)
            XdButton(stringResource(R.string.xd_done), onDismiss, kind = XdButtonKind.PRIMARY)
        }) {
        XdToml(text)
    }
}

