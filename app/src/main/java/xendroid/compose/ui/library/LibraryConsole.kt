package xendroid.compose.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import xendroid.compose.R
import xendroid.compose.data.Game
import xendroid.compose.ui.design.*
import kotlin.math.abs

/** The default library is a single console stage. Browsing tools are a separate, reversible view. */
@Composable
internal fun LibraryConsole(
    portrait: Boolean, area: XdArea, games: List<Game>, game: Game, data: LibraryData,
    artOf: (Game) -> CoverArt, onSelect: (Game) -> Unit, preparing: Boolean,
    onPlay: () -> Unit, onOpen: () -> Unit, onFavorite: () -> Unit,
    onBrowse: () -> Unit, onMenu: () -> Unit, notices: @Composable () -> Unit,
) {
    val c = Xd.colors
    val body: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.windowInsetsPadding(WindowInsets.safeDrawing.part(
            top = !portrait, bottom = true, end = true, start = portrait))) {
            Row(Modifier.fillMaxWidth().heightIn(min = if (portrait) 44.dp else 56.dp)
                .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!portrait) {
                    ZephyrOrbitMark(38.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(stringResource(if (portrait) R.string.lib_title else R.string.app_name),
                    Modifier.weight(1f), style = XdText.h1.copy(fontSize = if (portrait) 23.sp else 25.sp,
                        fontStyle = FontStyle.Italic), color = c.fg, maxLines = 1)
                XdIconButton(XdIcons.search, stringResource(R.string.lib_search), onBrowse,
                    modifier = Modifier.testTag("library-toggle-tools"))
                XdIconButton(XdIcons.more, stringResource(R.string.lib_more), onMenu)
            }
            notices()
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("library-console")) {
                val enlarged = LocalDensity.current.fontScale > 1.15f
                if (portrait) {
                    // At large font sizes content may scroll; the play action never loses its target.
                    val stageHeight = (maxHeight * .53f).coerceIn(190.dp, 370.dp)
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ConsoleCoverflow(games, game, data, artOf, onSelect,
                            Modifier.fillMaxWidth().height(stageHeight))
                        ConsoleIdentity(game, data, artOf(game), preparing, onPlay, onOpen, onFavorite,
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp), false)
                        RecentStrip(games, game, data, artOf, onSelect, onBrowse)
                    }
                } else {
                    Row(Modifier.fillMaxSize().padding(end = 16.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        ConsoleCoverflow(games, game, data, artOf, onSelect,
                            Modifier.weight(1f).fillMaxHeight())
                        ConsoleIdentity(game, data, artOf(game), preparing, onPlay, onOpen, onFavorite,
                            Modifier.width(if (enlarged) 270.dp else (maxWidth * .32f).coerceIn(220.dp, 300.dp))
                                .fillMaxHeight().verticalScroll(rememberScrollState()), true)
                    }
                }
            }
        }
    }
    if (portrait) Column(Modifier.fillMaxSize().consoleAtmosphere()) {
        BTopAreaBar(area)
        body(Modifier.weight(1f))
    } else Row(Modifier.fillMaxSize().consoleAtmosphere()) {
        ZephyrBladeRail(area)
        body(Modifier.weight(1f))
    }
}

/** Wide flowing ribbons and localized light are cached vector geometry, not a full-screen bitmap. */
private fun Modifier.consoleAtmosphere(): Modifier = drawWithCache {
    val ribbons = listOf(.03f, .62f, .88f).map { y ->
        Path().apply {
            moveTo(-size.width * .1f, size.height * (y + .24f))
            cubicTo(size.width * .22f, size.height * (y - .18f), size.width * .7f,
                size.height * (y + .25f), size.width * 1.1f, size.height * (y - .28f))
            lineTo(size.width * 1.1f, size.height * (y - .19f))
            cubicTo(size.width * .68f, size.height * (y + .39f), size.width * .2f,
                size.height * (y - .08f), -size.width * .1f, size.height * (y + .34f))
            close()
        }
    }
    val base = Brush.radialGradient(listOf(Color(0xFF0E3C27), Color(0xFF020B09)),
        center = Offset(size.width * .45f, size.height * .45f), radius = size.maxDimension * .85f)
    val ribbon = Brush.linearGradient(listOf(Color(0xFF020C08), Color(0xFF194E31), Color(0xFF062016)),
        end = Offset(size.width, size.height))
    onDrawBehind {
        drawRect(base)
        ribbons.forEach {
            drawPath(it, ribbon)
            drawPath(it, Color(0xFF91F15C).copy(alpha = .25f), style = Stroke(.7.dp.toPx()))
        }
    }
}

