package xendroid.compose

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import xendroid.compose.core.GameMetadataSource
import xendroid.compose.data.CoverStore
import xendroid.compose.data.GameLibraryRepository
import xendroid.compose.data.GameMetadataCache
import xendroid.compose.data.IconCache
import xendroid.compose.data.PreferencesStore
import xendroid.compose.data.TitleRegistry
import xendroid.compose.settings.ConfigStore
import xendroid.compose.settings.GameSettingsRepository
import xendroid.compose.settings.GameSettingsViewModel
import xendroid.compose.settings.SettingsRepository
import xendroid.compose.settings.SettingsViewModel
import xendroid.compose.ui.library.GameLibraryViewModel
import xendroid.compose.data.KeymapStore
import xendroid.compose.ui.keymap.KeymapViewModel
import xendroid.compose.ui.compress.GameCompressViewModel
import xendroid.compose.ui.content.ContentManagerViewModel
import xendroid.compose.ui.content.InstallContentViewModel
import xendroid.compose.ui.profile.ProfileManagerViewModel
import xendroid.compose.ui.saves.SaveManagerViewModel
import xendroid.compose.patches.AssetPatchAssets
import xendroid.compose.patches.GamePatchesViewModel
import xendroid.compose.patches.PatchPaths
import xendroid.compose.patches.PatchStore

