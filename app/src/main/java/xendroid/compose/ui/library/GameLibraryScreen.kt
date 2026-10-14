package xendroid.compose.ui.library

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xendroid.compose.R
import xendroid.compose.core.AllFilesAccess
import xendroid.compose.data.Game
import xendroid.compose.data.GameFormat
import xendroid.compose.settings.GameSettingsViewModel
import xendroid.compose.settings.PinnedSettings
import xendroid.compose.ui.compress.GameCompressViewModel
import xendroid.compose.ui.design.NoteTone
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdArea
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdLink
import xendroid.compose.ui.design.XdMenuItem
import xendroid.compose.ui.design.XdNote
import xendroid.compose.ui.design.XdSheet
import xendroid.compose.ui.design.XdSingleScreen
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.settings.GameSettingsEditing
import xendroid.compose.ui.settings.XdQuickSettings
import xendroid.compose.ui.userdata.openUserData
import xendroid.compose.ui.design.LocalXdToast

/** A scan shorter than this shows no progress row. */
private const val SCAN_PROGRESS_DELAY_MS = 700L

/**
 * The library (docs/ui-redesign/bc, Base): for touch, the rail, filters and a grid of covers with
 * the chosen game's panel; for a controller, the carousel with tabs. The game sheet is its own
 * screen ([onOpenGame]); what used to be the ⋮ menu lives in the rail's areas, except what is the
 * library's own (folders, missing games, the assistant).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameLibraryScreen(
    viewModel: GameLibraryViewModel,
    onOpenSettings: () -> Unit,
    onOpenKeymap: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenTouchControls: () -> Unit,
    onOpenPerGameSettings: (titleId: String, gameName: String, format: GameFormat, launchUri: String) -> Unit,
    onOpenGamePatches: (titleId: String, gameName: String) -> Unit,
    onOpenContentManager: (titleId: String, gameName: String) -> Unit,
    onOpenSaves: (titleId: String, gameName: String) -> Unit,
    onOpenDiagnostics: (String?) -> Unit,
    onOpenInstallContent: () -> Unit,
    onInstallFromDisc: (String) -> Unit,
    compressVm: GameCompressViewModel,
    onOpenPhoneController: () -> Unit = {},
    onOpenControllerTest: () -> Unit = {},
    onOpenBenchmark: () -> Unit = {},
    /** The game sheet, at a section ("settings") or its overview (null). */
    onOpenGame: (Game, String?) -> Unit = { _, _ -> },
    /** This game's settings view model (the panel's quick settings); null hides them. */
    gameSettings: (@Composable (String) -> GameSettingsViewModel)? = null,
    /** Opened as the Collections area: the first collection is shown. */
    collectionsArea: Boolean = false,
    /** The rail asked for Games or Collections: the filter starts over, once ([onAreaHandled]). */
    areaReset: Boolean = false,
    onAreaHandled: () -> Unit = {},
    /** The app updates screen (lote 6). */
    onOpenUpdates: () -> Unit = {},
    /** The first-run assistant creates a profile here and makes it P1; null: only "Open Profiles". */
    onCreateProfile: ((String) -> Unit)? = null,
    /** About asked for the setup assistant again: it opens at its first step, once ([onAssistantHandled]). */
    assistantRequested: Boolean = false,
    onAssistantHandled: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toast = LocalXdToast.current
    val actions = rememberGameActions(viewModel, compressVm, onInstallFromDisc)
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val activity by viewModel.activity.collectAsStateWithLifecycle()
    val compat by viewModel.compat.collectAsStateWithLifecycle()
    val coverRevision by viewModel.coverRevision.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val details by viewModel.details.collectAsStateWithLifecycle()
    val missing by viewModel.missing.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var filterKey by rememberSaveable { mutableStateOf<String?>(null) }
    var tabKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var density by remember { mutableStateOf(CoverDensityStore.read(context)) }
    var pinned by remember { mutableStateOf(PinnedSettings.read(context)) }
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(areaReset) { if (areaReset) { filterKey = null; query = ""; onAreaHandled() } }
    val gridState = rememberLazyGridState()

    var showBrowser by rememberSaveable { mutableStateOf(false) }
    // Declared before the browser's early return below: while the browser shows, flags declared
    // after it leave the composition and would start over (the assistant at its first step).
    var foldersOpen by rememberSaveable { mutableStateOf(false) }
    var missingOpen by rememberSaveable { mutableStateOf(false) }
    // L01: once, until finished or skipped; reopened from the menu or About. Not over the
    // no-Vulkan gate, which already explains why games cannot run.
    var assistantOpen by rememberSaveable { mutableStateOf(!FirstRunStore.done(context)) }
    var assistantStep by rememberSaveable { mutableStateOf(FirstRunStep.PHONE) }
    LaunchedEffect(assistantRequested) {
        if (assistantRequested) { assistantStep = FirstRunStep.PHONE; assistantOpen = true; onAssistantHandled() }
    }
    // Lote 6: a missing game's file chosen in the browser: its folder joins the game folders.
    var findingFile by remember { mutableStateOf<xendroid.compose.data.MissingTitle?>(null) }
    var allFilesGranted by remember { mutableStateOf(AllFilesAccess.isGranted()) }
    // Not-yet-granted sends the user to Settings; the grant returns no result, so it is
    // observed on the next ON_START.
    val startRealPathMode: () -> Unit = {
        if (AllFilesAccess.isGranted()) showBrowser = true
        else AllFilesAccess.requestAccess(context)
    }

    // Re-scan on return to foreground to pick up games added while backgrounded. The
    // ViewModel's init does the first cold-start load, so the first ON_START is skipped.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var firstStart = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                allFilesGranted = AllFilesAccess.isGranted()
                pinned = PinnedSettings.read(context)
                if (firstStart) firstStart = false else viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Folders in toasts are named as the phone's Files app names them.
    val storageRoots = rememberStorageRoots()
    // Round 2: XenDroid/<Games>, /TU and /DLC made and added in one go (the folders screen and
    // the first-run assistant offer it).
    val createStandard: (() -> Unit)? = if (!AllFilesAccess.isSupported) null else ({
        if (!AllFilesAccess.isGranted()) AllFilesAccess.requestAccess(context)
        else {
            val gamesName = context.getString(R.string.xd_fd_std_games)
            val made = xendroid.compose.data.StandardFolders.create(android.os.Environment.getExternalStorageDirectory(), gamesName)
            if (made == null) toast.show(context.getString(R.string.xd_fd_std_failed))
            else {
                viewModel.onRealPathFolderPicked(made.games.absolutePath)
                xendroid.compose.ui.content.ContentFolders.add(context, made.updates.absolutePath)
                xendroid.compose.ui.content.ContentFolders.add(context, made.dlc.absolutePath)
                toast.show(context.getString(R.string.xd_fd_std_done, gamesName))
            }
        }
    })
    if (showBrowser) {
        FolderBrowserScreen(
            onFolderChosen = { path -> showBrowser = false; viewModel.onRealPathFolderPicked(path) },
            onCancel = { showBrowser = false },
        )
        return
    }
    findingFile?.let { title ->
        FolderBrowserScreen(
            onFileChosen = { path ->
                findingFile = null
                java.io.File(path).parent?.let { folder ->
                    viewModel.onRealPathFolderPicked(folder)
                    toast.show(context.getString(R.string.xd_ms_added, StoragePaths.display(folder, storageRoots)))
                }
            },
            onCancel = { findingFile = null },
            start = generateSequence(java.io.File(title.lastPath).parentFile) { it.parentFile }.firstOrNull { it.isDirectory },
            title = stringResource(R.string.xd_ms_find_title, title.name),
            hint = stringResource(R.string.xd_ms_find_note),
        )
        return
    }

    // L03, lote 6: the library's game folders (add, remove, where installs go; files are never touched).
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val openFolders = { viewModel.loadFolders(); foldersOpen = true }
    if (foldersOpen) {
        GameFoldersScreen(
            folders = folders,
            unavailable = (state as? LibraryUiState.Loaded)?.unavailableRoots.orEmpty(),
            games = (state as? LibraryUiState.Loaded)?.games.orEmpty(),
            scanning = scanProgress != null || isRefreshing,
            onAdd = startRealPathMode,
            onCreateStandard = createStandard,
            standardGames = remember { xendroid.compose.data.StandardFolders.under(android.os.Environment.getExternalStorageDirectory(),
                context.getString(R.string.xd_fd_std_games)).games.absolutePath },
            onRemove = { folder ->
                val before = folders
                viewModel.removeFolder(folder)
                toast.show(context.getString(R.string.xd_fd_removed, StoragePaths.display(folder, storageRoots)),
                    context.getString(R.string.xd_undo)) { viewModel.restoreFolders(before) }
            },
            onMakeInstallFolder = { folder ->
                viewModel.makeInstallFolder(folder)
                toast.show(context.getString(R.string.xd_fd_install_moved))
            },
            onRescan = { viewModel.refresh() },
            onBack = { foldersOpen = false },
        )
        return
    }

    // L06, lote 6: games played or seen before that this scan did not list.
    if (missingOpen) {
        MissingGamesScreen(
            missing = missing, activity = activity, coverOf = viewModel::coverOfTitle,
            onRemove = { title ->
                viewModel.hideMissing(title)
                toast.show(context.getString(R.string.xd_ms_removed, title.name))
            },
            onAddFolderOf = { title ->
                java.io.File(title.lastPath).parent?.let { folder ->
                    viewModel.onRealPathFolderPicked(folder)
                    toast.show(context.getString(R.string.xd_ms_added, StoragePaths.display(folder, storageRoots)))
                }
            },
            onFind = { title -> findingFile = title },
            onFolders = { missingOpen = false; openFolders() },
            onBack = { missingOpen = false },
        )
        return
    }

    // The assistant (state above): the folder browser replaces it for a while, and it comes back on the same step.
    if (assistantOpen && state != LibraryUiState.NoVulkan) {
        LaunchedEffect(Unit) { viewModel.loadFolders() }
        val found = (state as? LibraryUiState.Loaded)?.games
        FirstRunAssistant(
            folderReady = state is LibraryUiState.Loaded,
            onChooseFolder = startRealPathMode,
            onCreateStandard = createStandard,
            onOpenProfiles = onOpenProfiles,
            onClose = {
                FirstRunStore.markDone(context)
                assistantOpen = false
                toast.show(context.getString(R.string.xd_fr_done))
            },
            folders = folders,
            gamesFound = found?.size,
            scanning = state == LibraryUiState.Loading || scanProgress != null,
            covers = remember(found) { found.orEmpty().take(10).map { viewModel.iconFileOrFallback(it) } },
            activeProfile = activeProfile,
            onCreateProfile = onCreateProfile,
            step = assistantStep,
            onStep = { assistantStep = it },
        )
    }

    val loaded = state as? LibraryUiState.Loaded
    if (loaded == null || loaded.games.isEmpty()) {
        LibraryEmpty(state, viewModel, allFilesGranted, startRealPathMode, missing, openFolders, onMenu = { menuOpen = true }) { missingOpen = true }
    } else {
        val data = remember(loaded.games, favorites, activity, compat, collections, sort) {
            LibraryData(loaded.games, favorites, activity, compat, collections, sort)
        }
        val artOf: (Game) -> CoverArt = { game ->
            CoverArt(viewModel.iconFileOrFallback(game), !viewModel.hasCustomCover(game))
        }
        val cachedArt = remember(coverRevision, loaded.games) { HashMap<String, CoverArt>() }
        val art: (Game) -> CoverArt = { game -> cachedArt.getOrPut(game.stableId) { artOf(game) } }
        // The last run's FPS once its details are read; a run without frames says so in the panel.
        val lastFps: (Game) -> String? = { game ->
            details?.takeIf { it.identityKey == game.identityKey }?.lastRun?.performance?.let { perf ->
                val median = perf.fpsPercentile(0.5); val low = perf.fpsPercentile(0.05)
                if (median != null && low != null) context.getString(R.string.xd_lib_fps_line, median, low) else null
            }
        }
        val lastSession: (Game) -> String? = { game ->
            lastFps(game) ?: details?.takeIf { it.identityKey == game.identityKey }?.lastRun?.let { context.getString(R.string.xd_lib_no_frames) }
        }
        val patchesOf: (Game) -> String? = { game ->
            details?.takeIf { it.identityKey == game.identityKey }?.let { d ->
                d.patchesTotal?.let { total -> context.getString(R.string.xd_of, d.patchesEnabled ?: 0, total) }
            }
        }
        if (Xd.controller) {
            val tab = LibraryFilter.parse(tabKey ?: if (activity.isEmpty()) "all" else "recent")
            val shown = data.shown(tab, "")
            val focused = shown.firstOrNull { it.stableId == selectedId } ?: shown.firstOrNull()
            LaunchedEffect(focused?.identityKey) { focused?.let { viewModel.loadDetails(it) } }
            LibraryController(
                data, tab, { tabKey = it.key }, focused?.stableId, { selectedId = it }, art, lastFps, patchesOf,
                onPlay = actions::play, onOpen = { onOpenGame(it, null) }, onFavorite = actions::toggleFavorite,
                gamertag = activeProfile, preparing = actions.preparing,
            )
        } else {
            val defaultFilter = if (collectionsArea) collections.firstOrNull()?.let { LibraryFilter.Collection(it.name) } ?: LibraryFilter.All else LibraryFilter.All
            val filter = filterKey?.let(LibraryFilter::parse) ?: defaultFilter
            val shown = data.shown(filter, query)
            val selected = shown.firstOrNull { it.stableId == selectedId } ?: shown.firstOrNull()
            LaunchedEffect(selected?.identityKey) { selected?.let { viewModel.loadDetails(it) } }
            PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { viewModel.refresh() }, modifier = Modifier.fillMaxSize()) {
                LibraryTouch(
                    data, filter, { filterKey = it.key }, query, { query = it }, density,
                    { density = it; CoverDensityStore.write(context, it) }, viewModel::setSort,
                    selected, { selectedId = it.stableId }, art, onOpen = { onOpenGame(it, null) }, onFavorite = actions::toggleFavorite,
                    onPlay = { actions.play(it) }, preparing = actions.preparing,
                    onMenu = { menuOpen = true }, gridState = gridState,
                    notices = { LibraryNotices(loaded, missing, viewModel, openFolders) { missingOpen = true } },
                    detail = { game ->
                        LibraryDetailPanel(
                            game, data, art(game), lastSession(game), patchesOf(game), activeProfile,
                            onPlay = { actions.play(game) }, onOpen = { onOpenGame(game, null) },
                            onFavorite = { actions.toggleFavorite(game) }, onAllSettings = { onOpenGame(game, "settings") },
                            preparing = actions.preparing,
                            quickSettings = game.titleId?.let { id -> gameSettings?.let { factory -> { PanelQuickSettings(factory(id), pinned) } } },
                        )
                    },
                    area = if (filter is LibraryFilter.Collection) XdArea.COLLECTIONS else XdArea.GAMES,
                )
            }
        }
    }

    if (menuOpen) LibraryMenu(
        onDismiss = { menuOpen = false },
        missingCount = missing.size,
        onAddFolder = { menuOpen = false; startRealPathMode() },
        onFolders = { menuOpen = false; openFolders() },
        onMissing = { menuOpen = false; missingOpen = true },
        onRescan = { menuOpen = false; viewModel.refresh() },
        onSetup = { menuOpen = false; assistantOpen = true },
        onUserData = { menuOpen = false; openUserData(context) },
        onUpdates = { menuOpen = false; onOpenUpdates() },
    )
    GameActionDialogs(actions)
}

