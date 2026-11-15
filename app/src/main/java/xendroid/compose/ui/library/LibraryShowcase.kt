package xendroid.compose.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import xendroid.compose.R
import xendroid.compose.data.Game
import xendroid.compose.ui.design.*

/** Library and game sheet share the green theme; the viewport only overrides button geometry. */
@Composable
internal fun LibraryPlane(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = Xd.colors
    CompositionLocalProvider(LocalXd provides colors, LocalXdButtonShape provides ZephyrActionShape) {
        Box(modifier.zephyrBackdrop()) { content() }
    }
}

/** Keep small embedded icons small; a real supplied cover alone fills the artwork frame. */
@Composable
internal fun LibraryArtwork(game: Game, art: CoverArt, modifier: Modifier = Modifier, favorite: Boolean = false) {
    val c = Xd.colors
    val shape = RoundedCornerShape(4.dp)
    BoxWithConstraints(modifier.aspectRatio(3f / 4f).clip(shape).background(c.s2).border(1.dp, c.line, shape)) {
        // The library card is a cover slot. Even when the game only exposes its embedded
        // title image, let the artwork own the complete slot instead of floating as a tiny icon.
        // Crop preserves the source aspect ratio; it never stretches the image to fit.
        if (art.smart && maxWidth > 80.dp) {
            // Embedded title art gets a complete editorial card, not an empty cover slot.
            val iconSize = minOf(maxWidth * .58f, 104.dp)
            AsyncImage(art.model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = .24f)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                listOf(c.s3.copy(alpha = .45f), c.s1.copy(alpha = .78f), c.bg))))
            Box(Modifier.matchParentSize().background(Brush.radialGradient(
                listOf(c.acc.copy(alpha = .20f), Color.Transparent))))
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(formatLabel(game.format), color = c.fg2, style = XdText.tiny)
                AsyncImage(art.model, null,
                    Modifier.align(Alignment.CenterHorizontally).size(iconSize)
                        .clip(RoundedCornerShape(12.dp)).border(1.dp, c.acc.copy(alpha = .5f), RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit)
                Text(game.name, color = c.fg, style = XdText.label,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        } else AsyncImage(
            art.model,
            null,
            Modifier.fillMaxSize(),
            contentScale = if (art.smart) ContentScale.Fit else ContentScale.Crop,
        )
        if (favorite) Icon(XdIcons.starFilled, null,
            Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp)
                .background(Color(0xFF15191F), RoundedCornerShape(3.dp)).padding(2.dp), tint = Color(0xFFF3CF55))
        discBadge(game)?.let { label ->
            Text(label, color = Color.White, style = XdText.tiny,
                modifier = Modifier.align(Alignment.BottomStart).background(Color(0xFF15191F)).padding(3.dp))
        }
    }
}

/** Real cover artwork leads the page; embedded title art uses the same full-slot treatment. */
@Composable
internal fun LibraryFeatureIdentity(
    game: Game, art: CoverArt, data: LibraryData, prominent: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val c = Xd.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (prominent && !art.smart) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).testTag("game-feature-cover")
                .clip(RoundedCornerShape(4.dp)).background(c.s2)) {
                AsyncImage(art.model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!prominent || art.smart) LibraryArtwork(game, art, Modifier.width(64.dp), data.favorite(game))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(game.name, style = XdText.h1.copy(fontSize = 26.sp, lineHeight = 30.sp), color = c.fg)
                Text(formatLabel(game.format), style = XdText.small, color = c.fg2)
                LibraryStatusLine(game, data)
            }
        }
    }
}

/** A featured title has explicit actions; tapping a neighbouring cover still only selects. */
@Composable
internal fun LibraryFeaturedGame(
    game: Game, art: CoverArt, data: LibraryData, preparing: Boolean,
    onPlay: () -> Unit, onOpen: () -> Unit, onFavorite: () -> Unit,
    nearby: List<Pair<Game, CoverArt>> = emptyList(),
    onSelectNearby: (Game) -> Unit = {},
) {
    val c = Xd.colors
    Column(Modifier.fillMaxWidth().testTag("library-featured")
        .zephyrSurface(emphasized = true).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // All covers are real entries, bounded to four neighbours. Decorative arcs carry no data.
        BoxWithConstraints(Modifier.fillMaxWidth().height(232.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width * .86f
                val h = 40.dp.toPx()
                val topLeft = Offset((size.width - w) / 2, size.height - h)
                drawOval(Brush.radialGradient(listOf(c.acc.copy(alpha = .16f), Color.Transparent),
                    center = Offset(size.width / 2, size.height - h / 2), radius = w / 2), topLeft, Size(w, h))
                drawOval(Color(0xFF06150F), topLeft + Offset(0f, 5.dp.toPx()), Size(w, h))
                drawOval(c.acc.copy(alpha = .55f), topLeft, Size(w, h), style = Stroke(1.5.dp.toPx()))
                drawOval(c.acc.copy(alpha = .16f), topLeft + Offset(w * .12f, h * .18f),
                    Size(w * .76f, h * .64f), style = Stroke(1.dp.toPx()))
            }
            val coverWidth = (maxWidth * .42f).coerceIn(110.dp, 154.dp)
            nearby.take(4).forEachIndexed { index, (neighbour, neighbourArt) ->
                val side = if (index % 2 == 0) -1 else 1
                val depth = index / 2 + 1
                LibraryArtwork(neighbour, neighbourArt,
                    Modifier.align(Alignment.Center).offset(x = coverWidth * (side * (.55f + depth * .12f)), y = 12.dp)
                        .width(coverWidth * (.76f - depth * .08f))
                        .graphicsLayer { rotationY = side * -22f; alpha = .94f - depth * .08f }
                        .zIndex(-depth.toFloat())
                        .clickable(role = Role.Button) { onSelectNearby(neighbour) }
                        .semantics { contentDescription = neighbour.name })
            }
            LibraryArtwork(game, art, Modifier.width(coverWidth).zIndex(1f)
                .shadow(18.dp, RoundedCornerShape(4.dp), ambientColor = c.acc, spotColor = c.acc)
                .border(1.dp, c.acc.copy(alpha = .85f), RoundedCornerShape(4.dp)), data.favorite(game))
        }
        Column(Modifier.fillMaxWidth().testTag("library-featured-identity"), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(game.name, style = XdText.heroSm, color = c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatLabel(game.format), style = XdText.small, color = c.fg2)
                LibraryStatusLine(game, data)
            }
            XdButton(stringResource(R.string.lib_play), onPlay,
                Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("library-featured-play"),
                kind = XdButtonKind.PRIMARY, size = XdButtonSize.LG, icon = XdIcons.play, enabled = !preparing)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                XdButton(stringResource(R.string.xd_lib_sheet), onOpen,
                    Modifier.weight(1f).heightIn(min = 48.dp).testTag("library-featured-open"),
                    kind = XdButtonKind.GHOST, icon = XdIcons.chevR)
                XdIconButton(if (data.favorite(game)) XdIcons.starFilled else XdIcons.star,
                    stringResource(R.string.lib_favorites), onFavorite, on = data.favorite(game))
            }
        }
    }
}

