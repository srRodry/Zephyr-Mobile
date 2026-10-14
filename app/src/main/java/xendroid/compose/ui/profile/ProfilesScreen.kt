package xendroid.compose.ui.profile

import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import xendroid.compose.R
import xendroid.compose.core.Gamertag
import xendroid.compose.core.ProfilePaths
import xendroid.compose.data.ProfilePick
import xendroid.compose.data.ProfileSlots
import xendroid.compose.saves.ProfileContentSummary
import xendroid.compose.saves.TrashedProfile
import xendroid.compose.settings.Setting
import xendroid.compose.settings.SettingsSchema
import xendroid.compose.ui.design.BadgeTone
import xendroid.compose.ui.design.LocalXdToast
import xendroid.compose.ui.design.NoteTone
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdArea
import xendroid.compose.ui.design.XdBadge
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdButtonSize
import xendroid.compose.ui.design.XdCard
import xendroid.compose.ui.design.XdEditorialHeading
import xendroid.compose.ui.design.XdSilverPanel
import androidx.compose.ui.text.font.FontWeight
import xendroid.compose.ui.design.XdEmpty
import xendroid.compose.ui.design.XdIconButton
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdKv
import xendroid.compose.ui.design.XdListRow
import xendroid.compose.ui.design.XdMenuItem
import xendroid.compose.ui.design.XdNote
import xendroid.compose.ui.design.XdSection
import xendroid.compose.ui.design.XdSectionedScreen
import xendroid.compose.ui.design.XdSelect
import xendroid.compose.ui.design.XdSheet
import xendroid.compose.ui.design.XdSheetOption
import xendroid.compose.ui.design.XdSwitch
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.XdTextInput
import xendroid.compose.ui.design.focusRing
import xendroid.compose.ui.profile.ProfileManagerViewModel.ListState
import xendroid.compose.ui.profile.ProfileManagerViewModel.OpState
import xendroid.compose.ui.profile.ProfileManagerViewModel.ProfileEntry
import xendroid.compose.ui.settings.optionText

// Top-level vals, so a hard cast on a key whose toml section moved would throw
// during class init, before anything can catch it.
private fun listChoice(key: String) = SettingsSchema.byKey[key] as? Setting.ListChoice

private val LANGUAGE = listChoice("Console|user_language")
private val COUNTRY = listChoice("Console|user_country")
private val DEFAULT_LANGUAGE = LANGUAGE?.default?.toIntOrNull() ?: 1     // en
private val DEFAULT_COUNTRY = COUNTRY?.default?.toIntOrNull() ?: 103     // United States

/** Where the Profiles area leads: a game's saves, and the names and covers of the games. */
class ProfilesLinks(
    val onSaves: (titleId: String) -> Unit = {},
    val gameName: (String) -> String? = { null },
    val gameArt: (String) -> Any? = { null },
)

object ProfilesSections {
    const val LIST = "list"
    const val PLAYERS = "players"
    const val TRASH = "trash"
}

private sealed interface Editing {
    data object None : Editing
    data object Create : Editing
    data class Rename(val entry: ProfileEntry) : Editing
}

/**
 * The Profiles area (lote 4): profiles as cards (avatar, gamertag, language and region, what
 * their saves take and in which games), who signs in as P1–P4, and the trash. With a controller
 * the profiles are big avatars side by side; A opens a profile's options.
 */