@Composable
private fun PanelQuickSettings(vm: GameSettingsViewModel, pinned: List<String>) {
    val overrides by vm.overrides.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    if (!ready) return
    XdQuickSettings(GameSettingsEditing(vm, overrides), pinned.filter { it != "Vulkan|vulkan_lib_path" })
    DisposableEffect(vm) { onDispose { vm.flush() } }
}

/** The library's own actions (folders, missing games, rescan, assistant, user data, updates). */
@Composable
private fun LibraryMenu(
    onDismiss: () -> Unit,
    missingCount: Int,
    onAddFolder: () -> Unit,
    onFolders: () -> Unit,
    onMissing: () -> Unit,
    onRescan: () -> Unit,
    onSetup: () -> Unit,
    onUserData: () -> Unit,
    onUpdates: () -> Unit,
) {
    XdSheet(onDismiss = onDismiss, title = stringResource(R.string.lib_title)) {
        Column {
            if (AllFilesAccess.isSupported) {
                XdMenuItem(stringResource(R.string.lib_menu_add_folder), onAddFolder, icon = XdIcons.plus)
                XdMenuItem(stringResource(R.string.lib_menu_folders), onFolders, icon = XdIcons.folder)
            }
            if (missingCount > 0) XdMenuItem(stringResource(R.string.lib_menu_missing, missingCount), onMissing, icon = XdIcons.inbox)
            XdMenuItem(stringResource(R.string.xd_lib_rescan), onRescan, icon = XdIcons.refresh)
            XdMenuItem(stringResource(R.string.lib_menu_setup), onSetup, icon = XdIcons.wand)
            XdMenuItem(stringResource(R.string.lib_menu_user_data), onUserData, icon = XdIcons.folder)
            XdMenuItem(stringResource(R.string.lib_menu_updates), onUpdates, icon = XdIcons.download)
        }
    }
}