/** Five bounded covers, centered on selection; swipe, neighbouring covers and shoulders select only. */
@Composable
private fun ConsoleCoverflow(
    games: List<Game>, game: Game, data: LibraryData, artOf: (Game) -> CoverArt,
    onSelect: (Game) -> Unit, modifier: Modifier,
) {
    val c = Xd.colors
    val index = games.indexOfFirst { it.stableId == game.stableId }.coerceAtLeast(0)
    fun advance(step: Int) { onSelect(games[Math.floorMod(index + step, games.size)]) }
    BoxWithConstraints(modifier.testTag("library-coverflow").onPreviewKeyEvent {
        when (padButtonOf(it)) {
            PadButton.LB -> { advance(-1); true }
            PadButton.RB -> { advance(1); true }
            else -> false
        }
    }.pointerInput(games, game.stableId) {
        var drag = 0f
        detectHorizontalDragGestures(onDragStart = { drag = 0f },
            onDragEnd = { if (abs(drag) > 40.dp.toPx()) advance(if (drag < 0f) 1 else -1) },
            onHorizontalDrag = { change, amount -> change.consume(); drag += amount })
    }, contentAlignment = Alignment.Center) {
        val coverWidth = minOf(maxHeight * .60f, maxWidth * .39f)
        val coverHeight = coverWidth * (4f / 3f)
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width * .97f
            val h = size.height * .16f
            val floorY = size.height * .86f
            drawRect(Brush.radialGradient(listOf(c.acc.copy(alpha = .12f), Color.Transparent),
                center = Offset(size.width / 2, floorY), radius = size.width * .55f))
            val tl = Offset((size.width - w) / 2, floorY - h / 2)
            drawOval(Color(0xFF03130C), tl + Offset(0f, 7.dp.toPx()), Size(w, h))
            drawOval(Brush.verticalGradient(listOf(Color(0xFF154D30), Color(0xFF061A12))), tl, Size(w, h))
            for (stroke in listOf(9f, 4f, 1f)) drawOval(c.acc.copy(alpha = if (stroke == 1f) .85f else .055f),
                tl, Size(w, h), style = Stroke(stroke.dp.toPx()))
            drawOval(c.acc.copy(alpha = .25f), tl + Offset(w * .06f, h * .14f),
                Size(w * .88f, h * .72f), style = Stroke(.7.dp.toPx()))
        }
        // Visit far covers first. Distinct entries avoid duplicated semantics for small libraries.
        val used = mutableSetOf(game.stableId)
        val neighbours = listOf(-1, 1, -2, 2).mapNotNull { step ->
            val neighbour = games[Math.floorMod(index + step, games.size)]
            if (used.add(neighbour.stableId)) step to neighbour else null
        }
        (neighbours + (0 to game)).sortedByDescending { abs(it.first) }.forEach { (step, title) ->
            val depth = abs(step)
            val scale = when (depth) { 0 -> 1f; 1 -> .80f; else -> .65f }
            val x = when (depth) { 0 -> 0f; 1 -> .73f; else -> 1.26f } * if (step < 0) -1 else 1
            Box(Modifier.offset(x = coverWidth * x, y = if (depth == 0) (-8).dp else (depth * 7).dp)
                .width(coverWidth * scale).zIndex(3f - depth)
                .graphicsLayer { rotationY = if (step < 0) 23f else if (step > 0) -23f else -5f
                    cameraDistance = 16f * density }
                .semantics { contentDescription = title.name; selected = depth == 0 }
                .clickable(role = Role.Button) { onSelect(title) }) {
                // A dim floor reflection is part of the stage, not another game or touch target.
                LibraryArtwork(title, artOf(title), Modifier.fillMaxWidth()
                    .graphicsLayer { scaleY = -1f; translationY = coverHeight.toPx() * scale * .97f; alpha = .075f })
                Box(Modifier.shadow(if (depth == 0) 18.dp else 5.dp, RoundedCornerShape(5.dp),
                    ambientColor = c.acc, spotColor = c.acc)
                    .border(if (depth == 0) 1.5.dp else .7.dp,
                        c.acc.copy(alpha = if (depth == 0) .9f else .5f), RoundedCornerShape(5.dp))) {
                    LibraryArtwork(title, artOf(title), Modifier.fillMaxWidth())
                    // Narrow spine and bevel supply depth without manufacturing a replacement cover.
                    Box(Modifier.matchParentSize().drawWithCache {
                        val bevel = Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .55f),
                            Color.White.copy(alpha = .2f), Color.Transparent, Color.Transparent),
                            endX = size.width * .12f)
                        onDrawBehind { drawRect(bevel) }
                    })
                }
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            XdIconButton(XdIcons.chevL, stringResource(R.string.zephyr_console_previous), { advance(-1) },
                modifier = Modifier.testTag("library-previous"))
            Text("${index + 1} / ${games.size}", style = XdText.small, color = c.fg2)
            XdIconButton(XdIcons.chevR, stringResource(R.string.zephyr_console_next), { advance(1) },
                modifier = Modifier.testTag("library-next"))
        }
    }
}