@Composable
fun ProfilesScreen(
    vm: ProfileManagerViewModel,
    onBack: () -> Unit,
    links: ProfilesLinks = ProfilesLinks(),
    initialSection: String? = null,
) {
    val context = LocalContext.current
    val toast = LocalXdToast.current
    val listState by vm.listState.collectAsStateWithLifecycle()
    val opState by vm.opState.collectAsStateWithLifecycle()
    val trash by vm.trash.collectAsStateWithLifecycle()
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Editing>(Editing.None) }
    var purgeTarget by remember { mutableStateOf<TrashedProfile?>(null) }
    var menuOf by remember { mutableStateOf<ProfileEntry?>(null) }
    var savesOf by remember { mutableStateOf<ProfileEntry?>(null) }
    var slotPicking by remember { mutableStateOf<Int?>(null) }
    var section by rememberSaveable { mutableStateOf(initialSection ?: ProfilesSections.LIST) }

    val loaded = listState as? ListState.Loaded
    val profiles = loaded?.profiles.orEmpty()
    val slots = loaded?.slots ?: List(ProfileSlots.COUNT) { null }
    val active = profiles.firstOrNull { it.isActive }

    val listBody: @Composable () -> Unit = {
        when (val s = listState) {
            ListState.Loading -> XdEmpty(stringResource(R.string.xd_game_loading))
            is ListState.Error -> {
                XdNote(s.message, tone = NoteTone.ERROR)
                XdButton(stringResource(R.string.common_retry), { vm.refresh() }, kind = XdButtonKind.PRIMARY)
            }
            is ListState.Loaded -> if (Xd.colors.controller) ControllerProfiles(s.profiles, s.slots, onPick = { menuOf = it }, onCreate = { editing = Editing.Create })
                else TouchProfiles(s.profiles, s.slots, summaries, onActive = { vm.setActive(it.xuid) }, onEdit = { editing = Editing.Rename(it) },
                    onSaves = { savesOf = it }, onTrash = { vm.requestDelete(it) }, onCreate = { editing = Editing.Create })
        }
    }
    val sections = listOf(
        XdSection(ProfilesSections.LIST, stringResource(R.string.xd_pf_sec_list), XdIcons.user, badge = profiles.size.takeIf { it > 0 }?.toString()) {
            listBody()
        },
        XdSection(ProfilesSections.PLAYERS, stringResource(R.string.xd_pf_sec_players), XdIcons.gamepad,
            lead = stringResource(R.string.xd_pf_players_lead)) {
            Players(profiles, slots, onPick = { slotPicking = it }, onNobody = { vm.setPlayer(it, null) })
        },
        XdSection(ProfilesSections.TRASH, stringResource(R.string.xd_pf_sec_trash), XdIcons.trash, badge = trash.size.takeIf { it > 0 }?.toString(),
            heading = stringResource(R.string.xd_pf_trash_heading)) {
            Trash(trash, onRestore = { vm.restore(it.id) }, onPurge = { purgeTarget = it })
        },
    )
    val count = pluralStringResource(R.plurals.xd_pf_count, profiles.size, profiles.size)
    XdSectionedScreen(
        title = stringResource(R.string.lib_menu_profiles), sections = sections, selected = section, onSelect = { section = it },
        area = XdArea.PROFILES, onBack = onBack, headIcon = XdIcons.user,
        subtitle = count + " · " + (active?.let { stringResource(R.string.xd_pf_active_is, it.gamertag.ifBlank { it.xuid }) }
            ?: stringResource(R.string.xd_pf_no_active)),
        actions = { XdButton(stringResource(R.string.pf_create), { editing = Editing.Create }, size = XdButtonSize.SM, icon = XdIcons.plus) },
    )

    when (val e = editing) {
        Editing.None -> {}
        Editing.Create -> ProfileForm(null, onDismiss = { editing = Editing.None }) { tag, lang, country, avatar ->
            editing = Editing.None
            vm.create(tag, lang, country, avatar)
        }
        is Editing.Rename -> ProfileForm(e.entry, onDismiss = { editing = Editing.None }) { tag, lang, country, avatar ->
            editing = Editing.None
            vm.rename(e.entry.xuid, tag, lang, country, avatar)
        }
    }

    menuOf?.let { p ->
        val games = summaries[p.xuid]?.gameTitles.orEmpty()
        XdSheet(onDismiss = { menuOf = null }, title = p.gamertag.ifBlank { p.xuid }, subtitle = statusText(p, slots) ?: p.xuid) {
            if (!p.isActive) XdMenuItem(stringResource(R.string.xd_pf_make_active), { menuOf = null; vm.setActive(p.xuid) }, icon = XdIcons.user,
                subtitle = stringResource(R.string.xd_pf_make_active_sub))
            XdMenuItem(stringResource(R.string.pf_edit), { menuOf = null; editing = Editing.Rename(p) }, icon = XdIcons.text,
                subtitle = stringResource(R.string.xd_pf_edit_sub))
            XdMenuItem(stringResource(R.string.xd_pf_saves), { menuOf = null; savesOf = p }, icon = XdIcons.save, enabled = games.isNotEmpty(),
                subtitle = savesText(summaries[p.xuid]))
            XdMenuItem(stringResource(R.string.pf_trash), { menuOf = null; vm.requestDelete(p) }, icon = XdIcons.trash)
        }
    }

    savesOf?.let { p ->
        val games = summaries[p.xuid]?.gameTitles.orEmpty()
        XdSheet(onDismiss = { savesOf = null }, title = stringResource(R.string.xd_pf_saves_of, p.gamertag.ifBlank { p.xuid }),
            subtitle = pluralStringResource(R.plurals.xd_pf_games_here, games.size, games.size)) {
            Column {
                games.forEachIndexed { i, t ->
                    XdListRow(links.gameName(t.titleId) ?: t.titleId,
                        subtitle = Formatter.formatShortFileSize(context, t.bytes),
                        lead = links.gameArt(t.titleId)?.let { art -> { AsyncImage(art, null, Modifier.width(34.dp).aspectRatio(0.75f).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop) } },
                        icon = XdIcons.save, divider = i < games.lastIndex) {
                        XdButton(stringResource(R.string.xd_open), { savesOf = null; links.onSaves(t.titleId) }, size = XdButtonSize.SM)
                    }
                }
            }
        }
    }

    slotPicking?.let { n ->
        val current = slots.getOrNull(n)
        XdSheet(onDismiss = { slotPicking = null },
            title = if (n == 0) stringResource(R.string.xd_pf_slot_p1_title) else stringResource(R.string.pf_player_signs_in_as, n + 1)) {
            Column {
                if (n > 0) XdMenuItem(stringResource(R.string.pf_nobody), { slotPicking = null; vm.setPlayer(n, null) }, icon = XdIcons.x)
                profiles.filter { n == 0 || !it.isActive }.forEach { p ->
                    val other = ProfileSlots.otherPlayerOf(slots, p.xuid)?.takeIf { it != n + 1 && !p.isActive }
                    ProfileChoice(p, when {
                        p.isActive -> stringResource(R.string.pf_active_p1)
                        other != null -> stringResource(R.string.pf_moves_from, other)
                        else -> languageText(p)
                    }, selected = current.equals(p.xuid, ignoreCase = true)) {
                        slotPicking = null
                        if (n == 0) vm.setActive(p.xuid) else vm.setPlayer(n, p.xuid)
                    }
                }
            }
            if (n == 0) XdNote(stringResource(R.string.xd_pf_slot_p1_note))
        }
    }

    purgeTarget?.let { target ->
        XdSheet(onDismiss = { purgeTarget = null }, title = stringResource(R.string.pf_purge_title), actions = {
            XdButton(stringResource(R.string.common_cancel), { purgeTarget = null }, kind = XdButtonKind.GHOST)
            XdButton(stringResource(R.string.pf_purge), { purgeTarget = null; vm.purge(target.id) }, kind = XdButtonKind.DANGER)
        }) { Text(stringResource(R.string.pf_purge_text, target.xuid), style = XdText.body, color = Xd.colors.fg2) }
    }

    when (val s = opState) {
        is OpState.ConfirmDelete -> XdSheet(onDismiss = vm::dismiss, title = stringResource(R.string.pf_trash_title), actions = {
            XdButton(stringResource(R.string.common_cancel), vm::dismiss, kind = XdButtonKind.GHOST)
            XdButton(stringResource(R.string.pf_trash), { vm.delete(s.entry.xuid) }, kind = XdButtonKind.DANGER)
        }) { Text(deleteSummaryText(s.entry, s.summary, links), style = XdText.body, color = Xd.colors.fg2) }
        is OpState.Busy -> XdSheet(onDismiss = {}, title = null, dismissible = false) {
            Text(s.message, style = XdText.label, color = Xd.colors.fg)
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Xd.colors.acc, trackColor = Xd.colors.s3)
        }
        is OpState.Done -> LaunchedEffect(s) { toast.show(s.message); vm.dismiss() }
        is OpState.Failed -> XdSheet(onDismiss = vm::dismiss, title = stringResource(R.string.pf_failed),
            actions = { XdButton(stringResource(R.string.common_ok), vm::dismiss, kind = XdButtonKind.PRIMARY) }) {
            Text(s.message, style = XdText.body, color = Xd.colors.fg2)
        }
        OpState.Idle -> {}
    }
}

