package xendroid.compose.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
 * covers; in landscape the chosen game's panel on the right (Play, details, quick settings).
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
    onMenu: () -> Unit,
    gridState: LazyGridState,
    notices: @Composable () -> Unit,
    detail: @Composable (Game) -> Unit,
    area: XdArea = XdArea.GAMES,
) {
    val c = Xd.colors
    val shown = remember(data, filter, query) { data.shown(filter, query) }
    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
        val portrait = maxHeight > maxWidth
        val main: @Composable (Modifier) -> Unit = { m ->
            Column(m.windowInsetsPadding(WindowInsets.safeDrawing.part(top = true)).fillMaxHeight()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    XdSearchField(query, onQuery, stringResource(R.string.lib_search), Modifier.weight(1f))
                    if (!portrait) XdSelect(LibrarySort.entries.map { it to sortText(it) }, data.sort, onSort)
                    XdSegmented(
                        listOf(CoverDensity.SMALL to stringResource(R.string.xd_lib_density_s), CoverDensity.MEDIUM to stringResource(R.string.xd_lib_density_m),
                            CoverDensity.LARGE to stringResource(R.string.xd_lib_density_l)),
                        density, onDensity,
                    )
                    XdIconButton(XdIcons.more, stringResource(R.string.lib_more), onMenu)
                }
                notices()
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
                    if (portrait) {
                        XdChipSeparator()
                        XdSelect(LibrarySort.entries.map { it to sortText(it) }, data.sort, onSort)
                    }
                }
                if (shown.isEmpty()) {
                    XdEmpty(stringResource(R.string.lib_no_match)) {
                        XdLink(stringResource(R.string.lib_clear_search), { onQuery(""); onFilter(LibraryFilter.All) })
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(density.minWidth.dp),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        items(shown, key = { it.stableId }) { game ->
                            LibraryCard(
                                game, data, artOf(game), picked = !portrait && game.stableId == selected?.stableId, small = density == CoverDensity.SMALL,
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
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    onPad: (PadButton) -> Boolean,
) {
    val c = Xd.colors
    var focused by remember { mutableStateOf(false) }
    val radius = RoundedCornerShape(c.coverRadius)
    val cardShape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .clip(cardShape)
            .background(if (picked) c.s2 else c.s1)
            .border(1.dp, if (picked) c.acc.copy(alpha = 0.55f) else c.line2, cardShape)
            .padding(8.dp)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .onPreviewKeyEvent { e -> padButtonOf(e)?.let(onPad) ?: false }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = game.name },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.then(
                when {
                    focused -> Modifier.border(2.5.dp, c.fg, radius)
                    picked -> Modifier.border(2.5.dp, c.acc, radius)
                    else -> Modifier
                }
            ).padding(if (focused || picked) 2.dp else 0.dp)
        ) {
            GameCover(art.model, game.name, art.smart, favorite = data.favorite(game), discLabel = discBadge(game))
        }
        Text(game.name, style = XdText.labelSm.copy(fontSize = if (small) 11.5.sp else 12.5.sp, lineHeight = 15.sp), color = c.fg,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!small) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            val status = data.status(game)
            XdDot(status.tone())
            Text(listOfNotNull(compatShortText(status), playedAgo(data.played(game)) ?: stringResource(R.string.xd_lib_never)).joinToString(" · "),
                style = XdText.tiny.copy(fontSize = 11.sp), color = c.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The chosen game's panel (touch, landscape): its art behind the head, Play / Details / favourite,
 * play time, last session, patches, who plays, and the quick settings of this game.
 */
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
        Box(Modifier.fillMaxWidth()) {
            AsyncImage(art.model, null, Modifier.matchParentSize().blur(26.dp), contentScale = ContentScale.Crop, alpha = 0.5f)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0x3315191C), c.s1), startY = 0f)))
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
                verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(86.dp)) { GameCover(art.model, game.name, art.smart, favorite = data.favorite(game)) }
                Column(Modifier.weight(1f)) {
                    Text(game.name, style = XdText.h2.copy(lineHeight = 22.sp), color = c.fg, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 6.dp))
                    Text(listOfNotNull(game.titleId, formatLabel(game.format)).joinToString(" · "), style = XdText.mono.copy(fontSize = 11.sp),
                        color = c.fg3, modifier = Modifier.padding(bottom = 6.dp))
                    XdStatusPill(compatShortText(data.status(game)), data.status(game).tone())
                }
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                XdButton(stringResource(R.string.lib_play), onPlay, kind = XdButtonKind.PRIMARY, icon = XdIcons.play, enabled = !preparing,
                    modifier = Modifier.weight(1f))
                XdButton(stringResource(R.string.xd_lib_sheet), onOpen)
                XdIconButton(if (data.favorite(game)) XdIcons.starFilled else XdIcons.star, stringResource(R.string.lib_favorites), onFavorite,
                    on = data.favorite(game))
            }
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