@Composable
private fun ConsoleIdentity(
    game: Game, data: LibraryData, art: CoverArt, preparing: Boolean,
    onPlay: () -> Unit, onOpen: () -> Unit, onFavorite: () -> Unit, modifier: Modifier, landscape: Boolean,
) {
    val c = Xd.colors
    val shape = RoundedCornerShape(topStart = 36.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 36.dp)
    Column(modifier.testTag("library-featured").then(if (landscape) Modifier.zephyrSurface(shape, true) else Modifier)
        .padding(if (landscape) 18.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (landscape) Box(Modifier.fillMaxWidth().heightIn(min = 70.dp, max = 110.dp).weight(.7f, fill = false)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 10.dp, bottomEnd = 10.dp, bottomStart = 10.dp))) {
            AsyncImage(art.model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, c.s1))))
        }
        Column(Modifier.testTag("library-featured-identity"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(game.name, style = XdText.heroSm.copy(fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold,
                fontSize = if (landscape) 25.sp else 28.sp, lineHeight = 31.sp), color = c.fg,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatLabel(game.format), style = XdText.small, color = c.fg2)
                LibraryStatusLine(game, data)
            }
        }
        if (landscape) Spacer(Modifier.weight(.2f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ConsolePlay(onPlay, !preparing, Modifier.weight(1f))
            if (!landscape) XdIconButton(if (data.favorite(game)) XdIcons.starFilled else XdIcons.star,
                stringResource(R.string.lib_favorites), onFavorite, on = data.favorite(game))
        }
        XdButton(stringResource(R.string.xd_lib_sheet), onOpen,
            Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("library-featured-open"),
            kind = XdButtonKind.GHOST, icon = XdIcons.info)
    }
}

/** Luminous bevel with a dark label bed: localized brightness rather than unreadable lime text. */
@Composable
private fun ConsolePlay(onClick: () -> Unit, enabled: Boolean, modifier: Modifier) {
    val c = Xd.colors
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 24.dp)
    Row(modifier.heightIn(min = 58.dp).shadow(12.dp, shape, ambientColor = c.acc, spotColor = c.acc)
        .clip(shape).background(Brush.verticalGradient(listOf(Color(0xFF73DC49), Color(0xFF176A32),
            Color(0xFF073D22), Color(0xFF258944))))
        .border(1.5.dp, c.acc, shape).focusRing(shape, c.fg)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .testTag("library-featured-play").padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
        Icon(XdIcons.play, null, tint = Color.White, modifier = Modifier.size(23.dp))
        Text(stringResource(R.string.lib_play), style = XdText.button.copy(fontSize = 20.sp, fontStyle = FontStyle.Italic), color = Color.White)
    }
}

@Composable
private fun RecentStrip(
    games: List<Game>, selected: Game, data: LibraryData, artOf: (Game) -> CoverArt,
    onSelect: (Game) -> Unit, onBrowse: () -> Unit,
) {
    val c = Xd.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.zephyr_library_your_library), Modifier.weight(1f), style = XdText.label, color = c.fg2)
            XdIconButton(XdIcons.grid, stringResource(R.string.zephyr_console_browse), onBrowse,
                modifier = Modifier.testTag("library-browse"))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("library-quick-selector")) {
            items(games, key = { it.stableId }) { title ->
                val picked = title.stableId == selected.stableId
                Box(Modifier.width(76.dp).height(52.dp).clip(RoundedCornerShape(10.dp))
                    .border(1.dp, if (picked) c.acc else c.line, RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button) { onSelect(title) }
                    .semantics { contentDescription = title.name; this.selected = picked }) {
                    AsyncImage(artOf(title).model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }
    }
}