@Composable
private fun statusText(p: ProfileEntry, slots: List<String?>): String? = when {
    p.isActive -> stringResource(R.string.pf_active_p1)
    else -> ProfileSlots.otherPlayerOf(slots, p.xuid)?.let { stringResource(R.string.pf_signs_in_as_p, it) }
}

@Composable
private fun languageText(p: ProfileEntry): String {
    val context = LocalContext.current
    val lang = LANGUAGE?.let { s -> s.options.firstOrNull { it.value.toIntOrNull() == p.language }?.let { optionText(context, s, it.value, it.label) } }
    val region = COUNTRY?.let { s -> s.options.firstOrNull { it.value.toIntOrNull() == p.country }?.let { optionText(context, s, it.value, it.label) } }
    return listOfNotNull(lang, region).joinToString(" · ").ifEmpty { "—" }
}

@Composable
private fun savesText(summary: ProfileContentSummary?): String {
    val context = LocalContext.current
    val games = summary?.gameTitles.orEmpty()
    if (games.isEmpty()) return stringResource(R.string.xd_pf_no_saves)
    // No file count: the folder also holds each save's header, which the game's saves screen
    // does not count as a save file.
    return pluralStringResource(R.plurals.xd_pf_games, games.size, games.size) + " · " + Formatter.formatShortFileSize(context, games.sumOf { it.bytes })
}

