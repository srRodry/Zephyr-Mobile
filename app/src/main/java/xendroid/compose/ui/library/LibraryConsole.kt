package xendroid.compose.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.ui.draw.clipToBounds
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
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.clearAndSetSemantics

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
                val sceneWidth = maxWidth
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
                            Modifier.width(if (enlarged) 270.dp else (sceneWidth * .32f).coerceIn(220.dp, 300.dp))
                                .fillMaxHeight().verticalScroll(rememberScrollState()), true)
                    }
                }
            }
        }
    }
    if (portrait) Column(Modifier.fillMaxSize().consoleAtmosphere()) {
        BTopAreaBar(area)
        body(Modifier.weight(1f))
        if (Xd.controller) ControllerConsoleHints()
    } else Row(Modifier.fillMaxSize().consoleAtmosphere()) {
        ZephyrBladeRail(area)
        Column(Modifier.weight(1f)) {
            body(Modifier.weight(1f))
            if (Xd.controller) ControllerConsoleHints()
        }
    }
}

@Composable
private fun ControllerConsoleHints() {
    val swap = LocalSwapConfirm.current
    CHints(listOf(
        XdHint(if (swap) "B" else "A", stringResource(R.string.lib_play)),
        XdHint("X", stringResource(R.string.xd_lib_sheet)),
        XdHint("Y", stringResource(R.string.xd_lib_favorite)),
        XdHint("LB", stringResource(R.string.zephyr_console_previous)),
        XdHint("RB", stringResource(R.string.zephyr_console_next)),
        XdHint("≡", stringResource(R.string.xd_hint_menu)),
    ), modifier = Modifier.padding(horizontal = 16.dp), scrim = false)
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

/** Bounded rendering of an unbounded circular sequence; drag, coast, then settle on one game. */
@Composable
private fun ConsoleCoverflow(
    games: List<Game>, game: Game, data: LibraryData, artOf: (Game) -> CoverArt,
    onSelect: (Game) -> Unit, modifier: Modifier,
) {
    if (games.isEmpty()) return
    val c = Xd.colors
    val identities = games.map { it.stableId }
    val index = games.indexOfFirst { it.stableId == game.stableId }.coerceAtLeast(0)
    var position by remember(identities) { mutableFloatStateOf(index.toFloat()) }
    var moving by remember(identities) { mutableStateOf(false) }
    var motion by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val screenDensity = LocalDensity.current
    val decay = remember(screenDensity) { splineBasedDecay<Float>(screenDensity) }
    val selectNow by rememberUpdatedState(onSelect)
    val currentGames by rememberUpdatedState(games)
    LaunchedEffect(identities) {
        snapshotFlow { circularCoverIndex(position, games.size) }
            .distinctUntilChanged().collect { selectNow(currentGames[it]) }
    }
    fun travel(target: Float) {
        val previous = motion
        motion = scope.launch {
            previous?.cancelAndJoin()
            moving = true
            try {
                Animatable(position).animateTo(target, spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow)) { position = value }
                selectNow(currentGames[circularCoverIndex(position, currentGames.size)])
            } finally { moving = false }
        }
    }
    LaunchedEffect(index, moving, identities) {
        if (!moving && circularCoverIndex(position, games.size) != index) {
            travel(nearestCoverPosition(position, index, games.size))
        }
    }
    fun advance(step: Int) {
        if (games.size > 1) travel(position.roundToInt() + step.toFloat())
    }
    DisposableEffect(identities) { onDispose { motion?.cancel() } }
    BoxWithConstraints(modifier.clipToBounds().testTag("library-coverflow").onPreviewKeyEvent {
        when (padButtonOf(it)) {
            PadButton.LB -> { advance(-1); true }
            PadButton.RB -> { advance(1); true }
            else -> false
        }
    }, contentAlignment = Alignment.Center) {
        val coverWidth = minOf(maxHeight * .60f, maxWidth * .39f)
        val spacingPx = with(screenDensity) { (coverWidth * .73f).toPx() }.coerceAtLeast(1f)
        val drag = rememberDraggableState { delta -> position -= delta / spacingPx }
        val dragModifier = Modifier.fillMaxSize().draggable(
            state = drag, orientation = Orientation.Horizontal, enabled = games.size > 1,
            startDragImmediately = moving,
            onDragStarted = {
                motion?.cancelAndJoin()
                moving = true
            },
            onDragStopped = { velocity ->
                motion = scope.launch {
                    try {
                        val pixels = Animatable(position * spacingPx)
                        pixels.animateDecay(-velocity.coerceIn(-10000f, 10000f), decay) {
                            position = value / spacingPx
                        }
                        pixels.animateTo(position.roundToInt() * spacingPx, spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow)) { position = value / spacingPx }
                        val settled = circularCoverIndex(position, games.size)
                        selectNow(currentGames[settled])
                        // Rebase only after rest: keep float precision for arbitrarily many circles.
                        position = settled.toFloat()
                    } finally { moving = false }
                }
            },
        )
        Box(dragModifier, contentAlignment = Alignment.Center) {
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
        val center = position.roundToInt()
        val slots = if (games.size == 1) listOf(center) else (floor(position).toInt() - 3..floor(position).toInt() + 3).toList()
        // Repeated visual slots keep small libraries continuous. Expose one accessible copy per game.
        val accessible = slots.groupBy { Math.floorMod(it, games.size) }
            .mapValues { (_, copies) -> copies.minBy { abs(it - position) } }
        slots.sortedByDescending { abs(it - position) }.forEach { slot -> key(slot) {
            val titleIndex = Math.floorMod(slot, games.size)
            val title = games[titleIndex]
            val relative = slot - position
            val depth = abs(relative)
            val scale = (1f - depth * .20f).coerceAtLeast(.48f)
            val primary = accessible[titleIndex] == slot
            Box(Modifier.offset(x = coverWidth * (relative * .73f), y = (-8 + depth * 15).dp)
                .width(coverWidth * scale).zIndex(4f - depth)
                .graphicsLayer {
                    rotationY = (-relative * 23f).coerceIn(-48f, 48f)
                    alpha = ((3f - depth) / .6f).coerceIn(0f, 1f)
                    cameraDistance = 16f * density
                }
                .then(if (primary) Modifier.semantics { contentDescription = title.name; selected = slot == center }
                    else Modifier.clearAndSetSemantics {})
                .clickable(role = Role.Button) { travel(nearestCoverPosition(position, titleIndex, games.size)) }) {
                // A dim floor reflection is part of the stage, not another game or touch target.
                LibraryArtwork(title, artOf(title), Modifier.fillMaxWidth()
                    .graphicsLayer { scaleY = -1f; translationY = coverHeight.toPx() * scale * .97f; alpha = .075f })
                Box(Modifier.shadow(if (slot == center) 18.dp else 5.dp, RoundedCornerShape(5.dp),
                    ambientColor = c.acc, spotColor = c.acc)
                    .border(if (slot == center) 1.5.dp else .7.dp,
                        c.acc.copy(alpha = if (slot == center) .9f else .5f), RoundedCornerShape(5.dp))) {
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
        if (landscape) Box(Modifier.fillMaxWidth().height(70.dp)
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
