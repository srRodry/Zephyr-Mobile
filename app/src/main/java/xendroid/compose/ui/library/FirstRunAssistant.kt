package xendroid.compose.ui.library

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xendroid.compose.R
import xendroid.compose.core.AllFilesAccess
import xendroid.compose.core.EmulatorRuntime
import xendroid.compose.core.Gamertag
import xendroid.compose.settings.GuestLocaleConfig
import xendroid.compose.settings.SettingLevelStore
import xendroid.compose.settings.UiMode
import xendroid.compose.settings.UiModeStore
import xendroid.compose.ui.design.NoteTone
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdButtonSize
import xendroid.compose.ui.design.XdCard
import xendroid.compose.ui.design.XdDialogEdgeToEdge
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdLogo
import xendroid.compose.ui.design.XdNote
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.XdTextInput
import xendroid.compose.ui.design.focusRing
import xendroid.compose.ui.profile.ProfileAvatar
import xendroid.compose.ui.settings.InputModeOption
import xendroid.compose.ui.settings.SettingLevelOption

/** The assistant's steps, in order. */
enum class FirstRunStep { PHONE, GAMES, LOCALE, PROFILE, USE }

/**
 * L01, lote 6: the first-run assistant in five steps, with Skip and Back always at hand: this
 * phone (its checks), your games (the folder, and what the scan finds there, with covers), the
 * games' language and region, the profile (created right here), and how to use the app
 * (controller mode and how many settings to show). Works offline; every choice can be changed
 * later. [onCreateProfile] creates a profile and makes it P1; null leaves only "Open Profiles".
 * [step] is the caller's: choosing a folder leaves the assistant for the browser and comes back
 * to the same step.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FirstRunAssistant(
    folderReady: Boolean,
    onChooseFolder: () -> Unit,
    onOpenProfiles: () -> Unit,
    onClose: () -> Unit,
    folders: List<String> = emptyList(),
    gamesFound: Int? = null,
    scanning: Boolean = false,
    covers: List<Any> = emptyList(),
    activeProfile: String? = null,
    onCreateProfile: ((String) -> Unit)? = null,
    /** Round 2: makes XenDroid/<Games>, /TU and /DLC and adds them. */
    onCreateStandard: (() -> Unit)? = null,
    step: FirstRunStep = FirstRunStep.PHONE,
    onStep: (FirstRunStep) -> Unit = {},
) {
    val context = LocalContext.current
    val steps = FirstRunStep.entries
    val finish = {
        if (!UiModeStore.isChosen(context)) UiModeStore.write(context, UiMode.PLAYER)
        onClose()
    }
    val next = { if (step == steps.last()) finish() else onStep(steps[step.ordinal + 1]) }
    val back = { if (step != steps.first()) onStep(steps[step.ordinal - 1]) }

    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
        dismissOnClickOutside = false, dismissOnBackPress = false)) {
        XdDialogEdgeToEdge()
        BackHandler(enabled = step != steps.first(), onBack = back)
        val c = Xd.colors
        // With a controller, each step starts on its main button, not on the first step of the rail.
        // Asked again when the dialog's window gets focus: Android then focuses its first item.
        val primary = remember { FocusRequester() }
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        LaunchedEffect(step, c.controller, windowFocused) {
            if (c.controller) { withFrameNanos { }; runCatching { primary.requestFocus() } }
        }
        BoxWithConstraints(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
            val wide = maxWidth > maxHeight
            val body: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = if (wide) 32.dp else 20.dp, vertical = 22.dp)) {
                    Column(Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        when (step) {
                            FirstRunStep.PHONE -> PhoneStep(folderReady)
                            FirstRunStep.GAMES -> GamesStep(folderReady, folders, gamesFound, scanning, covers, onChooseFolder, onCreateStandard)
                            FirstRunStep.LOCALE -> LocaleStep()
                            FirstRunStep.PROFILE -> ProfileStep(activeProfile, onCreateProfile, onOpenProfiles)
                            FirstRunStep.USE -> UseStep()
                        }
                    }
                }
            }
            val footer: @Composable () -> Unit = {
                Column {
                    HorizontalDivider(thickness = 1.dp, color = c.line)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        XdButton(stringResource(R.string.fr_skip), finish, kind = XdButtonKind.GHOST)
                        Box(Modifier.weight(1f))
                        if (step != steps.first()) XdButton(stringResource(R.string.common_back), back, kind = XdButtonKind.SECONDARY)
                        XdButton(stringResource(if (step == steps.last()) R.string.xd_fr_start else R.string.xd_fr_continue), next,
                            Modifier.focusRequester(primary), kind = XdButtonKind.PRIMARY)
                    }
                }
            }
            if (wide) Row(Modifier.fillMaxSize()) {
                StepRail(step, onStep, Modifier.width(250.dp).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    body(Modifier.weight(1f).fillMaxWidth())
                    footer()
                }
            } else Column(Modifier.fillMaxSize()) {
                StepStrip(step, onStep)
                body(Modifier.weight(1f).fillMaxWidth())
                footer()
            }
        }
    }
}

