package xendroid.compose.ui.library

import xendroid.compose.core.R
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import xendroid.compose.core.EmulatorRuntime
import xendroid.compose.core.ContentPaths
import java.io.File
import xendroid.compose.core.GameMetadataSource
import xendroid.compose.core.ProfileBootstrap
import xendroid.compose.archive.ArchiveFiles
import xendroid.compose.data.CoverPolicy
import xendroid.compose.data.CoverStore
import xendroid.compose.data.Game
import xendroid.compose.data.GameCollection
import xendroid.compose.data.GameFormat
import xendroid.compose.data.GameLibraryRepository
import xendroid.compose.data.IconCache
import xendroid.compose.data.MissingTitle
import xendroid.compose.data.PreferencesStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The launch action emitted on game tap. Resolves once a host Activity registers
 *  for this action; until then the Intent will not resolve (no-op resolveActivity). */
const val ACTION_LAUNCH_GAME = "xendroid.intent.action.xendroid"
const val EXTRA_GAME_URI = "game_uri"
const val EXTRA_DISC_LABELS = "disc_labels"
const val EXTRA_DISC_PATHS = "disc_paths"
/** 15e: what the loading screen shows (the cover as one of this app's own files). */
const val EXTRA_GAME_NAME = "game_name"
const val EXTRA_GAME_ART = "game_art"

/** Minimum time the pull-to-refresh indicator stays up, so a fast warm-cache rescan
 *  doesn't outrun its reveal animation and leave it visually stuck. */
private const val MIN_REFRESH_INDICATOR_MS = 500L

/** Which long-press action triggered title-id resolution (both need the id, then branch). */
enum class GameAction { PER_GAME_SETTINGS, GAME_PATCHES, MANAGE_CONTENT, SAVES, DIAGNOSTICS }
enum class LibrarySort(val label: String) {
    NAME_ASC("Name A–Z"), NAME_DESC("Name Z–A"), FORMAT("Format"), RECENT("Recently played"),
}

/** Orders by the title's last finished run (newest first); never-played games follow by name. */
fun sortByRecent(games: List<Game>, activity: Map<String, xendroid.compose.sessions.TitleActivity>): List<Game> =
    games.sortedWith(compareByDescending<Game> { game -> game.titleId?.uppercase()?.let { activity[it]?.lastPlayedAt } ?: Long.MIN_VALUE }
        .thenBy { it.name.lowercase() })

/** Async resolution of a game's title id (needed before the per-game settings editor or the
 *  patches screen can open). Driven by the long-press dialog; all formats resolve boot-free. */
sealed interface TitleIdState {
    data object Idle : TitleIdState
    data class Loading(val game: Game, val action: GameAction) : TitleIdState
    data class Resolved(val game: Game, val titleId: String, val action: GameAction) : TitleIdState
    data class Error(val game: Game, val message: String) : TitleIdState
}

