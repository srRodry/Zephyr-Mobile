package xendroid.compose.ui.library

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xendroid.compose.R
import xendroid.compose.data.Game
import xendroid.compose.gamepad.NavRepeat
import xendroid.compose.ui.design.ButtonGlyph
import xendroid.compose.ui.design.CFrame
import xendroid.compose.ui.design.LocalSwapConfirm
import xendroid.compose.ui.design.LocalXdNavigator
import xendroid.compose.ui.design.PadButton
import xendroid.compose.ui.design.WithCoverColors
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdEmpty
import xendroid.compose.ui.design.XdHint
import xendroid.compose.ui.design.XdIconButton
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdSearchField
import xendroid.compose.ui.design.XdSheet
import xendroid.compose.ui.design.XdStatusPill
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.GameCover
import xendroid.compose.ui.design.rememberCoverColors
import kotlin.math.abs

/** The controller tabs: recents, favourites, everything, then each collection. */
fun controllerTabs(data: LibraryData): List<LibraryFilter> =
    listOf(LibraryFilter.Recent, LibraryFilter.Favorites, LibraryFilter.All) + data.collections.map { LibraryFilter.Collection(it.name) }

@Composable
private fun tabTitle(f: LibraryFilter): String = when (f) {
    LibraryFilter.Recent -> stringResource(R.string.xd_lib_recent)
    LibraryFilter.Favorites -> stringResource(R.string.lib_favorites)
    LibraryFilter.All -> stringResource(R.string.xd_lib_all)
    is LibraryFilter.Collection -> f.name
    is LibraryFilter.Format -> formatLabel(f.format)
}