/** No list to show: no folder, no access, scanning, an error, or a folder without games. */
@Composable
private fun LibraryEmpty(
    state: LibraryUiState,
    viewModel: GameLibraryViewModel,
    allFilesGranted: Boolean,
    startRealPathMode: () -> Unit,
    missing: List<xendroid.compose.data.MissingTitle>,
    onFolders: () -> Unit,
    onMenu: () -> Unit,
    onMissing: () -> Unit,
) {
    val context = LocalContext.current
    if (state == LibraryUiState.NoVulkan) {
        NoVulkanScreen(onQuit = { (context as? Activity)?.finish() })
        return
    }
    val c = Xd.colors
    // The library's own menu (folders, assistant, updates) stays at hand while it is empty.
    XdSingleScreen(title = stringResource(R.string.lib_title), area = XdArea.GAMES, headIcon = XdIcons.grid, scroll = false,
        actions = { xendroid.compose.ui.design.XdIconButton(XdIcons.more, stringResource(R.string.lib_more), onMenu) }) {
        Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 520.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val setFolder = stringResource(if (allFilesGranted) R.string.lib_set_folder else R.string.lib_grant_access)
                when (state) {
                    LibraryUiState.Loading -> {
                        CircularProgressIndicator(color = c.acc)
                        ScanProgressRow(viewModel)
                    }
                    LibraryUiState.NoFolder ->
                        if (AllFilesAccess.isSupported) EmptyAction(stringResource(R.string.lib_no_folder), setFolder, startRealPathMode)
                        else EmptyAction(stringResource(R.string.lib_needs_android11), null) {}
                    LibraryUiState.PermissionLost -> EmptyAction(stringResource(R.string.lib_access_lost), setFolder, startRealPathMode)
                    is LibraryUiState.Error -> EmptyAction(state.message, stringResource(R.string.common_retry)) { viewModel.refresh() }
                    is LibraryUiState.Loaded -> {
                        LibraryNotices(state, missing, viewModel, onFolders, onMissing)
                        EmptyAction(stringResource(R.string.lib_no_games), stringResource(R.string.lib_choose_another), startRealPathMode)
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun EmptyAction(text: String, action: String?, onAction: () -> Unit) {
    val c = Xd.colors
    Text(text, style = XdText.body, color = c.fg2, textAlign = TextAlign.Center)
    if (action != null) XdButton(action, onAction, kind = XdButtonKind.PRIMARY)
}

/** L09: what the scan is doing, with "Stop"; nothing for the first moments of a scan, so a quick
 *  rescan on return to the app does not shift the grid. */
@Composable
private fun ScanProgressRow(viewModel: GameLibraryViewModel) {
    val progress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val scanning = progress != null
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(scanning) {
        shown = false
        if (scanning) { kotlinx.coroutines.delay(SCAN_PROGRESS_DELAY_MS); shown = true }
    }
    val p = progress?.takeIf { shown } ?: return
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        XdNote(
            when {
                p.reading != null -> stringResource(R.string.lib_scan_reading, p.reading, p.checked + 1, p.candidates)
                p.candidates > 0 -> pluralStringResource(R.plurals.lib_scan_checking, p.candidates, p.checked, p.candidates)
                else -> pluralStringResource(R.plurals.lib_scan_looking, p.entries, p.entries)
            },
            tone = NoteTone.INFO, icon = XdIcons.refresh, modifier = Modifier.weight(1f, fill = false),
        )
        XdLink(stringResource(R.string.lib_stop), viewModel::stopScan)
    }
}

/** Scan progress, a partial scan, game folders not available now (L03), games no longer there (L06). */
@Composable
private fun LibraryNotices(
    s: LibraryUiState.Loaded,
    missing: List<xendroid.compose.data.MissingTitle>,
    viewModel: GameLibraryViewModel,
    onFolders: () -> Unit,
    onMissing: () -> Unit,
) {
    ScanProgressRow(viewModel)
    val pad = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
    if (s.truncated) Row(pad) { XdLink(stringResource(R.string.lib_scan_truncated), onFolders) }
    if (s.unavailableRoots.isNotEmpty()) Row(pad, verticalAlignment = Alignment.CenterVertically) {
        XdNote(pluralStringResource(R.plurals.lib_folders_unavailable, s.unavailableRoots.size, s.unavailableRoots.size), tone = NoteTone.WARN,
            modifier = Modifier.weight(1f, fill = false))
        XdLink(stringResource(R.string.lib_menu_folders), onFolders, modifier = Modifier.padding(start = 10.dp))
    }
    val gone = missing.count { it.reason != xendroid.compose.data.MissingTitles.Reason.FOLDER_AWAY }
    if (gone > 0) Row(pad) { XdLink(pluralStringResource(R.plurals.lib_games_gone, gone, gone), onMissing) }
}


/** U02: a compatibility result as shown; reports keep the English label. */
@Composable
fun compatStatusText(status: xendroid.compose.compatibility.CompatStatus): String = when (status) {
    xendroid.compose.compatibility.CompatStatus.NOTHING -> stringResource(R.string.compat_nothing)
    xendroid.compose.compatibility.CompatStatus.BOOTS -> stringResource(R.string.compat_boots)
    xendroid.compose.compatibility.CompatStatus.INTRO -> stringResource(R.string.compat_intro)
    xendroid.compose.compatibility.CompatStatus.IN_GAME -> stringResource(R.string.compat_in_game)
    xendroid.compose.compatibility.CompatStatus.PLAYABLE -> stringResource(R.string.compat_playable)
}
