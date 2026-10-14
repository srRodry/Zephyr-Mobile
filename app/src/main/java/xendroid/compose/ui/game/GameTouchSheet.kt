package xendroid.compose.ui.game

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xendroid.compose.R
import xendroid.compose.data.Game
import xendroid.compose.ui.design.*
import xendroid.compose.ui.library.*

/** Presentation groups only: setting-group, content, performance and data IDs remain intact. */
private fun mainSection(id: String?): String = when {
    id == GameSections.SETTINGS || id?.startsWith("set:") == true -> GameSections.SETTINGS
    id == GameSections.CONTENT || id == GameSections.PERF || id == GameSections.DATA -> GameSections.DATA
    else -> GameSections.OVERVIEW
}

@Composable
internal fun GameTouchSheet(
    game: Game, art: CoverArt, data: LibraryData, sections: List<XdSection>,
    current: XdSection?, history: XdSectionHistory, preparing: Boolean,
    onBack: () -> Unit, onPlay: () -> Unit, onLaunch: () -> Unit,
    onFavorite: () -> Unit, onMore: () -> Unit,
) {
    val frameColors = Xd.colors
    BoxWithConstraints(Modifier.fillMaxSize().background(frameColors.bg)) {
        val compact = maxHeight > maxWidth || maxWidth < 840.dp
        val body: @Composable (Modifier) -> Unit = { modifier ->
            LibraryPlane(modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing.part(
                top = !compact, bottom = true, start = compact, end = true))) {
                val c = Xd.colors
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        XdIconButton(XdIcons.back, stringResource(R.string.lib_title), onBack)
                        Text(stringResource(R.string.lib_title), Modifier.weight(1f), style = XdText.label, color = c.fg)
                        XdIconButton(if (data.favorite(game)) XdIcons.starFilled else XdIcons.star,
                            stringResource(R.string.lib_favorites), onFavorite, on = data.favorite(game))
                        XdIconButton(XdIcons.more, stringResource(R.string.lib_more), onMore)
                    }
                    HorizontalDivider(color = c.line)
                    val scroll = rememberSaveable(current?.id, saver = ScrollState.Saver) { ScrollState(0) }
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).testTag("game-sheet-scroll")
                        .padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SheetIdentity(game, art, data, preparing, onPlay, onLaunch)
                        MainTabs(mainSection(current?.id), history::pick)
                        val active = mainSection(current?.id)
                        when (active) {
                            GameSections.SETTINGS -> SectionChoice(
                                sections.filter { it.id == GameSections.SETTINGS || it.id.startsWith("set:") }, current, history::pick)
                            GameSections.DATA -> SubTabs(
                                sections.filter { it.id in listOf(GameSections.DATA, GameSections.CONTENT, GameSections.PERF) },
                                current?.id, history::pick)
                        }
                        val previous = sections.firstOrNull { it.id == history.previous }
                        if (previous != null) XdButton(stringResource(R.string.xd_back_to, previous.title), history::back,
                            Modifier.heightIn(min = 48.dp).testTag("game-sheet-return"), kind = XdButtonKind.GHOST, icon = XdIcons.back)
                        // Existing warnings, charts and setting widgets keep their dark semantic tokens.
                        if (current != null) CompositionLocalProvider(LocalXd provides frameColors) {
                            Column(Modifier.fillMaxWidth().testTag("game-sheet-content:${current.id}").background(frameColors.bg, RoundedCornerShape(4.dp))
                                .padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                current.heading?.let { Text(it, style = XdText.h2, color = frameColors.fg) }
                                current.lead?.let { Text(it, style = XdText.bodySm, color = frameColors.fg2) }
                                current.content?.invoke(this)
                            }
                        }
                    }
                }
            }
        }
        if (compact) Column(Modifier.fillMaxSize()) { BTopAreaBar(XdArea.GAMES); body(Modifier.weight(1f)) }
        else Row(Modifier.fillMaxSize()) { BRail(XdArea.GAMES); body(Modifier.weight(1f)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetIdentity(game: Game, art: CoverArt, data: LibraryData, preparing: Boolean, onPlay: () -> Unit, onLaunch: () -> Unit) {
    val c = Xd.colors
    var identifiers by rememberSaveable(game.identityKey) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LibraryArtwork(game, art, Modifier.width(76.dp), data.favorite(game))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(game.name, style = XdText.h1.copy(fontSize = 26.sp, lineHeight = 30.sp), color = c.fg)
                Text(formatLabel(game.format), style = XdText.small, color = c.fg2)
                LibraryStatusLine(game, data)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            XdButton(stringResource(R.string.lib_play), onPlay, Modifier.heightIn(min = 48.dp),
                kind = XdButtonKind.PRIMARY, icon = XdIcons.play, enabled = !preparing)
            XdButton(stringResource(R.string.xd_game_sec_launch), onLaunch,
                Modifier.heightIn(min = 48.dp).testTag("game-sheet-launch"), enabled = !preparing)
        }
        XdButton(stringResource(R.string.zephyr_game_identifiers), { identifiers = !identifiers },
            Modifier.heightIn(min = 48.dp).testTag("game-sheet-identifiers"), kind = XdButtonKind.GHOST, icon = XdIcons.info)
        if (identifiers) XdKv(listOf(
            stringResource(R.string.zephyr_game_title_id) to (game.titleId ?: "—"),
            stringResource(R.string.zephyr_game_media_id) to (game.mediaId ?: "—"),
        ), Modifier.testTag("game-sheet-identifiers-values"))
    }
}

