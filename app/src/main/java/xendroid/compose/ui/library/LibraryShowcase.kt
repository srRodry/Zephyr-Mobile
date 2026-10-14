package xendroid.compose.ui.library

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
import androidx.compose.ui.draw.clip
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
    CompositionLocalProvider(LocalXd provides colors, LocalXdButtonShape provides RoundedCornerShape(4.dp)) {
        Box(modifier.background(colors.bg)) { content() }
    }
}

/** Keep small embedded icons small; a real supplied cover alone fills the artwork frame. */
@Composable
internal fun LibraryArtwork(game: Game, art: CoverArt, modifier: Modifier = Modifier, favorite: Boolean = false) {
    val c = Xd.colors
    val shape = RoundedCornerShape(4.dp)
    BoxWithConstraints(modifier.aspectRatio(3f / 4f).clip(shape).background(c.s2).border(1.dp, c.line, shape)) {
        if (art.smart) {
            val iconSize = (maxWidth * 0.55f).coerceAtMost(64.dp)
            AsyncImage(art.model, null, Modifier.size(iconSize).align(Alignment.Center), contentScale = ContentScale.Fit)
        } else {
            AsyncImage(art.model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (favorite) Icon(XdIcons.starFilled, null,
            Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp)
                .background(Color(0xFF15191F), RoundedCornerShape(3.dp)).padding(2.dp), tint = Color(0xFFF3CF55))
        discBadge(game)?.let { label ->
            Text(label, color = Color.White, style = XdText.tiny,
                modifier = Modifier.align(Alignment.BottomStart).background(Color(0xFF15191F)).padding(3.dp))
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
                val shape = RoundedCornerShape(4.dp)
                Column(Modifier.width(tileWidth).border(if (picked || focused) 2.dp else 1.dp,
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

/** Status remains the user's recorded result; foreground text stays readable on silver. */
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
    onPlay: () -> Unit, onOpen: () -> Unit, onFavorite: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(game.name, style = XdText.h1.copy(fontSize = 24.sp, lineHeight = 28.sp), color = Xd.colors.fg)
        Text(formatLabel(game.format), style = XdText.small, color = Xd.colors.fg2)
        LibraryStatusLine(game, data)
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
