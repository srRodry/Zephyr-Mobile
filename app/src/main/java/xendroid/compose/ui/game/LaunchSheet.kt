package xendroid.compose.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xendroid.compose.R
import xendroid.compose.core.launchExecutables
import xendroid.compose.core.LaunchOptions
import java.io.File
import xendroid.compose.data.Game
import xendroid.compose.data.PlayableProfile
import xendroid.compose.driver.InstalledDriverPackage
import xendroid.compose.driver.InstalledDrivers
import xendroid.compose.settings.GameSettingsViewModel
import xendroid.compose.settings.SettingsSchema
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdNote
import xendroid.compose.ui.design.XdSegmented
import xendroid.compose.ui.design.XdSelect
import xendroid.compose.ui.design.XdSheet
import xendroid.compose.ui.design.XdSheetOption
import xendroid.compose.ui.design.XdSwitch
import xendroid.compose.ui.design.XdTextInput
import xendroid.compose.ui.library.GameLibraryViewModel
import xendroid.compose.ui.settings.driverName

/** What "Start with…" holds while it is open: for this launch only, nothing is saved. */
@Stable
class LaunchFormState {
    /** Who plays as P1 (XUID); null = as configured. */
    var profile by mutableStateOf<String?>(null)
    /** The driver library ("" = the system driver); null = as configured. */
    var driver by mutableStateOf<String?>(null)
    var module by mutableStateOf("")
    var noPatches by mutableStateOf(false)
    var ignoreGameSettings by mutableStateOf(false)
    var commandLine by mutableStateOf("")

    /** The options, with the global values that replace this game's own [overrides]. */
    fun options(overrides: Map<String, String>, inheritedRaw: (String) -> String?): LaunchOptions = LaunchOptions(
        profileXuid = profile,
        driverPath = driver,
        launchModule = module.trim().ifEmpty { null },
        noPatches = noPatches,
        ignoreGameSettings = if (!ignoreGameSettings) emptyMap() else overrides.keys.mapNotNull { key ->
            inheritedRaw(key)?.let { key to it }
        }.toMap(),
        extraCommandLine = commandLine.trim().ifEmpty { null },
    )
}

/** Profiles and installed drivers the form offers, read once off the main thread. */
private class LaunchChoices(val profiles: List<PlayableProfile>, val p1: String?, val drivers: List<InstalledDriverPackage>)

/**
 * The options of "Start with…": profile, driver, executable, without patches, without this
 * game's settings, extra command line. Shared by the sheet (touch) and the section (controller).
 */
