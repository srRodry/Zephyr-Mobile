package xendroid.compose.core

import android.content.Context
import android.util.Log
import xendroid.compose.settings.ConfigStore
import xendroid.compose.settings.Setting
import xendroid.compose.settings.SettingsSchema

object ProfileBootstrap {
    private const val DEFAULT_GAMERTAG = "Zephyr"

    @Volatile private var ensured = false

    fun ensureDefaultProfile(appContext: Context) {
        if (ensured) return
        val emu = EmulatorRuntime.emulator ?: return
        val root = ContentPaths.contentRoot().absolutePath
        if (emu.list_profiles(root)?.isNotEmpty() == true) { ensured = true; return }
        // Same storage lease as every other profile/save mutation: a running game or a
        // backup/restore owns the content tree. Retry on a later library visit.
        val xuid = runCatching {
            StorageAccess.acquire().use {
                if (emu.list_profiles(root)?.isNotEmpty() == true) return@use null
                emu.create_profile(root, DEFAULT_GAMERTAG, defaultLanguage(), defaultCountry())
            }
        }.onFailure { Log.w("ProfileBootstrap", "Default profile not created now", it) }
            .getOrNull() ?: run {
                ensured = emu.list_profiles(root)?.isNotEmpty() == true
                return
            }
        ConfigStore(appContext).editLiveConfig { h ->
            h.putString("Profiles", "logged_profile_slot_0_xuid", xuid.uppercase())
        }
        ensured = true
    }

    // A cvar's toml section can move, which changes its schema key; fall back
    // rather than hard-cast.
    private fun defaultLanguage() =
        (SettingsSchema.byKey["Console|user_language"] as? Setting.ListChoice)
            ?.default?.toIntOrNull() ?: 1   // en

    private fun defaultCountry() =
        (SettingsSchema.byKey["Console|user_country"] as? Setting.ListChoice)
            ?.default?.toIntOrNull() ?: 103 // United States
}
