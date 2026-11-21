package xendroid.compose.ui

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xendroid.compose.ui.design.InputMode
import xendroid.compose.ui.design.InputModePref
import xendroid.compose.ui.design.InputModeStore
import xendroid.compose.ui.design.ProvideXdNavigation
import xendroid.compose.ui.design.XdArea
import xendroid.compose.ui.design.XdNavigator
import xendroid.compose.ui.design.XdShortcut
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import xendroid.compose.AppContainer
import xendroid.compose.data.GameFormat
import xendroid.compose.gamepad.GamepadController
import xendroid.compose.gamepad.GamepadEditorScreen
import xendroid.compose.settings.GameSettingsViewModel
import xendroid.compose.settings.SettingsViewModel
import xendroid.compose.ui.compress.GameCompressViewModel
import xendroid.compose.ui.content.ContentManagerViewModel
import xendroid.compose.ui.content.ContentScreen
import xendroid.compose.ui.content.InstallContentViewModel
import xendroid.compose.ui.content.InstallContentScreen
import xendroid.compose.ui.library.GameLibraryScreen
import xendroid.compose.ui.library.GameLibraryViewModel
import xendroid.compose.ui.settings.PerGameSettingsScreen
import xendroid.compose.ui.settings.SettingsScreen
import xendroid.compose.ui.about.AboutScreen
import xendroid.compose.ui.keymap.KeymapScreen
import xendroid.compose.ui.keymap.KeymapViewModel
import xendroid.compose.ui.profile.ProfileManagerViewModel
import xendroid.compose.ui.profile.ProfilesScreen
import xendroid.compose.patches.GamePatchesViewModel
import xendroid.compose.ui.patches.GamePatchesScreen
import xendroid.compose.ui.saves.SaveManagerViewModel
import xendroid.compose.ui.saves.SaveManagerScreen
import xendroid.compose.ui.diagnostics.DiagnosticsScreen
import xendroid.compose.ui.companion.PhoneControllerScreen
import xendroid.compose.ui.companion.PhoneControllerViewModel

object Routes {
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val KEYMAP = "keymap"
    const val ABOUT = "about"
    const val PROFILES = "profiles"
    const val USERDATA = "userdata"   // action, not a destination (see GameLibraryScreen)
    const val GAMEPAD_EDITOR = "gamepad_editor"
    // "$PER_GAME_SETTINGS/{titleId}?name={name}&format={format}&uri={uri}"
    const val PER_GAME_SETTINGS = "per_game_settings"
    // "$GAME_PATCHES/{titleId}?name={name}"
    const val GAME_PATCHES = "game_patches"
    // "$CONTENT_MANAGER/{titleId}?name={name}"
    const val CONTENT_MANAGER = "content_manager"
    const val INSTALL_CONTENT = "install_content"
    /** Lote 5: the Content area, "$CONTENT?section={section}" (every game's content). */
    const val CONTENT = "content"
    const val SAVES = "saves"
    const val DIAGNOSTICS = "diagnostics"
    const val PHONE_CONTROLLER = "phone_controller"
    const val CONTROLLER_TEST = "controller_test"
    // "$BENCHMARK?title={title}": compare runs, of that game first
    const val BENCHMARK = "benchmark"
    // "$GAME?key={key}&section={section}": the game sheet, by the game's launch path
    const val GAME = "game"
    /** Batch 1: the drivers area (installed, downloads, sources, Turnip flags). */
    const val DRIVERS = "drivers"
    /** The Controls area (lote 3): its tools are the routes above. */
    const val CONTROLS = "controls"
    /** Lote 6: app updates (channel, version, the offered update and its steps). */
    const val UPDATES = "updates"
}

/** The game sheet's route for [game], opened at [section] (null: the overview). */
fun gameRoute(game: xendroid.compose.data.Game, section: String? = null): String =
    "${Routes.GAME}?key=${Uri.encode(game.stableId)}" + (section?.let { "&section=${Uri.encode(it)}" } ?: "")

private fun NavBackStackEntry.canNavigate(nav: NavController): Boolean =
    nav.currentBackStackEntry == this && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

private fun NavBackStackEntry.backOnce(nav: NavController): () -> Unit = {
    if (canNavigate(nav)) nav.popBackStack()
}