/**
 * The library for a controller (C): tabs by LB/RB, a carousel of big covers (left/right, A plays,
 * X opens the sheet, Y favourites, View searches, Start opens the menu), the focused game's colour
 * behind everything, its name and facts under the carousel, and the button hints.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryController(
    data: LibraryData,
    tab: LibraryFilter,
    onTab: (LibraryFilter) -> Unit,
    focusedId: String?,
    onFocusedId: (String) -> Unit,
    artOf: (Game) -> CoverArt,
    lastSession: (Game) -> String?,
    patches: (Game) -> String?,
    onPlay: (Game) -> Unit,
    onOpen: (Game) -> Unit,
    onFavorite: (Game) -> Unit,
    gamertag: String?,
    preparing: Boolean,
) {
    val tabs = controllerTabs(data)
    val current = tabs.firstOrNull { it == tab } ?: LibraryFilter.All
    val games = remember(data, current) {
        data.shown(current, "").let { if (current == LibraryFilter.All) data.sorted(it, LibrarySort.NAME_ASC) else it }
    }
    val index = games.indexOfFirst { it.stableId == focusedId }.coerceAtLeast(0)
    val game = games.getOrNull(index)
    val art = game?.let(artOf)
    val colors = rememberCoverColors(art?.model)
    var searching by remember { mutableStateOf(false) }
    val nav = LocalXdNavigator.current
    val swap = LocalSwapConfirm.current
    val carousel = remember { FocusRequester() }
    val shiftTab: (Int) -> Unit = { d ->
        val i = tabs.indexOf(current)
        onTab(tabs[Math.floorMod(i + d, tabs.size)])
    }
    // Round 2: LT/RT move five covers at once in a long shelf.
    val jump: (Int) -> Unit = { d -> if (games.isNotEmpty()) onFocusedId(games[(index + d).coerceIn(0, games.lastIndex)].stableId) }
    WithCoverColors(colors?.dyn, colors?.accent) {
        val c = Xd.colors
        CFrame(
            art = art?.model,
            hints = listOf(
                XdHint(if (swap) "B" else "A", stringResource(R.string.lib_play)) { game?.let(onPlay) },
                XdHint("X", stringResource(R.string.xd_lib_sheet)) { game?.let(onOpen) },
                XdHint("Y", stringResource(if (game != null && data.favorite(game)) R.string.xd_lib_unfavorite else R.string.xd_lib_favorite)) { game?.let(onFavorite) },
                XdHint("LB/RB", stringResource(R.string.xd_hint_tabs)) { shiftTab(1) },
                XdHint("LT/RT", stringResource(R.string.xd_hint_jump)) { jump(5) },
                XdHint("≡", stringResource(R.string.xd_hint_menu)),
            ),
            onShoulder = shiftTab,
            onPad = { b ->
                when (b) {
                    PadButton.X -> { game?.let(onOpen); true }
                    PadButton.Y -> { game?.let(onFavorite); true }
                    PadButton.SELECT -> { searching = true; true }
                    PadButton.LT -> { jump(-5); true }
                    PadButton.RT -> { jump(5); true }
                    else -> false
                }
            },
        ) { openGuide ->
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val portrait = maxHeight > maxWidth
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().height(50.dp).padding(start = 16.dp, end = 16.dp, top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ButtonGlyph("LB")
                        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (t in tabs) Blade(tabTitle(t), t == current) { onTab(t) }
                        }
                        ButtonGlyph("RB")
                        XdIconButton(XdIcons.search, stringResource(R.string.lib_search), { searching = true })
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).clickable { openGuide() }.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(c.s3), contentAlignment = Alignment.Center) {
                                Icon(XdIcons.user, null, Modifier.size(16.dp), tint = c.fg2)
                            }
                            if (!portrait) Text(gamertag ?: nav.gamertag.orEmpty(), style = XdText.labelSm, color = c.fg2, maxLines = 1)
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                        if (games.isEmpty()) {
                            XdEmpty(stringResource(R.string.xd_lib_empty_tab))
                        } else {
                            Carousel(games, index, artOf, data, portrait, carousel,
                                onIndex = { onFocusedId(games[it].stableId) },
                                onPlay = onPlay, onOpen = onOpen)
                            if (game != null) GameInfo(game, data, lastSession(game), patches(game), portrait, preparing, onPlay, onOpen)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(current, games.isNotEmpty()) { if (games.isNotEmpty()) runCatching { carousel.requestFocus() } }
    if (searching) SearchSheet(data, artOf, onDismiss = { searching = false }) { g ->
        searching = false
        onTab(LibraryFilter.All)
        onFocusedId(g.stableId)
    }
}

@Composable
private fun Blade(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = Xd.colors
    Box(
        Modifier.height(34.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .drawWithContent {
                drawContent()
                if (selected) drawRect(c.acc, topLeft = androidx.compose.ui.geometry.Offset(12.dp.toPx(), size.height - 5.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width - 24.dp.toPx(), 3.dp.toPx()))
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = XdText.blade, color = if (selected) c.fg else c.fg3, maxLines = 1)
    }
}

/** The covers in a row around the focused one, which is larger and lit; held directions repeat at a readable pace. */
@Composable
private fun Carousel(
    games: List<Game>,
    index: Int,
    artOf: (Game) -> CoverArt,
    data: LibraryData,
    portrait: Boolean,
    focusRequester: FocusRequester,
    onIndex: (Int) -> Unit,
    onPlay: (Game) -> Unit,
    onOpen: (Game) -> Unit,
) {
    val c = Xd.colors
    val itemW = if (portrait) 240.dp else 178.dp
    val step = itemW - (if (portrait) 72.dp else 44.dp)
    val animated by animateFloatAsState(index.toFloat(), tween(420), label = "carousel")
    val repeat = remember { NavRepeat() }
    var focused by remember { mutableStateOf(false) }
    val stepPx = with(LocalDensity.current) { step.toPx() }
    Box(
        Modifier.fillMaxWidth().height(if (portrait) 350.dp else 254.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                val code = e.nativeKeyEvent.keyCode
                val dir = when (code) { AndroidKeyEvent.KEYCODE_DPAD_LEFT -> -1; AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> 1; else -> 0 }
                if (dir != 0) {
                    if (e.type == KeyEventType.KeyUp) { repeat.release(); return@onPreviewKeyEvent true }
                    if (repeat.press(dir, SystemClock.uptimeMillis())) onIndex((index + dir).coerceIn(0, games.lastIndex))
                    return@onPreviewKeyEvent true
                }
                if (code == AndroidKeyEvent.KEYCODE_DPAD_CENTER || code == AndroidKeyEvent.KEYCODE_ENTER) {
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) onPlay(games[index])
                    return@onPreviewKeyEvent true
                }
                false
            }
            .focusable()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(Brush.horizontalGradient(0f to Color.Transparent, 0.1f to Color.Black, 0.9f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn)
            },
        contentAlignment = Alignment.Center,
    ) {
        val from = (index - 4).coerceAtLeast(0)
        val to = (index + 4).coerceAtMost(games.lastIndex)
        // Farther covers first, so the focused one draws on top.
        val order = (from..to).sortedByDescending { abs(it - animated) }
        for (i in order) {
            val g = games[i]
            val distance = abs(i - animated).coerceAtMost(1f)
            val scale = 1f - 0.3f * distance
            val on = i == index
            Box(
                Modifier.width(itemW)
                    .graphicsLayer {
                        translationX = (i - animated) * stepPx
                        scaleX = scale; scaleY = scale
                        alpha = 1f - 0.5f * distance
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .then(if (on) Modifier.border(3.dp, if (focused) c.fg else c.acc, RoundedCornerShape(14.dp)) else Modifier)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (on) onOpen(g) else onIndex(i)
                    }
                    .semantics { contentDescription = g.name; selected = on },
            ) {
                val art = artOf(g)
                GameCover(art.model, g.name, art.smart, favorite = data.favorite(g), discLabel = discBadge(g), radius = 12.dp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameInfo(
    game: Game,
    data: LibraryData,
    lastSession: String?,
    patches: String?,
    portrait: Boolean,
    preparing: Boolean,
    onPlay: (Game) -> Unit,
    onOpen: (Game) -> Unit,
) {
    val c = Xd.colors
    val played = data.played(game)
    val content: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(game.name, style = XdText.heroSm, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                XdStatusPill(compatShortText(data.status(game)), data.status(game).tone())
                Text(if (played != null) listOfNotNull(playedAgo(played), playTime(played)).joinToString(" · ")
                    else stringResource(R.string.xd_lib_never_played), style = XdText.bodySm, color = c.fg2)
                Text(listOfNotNull(formatLabel(game.format), patches?.let { stringResource(R.string.xd_lib_patches_short, it) }).joinToString(" · "),
                    style = XdText.bodySm, color = c.fg2)
                if (lastSession != null) Text(lastSession, style = XdText.bodySm, color = c.fg2)
            }
        }
    }
    if (portrait) {
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                XdButton(stringResource(R.string.lib_play), { onPlay(game) }, kind = XdButtonKind.PRIMARY, icon = XdIcons.play, enabled = !preparing, modifier = Modifier.weight(1f))
                XdButton(stringResource(R.string.xd_lib_sheet), { onOpen(game) }, modifier = Modifier.weight(1f))
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(start = 24.dp, end = 24.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.weight(1f)) { content() }
            XdButton(stringResource(R.string.lib_play), { onPlay(game) }, kind = XdButtonKind.PRIMARY, icon = XdIcons.play, enabled = !preparing)
            XdButton(stringResource(R.string.xd_lib_sheet), { onOpen(game) })
        }
    }
}

@Composable
private fun SearchSheet(data: LibraryData, artOf: (Game) -> CoverArt, onDismiss: () -> Unit, onPick: (Game) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(data, query) { data.shown(LibraryFilter.All, query) }
    XdSheet(onDismiss = onDismiss, title = stringResource(R.string.xd_lib_search_title), wide = true) {
        XdSearchField(query, { query = it }, stringResource(R.string.lib_search), Modifier.fillMaxWidth())
        if (shown.isEmpty()) XdEmpty(stringResource(R.string.lib_no_match))
        else LazyVerticalGrid(GridCells.Adaptive(86.dp), Modifier.fillMaxWidth().heightIn(max = 420.dp),
            contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { it.stableId }) { g ->
                val art = artOf(g)
                Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable { onPick(g) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GameCover(art.model, g.name, art.smart, favorite = data.favorite(g))
                    Text(g.name, style = XdText.tiny.copy(fontSize = 11.5.sp), color = Xd.colors.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