@Composable
private fun stepTitle(step: FirstRunStep): String = stringResource(when (step) {
    FirstRunStep.PHONE -> R.string.fr_this_phone
    FirstRunStep.GAMES -> R.string.fr_your_games
    FirstRunStep.LOCALE -> R.string.xd_fr_step_locale
    FirstRunStep.PROFILE -> R.string.fr_profile
    FirstRunStep.USE -> R.string.xd_fr_step_use
})

/** The steps beside the page (landscape): done ones ticked, the current one lit; each can be revisited. */
@Composable
private fun StepRail(current: FirstRunStep, onStep: (FirstRunStep) -> Unit, modifier: Modifier) {
    val c = Xd.colors
    Column(modifier.background(c.s1).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        XdLogo(40.dp)
        Text(stringResource(R.string.app_name), style = XdText.h2, color = c.fg, modifier = Modifier.padding(top = 12.dp))
        Text(stringResource(R.string.xd_fr_tagline), style = XdText.note, color = c.fg3, modifier = Modifier.padding(bottom = 16.dp))
        FirstRunStep.entries.forEach { s -> StepItem(s, current, onStep) }
    }
}

@Composable
private fun StepItem(s: FirstRunStep, current: FirstRunStep, onStep: (FirstRunStep) -> Unit, compact: Boolean = false) {
    val c = Xd.colors
    val done = s.ordinal < current.ordinal
    val on = s == current
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.then(if (compact) Modifier else Modifier.fillMaxWidth()).focusRing(shape).clip(shape)
            .background(if (on) c.s3 else Color.Transparent)
            .clickable(role = Role.Tab) { onStep(s) }.semantics { selected = on }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(if (done || on) c.acc else c.s3), contentAlignment = Alignment.Center) {
            if (done) Icon(XdIcons.check, null, Modifier.size(14.dp), tint = c.onAcc)
            else Text("${s.ordinal + 1}", style = XdText.monoNum, color = if (on) c.onAcc else c.fg2)
        }
        Text(stepTitle(s), style = XdText.labelSm, color = if (on) c.fg else c.fg2, maxLines = 1)
    }
}

/** The steps above the page (portrait). */
@Composable
private fun StepStrip(current: FirstRunStep, onStep: (FirstRunStep) -> Unit) {
    val c = Xd.colors
    Column(Modifier.fillMaxWidth().background(c.s1)) {
        Row(Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            XdLogo(28.dp)
            Text(stringResource(R.string.app_name), style = XdText.label, color = c.fg)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FirstRunStep.entries.forEach { s -> StepItem(s, current, onStep, compact = true) }
        }
        HorizontalDivider(thickness = 1.dp, color = c.line)
    }
}

@Composable
private fun StepHead(title: String, note: String? = null) {
    val c = Xd.colors
    Text(title, style = XdText.h1, color = c.fg)
    if (note != null) Text(note, style = XdText.body, color = c.fg2)
}

