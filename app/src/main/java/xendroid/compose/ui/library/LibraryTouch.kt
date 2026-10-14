package xendroid.compose.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.font.FontWeight
import xendroid.compose.ui.design.XdEditorialHero
import xendroid.compose.ui.design.XdButtonSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xendroid.compose.R
import xendroid.compose.data.Game
import xendroid.compose.ui.design.BBottomBar
import xendroid.compose.ui.design.BRail
import xendroid.compose.ui.design.GameCover
import xendroid.compose.ui.design.PadButton
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdArea
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdChip
import xendroid.compose.ui.design.XdChipRow
import xendroid.compose.ui.design.XdChipSeparator
import xendroid.compose.ui.design.XdDot
import xendroid.compose.ui.design.XdEmpty
import xendroid.compose.ui.design.XdIconButton
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdKv
import xendroid.compose.ui.design.XdLink
import xendroid.compose.ui.design.XdSearchField
import xendroid.compose.ui.design.XdSegmented
import xendroid.compose.ui.design.XdSelect
import xendroid.compose.ui.design.XdStatusPill
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.padButtonOf
import xendroid.compose.ui.design.part

/** One library card's art: the cover file (or icon) and whether only the 64 px icon exists. */
class CoverArt(val model: Any, val smart: Boolean)