class GameLibraryViewModel(
    private val repo: GameLibraryRepository,
    private val iconCache: IconCache,
    private val covers: CoverStore,
    private val appContext: Context,
) : ViewModel() {

    private val preferences = PreferencesStore(appContext)
    val favorites = preferences.favoriteIds
        .catch { Log.w("GameLibrary", "Reading favorites failed", it); emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    val sort = preferences.librarySort
        .map { raw -> LibrarySort.entries.firstOrNull { it.name == raw } ?: LibrarySort.NAME_ASC }
        .catch { Log.w("GameLibrary", "Reading library sort failed", it); emit(LibrarySort.NAME_ASC) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibrarySort.NAME_ASC)

    fun toggleFavorite(game: Game) {
        viewModelScope.launch {
            runCatching { preferences.toggleFavorite(game) }
                .onFailure { Log.w("GameLibrary", "Saving favorite failed", it) }
        }
    }

    /** L06: the user's collections (names and members by [Game.identityKey]). */
    val collections = preferences.collections
        .catch { Log.w("GameLibrary", "Reading collections failed", it); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** One transaction; a refused change (duplicate name, full) comes back as the failure. */
    suspend fun editCollections(change: (List<GameCollection>) -> List<GameCollection>): Result<Unit> =
        try {
            preferences.editCollections(change)
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("GameLibrary", "Changing collections failed", e)
            Result.failure(e)
        }

    fun setSort(sort: LibrarySort) {
        viewModelScope.launch {
            runCatching { preferences.setLibrarySort(sort.name) }
                .onFailure { Log.w("GameLibrary", "Saving library sort failed", it) }
        }
    }

    private val _state = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private val _titleId = MutableStateFlow<TitleIdState>(TitleIdState.Idle)
    val titleIdState: StateFlow<TitleIdState> = _titleId.asStateFlow()

    /** True while a scan is running. Drives the swipe-down (pull-to-refresh) indicator
     *  WITHOUT flashing the full-screen spinner over an already-loaded list. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Last played / play time per Title ID, from finished game runs (sessions/). */
    private val _activity = MutableStateFlow<Map<String, xendroid.compose.sessions.TitleActivity>>(emptyMap())
    val activity: StateFlow<Map<String, xendroid.compose.sessions.TitleActivity>> = _activity.asStateFlow()

    /** Redesign: each title's latest own compatibility result, for the library's cards. */
    private val _compat = MutableStateFlow<Map<String, xendroid.compose.compatibility.CompatStatus>>(emptyMap())
    val compat: StateFlow<Map<String, xendroid.compose.compatibility.CompatStatus>> = _compat.asStateFlow()

    /** Compatibility reports and the last finished run of the game whose sheet is open. */
    data class GameDetails(
        val identityKey: String,
        val titleId: String?,
        val compatibility: xendroid.compose.compatibility.TitleCompatibility?,
        val lastRun: xendroid.compose.sessions.SessionRun?,
        /** Flight recorder of [lastRun] (C01), when it saved one. */
        val lastRunEvents: xendroid.compose.sessions.RunEventLog? = null,
        /** L06: names of the installed title updates and number of DLC packages; null if unreadable. */
        val updates: List<String>? = null,
        val dlcCount: Int? = null,
        /** L06: patch entries enabled and shipped for the title; null when none ship for it. */
        val patchesEnabled: Int? = null,
        val patchesTotal: Int? = null,
        /** C04: the catalog's results for the game; null when this build has no catalog. */
        val catalog: CatalogView? = null,
        /** L06: the gamertag [lastRun] started with as P1, while that profile still exists. */
        val lastProfile: String? = null,
        /** The game's shader and pipeline cache: files and bytes; null when unreadable. */
        val shaderCache: Pair<Int, Long>? = null,
        /** Round 2: the title's finished runs, newest first (Performance picks one to show). */
        val runs: List<xendroid.compose.sessions.SessionRun> = emptyList(),
        /** Round 2: by name, the version each installed title update brings ("1.0.3.0"). */
        val updateVersions: Map<String, String> = emptyMap(),
    )

    /** C04: the kept catalog copy (null = never downloaded) and the game's results by setup. */
    data class CatalogView(
        val copy: xendroid.compose.compatibility.CompatCatalogStore.Copy?,
        val results: List<xendroid.compose.compatibility.CompatCatalog.SetupResults>,
        val refreshing: Boolean = false,
        val message: String? = null,
    )
    private val _details = MutableStateFlow<GameDetails?>(null)
    val details: StateFlow<GameDetails?> = _details.asStateFlow()
    private val compatibilityStore by lazy {
        xendroid.compose.compatibility.CompatibilityStore(
            java.io.File(xendroid.compose.Application.get_internal_data_dir(), "compatibility"))
    }

    private fun validTitle(game: Game): String? =
        game.titleId?.uppercase()?.takeIf { it.matches(Regex("[0-9A-F]{8}")) && it != "00000000" }

    fun loadDetails(game: Game, catalogMessage: String? = null) {
        val title = validTitle(game)
        if (_details.value?.identityKey != game.identityKey) _details.value = GameDetails(game.identityKey, title, null, null)
        if (title == null) return
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val runs = xendroid.compose.sessions.SessionRuns.store()
                    // One read of the records: this title's runs, and its play history (the
                    // library's is from the last scan, which does not run with the sheet open).
                    val all = runs.runs()
                    val mine = all.filter { it.titleId.equals(title, ignoreCase = true) && it.state.final }
                    val lastRun = mine.firstOrNull()
                    xendroid.compose.sessions.titleActivityOf(mine).firstOrNull()?.let { played ->
                        _activity.update { it + (played.titleId to played) }
                    }
                    val content = runCatching { installedContent(title) }
                        .onFailure { Log.w("GameLibrary", "Listing installed content failed", it) }.getOrNull()
                    val patches = runCatching { patchesOf(title) }
                        .onFailure { Log.w("GameLibrary", "Reading patches failed", it) }.getOrNull()
                    val gpu = lastRun?.driver?.gpu?.ifBlank { null } ?: EmulatorRuntime.gpuDeviceName
                    val lastProfile = lastRun?.profileXuid?.let { xuid ->
                        runCatching { localProfiles() }.getOrDefault(emptyList())
                            .firstOrNull { it.xuid.equals(xuid, ignoreCase = true) }?.gamertag?.ifBlank { null }
                    }
                    val shaderCache = runCatching {
                        xendroid.compose.core.ShaderCaches.files(shaderCacheRoot(), title).let { it.size to it.sumOf(java.io.File::length) }
                    }.onFailure { Log.w("GameLibrary", "Reading the shader cache failed", it) }.getOrNull()
                    GameDetails(game.identityKey, title, compatibilityStore.get(title), lastRun,
                        lastRun?.let { runs.events(it.runId) }, content?.first, content?.second,
                        patches?.first, patches?.second, catalogView(title, gpu, catalogMessage), lastProfile, shaderCache,
                        runs = mine.take(MAX_SHEET_RUNS), updateVersions = content?.third.orEmpty())
                }.onFailure { Log.w("GameLibrary", "Reading game details failed", it) }.getOrNull()
            }
            if (loaded != null && _details.value?.identityKey == game.identityKey) _details.value = loaded
        }
    }

    /** C04: off unless the build names a catalog and its publisher's keys. */
    private val catalogStore: xendroid.compose.compatibility.CompatCatalogStore? by lazy {
        xendroid.compose.compatibility.CatalogConfig.parse(xendroid.compose.BuildConfig.CATALOG_URL,
            xendroid.compose.BuildConfig.CATALOG_KEYS)?.let { config ->
            xendroid.compose.compatibility.CompatCatalogStore(
                java.io.File(xendroid.compose.Application.get_internal_data_dir(), "catalog"), config,
                xendroid.compose.compatibility.CatalogHttp::fetch)
        }
    }

    private fun catalogView(title: String, gpu: String?, message: String?): CatalogView? {
        val store = catalogStore ?: return null
        val copy = runCatching { store.copy() }.onFailure { Log.w("GameLibrary", "Reading the catalog failed", it) }.getOrNull()
        return CatalogView(copy, copy?.let {
            xendroid.compose.compatibility.CompatCatalog.resultsFor(it.payload, title, xendroid.compose.BuildConfig.VERSION_NAME, gpu)
        }.orEmpty(), message = message)
    }

    /** C04: downloads the catalog now (only when the player asks); the kept copy stays on any problem. */
    fun refreshCatalog(game: Game) {
        val store = catalogStore ?: return
        _details.value = _details.value?.let { it.copy(catalog = it.catalog?.copy(refreshing = true, message = null)) }
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                when (val result = runCatching { store.refresh() }.getOrElse {
                    xendroid.compose.compatibility.CompatCatalogStore.Refresh.Failed(it.message ?: "error")
                }) {
                    is xendroid.compose.compatibility.CompatCatalogStore.Refresh.Updated -> appContext.getString(xendroid.compose.R.string.lib_catalog_updated, result.sequence)
                    xendroid.compose.compatibility.CompatCatalogStore.Refresh.Unchanged -> appContext.getString(xendroid.compose.R.string.lib_catalog_current)
                    is xendroid.compose.compatibility.CompatCatalogStore.Refresh.Refused ->
                        appContext.getString(xendroid.compose.R.string.lib_catalog_refused, result.reason)
                    is xendroid.compose.compatibility.CompatCatalogStore.Refresh.Failed -> appContext.getString(xendroid.compose.R.string.lib_catalog_failed, result.reason)
                }
            }
            loadDetails(game, message)
        }
    }

    fun clearDetails() { _details.value = null }

    /** The core's cache root, honoring Storage|cache_root as the core does at boot. */
    private fun shaderCacheRoot(): java.io.File {
        val configured = runCatching {
            val handle = xendroid.compose.settings.ConfigStore(appContext).openLiveSnapshot()
            try { handle.getString("Storage", "cache_root") } finally { handle.closeDiscard() }
        }.getOrNull()
        return xendroid.compose.core.ShaderCaches.cacheRoot(java.io.File(xendroid.compose.Utils.get_storage_root_path()), configured)
    }

    /** Removes the game's shader and pipeline caches; null while a game holds the storage
     *  (a running game writes them), else what was removed. Reloads the sheet's details. */
    suspend fun clearShaderCache(game: Game): xendroid.compose.core.ShaderCaches.Cleared? {
        val title = validTitle(game) ?: return null
        val cleared = withContext(Dispatchers.IO) {
            try {
                xendroid.compose.core.StorageAccess.acquire().use { xendroid.compose.core.ShaderCaches.clear(shaderCacheRoot(), title) }
            } catch (e: xendroid.compose.archive.ContentBusyException) {
                null
            }
        }
        loadDetails(game)
        return cleared
    }

    /** Installed title updates (names) and DLC count, from the core's own content listing. The
     *  library already loaded the core; without it this answers nothing rather than loading it. */
    private fun installedContent(title: String): Triple<List<String>, Int, Map<String, String>>? {
        val emu = EmulatorRuntime.emulator ?: return null
        val root = ContentPaths.contentRoot().absolutePath
        val updates = emu.list_content(root, title, ContentPaths.TU_CONTENT_TYPE) ?: return null
        val dlc = emu.list_content(root, title, ContentPaths.DLC_CONTENT_TYPE) ?: return null
        val named = updates.map { (it.displayName?.ifBlank { null } ?: it.pkgDir) to it.pkgDir }
        val versions = named.mapNotNull { (name, dir) ->
            runCatching { xendroid.compose.core.ContentVersion.ofInstalled(java.io.File(ContentPaths.contentDir(title, ContentPaths.TU_CONTENT_TYPE), dir)) }
                .getOrNull()?.let { name to it }
        }.toMap()
        return Triple(named.map { it.first }, dlc.size, versions)
    }

    /** Enabled and shipped patch entries of the title (bundled catalog + the user's toggles). */
    private fun patchesOf(title: String): Pair<Int, Int>? {
        val files = xendroid.compose.patches.PatchStore(xendroid.compose.patches.AssetPatchAssets(appContext),
            xendroid.compose.patches.PatchPaths.patchesDir()).patchesForTitle(title)
        if (files.isEmpty()) return null
        return files.sumOf { file -> file.entries.count { it.isEnabled } } to files.sumOf { it.entries.size }
    }

    /** Stores the user's own result with this build and the driver of the last run. */
    fun rateCompatibility(game: Game, status: xendroid.compose.compatibility.CompatStatus, note: String) {
        val title = validTitle(game) ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val lastRun = xendroid.compose.sessions.SessionRuns.store().lastRun(title)
                    compatibilityStore.report(title, status, note, xendroid.compose.BuildConfig.VERSION_NAME,
                        lastRun?.driver?.gpu?.ifBlank { null } ?: EmulatorRuntime.gpuDeviceName ?: "unknown GPU",
                        lastRun?.driver, game.mediaId, game.discNumber)
                }.onFailure { Log.w("GameLibrary", "Saving the compatibility report failed", it) }
            }
            _compat.value = _compat.value + (title to status)
            loadDetails(game)
        }
    }

    /** U11: the gamertag games sign in with now (P1), for the game sheet. */
    private val _activeProfile = MutableStateFlow<String?>(null)
    val activeProfile: StateFlow<String?> = _activeProfile.asStateFlow()

    private val profilePrefs get() = appContext.getSharedPreferences(xendroid.compose.data.ProfilePick.PREFS, Context.MODE_PRIVATE)

    /** U11: ask which profile plays before each game (when there is more than one). */
    var askProfileBeforePlaying: Boolean
        get() = profilePrefs.getBoolean(xendroid.compose.data.ProfilePick.ASK, true)
        set(value) = profilePrefs.edit { putBoolean(xendroid.compose.data.ProfilePick.ASK, value) }

    private fun localProfiles(): List<xendroid.compose.data.PlayableProfile> = EmulatorRuntime.emulator
        ?.list_profiles(ContentPaths.contentRoot().absolutePath)
        ?.map { xendroid.compose.data.PlayableProfile(it.xuid, it.gamertag.orEmpty()) }.orEmpty()

    private fun configuredXuid(): String? = configuredSlots()[0]

    /** U11: the profile of each player slot (P1–P4) in the config; null = nobody. */
    private fun configuredSlots(): List<String?> {
        val handle = xendroid.compose.settings.ConfigStore(appContext).openLiveSnapshot()
        return try {
            xendroid.compose.data.ProfileSlots.normalize(List(xendroid.compose.data.ProfileSlots.COUNT) { slot ->
                handle.getString(xendroid.compose.data.ProfileSlots.SECTION, xendroid.compose.data.ProfileSlots.key(slot))
            })
        } finally { handle.closeDiscard() }
    }

    /** Writes the slots that differ from [before], under the config lock. */
    private fun writeSlots(before: List<String?>, after: List<String?>) {
        val changes = xendroid.compose.data.ProfileSlots.changes(before, after)
        if (changes.isEmpty()) return
        xendroid.compose.settings.ConfigStore(appContext).editLiveConfig { handle ->
            changes.forEach { (slot, xuid) ->
                handle.putString(xendroid.compose.data.ProfileSlots.SECTION, xendroid.compose.data.ProfileSlots.key(slot), xuid)
            }
        }
    }

    private fun refreshActiveProfile() {
        val xuid = configuredXuid()
        _activeProfile.value = localProfiles().firstOrNull { it.xuid.equals(xuid, ignoreCase = true) }?.gamertag
    }

    /** U11: launch as the configured profile, or ask (see [xendroid.compose.data.ProfilePick]).
     *  Player slots naming a profile that is gone (or twice) are cleared first. */
    suspend fun profileDecision(): xendroid.compose.data.ProfilePick.Decision = withContext(Dispatchers.IO) {
        EmulatorRuntime.ensureLoaded()
        val profiles = localProfiles()
        val before = configuredSlots()
        val slots = if (profiles.isEmpty()) before else xendroid.compose.data.ProfileSlots.reconcile(before, profiles.map { it.xuid })
        runCatching { writeSlots(before, slots) }.onFailure { Log.w("GameLibrary", "Clearing stale player slots failed", it) }
        xendroid.compose.data.ProfilePick.decide(profiles, slots[0], askProfileBeforePlaying, slots)
    }

    /** Signs [xuid] in as P1 for the next boot (under the config lock); another player slot
     *  that had it signs in nobody. */
    suspend fun playAs(xuid: String, dontAskAgain: Boolean) = withContext(Dispatchers.IO) {
        if (dontAskAgain) askProfileBeforePlaying = false
        val before = configuredSlots()
        writeSlots(before, xendroid.compose.data.ProfileSlots.assign(before, 0, xuid))
        runCatching { refreshActiveProfile() }
    }

    /** L06: played or seen titles that the last scan did not list (file gone, folder away...). */
    private val _missing = MutableStateFlow<List<MissingTitle>>(emptyList())
    val missing: StateFlow<List<MissingTitle>> = _missing.asStateFlow()

    /** Play history (recents, play time) and, with it, the missing titles of [loaded]: one
     *  read of the run records per scan. A [LibraryUiState.Loaded.cached] list is unverified,
     *  so it gets the history only. */
    private suspend fun refreshHistory(loaded: LibraryUiState.Loaded) {
        val (activity, missing) = withContext(Dispatchers.IO) {
            val runs = runCatching { xendroid.compose.sessions.SessionRuns.store().runs() }
                .onFailure { Log.w("GameLibrary", "Reading play history failed", it) }
                .getOrDefault(emptyList())
            val missing = if (loaded.cached) null else runCatching {
                repo.missingTitles(loaded.games, loaded.unavailableRoots, xendroid.compose.sessions.lastGamePaths(runs))
            }.onFailure { Log.w("GameLibrary", "Listing missing games failed", it) }.getOrDefault(emptyList())
            xendroid.compose.sessions.titleActivityOf(runs).associateBy { it.titleId } to missing
        }
        _activity.value = activity
        missing?.let { _missing.value = it }
        _compat.value = withContext(Dispatchers.IO) {
            runCatching { compatibilityStore.all().mapNotNull { (id, c) -> c.latest?.let { id to it.status } }.toMap() }
                .onFailure { Log.w("GameLibrary", "Reading compatibility results failed", it) }.getOrDefault(emptyMap())
        }
    }

    /** L09: what the running scan is doing (null when idle). */
    val scanProgress: StateFlow<GameLibraryRepository.ScanProgress?> = repo.progress

    /** The running refresh. A refresh asked for meanwhile runs once after it ([rescanPending]),
     *  so a folder added during a scan is scanned too. Main thread only. */
    private var refreshJob: Job? = null
    private var rescanPending = false

    /** "Stop" while scanning: what is on screen stays (last time's list, if any); what was
     *  extracted so far stays cached. */
    fun stopScan() {
        val job = refreshJob?.takeIf { it.isActive } ?: return
        rescanPending = false
        job.cancel()
        _isRefreshing.value = false
        if (_state.value !is LibraryUiState.Loaded) {
            _state.value = LibraryUiState.Error(appContext.getString(xendroid.compose.R.string.lib_scan_stopped))
        }
    }

    fun hideMissing(title: MissingTitle) {
        _missing.value = _missing.value.filterNot { it.titleId == title.titleId }
        viewModelScope.launch {
            runCatching { repo.hideMissingTitle(title) }.onFailure { Log.w("GameLibrary", "Saving the choice failed", it) }
        }
    }

    /** The kept cover of a title that is not in the library (L05/L06). */
    fun coverOfTitle(titleId: String): Any = covers.displayCover(titleId, null) ?: R.drawable.zephyr_app_icon

    fun refresh() {
        if (!EmulatorRuntime.supportsVulkan) { _state.value = LibraryUiState.NoVulkan; return }
        if (refreshJob?.isActive == true) { rescanPending = true; return }
        _isRefreshing.value = true
        refreshJob = viewModelScope.launch {
            do {
                rescanPending = false
                refreshOnce()
            } while (rescanPending)
            _isRefreshing.value = false
        }
    }

    private suspend fun refreshOnce() {
        // Keep an existing list visible during a pull-to-refresh (show only the pull
        // indicator); the full-screen spinner is for the first/empty load.
        val wasLoaded = _state.value is LibraryUiState.Loaded
        if (!wasLoaded) {
            _state.value = LibraryUiState.Loading
            // L09: last time's list at once, from the metadata cache, while the walk runs.
            val cached = try {
                repo.cachedGames()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("GameLibrary", "Reading the cached library failed", e)
                emptyList()
            }
            if (cached.isNotEmpty()) {
                val shown = LibraryUiState.Loaded(cached, cached = true)
                refreshHistory(shown)
                _state.value = shown
            }
        }
        val startMs = SystemClock.elapsedRealtime()
        val next = try {
            // ensureLoaded() can sleep + System.loadLibrary on delay-load devices
            // (Adreno 5xx/6xx) -> never on the main thread.
            withContext(Dispatchers.IO) {
                EmulatorRuntime.ensureLoaded()
                ProfileBootstrap.ensureDefaultProfile(appContext)
            }
            when (val r = repo.scan()) {
                GameLibraryRepository.ScanResult.NoFolder -> LibraryUiState.NoFolder
                GameLibraryRepository.ScanResult.PermissionLost -> LibraryUiState.PermissionLost
                is GameLibraryRepository.ScanResult.Games ->
                    LibraryUiState.Loaded(r.games, r.unavailableRoots, truncated = r.truncated)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            LibraryUiState.Error(e.message ?: appContext.getString(xendroid.compose.R.string.lib_load_failed))
        }
        // History before the list, so "Recently played" and the missing games match it.
        if (next is LibraryUiState.Loaded) refreshHistory(next) else _missing.value = emptyList()
        // U11: who plays, for the game sheet.
        if (next is LibraryUiState.Loaded) withContext(Dispatchers.IO) {
            runCatching { refreshActiveProfile() }.onFailure { Log.w("GameLibrary", "Reading the active profile failed", it) }
        }
        // L10: patch copies the user toggled follow this app version's catalog before a launch.
        if (next is LibraryUiState.Loaded) withContext(Dispatchers.IO) {
            runCatching {
                xendroid.compose.patches.PatchStore(xendroid.compose.patches.AssetPatchAssets(appContext),
                    xendroid.compose.patches.PatchPaths.patchesDir()).syncAll()
            }.onFailure { Log.w("GameLibrary", "Updating patch copies failed", it) }
        }
        _state.value = next
        // A warm-cache rescan finishes faster than the PullToRefreshBox reveal animation,
        // which leaves the indicator visually stuck; hold it to a floor so it settles before
        // retracting (pull-to-refresh only -- the cold load shows the full-screen spinner).
        if (wasLoaded) {
            val elapsed = SystemClock.elapsedRealtime() - startMs
            if (elapsed < MIN_REFRESH_INDICATOR_MS) delay(MIN_REFRESH_INDICATOR_MS - elapsed)
        }
    }

    /** L03: the library's game folders, in order (the first receives full-game installs). */
    private val _folders = MutableStateFlow<List<String>>(emptyList())
    val folders: StateFlow<List<String>> = _folders

    fun loadFolders() {
        viewModelScope.launch { _folders.value = repo.gameDirPaths() }
    }

    /** Real-path (All Files Access) folder chosen in the built-in browser: added to the
     *  library's folders (a readable directory only) + rescan. */
    fun onRealPathFolderPicked(path: String) {
        viewModelScope.launch {
            repo.addGameDirPath(path)
            _folders.value = repo.gameDirPaths()
            refresh()
        }
    }

    /** Lote 6: full-game installs go to [path] from now on (it becomes the first folder). */
    fun makeInstallFolder(path: String) {
        viewModelScope.launch {
            repo.firstGameDirPath(path)
            _folders.value = repo.gameDirPaths()
        }
    }

    /** Stops scanning [path] (its files stay where they are) + rescan. */
    fun removeFolder(path: String) {
        viewModelScope.launch {
            repo.removeGameDirPath(path)
            _folders.value = repo.gameDirPaths()
            refresh()
        }
    }

    /** Lote 6: Undo of [removeFolder]: [before] comes back in its order (installs go where they went). */
    fun restoreFolders(before: List<String>) {
        viewModelScope.launch {
            repo.setGameDirPaths(before)
            _folders.value = repo.gameDirPaths()
            refresh()
        }
    }

    fun requestPerGameSettings(game: Game) = request(game, GameAction.PER_GAME_SETTINGS)
    fun requestGamePatches(game: Game) = request(game, GameAction.GAME_PATCHES)
    fun requestContentManager(game: Game) = request(game, GameAction.MANAGE_CONTENT)
    fun requestSaves(game: Game) = request(game, GameAction.SAVES)
    fun requestDiagnostics(game: Game) = request(game, GameAction.DIAGNOSTICS)

    /** Resolve a game's title id off-main, then the long-press dialog opens the matching screen. */
    private fun request(game: Game, action: GameAction) {
        _titleId.value = TitleIdState.Loading(game, action)
        viewModelScope.launch(Dispatchers.IO) {
            _titleId.value = runCatching {
                EmulatorRuntime.ensureLoaded()
                val tid = repo.readTitleId(appContext, game)
                // 00000000 is the unknown/placeholder title id (no real game carries it).
                if (tid.isNullOrBlank() || tid == "00000000")
                    TitleIdState.Error(game, appContext.getString(xendroid.compose.R.string.lib_title_id_unreadable))
                else TitleIdState.Resolved(game, tid, action)
            }.getOrElse { TitleIdState.Error(game, it.message ?: appContext.getString(xendroid.compose.R.string.lib_title_id_unreadable)) }
        }
    }

    fun clearTitleIdRequest() { _titleId.value = TitleIdState.Idle }

    /** Build the launch Intent that the host Activity resolves. Caller startActivity()s it. */
    /** Packages [game]'s disc carries that are not installed yet, i.e. the payload a
     *  mandatory-install title (GTA V and friends) needs on the HDD before it will run.
     *  Empty for an ordinary disc, or once its content is installed. Off-main: walks the
     *  disc image. */
    suspend fun uninstalledDiscContent(game: Game): List<GameMetadataSource.DiscContent> =
        withContext(Dispatchers.IO) {
            if (game.format != GameFormat.ISO && game.format != GameFormat.ZAR) {
                return@withContext emptyList()
            }
            EmulatorRuntime.ensureLoaded()
            GameMetadataSource().listDiscContent(game.launchUri).filterNot { item ->
                val titleId = item.titleId ?: return@filterNot false
                File(ContentPaths.contentDir(titleId, item.contentType), item.displayName)
                    .exists()
            }
        }

    fun buildLaunchIntent(game: Game, launchArgs: List<String> = emptyList()): Intent =
        Intent(ACTION_LAUNCH_GAME).apply {
            setPackage(appContext.packageName)          // self; host is in this app
            putExtra(EXTRA_GAME_URI, game.launchUri)
            // :emu cannot read the library, so the discs travel with the launch.
            val discs = discsOfTitle(game)
            putExtra(EXTRA_DISC_LABELS, discs.map { discLabelOf(it) }.toTypedArray())
            putExtra(EXTRA_DISC_PATHS, discs.map { it.launchUri }.toTypedArray())
            putExtra(EXTRA_GAME_NAME, game.name)
            coverFile(game)?.let { putExtra(EXTRA_GAME_ART, it.absolutePath) }
            // R4: this launch's own options, with the token that tells the host they are ours.
            if (launchArgs.isNotEmpty()) {
                putExtra(xendroid.compose.EmulatorHostActivity.EXTRA_LAUNCH_ARGS, launchArgs.toTypedArray())
                putExtra(xendroid.compose.EmulatorHostActivity.EXTRA_LAUNCH_TOKEN,
                    xendroid.compose.core.LaunchToken.get(appContext.filesDir))
            }
        }

    /** R4: [options] as command-line cvars, against the player slots configured now. */
    suspend fun launchArgs(options: xendroid.compose.core.LaunchOptions): List<String> = withContext(Dispatchers.IO) {
        val slots = if (options.profileXuid != null) runCatching { configuredSlots() }.getOrDefault(emptyList()) else emptyList()
        options.toArgs(slots).also { xendroid.compose.core.LaunchToken.get(appContext.filesDir) }
    }

    /** R4: who can play ("Start with…" profile choice) and who plays as P1 now. */
    suspend fun launchProfiles(): Pair<List<xendroid.compose.data.PlayableProfile>, String?> = withContext(Dispatchers.IO) {
        runCatching {
            EmulatorRuntime.ensureLoaded()
            localProfiles() to configuredXuid()
        }.onFailure { Log.w("GameLibrary", "Reading profiles failed", it) }.getOrDefault(emptyList<xendroid.compose.data.PlayableProfile>() to null)
    }

    /** The Title ID of [game]: the scan's, else read from the file now (null when unreadable). */
    suspend fun resolveTitleId(game: Game): String? {
        validTitle(game)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                EmulatorRuntime.ensureLoaded()
                repo.readTitleId(appContext, game)?.uppercase()?.takeIf { it.matches(Regex("[0-9A-F]{8}")) && it != "00000000" }
            }.onFailure { Log.w("GameLibrary", "Reading the title id failed", it) }.getOrNull()
        }
    }

    /** Every disc of [game]'s title, including the one being launched: after a swap
     *  the launched disc becomes a swap target again (install disc -> play disc).
     *  Matched on title id, which a set shares; STFS is excluded because updates
     *  and DLC share it too without being discs. */
    fun discsOfTitle(game: Game): List<Game> {
        val titleId = game.titleId?.takeIf { it.isNotBlank() && it != "00000000" }
            ?: return emptyList()
        val all = (_state.value as? LibraryUiState.Loaded)?.games ?: return emptyList()
        return all.filter {
            it.format != GameFormat.STFS &&
                it.titleId?.equals(titleId, ignoreCase = true) == true
        }.sortedWith(compareBy({ if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE },
                                { it.name.lowercase() }))
    }

    /** A set shares one XDBF title, so prefer the header's disc number and fall
     *  back to the file name. */
    fun discLabelOf(game: Game): String = when {
        game.discNumber > 0 && game.discCount > 1 ->
            appContext.getString(xendroid.compose.R.string.lib_disc_of, game.discNumber, game.discCount)
        game.discNumber > 0 -> appContext.getString(xendroid.compose.R.string.lib_disc, game.discNumber)
        else -> java.io.File(game.launchUri).name
    }

    /** Coil model for a game's tile: the user's cover, else the icon extracted from this file,
     *  else the copy kept by Title ID (L05), else the app_icon drawable resource id. Kept here so
     *  the View carries no IconCache dep. */
    fun iconFileOrFallback(game: Game): Any = coverFile(game) ?: R.drawable.zephyr_app_icon

    private fun coverFile(game: Game): File? =
        covers.displayCover(game.titleId, game.iconCacheName?.let { iconCache.fileFor(it) })

    /** Bumped when a cover changes, so tiles drop the model they remembered. */
    private val _coverRevision = MutableStateFlow(0)
    val coverRevision: StateFlow<Int> = _coverRevision.asStateFlow()

    fun hasCustomCover(game: Game): Boolean = covers.customFor(game.titleId) != null

    /** L05: the picked image becomes the cover of [game]'s title (every disc, wherever the file
     *  is). Bounded read, oriented and shrunk to [CoverPolicy.TARGET_SIDE] before it is stored. */
    suspend fun setCustomCover(game: Game, image: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val title = requireNotNull(CoverStore.normalize(game.titleId)) { appContext.getString(xendroid.compose.R.string.lib_no_title_id) }
            covers.setCustom(title, decodeCover(image))
            _coverRevision.update { it + 1 }
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("GameLibrary", "Changing the cover failed", e)
            Result.failure(e)
        }
    }

    suspend fun clearCustomCover(game: Game) {
        withContext(Dispatchers.IO) {
            runCatching { covers.clearCustom(game.titleId) }
                .onFailure { Log.w("GameLibrary", "Removing the cover failed", it) }
        }
        _coverRevision.update { it + 1 }
    }

    private fun decodeCover(image: Uri): ByteArray {
        val bytes = appContext.contentResolver.openInputStream(image)?.use {
            ArchiveFiles.readBounded(it, CoverPolicy.MAX_INPUT_BYTES)
        } ?: error(appContext.getString(xendroid.compose.R.string.pf_image_unreadable))
        // ImageDecoder applies the EXIF orientation; the size is checked before any pixel is decoded.
        val bitmap = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                val size = info.size
                val (width, height) = runCatching { CoverPolicy.scaledSize(size.width, size.height) }.getOrElse {
                    // U02: said in the shown language, like the avatar's.
                    throw IllegalArgumentException(if (size.width > 0 && size.height > 0)
                        appContext.getString(xendroid.compose.R.string.pf_image_too_large, size.width, size.height, CoverPolicy.MAX_SIDE)
                        else appContext.getString(xendroid.compose.R.string.lib_not_an_image), it)
                }
                decoder.setTargetSize(width, height)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (e: ImageDecoder.DecodeException) {
            throw IllegalArgumentException(appContext.getString(xendroid.compose.R.string.lib_not_an_image), e)
        }
        try {
            val out = java.io.ByteArrayOutputStream()
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { appContext.getString(xendroid.compose.R.string.lib_cover_encode_failed) }
            return out.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    val isPinShortcutSupported: Boolean
        get() = appContext.getSystemService<ShortcutManager>()
            ?.isRequestPinShortcutSupported == true

    /** True once a host Activity resolves the launch action.
     *  Until then, suppress launch-dependent affordances so we never pin a dead shortcut. */
    val canLaunchGames: Boolean
        get() = Intent(ACTION_LAUNCH_GAME)
            .setPackage(appContext.packageName)
            .resolveActivity(appContext.packageManager) != null

    /** Pin a launcher shortcut. Stable id = game.stableId (uri) to avoid name
     *  collisions (legacy used the display name -> collisions). */
    fun createShortcut(game: Game) {
        if (!isPinShortcutSupported || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val sm = appContext.getSystemService<ShortcutManager>() ?: return
        val intent = buildLaunchIntent(game).apply { action = Intent.ACTION_VIEW }
        // No resolving host yet; don't pin a shortcut that goes nowhere.
        if (intent.resolveActivity(appContext.packageManager) == null) return
        val icon = coverFile(game)
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }
            ?.let { Icon.createWithBitmap(it) }
            ?: Icon.createWithResource(appContext, R.drawable.zephyr_app_icon)
        sm.requestPinShortcut(
            ShortcutInfo.Builder(appContext, game.stableId)
                .setShortLabel(game.name)
                .setIcon(icon)
                .setIntent(intent)
                .build(),
            null
        )
    }

    // Last in the class: refresh() starts a coroutine on Main.immediate that reads properties
    // declared above, which must be initialized first.
    init { refresh() }
}

/** Round 2: the runs a game sheet offers to pick from in Performance. */
private const val MAX_SHEET_RUNS = 40