/** Bounded, lazy presentation of the current filtered/sorted library; selecting never launches. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibraryShowcase(
    games: List<Game>, selected: Game?, data: LibraryData, artOf: (Game) -> CoverArt,
    onSelect: (Game) -> Unit, onOpen: (Game) -> Unit, onFavorite: (Game) -> Unit,
) {
    val state = rememberLazyListState()
    LaunchedEffect(games, selected?.stableId) {
        val index = games.indexOfFirst { it.stableId == selected?.stableId }
        if (index >= 0 && state.layoutInfo.visibleItemsInfo.none { it.key == selected?.stableId }) state.scrollToItem(index)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tileWidth = ((maxWidth - 16.dp) / 3f).coerceIn(84.dp, 144.dp)
        LazyRow(state = state, modifier = Modifier.fillMaxWidth().testTag("library-showcase"),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            items(games, key = { it.stableId }) { game ->
                val picked = game.stableId == selected?.stableId
                val c = Xd.colors
                var focused by remember { mutableStateOf(false) }
                val shape = ZephyrActionShape
                val elevation by animateFloatAsState(if (picked || focused) 1f else .88f, label = "cover-selection")
                Column(Modifier.width(tileWidth).graphicsLayer {
                    scaleX = elevation; scaleY = elevation
                    rotationY = if (picked || focused) 0f else -8f
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f)
                }.border(if (picked || focused) 2.dp else 1.dp,
                    if (picked || focused) c.acc else Color.Transparent, shape)
                    .clip(shape).onFocusChanged { focused = it.isFocused; if (it.isFocused) onSelect(game) }
                    .onPreviewKeyEvent { event ->
                        when (padButtonOf(event)) {
                            PadButton.X -> { onOpen(game); true }
                            PadButton.Y -> { onFavorite(game); true }
                            else -> false
                        }
                    }.combinedClickable(role = Role.Button, onClick = { onSelect(game) }, onLongClick = { onOpen(game) })
                    .semantics { contentDescription = game.name; this.selected = picked }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LibraryArtwork(game, artOf(game), Modifier.width(tileWidth - if (picked) 8.dp else 20.dp), data.favorite(game))
                    Text(game.name, color = c.fg, style = XdText.labelSm.copy(fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Status remains the user's recorded result; foreground text uses the shared readable palette. */
@Composable
internal fun LibraryStatusLine(game: Game, data: LibraryData) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        XdDot(data.status(game).tone())
        Text(compatShortText(data.status(game)), style = XdText.small, color = Xd.colors.fg2)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibrarySelectedActions(
    game: Game, data: LibraryData, preparing: Boolean,
    onPlay: () -> Unit, onOpen: () -> Unit, onFavorite: () -> Unit, showIdentity: Boolean = true,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showIdentity) {
            Text(game.name, style = XdText.h1.copy(fontSize = 24.sp, lineHeight = 28.sp), color = Xd.colors.fg)
            Text(formatLabel(game.format), style = XdText.small, color = Xd.colors.fg2)
            LibraryStatusLine(game, data)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            XdButton(stringResource(R.string.lib_play), onPlay, Modifier.widthIn(min = 140.dp).heightIn(min = 48.dp),
                kind = XdButtonKind.PRIMARY, icon = XdIcons.play, enabled = !preparing)
            XdButton(stringResource(R.string.xd_lib_sheet), onOpen, Modifier.heightIn(min = 48.dp))
            XdIconButton(if (data.favorite(game)) XdIcons.starFilled else XdIcons.star,
                stringResource(R.string.lib_favorites), onFavorite, on = data.favorite(game))
        }
    }
}

/** Visible view/sort controls wrap in their parent and retain a full 48dp click target. */
@Composable
internal fun <T> LibraryChoice(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val c = Xd.colors
    Box {
        XdButton(label, { expanded = true }, Modifier.heightIn(min = 48.dp), kind = XdButtonKind.GHOST, icon = XdIcons.chevD)
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(c.sheet)) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text, color = if (value == selected) c.acc else c.fg) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { this.selected = value == selected },
                    onClick = { expanded = false; onSelect(value) })
            }
        }
    }
}
