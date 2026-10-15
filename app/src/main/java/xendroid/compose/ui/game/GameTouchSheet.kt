package xendroid.compose.ui.game

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
                        SheetIdentity(game, art, data, preparing, onPlay, onLaunch, prominent = mainSection(current?.id) == GameSections.OVERVIEW)
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
                            val contentShape = RoundedCornerShape(6.dp)
                            Column(
                                Modifier.fillMaxWidth().testTag("game-sheet-content:${current.id}")
                                    .clip(contentShape).background(frameColors.s1)
                                    .border(1.dp, frameColors.line, contentShape)
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
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
private fun SheetIdentity(game: Game, art: CoverArt, data: LibraryData, preparing: Boolean, onPlay: () -> Unit, onLaunch: () -> Unit, prominent: Boolean) {
    var identifiers by rememberSaveable(game.identityKey) { mutableStateOf(false) }
    val c = Xd.colors
    val heroShape = ZephyrPanelShape
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(heroShape).background(c.s1).border(1.dp, c.line2, heroShape)
                .padding(12.dp).testTag("game-sheet-hero"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LibraryFeatureIdentity(game, art, data, prominent, Modifier.testTag("game-sheet-identity"))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val sideBySide = maxWidth >= 520.dp
                if (sideBySide) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        XdButton(
                            stringResource(R.string.lib_play),
                            onPlay,
                            Modifier.weight(1f).heightIn(min = 48.dp).testTag("game-sheet-play"),
                            kind = XdButtonKind.PRIMARY,
                            icon = XdIcons.play,
                            enabled = !preparing,
                        )
                        XdButton(
                            stringResource(R.string.xd_game_sec_launch),
                            onLaunch,
                            Modifier.weight(1f).heightIn(min = 48.dp).testTag("game-sheet-launch"),
                            kind = XdButtonKind.GHOST,
                            enabled = !preparing,
                        )
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        XdButton(
                            stringResource(R.string.lib_play),
                            onPlay,
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("game-sheet-play"),
                            kind = XdButtonKind.PRIMARY,
                            icon = XdIcons.play,
                            enabled = !preparing,
                        )
                        XdButton(
                            stringResource(R.string.xd_game_sec_launch),
                            onLaunch,
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("game-sheet-launch"),
                            kind = XdButtonKind.GHOST,
                            enabled = !preparing,
                        )
                    }
                }
            }
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
    val railShape = RoundedCornerShape(6.dp)
    Row(
        Modifier.fillMaxWidth().clip(railShape).background(c.s1).border(1.dp, c.line2, railShape)
            .padding(4.dp).testTag("game-sheet-main-tabs"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        choices.forEach { (id, label) ->
            val picked = selected == id
            val shape = RoundedCornerShape(4.dp)
            Column(
                Modifier.weight(1f).heightIn(min = 52.dp)
                    .focusRing(shape, color = if (picked) c.fg else c.acc)
                    .clip(shape)
                    .background(if (picked) c.action else Color.Transparent)
                    .clickable(role = Role.Tab) { if (!picked) onSelect(id) }
                    .semantics { this.selected = picked }
                    .testTag("game-sheet-tab:$id")
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(label),
                    style = XdText.label.copy(fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium),
                    color = if (picked) c.onAction else c.fg2,
                    maxLines = 1,
                )
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