/** A profile's picture: its avatar, or its first letter on a colour of its own. */
@Composable
fun ProfileAvatar(xuid: String, gamertag: String, hasAvatar: Boolean, size: Dp, modifier: Modifier = Modifier) {
    if (hasAvatar) {
        AsyncImage(ProfilePaths.tile64Path(xuid), null, modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
        return
    }
    val palette = listOf(Color(0xFF2E6B3A), Color(0xFF6B3F9E), Color(0xFF9A5A16), Color(0xFF1C7C86), Color(0xFF8A2F3C), Color(0xFF3F5BA8))
    val color = palette[Math.floorMod(xuid.hashCode(), palette.size)]
    Box(modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(gamertag.firstOrNull()?.uppercase() ?: "?", style = XdText.h2.copy(fontSize = (size.value * 0.42f).sp), color = Color.White)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TouchProfiles(
    profiles: List<ProfileEntry>,
    slots: List<String?>,
    summaries: Map<String, ProfileContentSummary>,
    onActive: (ProfileEntry) -> Unit,
    onEdit: (ProfileEntry) -> Unit,
    onSaves: (ProfileEntry) -> Unit,
    onTrash: (ProfileEntry) -> Unit,
    onCreate: () -> Unit,
) {
    val c = Xd.colors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        XdEditorialHeading(stringResource(R.string.zephyr_ui2_profiles_title), stringResource(R.string.zephyr_ui2_profiles_subtitle))
        val active = profiles.firstOrNull { it.isActive }
        if (active != null) ActiveProfileCard(active, slots, summaries[active.xuid],
            onEdit = { onEdit(active) }, onSaves = { onSaves(active) }, onTrash = { onTrash(active) })
        BoxWithConstraints {
            val columns = when {
                maxWidth >= 840.dp -> 3
                maxWidth >= 500.dp -> 2
                else -> 1
            }
            // The profiles, then the card that creates one (null).
            val cells: List<ProfileEntry?> = profiles.filter { it != active } + listOf(null)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                cells.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { p ->
                            if (p == null) NewProfileCard(onCreate, Modifier.weight(1f))
                            else ProfileCard(p, slots, summaries[p.xuid], { onActive(p) }, { onEdit(p) }, { onSaves(p) }, { onTrash(p) }, Modifier.weight(1f))
                        }
                        repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Text(stringResource(R.string.xd_pf_note), style = XdText.note, color = c.fg3)
    }
}

/** The real active profile, presented separately without changing sign-in or save ownership. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveProfileCard(
    p: ProfileEntry,
    slots: List<String?>,
    summary: ProfileContentSummary?,
    onEdit: () -> Unit,
    onSaves: () -> Unit,
    onTrash: () -> Unit,
) {
    XdSilverPanel {
        val c = Xd.colors
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ProfileAvatar(p.xuid, p.gamertag, p.hasAvatar, 64.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    statusText(p, slots)?.let { Text(it, style = XdText.labelSm, color = c.acc) }
                    Text(p.gamertag.ifBlank { p.xuid }, style = XdText.body.copy(fontSize = 26.sp, lineHeight = 30.sp,
                        fontWeight = FontWeight.Bold), color = c.fg)
                }
            }
            XdKv(listOf(
                stringResource(R.string.xd_pf_lang_region) to languageText(p),
                stringResource(R.string.xd_pf_saves) to savesText(summary),
            ))
            Text(p.xuid, style = XdText.mono.copy(fontSize = 11.sp), color = c.fg3)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                XdButton(stringResource(R.string.pf_edit), onEdit, Modifier.heightIn(min = 48.dp),
                    kind = XdButtonKind.PRIMARY, icon = XdIcons.text)
                XdButton(stringResource(R.string.xd_pf_saves), onSaves, Modifier.heightIn(min = 48.dp),
                    icon = XdIcons.save, enabled = summary?.gameTitles?.isNotEmpty() == true)
                XdIconButton(XdIcons.trash, stringResource(R.string.xd_pf_to_trash_cd, p.gamertag.ifBlank { p.xuid }), onTrash, size = 48.dp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileCard(
    p: ProfileEntry,
    slots: List<String?>,
    summary: ProfileContentSummary?,
    onActive: () -> Unit,
    onEdit: () -> Unit,
    onSaves: () -> Unit,
    onTrash: () -> Unit,
    modifier: Modifier,
) {
    val c = Xd.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape).background(if (p.isActive) c.acc.copy(alpha = 0.07f).compositeOver(c.solid(c.s1)) else c.s1)
            .then(if (p.isActive) Modifier.border(1.dp, c.acc.copy(alpha = 0.35f), shape) else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProfileAvatar(p.xuid, p.gamertag, p.hasAvatar, 52.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(p.gamertag.ifBlank { p.xuid }, style = XdText.label.copy(fontSize = 16.sp), color = c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                statusText(p, slots)?.let { XdBadge(it, tone = if (p.isActive) BadgeTone.ACCENT else BadgeTone.NEUTRAL) }
                Text(p.xuid, style = XdText.mono.copy(fontSize = 11.sp), color = c.fg3)
            }
            XdIconButton(XdIcons.trash, stringResource(R.string.xd_pf_to_trash_cd, p.gamertag.ifBlank { p.xuid }), onTrash, size = 48.dp)
        }
        XdKv(listOf(
            stringResource(R.string.xd_pf_lang_region) to languageText(p),
            stringResource(R.string.xd_pf_saves) to savesText(summary),
        ))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!p.isActive) XdButton(stringResource(R.string.xd_pf_make_active), onActive, modifier = Modifier.heightIn(min = 48.dp), size = XdButtonSize.SM)
            XdButton(stringResource(R.string.pf_edit), onEdit, modifier = Modifier.heightIn(min = 48.dp), kind = XdButtonKind.GHOST, size = XdButtonSize.SM)
            XdButton(stringResource(R.string.xd_pf_saves), onSaves, modifier = Modifier.heightIn(min = 48.dp), kind = XdButtonKind.GHOST, size = XdButtonSize.SM, icon = XdIcons.save,
                enabled = summary?.gameTitles?.isNotEmpty() == true)
        }
    }
}

@Composable
private fun NewProfileCard(onCreate: () -> Unit, modifier: Modifier) {
    val c = Xd.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.heightIn(min = 170.dp).focusRing(shape).clip(shape).border(1.5.dp, c.line2, shape)
            .clickable(role = Role.Button, onClick = onCreate).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Icon(XdIcons.plus, null, Modifier.size(30.dp), tint = c.fg2)
        Text(stringResource(R.string.pf_create), style = XdText.label, color = c.fg2)
    }
}

/** Controller mode: big avatars side by side; A opens the profile's options. */
@Composable
private fun ControllerProfiles(profiles: List<ProfileEntry>, slots: List<String?>, onPick: (ProfileEntry) -> Unit, onCreate: () -> Unit) {
    val c = Xd.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(profiles.size) { i ->
                val p = profiles[i]
                BigTile(onClick = { onPick(p) }, on = p.isActive) {
                    ProfileAvatar(p.xuid, p.gamertag, p.hasAvatar, 88.dp)
                    Text(p.gamertag.ifBlank { p.xuid }, style = XdText.label.copy(fontSize = 16.sp), color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(statusText(p, slots) ?: languageText(p), style = XdText.small, color = if (p.isActive) c.acc else c.fg3, maxLines = 1)
                }
            }
            item {
                BigTile(onClick = onCreate, on = false) {
                    Box(Modifier.size(88.dp).clip(CircleShape).background(c.s3), contentAlignment = Alignment.Center) {
                        Icon(XdIcons.plus, null, Modifier.size(38.dp), tint = c.fg2)
                    }
                    Text(stringResource(R.string.pf_create), style = XdText.label.copy(fontSize = 16.sp), color = c.fg)
                    Text(stringResource(R.string.xd_pf_new_sub), style = XdText.small, color = c.fg3)
                }
            }
        }
        Text(stringResource(R.string.xd_pf_c_note), style = XdText.note, color = c.fg3)
    }
}

@Composable
private fun BigTile(onClick: () -> Unit, on: Boolean, content: @Composable () -> Unit) {
    val c = Xd.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.width(160.dp).focusRing(shape).clip(shape).background(if (on) c.acc.copy(alpha = 0.12f) else c.s1)
            .clickable(role = Role.Button, onClick = onClick).padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** U11: who signs in as P1–P4; P1 is the active profile. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Players(profiles: List<ProfileEntry>, slots: List<String?>, onPick: (Int) -> Unit, onNobody: (Int) -> Unit) {
    val c = Xd.colors
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoxWithConstraints {
            val columns = if (maxWidth >= 720.dp) 4 else 2
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                (0 until ProfileSlots.COUNT).chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { n ->
                            val p = profiles.firstOrNull { it.xuid.equals(slots.getOrNull(n), ignoreCase = true) }
                            val shape = RoundedCornerShape(14.dp)
                            Column(
                                Modifier.weight(1f).clip(shape).then(if (p != null) Modifier.background(c.s1) else Modifier.border(1.dp, c.line2, shape))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("P${n + 1}", style = XdText.monoNum, color = if (p != null) c.acc else c.fg3)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (p != null) ProfileAvatar(p.xuid, p.gamertag, p.hasAvatar, 30.dp)
                                    Text(p?.gamertag?.ifBlank { p.xuid } ?: stringResource(R.string.pf_nobody_signs_in), style = XdText.label,
                                        color = if (p != null) c.fg else c.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    XdButton(stringResource(when {
                                        n == 0 -> R.string.xd_pf_change_active
                                        p != null -> R.string.xd_pf_change
                                        else -> R.string.xd_pf_choose
                                    }), { onPick(n) }, size = XdButtonSize.SM, kind = if (p != null) XdButtonKind.GHOST else XdButtonKind.SECONDARY,
                                        enabled = profiles.isNotEmpty())
                                    if (p != null && n > 0) XdButton(stringResource(R.string.pf_nobody), { onNobody(n) }, kind = XdButtonKind.GHOST, size = XdButtonSize.SM)
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.pf_players_note), style = XdText.note, color = c.fg3)
        val prefs = remember { context.getSharedPreferences(ProfilePick.PREFS, android.content.Context.MODE_PRIVATE) }
        var ask by remember { mutableStateOf(prefs.getBoolean(ProfilePick.ASK, true)) }
        XdSheetOption(stringResource(R.string.pf_ask), subtitle = stringResource(if (ask) R.string.pf_ask_on else R.string.pf_ask_off)) {
            XdSwitch(ask, { ask = it; prefs.edit { putBoolean(ProfilePick.ASK, it) } })
        }
    }
}

@Composable
private fun Trash(trash: List<TrashedProfile>, onRestore: (TrashedProfile) -> Unit, onPurge: (TrashedProfile) -> Unit) {
    val c = Xd.colors
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (trash.isEmpty()) Text(stringResource(R.string.xd_pf_trash_empty), style = XdText.note, color = c.fg3)
        else XdCard(Modifier.fillMaxWidth()) {
            trash.forEachIndexed { i, t ->
                XdListRow(t.xuid, subtitle = stringResource(R.string.pf_removed_on,
                    DateUtils.formatDateTime(context, t.deletedAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH)),
                    lead = { ProfileAvatar(t.xuid, "", false, 34.dp) }, divider = i < trash.lastIndex) {
                    XdButton(stringResource(R.string.prof_restore), { onRestore(t) }, size = XdButtonSize.SM)
                    XdButton(stringResource(R.string.pf_purge), { onPurge(t) }, kind = XdButtonKind.GHOST, size = XdButtonSize.SM)
                }
            }
        }
        Text(stringResource(R.string.xd_pf_trash_note), style = XdText.note, color = c.fg3)
    }
}

/** A profile to choose in a sheet: avatar, name, a line under it, a check when it is the current one. */
@Composable
internal fun ProfileChoice(p: ProfileEntry, line: String, selected: Boolean, onClick: () -> Unit) {
    val c = Xd.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().focusRing(shape).clip(shape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfileAvatar(p.xuid, p.gamertag, p.hasAvatar, 36.dp)
        Column(Modifier.weight(1f)) {
            Text(p.gamertag.ifBlank { p.xuid }, style = XdText.label, color = c.fg)
            Text(line, style = XdText.small, color = c.fg3)
        }
        if (selected) Icon(XdIcons.check, null, Modifier.size(18.dp), tint = c.acc)
    }
}

@Composable
private fun ProfileForm(
    initial: ProfileEntry?,
    onDismiss: () -> Unit,
    onSubmit: (gamertag: String, language: Int, country: Int, avatar: Uri?) -> Unit,
) {
    val c = Xd.colors
    val context = LocalContext.current
    var gamertag by rememberSaveable { mutableStateOf(initial?.gamertag ?: "") }
    var language by rememberSaveable { mutableIntStateOf(initial?.language ?: DEFAULT_LANGUAGE) }
    var country by rememberSaveable { mutableIntStateOf(initial?.country ?: DEFAULT_COUNTRY) }
    var avatar by remember { mutableStateOf<Uri?>(null) }
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) avatar = uri }
    val valid = Gamertag.isValid(gamertag)
    XdSheet(onDismiss = onDismiss, title = stringResource(if (initial == null) R.string.pf_create else R.string.pf_edit_title), actions = {
        XdButton(stringResource(R.string.common_cancel), onDismiss, kind = XdButtonKind.GHOST)
        XdButton(stringResource(if (initial == null) R.string.col_create else R.string.common_save), { onSubmit(gamertag, language, country, avatar) },
            kind = XdButtonKind.PRIMARY, enabled = valid)
    }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val picked = avatar
            when {
                picked != null -> AsyncImage(picked, stringResource(R.string.pf_avatar), Modifier.size(64.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                initial != null -> ProfileAvatar(initial.xuid, gamertag.ifBlank { initial.gamertag }, initial.hasAvatar, 64.dp)
                else -> Box(Modifier.size(64.dp).clip(CircleShape).background(c.s3), contentAlignment = Alignment.Center) {
                    Icon(XdIcons.user, null, Modifier.size(30.dp), tint = c.fg2)
                }
            }
            XdButton(stringResource(R.string.pf_pick_avatar), {
                pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, kind = XdButtonKind.GHOST, size = XdButtonSize.SM, icon = XdIcons.image)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.pf_gamertag), style = XdText.labelSm, color = c.fg2)
            XdTextInput(gamertag, { if (it.length <= 15) gamertag = it }, Modifier.fillMaxWidth(), placeholder = "XenPlayer", mono = false, width = 640.dp)
            if (gamertag.isNotEmpty() && !valid) XdNote(stringResource(R.string.pf_gamertag_rule), tone = NoteTone.ERROR, icon = XdIcons.warn)
        }
        BoxWithConstraints {
            val two = maxWidth >= 420.dp
            val fields: List<@Composable (Modifier) -> Unit> = listOf(
                { m -> Field(stringResource(R.string.pf_language), m) {
                    LANGUAGE?.let { s -> XdSelect(s.options.map { it.value to optionText(context, s, it.value, it.label) }, language.toString(),
                        { v -> v.toIntOrNull()?.let { language = it } }, maxWidth = 400.dp) }
                } },
                { m -> Field(stringResource(R.string.pf_region), m) {
                    COUNTRY?.let { s -> XdSelect(s.options.map { it.value to optionText(context, s, it.value, it.label) }.sortedBy { it.second }, country.toString(),
                        { v -> v.toIntOrNull()?.let { country = it } }, maxWidth = 400.dp) }
                } },
            )
            if (two) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { fields.forEach { it(Modifier.weight(1f)) } }
            else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { fields.forEach { it(Modifier.fillMaxWidth()) } }
        }
        XdNote(stringResource(R.string.xd_pf_form_note))
    }
}

@Composable
private fun Field(label: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = XdText.labelSm, color = Xd.colors.fg2)
        content()
    }
}

@Composable
private fun deleteSummaryText(entry: ProfileEntry, summary: ProfileContentSummary, links: ProfilesLinks): String {
    val name = entry.gamertag.ifBlank { entry.xuid }
    val games = summary.gameTitles
    val megabytes = "%.1f".format(summary.bytes / (1024.0 * 1024.0))
    val detail = if (games.isEmpty()) stringResource(R.string.pf_del_no_saves)
    else pluralStringResource(R.plurals.pf_del_saves, games.size, games.size,
        games.joinToString(", ") { links.gameName(it.titleId) ?: it.titleId }, summary.files, megabytes)
    return listOfNotNull(stringResource(R.string.pf_del_name, name), detail,
        stringResource(R.string.pf_del_partial).takeIf { summary.truncated }, stringResource(R.string.pf_del_trash)).joinToString(" ")
}