/** Manual DI (no Hilt). One instance per process, created lazily in MainActivity
 *  from applicationContext (so it survives config changes / outlives any Activity). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val prefs = PreferencesStore(appContext)
    private val keymapStore = KeymapStore(appContext)
    private val metadataSource = GameMetadataSource()
    val iconCache = IconCache(appContext.cacheDir)
    // Per-game extraction-result cache, stored alongside game_icons/ in cacheDir so an
    // OS cache-clear wipes the metadata cache AND the icon files together (stay consistent).
    private val metadataCache = GameMetadataCache(appContext.cacheDir)
    // L05: covers by Title ID live in filesDir, so a cache clear or a moved file keeps them.
    private val covers = CoverStore(java.io.File(appContext.filesDir, "covers"))
    // L06: titles the library has seen, so a game whose file is gone is reported, not forgotten.
    private val titles = TitleRegistry(java.io.File(appContext.filesDir, "library"))
    val repository =
        GameLibraryRepository(appContext, prefs, metadataSource, iconCache, metadataCache, covers, titles)

    // ConfigStore is a stateless factory and is safe to share; the SettingsRepository
    // (which owns a single-use ConfigHandle) is built FRESH per ViewModel so one
    // settings VM closing its handle can never pull it out from under another.
    private val configStore = ConfigStore(appContext)

    fun libraryViewModelFactory(): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == GameLibraryViewModel::class.java) {
                    "Unknown ViewModel ${modelClass.name}"
                }
                return GameLibraryViewModel(repository, iconCache, covers, appContext) as T
            }
        }

    // C05: recommended settings per game. The app's own list ships as an asset; a vendor's or the
    // player's file goes in settings-profiles/ of the user data; what was applied is kept privately.
    private val settingsProfiles by lazy {
        xendroid.compose.compatibility.SettingsProfileStore(
            bundled = {
                runCatching {
                    appContext.assets.open(xendroid.compose.compatibility.SettingsProfileStore.ASSET)
                        .use { it.readBytes().toString(Charsets.UTF_8) }
                }.getOrNull()
            },
            localDir = java.io.File(Utils.get_storage_root_path(), xendroid.compose.compatibility.SettingsProfileStore.LOCAL_FOLDER),
            recordsDir = java.io.File(Application.get_internal_data_dir(), "settings-profiles-applied"),
            experimental = {
                runCatching {
                    appContext.assets.open(xendroid.compose.compatibility.SettingsProfileStore.EXPERIMENTAL_ASSET)
                        .use { it.readBytes().toString(Charsets.UTF_8) }
                }.getOrNull()
            },
        )
    }

    /** The phone as profile requirements see it: GPU and driver of the game's last run. */
    private fun deviceFacts(titleId: String): xendroid.compose.compatibility.DeviceFacts {
        val driver = runCatching { xendroid.compose.sessions.SessionRuns.store().lastRun(titleId)?.driver }.getOrNull()
        return xendroid.compose.compatibility.DeviceFacts(
            gpu = driver?.gpu?.ifBlank { null } ?: xendroid.compose.core.EmulatorRuntime.gpuDeviceName,
            driverLabel = driver?.label,
            driverLoader = driver?.loader?.ifBlank { null },
            manufacturer = android.os.Build.MANUFACTURER.orEmpty(),
            model = android.os.Build.MODEL.orEmpty(),
            androidSdk = android.os.Build.VERSION.SDK_INT,
            appVersionCode = BuildConfig.VERSION_CODE,
            soc = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.os.Build.SOC_MODEL.takeIf { it.isNotBlank() && it != android.os.Build.UNKNOWN }
            } else null,
        )
    }

    // 15b: community configs, only in a build that names a server (-PxendroidCommunityUrl, https).
    private val communityService: xendroid.compose.community.CommunityService? by lazy {
        val url = xendroid.compose.community.CommunityConfigs.baseUrl(BuildConfig.COMMUNITY_URL) ?: return@lazy null
        xendroid.compose.community.CommunityService(
            client = xendroid.compose.community.CommunityClient(url),
            store = xendroid.compose.community.CommunityStore(java.io.File(Application.get_internal_data_dir(), "community")),
            server = java.net.URI(url).host,
            appVersionCode = BuildConfig.VERSION_CODE,
            appBuild = BuildConfig.VERSION_NAME,
        )
    }

    fun settingsViewModelFactory(): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == SettingsViewModel::class.java) { "Unknown ViewModel ${modelClass.name}" }
                return SettingsViewModel(SettingsRepository(configStore), xendroid.compose.settings.GameOverridesIndex(configStore)) as T
            }
        }

    /** Per-game settings VM for one title id. Fresh repo per VM (it owns single-use
     *  ConfigHandles), shared stateless configStore — same rule as settingsViewModelFactory. */
    fun gameSettingsViewModelFactory(titleId: String): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == GameSettingsViewModel::class.java) { "Unknown ViewModel ${modelClass.name}" }
                return GameSettingsViewModel(GameSettingsRepository(configStore, titleId), settingsProfiles,
                    facts = { deviceFacts(titleId) }, community = communityService) as T
            }
        }

    /** Per-game patches VM for one title id. Stateless asset reads + on-disk toggles; no native. */
    fun gamePatchesViewModelFactory(titleId: String): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == GamePatchesViewModel::class.java) { "Unknown ViewModel ${modelClass.name}" }
                return GamePatchesViewModel(
                    titleId,
                    PatchStore(AssetPatchAssets(appContext), PatchPaths.patchesDir()),
                    appContext,
                ) as T
            }
        }

    /** Per-game ISO->.zar compressor. The launch path is passed into compress() at call
     *  time so the VM is reusable across games. */
    fun gameCompressViewModelFactory(): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == GameCompressViewModel::class.java) {
                    "Unknown ViewModel ${modelClass.name}"
                }
                return GameCompressViewModel(appContext) as T
            }
        }

    /** Per-game content/DLC manager (install + list + delete) for one title id. */
    /** One game's content ([titleId]), or every game's (null: the Content area). */
    fun gameContentManagerViewModelFactory(titleId: String?): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == ContentManagerViewModel::class.java) {
                    "Unknown ViewModel ${modelClass.name}"
                }
                return ContentManagerViewModel(appContext, metadataSource, titleId) as T
            }
        }

    /** Global install-only content entrypoint (no title id, any content type). */
    fun installContentViewModelFactory(): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == InstallContentViewModel::class.java) {
                    "Unknown ViewModel ${modelClass.name}"
                }
                return InstallContentViewModel(appContext, metadataSource, prefs) as T
            }
        }

    fun profileManagerViewModelFactory(): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == ProfileManagerViewModel::class.java) {
                    "Unknown ViewModel ${modelClass.name}"
                }
                return ProfileManagerViewModel(appContext, configStore) as T
            }
        }

    fun saveManagerViewModelFactory(titleId: String): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == SaveManagerViewModel::class.java)
                return SaveManagerViewModel(appContext, titleId) as T
            }
        }

    fun keymapViewModelFactory(): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == KeymapViewModel::class.java) {
                    "Unknown ViewModel ${modelClass.name}"
                }
                return KeymapViewModel(keymapStore) as T
            }
        }
}