@Composable
fun AppNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val mode = xendroid.compose.ui.design.LocalInputMode.current
    // Collections is the library's own area: the library entry stays, its filter changes.
    var collectionsArea by rememberSaveable { mutableStateOf(false) }
    // One-shot requests the library consumes: a counter kept its value, so every return to the
    // library started the filter over and (after About) reopened the setup assistant.
    var areaReset by remember { mutableStateOf(false) }
    var gamertag by remember { mutableStateOf<String?>(null) }
    // About asks for the setup assistant again: the library opens it once.
    var assistantRequested by remember { mutableStateOf(false) }
    val navigator = remember(nav, mode) {
        object : XdNavigator {
            /** A top-level area: one entry above the library, never a stack of them. */
            private fun top(route: String) {
                if (nav.currentDestination?.route == route) return
                nav.navigate(route) {
                    popUpTo(Routes.LIBRARY) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            override fun area(area: XdArea) = when (area) {
                XdArea.GAMES, XdArea.COLLECTIONS -> {
                    collectionsArea = area == XdArea.COLLECTIONS
                    areaReset = true
                    nav.popBackStack(Routes.LIBRARY, inclusive = false)
                    Unit
                }
                XdArea.CONTENT -> top(Routes.CONTENT)
                XdArea.PROFILES -> top(Routes.PROFILES)
                XdArea.CONTROLS -> top(Routes.CONTROLS)
                XdArea.DRIVERS -> top(Routes.DRIVERS)
                XdArea.SETTINGS -> top(Routes.SETTINGS)
            }
            override fun shortcut(shortcut: XdShortcut) = when (shortcut) {
                XdShortcut.DIAGNOSTICS -> top("${Routes.DIAGNOSTICS}?title=")
                XdShortcut.COMPARE -> top(Routes.BENCHMARK)
                XdShortcut.ABOUT -> top(Routes.ABOUT)
            }
            override val gamertag: String? get() = gamertag
            override fun toggleInputMode() = InputModeStore.write(context,
                if (mode == InputMode.CONTROLLER) InputModePref.TOUCH else InputModePref.CONTROLLER)
        }
    }
    ProvideXdNavigation(navigator, remember { xendroid.compose.gamepad.MenuButtonPrefs.swapConfirm(context) }) {
    // Round 2: back from a session, what it suggests changing (when there is something).
    xendroid.compose.ui.advice.SessionAdviceHost { titleId, path ->
        val games = runCatching { container.repository.cachedGames() }.getOrDefault(emptyList())
        val game = games.firstOrNull { titleId != null && it.titleId.equals(titleId, ignoreCase = true) } ?: games.firstOrNull { it.launchUri == path }
        game?.titleId to game?.name
    }
    NavHost(
        navController = nav,
        startDestination = Routes.LIBRARY,
        enterTransition = { fadeIn(tween(105)) },
        exitTransition = { fadeOut(tween(75)) },
        popEnterTransition = { fadeIn(tween(105)) },
        popExitTransition = { fadeOut(tween(75)) },
    ) {
        composable(Routes.LIBRARY) { libraryEntry ->
            val vm: GameLibraryViewModel =
                viewModel(factory = container.libraryViewModelFactory())
            val compressVm: GameCompressViewModel =
                viewModel(factory = container.gameCompressViewModelFactory())
            // Only the current entry may navigate, including while its fade is running.
            val navigateOnce: (String) -> Unit = { route ->
                if (libraryEntry.canNavigate(nav)) {
                    nav.navigate(route)
                }
            }
            val profile by vm.activeProfile.collectAsStateWithLifecycle()
            LaunchedEffect(profile) { gamertag = profile }
            // The first-run assistant creates the first profile through it.
            val profiles: ProfileManagerViewModel = viewModel(factory = container.profileManagerViewModelFactory())
            GameLibraryScreen(
                viewModel = vm,
                compressVm = compressVm,
                onOpenGame = { game, section -> navigateOnce(gameRoute(game, section)) },
                gameSettings = { titleId -> viewModel(key = "panel-settings-$titleId", factory = container.gameSettingsViewModelFactory(titleId)) },
                collectionsArea = collectionsArea,
                areaReset = areaReset,
                onAreaHandled = { areaReset = false },
                onOpenSettings = { navigateOnce(Routes.SETTINGS) },
                onOpenKeymap = { navigateOnce(Routes.KEYMAP) },
                onOpenAbout = { navigateOnce(Routes.ABOUT) },
                onOpenProfiles = { navigateOnce(Routes.PROFILES) },
                onOpenTouchControls = { navigateOnce(Routes.GAMEPAD_EDITOR) },
                onOpenPerGameSettings = { titleId, name, format, launchUri ->
                    navigateOnce(
                        "${Routes.PER_GAME_SETTINGS}/$titleId" +
                            "?name=${Uri.encode(name)}" +
                            "&format=${format.name}" +
                            "&uri=${Uri.encode(launchUri)}"
                    )
                },
                onOpenGamePatches = { titleId, name ->
                    navigateOnce("${Routes.GAME_PATCHES}/$titleId?name=${Uri.encode(name)}")
                },
                onOpenContentManager = { titleId, name ->
                    navigateOnce("${Routes.CONTENT_MANAGER}/$titleId?name=${Uri.encode(name)}")
                },
                onOpenSaves = { titleId, name ->
                    navigateOnce("${Routes.SAVES}/$titleId?name=${Uri.encode(name)}")
                },
                onOpenDiagnostics = { titleId ->
                    navigateOnce("${Routes.DIAGNOSTICS}?title=${titleId.orEmpty()}")
                },
                onOpenInstallContent = { navigateOnce("${Routes.CONTENT}?section=${xendroid.compose.ui.content.ContentSections.INSTALL}") },
                onInstallFromDisc = { path ->
                    navigateOnce("${Routes.INSTALL_CONTENT}?src=" + Uri.encode(path))
                },
                onOpenPhoneController = { navigateOnce(Routes.PHONE_CONTROLLER) },
                onOpenControllerTest = { navigateOnce(Routes.CONTROLLER_TEST) },
                onOpenBenchmark = { navigateOnce(Routes.BENCHMARK) },
                onOpenUpdates = { navigateOnce(Routes.UPDATES) },
                onCreateProfile = { tag ->
                    val locale = java.util.Locale.getDefault().let { xendroid.compose.ui.library.FirstRun.guestLocale(it.language, it.country) }
                    profiles.create(tag, locale.languageValue?.toIntOrNull() ?: 1, locale.countryValue?.toIntOrNull() ?: 103, null, activate = true)
                },
                assistantRequested = assistantRequested,
                onAssistantHandled = { assistantRequested = false },
            )
        }
        composable(
            "${Routes.GAME}?key={key}&section={section}",
            arguments = listOf(
                navArgument("key") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("section") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            val libraryEntry = remember(entry) { nav.getBackStackEntry(Routes.LIBRARY) }
            val library: GameLibraryViewModel = viewModel(libraryEntry, factory = container.libraryViewModelFactory())
            val compressVm: GameCompressViewModel = viewModel(libraryEntry, factory = container.gameCompressViewModelFactory())
            val state by library.state.collectAsStateWithLifecycle()
            val key = entry.arguments?.getString("key")?.let { Uri.decode(it) }
            val game = (state as? xendroid.compose.ui.library.LibraryUiState.Loaded)?.games?.firstOrNull { it.stableId == key }
            val back = entry.backOnce(nav)
            if (game == null) {
                // The list is still loading (process restored) or the game left it: back to the library.
                LaunchedEffect(state) { if (state !is xendroid.compose.ui.library.LibraryUiState.Loading) back() }
                return@composable
            }
            var titleId by remember(game.stableId) { mutableStateOf(game.titleId?.uppercase()?.takeIf { it.matches(Regex("[0-9A-F]{8}")) && it != "00000000" }) }
            LaunchedEffect(game.stableId) { if (titleId == null) titleId = library.resolveTitleId(game) }
            val id = titleId
            val settings: GameSettingsViewModel? = id?.let { viewModel(key = "game-settings-$it", factory = container.gameSettingsViewModelFactory(it)) }
            val patches: GamePatchesViewModel? = id?.let { viewModel(key = "game-patches-$it", factory = container.gamePatchesViewModelFactory(it)) }
            val global: SettingsViewModel = viewModel(key = "global-settings", factory = container.settingsViewModelFactory())
            val go: (String) -> Unit = { route -> if (entry.canNavigate(nav)) nav.navigate(route) }
            xendroid.compose.ui.game.GameScreen(
                game = game, library = library, compressVm = compressVm, settings = settings, global = global, patches = patches,
                initialSection = entry.arguments?.getString("section"),
                links = xendroid.compose.ui.game.GameScreenLinks(
                    onBack = back,
                    onPatches = { id?.let { go("${Routes.GAME_PATCHES}/$it?name=${Uri.encode(game.name)}") } },
                    onContent = { id?.let { go("${Routes.CONTENT_MANAGER}/$it?name=${Uri.encode(game.name)}") } },
                    onSaves = { id?.let { go("${Routes.SAVES}/$it?name=${Uri.encode(game.name)}") } },
                    onDiagnostics = { go("${Routes.DIAGNOSTICS}?title=${id.orEmpty()}") },
                    onCompare = { go("${Routes.BENCHMARK}?title=${id.orEmpty()}") },
                    onDrivers = { go(Routes.DRIVERS) },
                    onInstallFromDisc = { path -> go("${Routes.INSTALL_CONTENT}?src=" + Uri.encode(path)) },
                ),
            )
        }
        composable("${Routes.BENCHMARK}?title={title}", arguments = listOf(
            navArgument("title") { nullable = true; defaultValue = null },
        )) { entry ->
            val games = libraryGames(nav, entry, container)
            xendroid.compose.ui.benchmark.BenchmarkScreen(onBack = entry.backOnce(nav),
                titleId = entry.arguments?.getString("title")?.takeIf { it.isNotEmpty() },
                links = xendroid.compose.ui.benchmark.BenchmarkLinks(gameName = { games.byTitle[it]?.name }))
        }
        composable(Routes.CONTROLLER_TEST) { entry ->
            val vm: SettingsViewModel = viewModel(factory = container.settingsViewModelFactory())
            xendroid.compose.ui.controllertest.ControllerTestScreen(onBack = entry.backOnce(nav), global = vm)
        }
        composable(Routes.PHONE_CONTROLLER) { entry ->
            val vm: PhoneControllerViewModel = viewModel()
            PhoneControllerScreen(vm = vm, onBack = entry.backOnce(nav))
        }
        composable(Routes.SETTINGS) { entry ->
            val vm: SettingsViewModel = viewModel(factory = container.settingsViewModelFactory())
            val games = libraryGames(nav, entry, container)
            val go: (String) -> Unit = { route -> if (entry.canNavigate(nav)) nav.navigate(route) }
            SettingsScreen(vm = vm, onBack = entry.backOnce(nav), links = xendroid.compose.ui.settings.SettingsLinks(
                onDrivers = { go(Routes.DRIVERS) },
                onGameSettings = { title -> games.byTitle[title]?.let { go(gameRoute(it, xendroid.compose.ui.game.GameSections.SETTINGS)) } },
                onDiagnostics = { go("${Routes.DIAGNOSTICS}?title=") },
                onCompare = { go(Routes.BENCHMARK) },
                onControllerTest = { go(Routes.CONTROLLER_TEST) },
                onAbout = { go(Routes.ABOUT) },
                gameName = { games.byTitle[it]?.name },
                gameArt = { title -> games.byTitle[title]?.let { games.art(it) } },
            ))
        }
        composable(Routes.DRIVERS) { entry ->
            val vm: SettingsViewModel = viewModel(factory = container.settingsViewModelFactory())
            val games = libraryGames(nav, entry, container)
            val go: (String) -> Unit = { route -> if (entry.canNavigate(nav)) nav.navigate(route) }
            xendroid.compose.ui.drivers.DriversScreen(
                global = vm, onBack = entry.backOnce(nav),
                onGameSettings = { title -> games.byTitle[title]?.let { go(gameRoute(it, xendroid.compose.ui.game.GameSections.SETTINGS)) } },
                gameName = { games.byTitle[it]?.name },
                gameArt = { title -> games.byTitle[title]?.let { games.art(it) } },
            )
        }
        composable(Routes.CONTROLS) { entry ->
            val vm: SettingsViewModel = viewModel(factory = container.settingsViewModelFactory())
            val keymap: KeymapViewModel = viewModel(factory = container.keymapViewModelFactory())
            val go: (String) -> Unit = { route -> if (entry.canNavigate(nav)) nav.navigate(route) }
            xendroid.compose.ui.controls.ControlsScreen(vm, keymap, onBack = entry.backOnce(nav), links = xendroid.compose.ui.controls.ControlsLinks(
                onKeymap = { go(Routes.KEYMAP) },
                onEditor = { go(Routes.GAMEPAD_EDITOR) },
                onTest = { go(Routes.CONTROLLER_TEST) },
                onPhone = { go(Routes.PHONE_CONTROLLER) },
            ))
        }
        composable(Routes.KEYMAP) { entry ->
            val vm: KeymapViewModel =
                viewModel(factory = container.keymapViewModelFactory())
            KeymapScreen(vm = vm, onBack = entry.backOnce(nav))
        }
        composable(Routes.ABOUT) { entry ->
            val go: (String) -> Unit = { route -> if (entry.canNavigate(nav)) nav.navigate(route) }
            AboutScreen(onBack = entry.backOnce(nav), onUpdates = { go(Routes.UPDATES) }, onDiagnostics = { go("${Routes.DIAGNOSTICS}?title=") },
                onSetup = {
                    assistantRequested = true
                    nav.popBackStack(Routes.LIBRARY, inclusive = false)
                })
        }
        composable(Routes.UPDATES) { entry ->
            xendroid.compose.updater.UpdateScreen(onBack = entry.backOnce(nav))
        }
        composable(Routes.PROFILES) { entry ->
            val vm: ProfileManagerViewModel =
                viewModel(factory = container.profileManagerViewModelFactory())
            val games = libraryGames(nav, entry, container)
            val go: (String) -> Unit = { route -> if (entry.canNavigate(nav)) nav.navigate(route) }
            ProfilesScreen(vm = vm, onBack = entry.backOnce(nav), links = xendroid.compose.ui.profile.ProfilesLinks(
                onSaves = { title -> go("${Routes.SAVES}/$title?name=${Uri.encode(games.byTitle[title]?.name.orEmpty())}") },
                gameName = { games.byTitle[it]?.name },
                gameArt = { title -> games.byTitle[title]?.let { games.art(it) } },
            ))
        }
        composable(Routes.GAMEPAD_EDITOR) { entry ->
            val ctx = LocalContext.current
            val controller = remember { GamepadController(ctx.applicationContext) }
            GamepadEditorScreen(controller = controller, onDone = entry.backOnce(nav))
        }
        composable(
            route = "${Routes.PER_GAME_SETTINGS}/{titleId}?name={name}&format={format}&uri={uri}",
            arguments = listOf(
                navArgument("titleId") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("format") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("uri") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { backStackEntry ->
            val titleId = backStackEntry.arguments?.getString("titleId") ?: return@composable
            val gameName = backStackEntry.arguments?.getString("name") ?: ""
            val vm: GameSettingsViewModel =
                viewModel(factory = container.gameSettingsViewModelFactory(titleId))
            PerGameSettingsScreen(
                vm = vm,
                gameName = gameName,
                onBack = backStackEntry.backOnce(nav),
            )
        }
        composable(
            route = "${Routes.GAME_PATCHES}/{titleId}?name={name}",
            arguments = listOf(
                navArgument("titleId") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { backStackEntry ->
            val titleId = backStackEntry.arguments?.getString("titleId") ?: return@composable
            val gameName = backStackEntry.arguments?.getString("name") ?: ""
            val vm: GamePatchesViewModel =
                viewModel(factory = container.gamePatchesViewModelFactory(titleId))
            GamePatchesScreen(vm = vm, gameName = gameName, onBack = backStackEntry.backOnce(nav))
        }
        composable(
            route = "${Routes.CONTENT_MANAGER}/{titleId}?name={name}",
            arguments = listOf(
                navArgument("titleId") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { backStackEntry ->
            val titleId = backStackEntry.arguments?.getString("titleId") ?: return@composable
            val gameName = backStackEntry.arguments?.getString("name") ?: ""
            val vm: ContentManagerViewModel =
                viewModel(factory = container.gameContentManagerViewModelFactory(titleId))
            val games = libraryGames(nav, backStackEntry, container)
            ContentScreen(vm = vm, onBack = backStackEntry.backOnce(nav), titleId = titleId.uppercase(), gameName = gameName,
                art = games.byTitle[titleId.uppercase()]?.let { games.art(it) },
                links = xendroid.compose.ui.content.ContentLinks(gameName = { games.byTitle[it]?.name }))
        }
        composable("${Routes.CONTENT}?section={section}", arguments = listOf(
            navArgument("section") { nullable = true; defaultValue = null },
        )) { entry ->
            val vm: ContentManagerViewModel = viewModel(factory = container.gameContentManagerViewModelFactory(null))
            val installer: InstallContentViewModel = viewModel(factory = container.installContentViewModelFactory())
            val games = libraryGames(nav, entry, container)
            val go: (String) -> Unit = { route -> if (entry.canNavigate(nav)) nav.navigate(route) }
            ContentScreen(vm = vm, onBack = entry.backOnce(nav), installer = installer,
                links = xendroid.compose.ui.content.ContentLinks(
                    gameName = { games.byTitle[it]?.name },
                    gameArt = { title -> games.byTitle[title]?.let { games.art(it) } },
                    onOpenGame = { title, name -> go("${Routes.CONTENT_MANAGER}/$title?name=${Uri.encode(name)}") },
                ),
                initialSection = entry.arguments?.getString("section")?.takeIf { it.isNotEmpty() })
        }
        composable(
            "${Routes.INSTALL_CONTENT}?src={src}",
            arguments = listOf(navArgument("src") { nullable = true; defaultValue = null }),
        ) { entry ->
            val vm: InstallContentViewModel =
                viewModel(factory = container.installContentViewModelFactory())
            InstallContentScreen(
                vm = vm,
                onBack = entry.backOnce(nav),
                sourcePath = entry.arguments?.getString("src")?.let { Uri.decode(it) },
            )
        }
        composable("${Routes.SAVES}/{titleId}?name={name}", arguments = listOf(
            navArgument("titleId") { type = NavType.StringType },
            navArgument("name") { nullable = true; defaultValue = null },
        )) { entry ->
            val titleId = entry.arguments?.getString("titleId") ?: return@composable
            val vm: SaveManagerViewModel = viewModel(factory = container.saveManagerViewModelFactory(titleId))
            val games = libraryGames(nav, entry, container)
            SaveManagerScreen(vm, entry.arguments?.getString("name").orEmpty(), entry.backOnce(nav),
                art = games.byTitle[titleId.uppercase()]?.let { games.art(it) })
        }
        composable("${Routes.DIAGNOSTICS}?title={title}", arguments = listOf(
            navArgument("title") { nullable = true; defaultValue = null },
        )) { entry ->
            val games = libraryGames(nav, entry, container)
            DiagnosticsScreen(entry.arguments?.getString("title")?.takeIf { it.isNotEmpty() }, entry.backOnce(nav),
                links = xendroid.compose.ui.diagnostics.DiagnosticsLinks(
                    gameName = { games.byTitle[it]?.name },
                    gameArt = { title -> games.byTitle[title]?.let { games.art(it) } },
                ))
        }
    }
    }
}

/** The library's games by Title ID (and their art), for screens that name games: Settings, Drivers. */
private class LibraryGames(val byTitle: Map<String, xendroid.compose.data.Game>, val art: (xendroid.compose.data.Game) -> Any)

@Composable
private fun libraryGames(nav: NavController, entry: NavBackStackEntry, container: AppContainer): LibraryGames {
    val libraryEntry = remember(entry) { runCatching { nav.getBackStackEntry(Routes.LIBRARY) }.getOrNull() } ?: return LibraryGames(emptyMap()) { it }
    val library: GameLibraryViewModel = viewModel(libraryEntry, factory = container.libraryViewModelFactory())
    val state by library.state.collectAsStateWithLifecycle()
    val games = (state as? xendroid.compose.ui.library.LibraryUiState.Loaded)?.games.orEmpty()
    return remember(games) {
        LibraryGames(games.filter { !it.titleId.isNullOrBlank() }.groupBy { it.titleId!!.uppercase() }.mapValues { it.value.first() }) { library.iconFileOrFallback(it) }
    }
}