/**
 * The library for touch (B): the rail, search, sort and cover size, the filter chips and a grid of
 * covers (or compact rows); on wide screens the chosen game's panel on the right (Play, details, quick settings).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryTouch(
    data: LibraryData,
    filter: LibraryFilter,
    onFilter: (LibraryFilter) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    density: CoverDensity,
    onDensity: (CoverDensity) -> Unit,
    onSort: (LibrarySort) -> Unit,
    selected: Game?,
    onSelect: (Game) -> Unit,
    artOf: (Game) -> CoverArt,
    onOpen: (Game) -> Unit,
    onFavorite: (Game) -> Unit,
    onPlay: (Game) -> Unit,
    preparing: Boolean,
    onMenu: () -> Unit,
    gridState: LazyGridState,
    notices: @Composable () -> Unit,
    detail: @Composable (Game) -> Unit,
    area: XdArea = XdArea.GAMES,
) {
    val c = Xd.colors
    val shown = remember(data, filter, query) { data.shown(filter, query) }
    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
        val portrait = maxHeight > maxWidth || maxWidth < 840.dp
        val listMode = portrait && density == CoverDensity.MEDIUM
        val featured = shown.firstOrNull { it.stableId == selected?.stableId } ?: shown.firstOrNull()
        val main: @Composable (Modifier) -> Unit = { m ->
            Column(m.windowInsetsPadding(WindowInsets.safeDrawing.part(top = true)).fillMaxHeight()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    XdSearchField(query, onQuery, stringResource(R.string.lib_search), Modifier.weight(1f))
                    if (!portrait) XdSelect(LibrarySort.entries.map { it to sortText(it) }, data.sort, onSort)
                    XdSegmented(
                        listOf(CoverDensity.SMALL to stringResource(R.string.xd_lib_density_s), CoverDensity.MEDIUM to stringResource(if (portrait) R.string.zephyr_ui2_list else R.string.xd_lib_density_m),
                            CoverDensity.LARGE to stringResource(R.string.xd_lib_density_l)),
                        density, onDensity,
                    )
                    XdIconButton(XdIcons.more, stringResource(R.string.lib_more), onMenu)
                }
                notices()
                // Keep sorting visible on phones without placing it inside the
                // horizontally scrolling filter chips (where it is easily missed).
                if (portrait) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        XdSelect(LibrarySort.entries.map { it to sortText(it) }, data.sort, onSort)
                    }
                }
                XdChipRow(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 10.dp)) {
                    XdChip(stringResource(R.string.xd_lib_all), filter == LibraryFilter.All, { onFilter(LibraryFilter.All) }, count = data.games.size)
                    XdChip(stringResource(R.string.lib_favorites), filter == LibraryFilter.Favorites, { onFilter(LibraryFilter.Favorites) },
                        count = data.count(LibraryFilter.Favorites), icon = XdIcons.star)
                    for (col in data.collections) {
                        val f = LibraryFilter.Collection(col.name)
                        XdChip(col.name, filter == f, { onFilter(f) }, count = col.members.size)
                    }
                    if (data.formats.size > 1) {
                        XdChipSeparator()
                        for (fmt in data.formats) {
                            val f = LibraryFilter.Format(fmt)
                            XdChip(formatLabel(fmt), filter == f, { onFilter(f) }, count = data.count(f))
                        }
                    }
                }
                if (shown.isEmpty()) {
                    // Keep the empty state inside the remaining viewport, not in
                    // an unbounded column that can push bottom navigation away.
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        XdEmpty(stringResource(R.string.lib_no_match)) {
                            XdLink(stringResource(R.string.lib_clear_search), { onQuery(""); onFilter(LibraryFilter.All) })
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = if (listMode) GridCells.Fixed(1) else GridCells.Adaptive(density.minWidth.dp),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        if (portrait && query.isBlank() && featured != null) {
                            item(key = "zephyr-editorial", span = { GridItemSpan(maxLineSpan) }) {
                                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                    Column {
                                        Text(stringResource(R.string.zephyr_ui2_headline), color = c.fg,
                                            style = XdText.body.copy(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold))
                                        Text(stringResource(R.string.zephyr_ui2_headline_accent), color = Color(0xFF69A5FF),
                                            style = XdText.body.copy(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold))
                                    }
                                    val featuredArt = artOf(featured)
                                    XdEditorialHero(featuredArt.model, featuredArt.smart, featured.name,
                                        listOfNotNull(formatLabel(featured.format), playedAgo(data.played(featured))).joinToString(" · "),
                                        stringResource(R.string.zephyr_ui2_featured)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            XdButton(stringResource(R.string.lib_play), { onPlay(featured) },
                                                Modifier.weight(1f).heightIn(min = 48.dp), kind = XdButtonKind.PRIMARY,
                                                size = XdButtonSize.LG, icon = XdIcons.play, enabled = !preparing)
                                            XdButton(stringResource(R.string.xd_lib_sheet), { onOpen(featured) },
                                                Modifier.heightIn(min = 48.dp), size = XdButtonSize.LG)
                                        }
                                    }
                                    Text(stringResource(R.string.zephyr_ui2_collection), style = XdText.h2, color = c.fg,
                                        modifier = Modifier.padding(top = 10.dp))
                                }
                            }
                        }
                        items(shown, key = { it.stableId }) { game ->
                            LibraryCard(
                                game, data, artOf(game), picked = !portrait && game.stableId == selected?.stableId, small = density == CoverDensity.SMALL, listMode = listMode,
                                onClick = { if (portrait || game.stableId == selected?.stableId) onOpen(game) else onSelect(game) },
                                onLongClick = { onOpen(game) },
                                onFocused = { if (!portrait) onSelect(game) },
                                onPad = { b ->
                                    when (b) {
                                        PadButton.Y -> { onFavorite(game); true }
                                        PadButton.X -> { onOpen(game); true }
                                        else -> false
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        if (portrait) {
            Column(Modifier.fillMaxSize()) { main(Modifier.weight(1f)); BBottomBar(area) }
        } else {
            Row(Modifier.fillMaxSize()) {
                BRail(area)
                main(Modifier.weight(1f))
                VerticalDivider(thickness = 1.dp, color = c.line)
                Box(Modifier.width(300.dp).fillMaxHeight().background(c.s1).windowInsetsPadding(WindowInsets.safeDrawing.part(top = true, end = true))) {
                    if (selected != null) detail(selected) else XdEmpty(stringResource(R.string.xd_lib_pick))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryCard(
    game: Game,
    data: LibraryData,
    art: CoverArt,
    picked: Boolean,
    small: Boolean,
    listMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    onPad: (PadButton) -> Boolean,
) {
    val c = Xd.colors
    var focused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .clip(cardShape)
            .background(if (picked || focused) c.s2 else c.s1)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> c.fg
                    picked -> c.acc.copy(alpha = 0.75f)
                    else -> c.line2
                },
                shape = cardShape,
            )
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .onPreviewKeyEvent { e -> padButtonOf(e)?.let(onPad) ?: false }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = game.name; selected = picked }
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (listMode) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                GameCover(art.model, game.name, art.smart, modifier = Modifier.width(60.dp),
                    favorite = data.favorite(game), discLabel = discBadge(game))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(game.name, style = XdText.label.copy(fontSize = 18.sp, lineHeight = 22.sp), color = c.fg,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(formatLabel(game.format), playedAgo(data.played(game)) ?: stringResource(R.string.xd_lib_never)).joinToString(" · "),
                        style = XdText.small, color = c.fg2)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val status = data.status(game)
                        XdDot(status.tone())
                        Text(compatShortText(status), style = XdText.small, color = c.fg2)
                    }
                }
            }
        } else {
            // The card outline already indicates focus/selection; avoid a second border around the art.
            GameCover(art.model, game.name, art.smart, favorite = data.favorite(game), discLabel = discBadge(game))
            Text(game.name, style = XdText.labelSm.copy(fontSize = if (small) 11.5.sp else 12.5.sp, lineHeight = 15.sp), color = c.fg,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!small) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val status = data.status(game)
                XdDot(status.tone())
                Text(listOfNotNull(compatShortText(status), playedAgo(data.played(game)) ?: stringResource(R.string.xd_lib_never)).joinToString(" · "),
                    style = XdText.small, color = c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * The chosen game's panel (touch, landscape): editorial art, Play / Details / favourite,
 * play time, last session, patches, who plays, and the quick settings of this game.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryDetailPanel(
    game: Game,
    data: LibraryData,
    art: CoverArt,
    lastSession: String?,
    patches: String?,
    signsInAs: String?,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onAllSettings: () -> Unit,
    preparing: Boolean,
    quickSettings: (@Composable () -> Unit)?,
) {
    val c = Xd.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            XdEditorialHero(art.model, art.smart, game.name,
                listOfNotNull(game.titleId, formatLabel(game.format)).joinToString(" · "),
                stringResource(R.string.zephyr_ui2_featured)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    XdButton(stringResource(R.string.lib_play), onPlay, kind = XdButtonKind.PRIMARY,
                        icon = XdIcons.play, enabled = !preparing, modifier = Modifier.heightIn(min = 48.dp))
                    XdButton(stringResource(R.string.xd_lib_sheet), onOpen, modifier = Modifier.heightIn(min = 48.dp))
                    XdIconButton(if (data.favorite(game)) XdIcons.starFilled else XdIcons.star,
                        stringResource(R.string.lib_favorites), onFavorite, on = data.favorite(game))
                }
            }
            XdStatusPill(compatShortText(data.status(game)), data.status(game).tone())
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val played = data.played(game)
            XdKv(listOf(
                stringResource(R.string.xd_lib_play_time) to (playTime(played) ?: "—"),
                stringResource(R.string.xd_lib_last_session) to (lastSession ?: "—"),
                stringResource(R.string.lib_patches) to (patches ?: "—"),
                stringResource(R.string.xd_lib_signs_in) to (signsInAs ?: "—"),
            ))
            if (quickSettings != null) Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.xd_lib_quick_game).uppercase(), style = XdText.cardHead.copy(fontSize = 11.sp), color = c.fg3,
                        modifier = Modifier.weight(1f))
                    XdLink(stringResource(R.string.xd_lib_all_settings), onAllSettings, style = XdText.labelSm.copy(fontSize = 12.sp))
                }
                quickSettings()
            }
        }
    }
}

/** U02: the sort order as shown (the enum's own label stays the stable English name). */
@Composable
fun sortText(sort: LibrarySort): String = when (sort) {
    LibrarySort.NAME_ASC -> stringResource(R.string.lib_sort_name_asc)
    LibrarySort.NAME_DESC -> stringResource(R.string.lib_sort_name_desc)
    LibrarySort.FORMAT -> stringResource(R.string.lib_sort_format)
    LibrarySort.RECENT -> stringResource(R.string.lib_sort_recent)
}