@Composable
private fun PhoneStep(folderReady: Boolean) {
    val checks = remember(folderReady) {
        FirstRun.deviceChecks(EmulatorRuntime.gpuDeviceName, Build.SUPPORTED_ABIS.toList(), Build.VERSION.SDK_INT) +
            FirstRun.folderCheck(folderReady, AllFilesAccess.isSupported)
    }
    StepHead(stringResource(R.string.fr_welcome), stringResource(R.string.fr_intro))
    XdCard(Modifier.fillMaxWidth()) { checks.forEach { CheckLine(it) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GamesStep(folderReady: Boolean, folders: List<String>, gamesFound: Int?, scanning: Boolean, covers: List<Any>, onChooseFolder: () -> Unit,
                      onCreateStandard: (() -> Unit)?) {
    val c = Xd.colors
    StepHead(stringResource(R.string.fr_your_games))
    if (!AllFilesAccess.isSupported) {
        XdNote(stringResource(R.string.fr_check_folder_unsupported), tone = NoteTone.WARN)
        return
    }
    Text(stringResource(R.string.xd_fr_games_note), style = XdText.body, color = c.fg2)
    if (!folderReady && folders.isEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            XdButton(stringResource(R.string.fr_choose_folder), onChooseFolder, kind = XdButtonKind.PRIMARY, size = XdButtonSize.LG, icon = XdIcons.folder)
            // Round 2: no folder yet? The app's own, ready to fill.
            if (onCreateStandard != null) XdButton(stringResource(R.string.xd_fd_std), onCreateStandard, kind = XdButtonKind.SECONDARY,
                size = XdButtonSize.LG, icon = XdIcons.plus)
        }
        if (onCreateStandard != null) Text(stringResource(R.string.xd_fd_std_note, stringResource(R.string.xd_fd_std_games)),
            style = XdText.note, color = c.fg3)
        return
    }
    XdCard(Modifier.fillMaxWidth()) {
        folders.forEach { folder ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(XdIcons.folder, null, Modifier.size(20.dp), tint = c.fg3)
                Text(displayPath(folder), style = XdText.label, color = c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                when {
                    scanning -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.acc, trackColor = c.s3)
                        Text(stringResource(R.string.xd_fr_scanning), style = XdText.note, color = c.fg3)
                    }
                    gamesFound != null -> XdNote(pluralStringResource(R.plurals.xd_fr_found, gamesFound, gamesFound),
                        tone = if (gamesFound > 0) NoteTone.OK else NoteTone.WARN)
                }
            }
            XdButton(stringResource(R.string.xd_fr_add_folder), onChooseFolder, kind = XdButtonKind.GHOST, size = XdButtonSize.SM, icon = XdIcons.plus)
        }
        if (covers.isNotEmpty() && !scanning) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            covers.take(10).forEach { art ->
                AsyncImage(art, null, Modifier.width(54.dp).aspectRatio(0.75f).clip(RoundedCornerShape(7.dp)), contentScale = ContentScale.Crop)
            }
        }
    }
}

@Composable
private fun LocaleStep() {
    val context = LocalContext.current
    val c = Xd.colors
    val scope = rememberCoroutineScope()
    val locale = remember { Locale.getDefault().let { FirstRun.guestLocale(it.language, it.country) } }
    var status by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    StepHead(stringResource(R.string.fr_locale))
    XdCard(Modifier.fillMaxWidth()) {
        if (!locale.any) {
            Text(stringResource(R.string.fr_no_locale), style = XdText.body, color = c.fg2)
        } else {
            // The console's lists keep ISO codes; the phone names them in its own language.
            val shown = Locale.getDefault()
            val language = locale.languageLabel?.let { code ->
                Locale.forLanguageTag(code).getDisplayLanguage(shown).replaceFirstChar { it.titlecase(shown) }.ifBlank { code }
            }
            val region = locale.countryLabel?.let { code -> Locale("", code).getDisplayCountry(shown).ifBlank { code } }
            Text(stringResource(R.string.fr_from_phone, listOfNotNull(language?.let { stringResource(R.string.fr_language, it) },
                region?.let { stringResource(R.string.fr_region, it) }).joinToString(" · ")), style = XdText.body, color = c.fg)
            if (saved) XdNote(stringResource(R.string.fr_locale_saved), tone = NoteTone.OK)
            else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                XdButton(stringResource(R.string.fr_use_locale), {
                    status = context.getString(R.string.fr_saving)
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                GuestLocaleConfig.save(context.applicationContext, locale.languageValue, locale.countryValue)
                            }
                        }
                        saved = result.isSuccess
                        status = result.exceptionOrNull()?.let { context.getString(R.string.fr_locale_failed, it.message ?: it.javaClass.simpleName) }
                    }
                }, kind = XdButtonKind.PRIMARY)
                status?.let { Text(it, style = XdText.note, color = c.fg3, modifier = Modifier.weight(1f)) }
            }
        }
    }
    XdNote(stringResource(R.string.xd_fr_locale_later))
}