@Composable
fun LaunchForm(
    game: Game,
    state: LaunchFormState,
    library: GameLibraryViewModel,
    settings: GameSettingsViewModel?,
    overrides: Map<String, String>,
    patchesOn: Int?,
    modifier: Modifier = Modifier,
) {
    val c = Xd.colors
    var choices by remember { mutableStateOf<LaunchChoices?>(null) }
    LaunchedEffect(Unit) {
        val (profiles, p1) = library.launchProfiles()
        val drivers = withContext(Dispatchers.IO) {
            runCatching { InstalledDrivers.list(xendroid.compose.Application.get_custom_driver_dir()) }.getOrDefault(emptyList())
        }
        choices = LaunchChoices(profiles, p1, drivers)
    }
    var modules by remember(game.launchUri) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(game.launchUri) {
        modules = withContext(Dispatchers.IO) {
            launchExecutables(game.launchUri)
        }
    }
    val loaded = choices
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val profiles = loaded?.profiles.orEmpty()
        if (profiles.size > 1) {
            // Who plays as P1 now is chosen until another is picked (picking it again changes nothing).
            val p1 = profiles.firstOrNull { it.xuid.equals(loaded?.p1, ignoreCase = true) }?.xuid
            val options = profiles.map { it.xuid to it.gamertag.ifBlank { it.xuid } }
            XdSheetOption(stringResource(R.string.xd_launch_profile), subtitle = stringResource(R.string.xd_launch_profile_sub)) {
                if (options.size <= 3) XdSegmented(options, state.profile ?: p1, { state.profile = it })
                else XdSelect(options, state.profile ?: p1, { state.profile = it })
            }
        }
        if (settings?.isCustomDriverSupported == true) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val configured = SettingsSchema.byKey["Vulkan|vulkan_lib_path"]?.let { s -> overrides[s.key] ?: settings.inheritedRaw(s) }.orEmpty()
            val drivers = loaded?.drivers.orEmpty()
            val options = listOf<String?>(null, "") + drivers.map { it.library.absolutePath }
            XdSheetOption(stringResource(R.string.xd_launch_driver),
                subtitle = stringResource(R.string.xd_launch_driver_sub, driverName(context, configured))) {
                XdSelect(options.map { p ->
                    p to when (p) {
                        null -> stringResource(R.string.xd_launch_driver_configured)
                        "" -> stringResource(R.string.drv_system)
                        else -> drivers.first { it.library.absolutePath == p }.let { d -> listOfNotNull(d.name, d.version).joinToString(" ") }
                    }
                }, state.driver, { state.driver = it }, maxWidth = 220.dp)
            }
        }
        XdSheetOption(stringResource(R.string.xd_launch_module), subtitle = stringResource(R.string.xd_launch_module_sub)) {
            val found = modules
            if (!found.isNullOrEmpty()) {
                XdSelect(listOf("" to stringResource(R.string.xd_launch_module_configured)) + found.map { it to it },
                    state.module, { state.module = it }, maxWidth = 220.dp)
            } else {
                XdTextInput(state.module, { state.module = it.take(260) }, placeholder = "default.xex", width = 180.dp)
            }
        }
        XdSheetOption(stringResource(R.string.xd_launch_no_patches),
            subtitle = if (patchesOn != null && patchesOn > 0) pluralStringResource(R.plurals.xd_launch_no_patches_sub, patchesOn, patchesOn)
            else stringResource(R.string.xd_launch_no_patches_none)) {
            XdSwitch(state.noPatches, { state.noPatches = it }, contentDescription = stringResource(R.string.xd_launch_no_patches))
        }
        if (settings != null) XdSheetOption(stringResource(R.string.xd_launch_ignore),
            subtitle = if (overrides.isEmpty()) stringResource(R.string.xd_launch_ignore_none)
            else pluralStringResource(R.plurals.xd_launch_ignore_sub, overrides.size, overrides.size)) {
            XdSwitch(state.ignoreGameSettings, { state.ignoreGameSettings = it }, enabled = overrides.isNotEmpty(),
                contentDescription = stringResource(R.string.xd_launch_ignore))
        }
        XdSheetOption(stringResource(R.string.xd_launch_cl), subtitle = stringResource(R.string.xd_launch_cl_sub)) {
            XdTextInput(state.commandLine, { state.commandLine = it.take(512) }, placeholder = stringResource(R.string.xd_launch_cl_none), width = 180.dp)
        }
        val configured = SettingsSchema.byKey["General|launch_module"]?.let { setting ->
            (if (state.ignoreGameSettings) null else overrides[setting.key]) ?: settings?.inheritedRaw(setting)
        }.orEmpty().trim().trim('"')
        val requested = state.module.trim().ifEmpty {
            configured.ifEmpty { File(game.launchUri).takeIf { it.extension.equals("xex", true) }?.name ?: "default.xex" }
        }
        XdNote(stringResource(R.string.xd_launch_module_requested, requested), icon = XdIcons.info)
        XdNote(stringResource(R.string.xd_launch_note), icon = XdIcons.info)
    }
}

/** "Start with…" as a sheet (touch): the form, Cancel and Play. */
@Composable
fun LaunchSheet(
    game: Game,
    state: LaunchFormState,
    library: GameLibraryViewModel,
    settings: GameSettingsViewModel?,
    overrides: Map<String, String>,
    patchesOn: Int?,
    onPlay: (LaunchOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    XdSheet(onDismiss = onDismiss, title = stringResource(R.string.xd_game_sec_launch),
        subtitle = stringResource(R.string.xd_launch_sub, game.name),
        actions = {
            XdButton(stringResource(R.string.xd_cancel), onDismiss, kind = XdButtonKind.GHOST)
            XdButton(stringResource(R.string.lib_play), {
                onPlay(state.options(overrides) { key -> SettingsSchema.byKey[key]?.let { settings?.inheritedRaw(it) } })
            }, kind = XdButtonKind.PRIMARY, icon = XdIcons.play)
        }) {
        LaunchForm(game, state, library, settings, overrides, patchesOn)
    }
}