@Composable
private fun MainTabs(selected: String, onSelect: (String) -> Unit) {
    val c = Xd.colors
    val choices = listOf(GameSections.OVERVIEW to R.string.xd_game_sec_overview,
        GameSections.SETTINGS to R.string.zephyr_game_adjustments, GameSections.DATA to R.string.zephyr_game_data)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        choices.forEach { (id, label) ->
            val picked = selected == id
            val shape = RoundedCornerShape(4.dp)
            Column(Modifier.weight(1f).heightIn(min = 56.dp).focusRing(shape, color = if (picked) c.fg else c.acc).clip(shape)
                .background(if (picked) c.acc else c.s2)
                .clickable(role = Role.Tab) { if (!picked) onSelect(id) }
                .semantics { this.selected = picked }.testTag("game-sheet-tab:$id").padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(label), style = XdText.label.copy(fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium),
                    color = if (picked) c.onAcc else c.fg2)
                Box(Modifier.width(20.dp).height(2.dp).background(if (picked) c.onAcc else Color.Transparent))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubTabs(sections: List<XdSection>, selected: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        sections.forEach { section ->
            XdButton(section.title, { onSelect(section.id) }, Modifier.heightIn(min = 48.dp)
                .semantics { this.selected = section.id == selected }.testTag("game-sheet-section:${section.id}"),
                kind = if (section.id == selected) XdButtonKind.PRIMARY else XdButtonKind.GHOST)
        }
    }
}

@Composable
private fun SectionChoice(sections: List<XdSection>, current: XdSection?, onSelect: (String) -> Unit) {
    val c = Xd.colors
    var open by remember(current?.id) { mutableStateOf(false) }
    Box {
        XdButton(current?.title.orEmpty(), { open = true }, Modifier.heightIn(min = 48.dp).testTag("game-sheet-settings-groups"),
            kind = XdButtonKind.GHOST, icon = XdIcons.chevD)
        DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.background(c.sheet)) {
            sections.forEach { section ->
                DropdownMenuItem(text = { Text(section.title, color = c.fg) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { selected = section.id == current?.id },
                    onClick = { open = false; onSelect(section.id) })
            }
        }
    }
}

/** Explicit links keep secondary sections discoverable without expanding the primary tab row. */
@Composable
internal fun GameSheetLink(title: String, id: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onSelect: (String) -> Unit) {
    val c = Xd.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("game-sheet-link:$id").focusRing(RoundedCornerShape(4.dp))
        .clickable(role = Role.Button) { onSelect(id) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = c.fg2)
        Text(title, Modifier.weight(1f), style = XdText.label, color = c.fg)
        Icon(XdIcons.chevR, null, Modifier.size(16.dp), tint = c.fg2)
    }
    HorizontalDivider(color = c.line)
}