@Composable
private fun ProfileStep(activeProfile: String?, onCreateProfile: ((String) -> Unit)?, onOpenProfiles: () -> Unit) {
    val c = Xd.colors
    var tag by rememberSaveable { mutableStateOf("") }
    var made by rememberSaveable { mutableStateOf<String?>(null) }
    var another by rememberSaveable { mutableStateOf(false) }
    StepHead(stringResource(R.string.fr_profile), stringResource(R.string.fr_profile_note))
    XdCard(Modifier.fillMaxWidth()) {
        val created = made
        when {
            created != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ProfileAvatar("", created, false, 56.dp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(created, style = XdText.h2, color = c.fg)
                    XdNote(stringResource(R.string.xd_fr_profile_made), tone = NoteTone.OK)
                }
            }
            onCreateProfile != null && (activeProfile == null || another) -> {
                Text(stringResource(R.string.pf_gamertag), style = XdText.labelSm, color = c.fg2)
                XdTextInput(tag, { tag = it.take(15) }, placeholder = "XenPlayer", mono = false, width = 320.dp)
                val valid = Gamertag.isValid(tag)
                if (tag.isNotEmpty() && !valid) XdNote(stringResource(R.string.pf_gamertag_rule), tone = NoteTone.ERROR, icon = XdIcons.warn)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    XdButton(stringResource(R.string.pf_create), { onCreateProfile(tag); made = tag }, kind = XdButtonKind.PRIMARY, enabled = valid)
                    XdButton(stringResource(R.string.fr_open_profiles), onOpenProfiles, kind = XdButtonKind.GHOST)
                }
            }
            else -> {
                if (activeProfile != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ProfileAvatar("", activeProfile, false, 48.dp)
                    Text(stringResource(R.string.xd_fr_profile_active, activeProfile), style = XdText.label, color = c.fg)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (onCreateProfile != null) XdButton(stringResource(R.string.xd_fr_another), { another = true }, kind = XdButtonKind.SECONDARY)
                    XdButton(stringResource(R.string.fr_open_profiles), onOpenProfiles, kind = XdButtonKind.GHOST)
                }
            }
        }
    }
}

@Composable
private fun UseStep() {
    val context = LocalContext.current
    val c = Xd.colors
    var level by remember { mutableStateOf(SettingLevelStore.read(context)) }
    StepHead(stringResource(R.string.xd_fr_step_use))
    XdCard(Modifier.fillMaxWidth()) {
        InputModeOption()
        SettingLevelOption(level) { level = it }
    }
    XdCard(Modifier.fillMaxWidth(), title = stringResource(R.string.fr_driver), icon = XdIcons.chip) {
        Text(stringResource(R.string.xd_fr_driver_note), style = XdText.bodySm, color = c.fg2)
    }
}

/** U02: the check as shown; the model keeps the English text for tests and logs. */
@Composable
private fun checkTitle(check: FirstRun.Check): String = when (check.kind) {
    FirstRun.Kind.GPU_OK, FirstRun.Kind.GPU_NONE -> stringResource(R.string.fr_check_gpu)
    FirstRun.Kind.ARM64_OK, FirstRun.Kind.ARM64_NONE -> stringResource(R.string.fr_check_arm64)
    FirstRun.Kind.ANDROID_OK, FirstRun.Kind.ANDROID_OLD -> "Android"
    FirstRun.Kind.FOLDER_SET, FirstRun.Kind.FOLDER_UNSUPPORTED, FirstRun.Kind.FOLDER_UNSET -> stringResource(R.string.fr_check_folder)
    null -> check.title
}

@Composable
private fun checkDetail(check: FirstRun.Check): String = when (check.kind) {
    FirstRun.Kind.GPU_OK, FirstRun.Kind.ARM64_OK -> check.arg ?: check.detail
    FirstRun.Kind.GPU_NONE -> stringResource(R.string.fr_check_gpu_none)
    FirstRun.Kind.ARM64_NONE -> stringResource(R.string.fr_check_arm64_none)
    FirstRun.Kind.ANDROID_OK -> stringResource(R.string.fr_check_android, check.arg.orEmpty())
    FirstRun.Kind.ANDROID_OLD -> stringResource(R.string.fr_check_android_old, check.arg.orEmpty())
    FirstRun.Kind.FOLDER_SET -> stringResource(R.string.fr_check_folder_set)
    FirstRun.Kind.FOLDER_UNSUPPORTED -> stringResource(R.string.fr_check_folder_unsupported)
    FirstRun.Kind.FOLDER_UNSET -> stringResource(R.string.fr_check_folder_unset)
    null -> check.detail
}

/** One check: a green tick, a yellow mark or a red cross, what was checked and what was found. */
@Composable
fun CheckLine(check: FirstRun.Check) {
    val c = Xd.colors
    val (icon, tint) = when (check.status) {
        FirstRun.Status.OK -> XdIcons.checkCircle to c.ok
        FirstRun.Status.WARNING -> XdIcons.alert to c.warn
        FirstRun.Status.BLOCKED -> XdIcons.xCircle to c.errText
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 1.dp).size(22.dp), tint = tint)
        Column(Modifier.weight(1f)) {
            Text(checkTitle(check), style = XdText.label, color = c.fg)
            Text(checkDetail(check), style = XdText.small, color = c.fg3)
        }
    }
}
