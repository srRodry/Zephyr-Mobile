@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package xendroid.compose.ui.ingame

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import xendroid.compose.R
import xendroid.compose.core.SessionLogs
import xendroid.compose.ui.design.ButtonGlyph
import xendroid.compose.ui.design.CHints
import xendroid.compose.ui.design.LocalSwapConfirm
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdSilverPanel
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdHint
import xendroid.compose.ui.design.XdIconButton
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdStepper
import xendroid.compose.ui.design.XdSwitch
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.part
import xendroid.compose.ui.panel.GuestPanelOption

/**
 * Round 2: the way into the menu without a button over the game. A strip along the left edge that a
 * drag inward opens; Back (the edge gesture or the button) and the controller's Guide open it too.
 */
@Composable
fun InGameMenuEdge(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.menu_open)
    Box(
        modifier.width(22.dp).fillMaxHeight()
            .semantics { contentDescription = description; role = Role.Button }
            .pointerInput(onOpen) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onHorizontalDrag = { change, amount ->
                        dragged += amount
                        if (dragged > 48.dp.toPx()) {
                            onOpen()
                            dragged = Float.NEGATIVE_INFINITY
                        }
                        change.consume()
                    },
                    onDragEnd = { dragged = 0f },
                )
            },
    )
}

/** One figure of the menu's status line: "30 FPS", "p99 34 ms", "41 °C"… ([value] in bold). */
data class MenuStat(val value: String, val unit: String? = null, val label: String? = null)

/**
 * What a row of the menu shows. The host builds one per row from its own state (screen tests build
 * them by hand); the row's kind ([InGameAction.kind]) says which fields it reads.
 */
data class MenuValue(
    /** CYCLE and INFO: the value; TOGGLE and BUTTON: a line under the title; SLIDER: the value as read. */
    val text: String? = null,
    /** CHOICE and MULTI: the choices. */
    val options: List<String> = emptyList(),
    /** CHOICE: the chosen one; -1 when none of them is (a value set elsewhere). */
    val selected: Int = -1,
    /** MULTI: the chips that are on. */
    val checked: Set<Int> = emptySet(),
    /** TOGGLE. */
    val on: Boolean = false,
    /** SLIDER: where it sits (0..1), and the stops between the ends (0 = any value). */
    val fraction: Float = 0f,
    val steps: Int = 0,
    val enabled: Boolean = true,
    /** The value is this game's own (its config or its own preference), not the global one. */
    val own: Boolean = false,
    /** A line under the row: why it is off, what it needs, when it applies. */
    val note: String? = null,
)

/** The menu's head, the value of every row, and the session's changes. */
data class InGameMenuModel(
    val values: Map<InGameAction, MenuValue> = emptyMap(),
    val gameName: String? = null,
    val art: Any? = null,
    val paused: Boolean = false,
    val status: List<MenuStat> = emptyList(),
    /** Lines under a category's rows (frame generation notes, phone controllers, the build). */
    val notes: Map<InGamePage, List<String>> = emptyMap(),
    val logSessions: List<SessionLogs.Session> = emptyList(),
    /** Round 2: settings this session changed and kept for the game (the "Kept for…" line). */
    val savedChanges: Int = 0,
    /** Round 2: the HUD as it is set now, drawn at the top of the HUD category (the HUD itself hides under the menu). */
    val hudPreview: HudPreview? = null,
)

/** The HUD's settings for the menu's preview: what it shows, how, and its size. */
data class HudPreview(
    val detail: xendroid.compose.core.HudDetail,
    val metrics: Set<xendroid.compose.core.HudMetric>,
    val look: xendroid.compose.core.HudLook,
    val style: xendroid.compose.core.HudStyle,
    val scale: Float,
)

/**
 * Round 2, the in-game menu: a rail of categories (tabs along the top in portrait) and, beside it,
 * the category's options in groups. Each row shows its value and changes it in place: pills for a
 * few choices, a switch, ‹ value › for longer lists, a slider for amounts. A controller moves with
 * ↑↓, changes the row with ←→, acts with A, switches category with LB/RB and closes with B; the host
 * drives that selection ([InGameMenuState]), so the rows only draw it.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun InGameMenu(
    state: InGameMenuState,
    model: InGameMenuModel,
    onPage: (InGamePage) -> Unit,
    onSelect: (Int) -> Unit,
    /** A, or a tap on the row: flips a switch, takes a cycle's next value, runs a button. */
    onAction: (InGameAction) -> Unit,
    /** ←→, or a tap on ‹ ›: the previous or the next value of a cycle. */
    onAdjust: (InGameAction, Int) -> Unit,
    /** A tap on a pill or a chip: that choice (a chip flips). */
    onChoose: (InGameAction, Int) -> Unit,
    /** A slider dragged to a place (0..1). */
    onSet: (InGameAction, Float) -> Unit,
    onLogChoice: (Int) -> Unit,
    onQuitChoice: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** A slider let go: what it changed is kept (the volume, for the game). */
    onSetDone: (InGameAction) -> Unit = {},
) {
    val c = Xd.colors
    val controller = Xd.controller
    BoxWithConstraints(
        modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        val portrait = maxHeight > maxWidth
        val short = maxHeight < 400.dp
        // The status line only where the rows keep room for several of them.
        val roomy = maxHeight >= 460.dp
        // Hardware navigation changes the selected row, not Compose focus: the row is scrolled
        // into view instead of leaving the highlight off-screen.
        val scrollState = remember(state.page, state.confirmingQuit, state.logPicker) { ScrollState(0) }
        val selectedRow = remember(state.page, state.confirmingQuit, state.logPicker) { BringIntoViewRequester() }
        LaunchedEffect(state.page, state.selected, state.confirmingQuit, state.logPicker) {
            // After this frame's layout: when the menu opens, the selected row is not placed yet.
            withFrameNanos { }
            selectedRow.bringIntoView()
        }
        val shape = if (portrait) RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp) else RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp)
        val panel = if (portrait) Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.86f)
        else Modifier.align(Alignment.CenterStart).width(minOf(maxWidth * (if (controller) 0.74f else 0.78f), if (controller) 660.dp else 720.dp)).fillMaxHeight()
        Column(
            panel.clip(shape).background(c.solid(c.sheet))
                .windowInsetsPadding(WindowInsets.safeDrawing.part(top = !portrait, bottom = true, start = true))
                .padding(horizontal = if (short) 12.dp else 16.dp, vertical = if (short) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (short) 8.dp else 10.dp),
        ) {
            val dialog = state.confirmingQuit || state.logPicker
            // Controller mode keeps its existing head; touch actions stay outside the scrolling rows.
            if (controller) Head(model, controller, compactActions = !portrait && !dialog, onClose = { onAction(InGameAction.RESUME) },
                onQuit = { onAction(InGameAction.QUIT) }, onChanges = { onPage(InGamePage.SESSION) })
            else TouchHead(model, short, onClose = { onAction(InGameAction.RESUME) })
            if (model.status.isNotEmpty() && !dialog && roomy) StatusLine(model.status)
            val body = Modifier.weight(1f).fillMaxWidth()
            when {
                dialog -> Column(body.verticalScroll(scrollState), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (state.confirmingQuit) QuitConfirm(state, selectedRow, onQuitChoice)
                    else LogPicker(state, model, selectedRow, onLogChoice)
                }
                portrait -> Column(body, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageTabs(state, controller, onPage)
                    Rows(state, model, scrollState, selectedRow, Modifier.weight(1f).fillMaxWidth(), onSelect, onAction, onAdjust, onChoose, onSet, onSetDone)
                }
                else -> Row(body, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PageRail(state, controller, short, onPage)
                    Rows(state, model, scrollState, selectedRow, Modifier.weight(1f).fillMaxHeight(), onSelect, onAction, onAdjust, onChoose, onSet, onSetDone)
                }
            }
            if (!dialog && portrait && model.savedChanges > 0) SavedLine(model, short) { onPage(InGamePage.SESSION) }
            if (controller) {
                val swap = LocalSwapConfirm.current
                CHints(listOf(XdHint(if (swap) "B" else "A", stringResource(R.string.xd_hint_select)),
                    XdHint("◀/▶", stringResource(R.string.menu_hint_adjust)),
                    XdHint("LB/RB", stringResource(R.string.menu_hint_categories)),
                    XdHint(if (swap) "A" else "B", stringResource(R.string.xd_close)) { onAction(InGameAction.RESUME) }),
                    Modifier.clip(RoundedCornerShape(12.dp)), scrim = false)
            } else if (!dialog) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    XdButton(stringResource(R.string.menu_continue), { onAction(InGameAction.RESUME) }, kind = XdButtonKind.PRIMARY,
                        icon = XdIcons.play, modifier = Modifier.heightIn(min = 48.dp))
                    if (short) XdIconButton(XdIcons.exit, stringResource(R.string.menu_exit_game),
                        { onAction(InGameAction.QUIT) }, size = 48.dp, tint = c.dangerText)
                    else XdButton(stringResource(R.string.menu_exit_game), { onAction(InGameAction.QUIT) }, kind = XdButtonKind.GHOST,
                        icon = XdIcons.exit, modifier = Modifier.heightIn(min = 48.dp))
                    if (!portrait && model.savedChanges > 0) {
                        val savedLabel = pluralStringResource(R.plurals.menu_saved_chip, model.savedChanges, model.savedChanges)
                        XdButton(if (short) model.savedChanges.toString() else savedLabel,
                            { onPage(InGamePage.SESSION) }, icon = XdIcons.save,
                            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = savedLabel })
                    }
                }
            }
        }
    }
}

/** A static touch header; the pause label is supplied by the host, never inferred from the menu. */
@Composable
private fun TouchHead(model: InGameMenuModel, short: Boolean, onClose: () -> Unit) {
    XdSilverPanel {
        val c = Xd.colors
        Row(Modifier.fillMaxWidth().padding(vertical = if (short) 0.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(4.dp).height(if (short) 38.dp else 48.dp).background(c.acc))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(model.gameName ?: stringResource(R.string.app_name), color = c.fg,
                    style = XdText.body.copy(fontSize = if (short) 21.sp else 24.sp,
                        lineHeight = if (short) 25.sp else 28.sp, fontWeight = FontWeight.Bold),
                    maxLines = if (short) 1 else 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(if (model.paused) R.string.xd_menu_paused else R.string.xd_menu_running),
                    style = XdText.labelSm, color = c.acc)
            }
            XdIconButton(XdIcons.x, stringResource(R.string.menu_continue), onClose, size = 48.dp)
        }
    }
}

/** The game and its state; in landscape also the kept changes (a chip to Session) and "Exit game". */
@Composable
private fun Head(model: InGameMenuModel, controller: Boolean, compactActions: Boolean, onClose: () -> Unit, onQuit: () -> Unit,
                 onChanges: () -> Unit) {
    val c = Xd.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (model.art != null) AsyncImage(model.art, null, Modifier.size(36.dp, 46.dp).clip(RoundedCornerShape(7.dp)), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f)) {
            Text(model.gameName ?: stringResource(R.string.app_name), style = XdText.h2, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(if (model.paused) R.string.xd_menu_paused else R.string.xd_menu_running), style = XdText.small, color = c.fg3,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (compactActions && model.savedChanges > 0) {
            Row(
                Modifier.height(32.dp).clip(RoundedCornerShape(50)).background(c.acc.copy(alpha = 0.12f)).clickable(onClick = onChanges)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(XdIcons.save, null, Modifier.size(14.dp), tint = c.acc)
                Text(pluralStringResource(R.plurals.menu_saved_chip, model.savedChanges, model.savedChanges), style = XdText.labelSm, color = c.fg2, maxLines = 1)
            }
        }
        if (compactActions && !controller) XdButton(stringResource(R.string.menu_exit_game), onQuit, kind = XdButtonKind.GHOST, icon = XdIcons.exit)
        if (!controller) XdIconButton(XdIcons.x, stringResource(R.string.menu_continue), onClose)
    }
}

@Composable
private fun StatusLine(status: List<MenuStat>) {
    val c = Xd.colors
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.05f))
        .horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        for (s in status) {
            Text(buildAnnotatedString {
                s.label?.let { append(it); append(' ') }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.fg)) { append(s.value) }
                s.unit?.let { append(' '); append(it) }
            }, style = XdText.small, color = c.fg2, maxLines = 1)
        }
    }
}

/** Landscape: the categories down the side, icon over label; LB and RB at the ends with a pad. */
@Composable
private fun PageRail(state: InGameMenuState, controller: Boolean, short: Boolean, onPage: (InGamePage) -> Unit) {
    val c = Xd.colors
    Column(
        Modifier.width(if (controller) { if (short) 76.dp else 84.dp } else { if (short) 92.dp else 104.dp }).fillMaxHeight().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (controller) ButtonGlyph("LB")
        for (page in InGamePage.entries) {
            val selected = page == state.page
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier.fillMaxWidth().heightIn(min = if (controller) 0.dp else 48.dp).clip(shape)
                    .background(if (controller) { if (selected) c.acc.copy(alpha = 0.16f) else Color.Transparent }
                        else { if (selected) Color(0xFF155ED2) else c.s1 })
                    .clickable(role = Role.Tab) { onPage(page) }.semantics { this.selected = selected }
                    .padding(vertical = if (short) 5.dp else 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(page.icon(), null, Modifier.size(20.dp), tint = if (selected && !controller) c.onAcc else if (selected) c.acc else c.fg3)
                Text(page.label(), style = XdText.labelSm.copy(fontSize = if (controller) 11.5.sp else 12.sp),
                    color = if (selected && !controller) c.onAcc else if (selected) c.acc else c.fg2, maxLines = if (controller) 1 else 2)
            }
        }
        if (controller) ButtonGlyph("RB")
    }
}

/** Portrait: the categories as tabs along the top. */
@Composable
private fun PageTabs(state: InGameMenuState, controller: Boolean, onPage: (InGamePage) -> Unit) {
    val c = Xd.colors
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (controller) ButtonGlyph("LB")
        for (page in InGamePage.entries) {
            val selected = page == state.page
            Row(
                Modifier.then(if (controller) Modifier.height(36.dp) else Modifier.heightIn(min = 48.dp)).clip(RoundedCornerShape(50))
                    .background(if (controller) { if (selected) c.acc.copy(alpha = 0.18f) else Color.Transparent }
                        else { if (selected) Color(0xFF155ED2) else c.s1 })
                    .clickable(role = Role.Tab) { onPage(page) }.semantics { this.selected = selected }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(page.icon(), null, Modifier.size(16.dp), tint = if (selected && !controller) c.onAcc else if (selected) c.acc else c.fg3)
                Text(page.label(), style = XdText.labelSm,
                    color = if (selected && !controller) c.onAcc else if (selected) c.acc else c.fg2, maxLines = 1)
            }
        }
        if (controller) ButtonGlyph("RB")
    }
}

/** The category's rows in their groups, then its notes. */
@Composable
private fun Rows(
    state: InGameMenuState,
    model: InGameMenuModel,
    scrollState: ScrollState,
    selectedRow: BringIntoViewRequester,
    modifier: Modifier,
    onSelect: (Int) -> Unit,
    onAction: (InGameAction) -> Unit,
    onAdjust: (InGameAction, Int) -> Unit,
    onChoose: (InGameAction, Int) -> Unit,
    onSet: (InGameAction, Float) -> Unit,
    onSetDone: (InGameAction) -> Unit,
) {
    Column(modifier.verticalScroll(scrollState), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (state.page == InGamePage.HUD) model.hudPreview?.let {
            if (!Xd.controller) Text(stringResource(R.string.zephyr_ui2_hud_preview), style = XdText.small, color = Xd.colors.fg2)
            HudPreviewBox(it)
        }
        var group: MenuGroup? = null
        state.actions().forEachIndexed { index, action ->
            val here = if (action == InGameAction.MORE_OPTIONS) null else groupOf(state.page, action)
            if (here != null && here !== group) {
                group = here
                here.title?.let { GroupHead(stringResource(it), first = index == 0) }
            }
            val selected = index == state.selected
            val value = model.values[action] ?: MenuValue()
            val rowModifier = if (selected) Modifier.bringIntoViewRequester(selectedRow) else Modifier
            if (action == InGameAction.MORE_OPTIONS) {
                GuestPanelOption(
                    label = if (state.page in state.advanced) stringResource(R.string.menu_fewer_options)
                    else stringResource(R.string.menu_more_options, state.advancedCount()),
                    selected = selected, subtle = true, modifier = rowModifier.padding(top = 4.dp),
                    onClick = { onSelect(index); onAction(action) },
                )
            } else {
                MenuRow(action, value, selected, if (selected) state.chip else -1, rowModifier,
                    onClick = { onSelect(index); onAction(action) },
                    onAdjust = { delta -> onSelect(index); onAdjust(action, delta) },
                    onChoose = { option -> onSelect(index); onChoose(action, option) },
                    onSet = { fraction -> onSelect(index); onSet(action, fraction) },
                    onSetDone = { onSetDone(action) })
            }
        }
        model.notes[state.page].orEmpty().forEach { MenuNote(it, Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(6.dp))
    }
}

/** Round 2: the HUD as set now, over a dark stand-in for the game, with steady sample figures. */
@Composable
private fun HudPreviewBox(preview: HudPreview) {
    val shape = RoundedCornerShape(12.dp)
    val sample = remember {
        xendroid.compose.HudSample(fps = 60.0, frameMs = 16.7, submissionsPerSecond = 60.0, cpu = 42f, gpu = 76, ramUsed = 6_600_000_000L,
            ramTotal = 11_500_000_000L, batteryCelsius = 38.5f, socCelsius = 64f,
            power = xendroid.compose.PowerReading(watts = 5.8, percent = 81, pluggedIn = false, minutesLeft = 142, minutesToFull = null),
            gpuMemory = 1_300_000_000L)
    }
    val graph = remember { List(60) { i -> 57f + 3f * kotlin.math.sin(i / 3f) - if (i in 40..42) 18f else 0f } }
    val scale = preview.scale.coerceIn(0.6f, 1.1f)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(shape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF1C2A33), Color(0xFF3B3022))))
            .padding(6.dp),
    ) {
        if (preview.style.layout == xendroid.compose.core.HudLayout.HORIZONTAL) {
            xendroid.compose.HudBar(sample, preview.detail, preview.metrics, preview.look, preview.style, scale,
                graph = if (preview.style.graph) graph else null,
                modifier = Modifier.align(if (preview.style.edge == xendroid.compose.core.HudEdge.TOP) Alignment.TopCenter else Alignment.BottomCenter))
        } else {
            xendroid.compose.HudView(sample, preview.detail, preview.metrics, preview.look, scale,
                style = preview.style, graph = if (preview.style.graph) graph else null)
        }
    }
}

@Composable
private fun GroupHead(text: String, first: Boolean) {
    Text(text.uppercase(), style = XdText.cardHead.copy(fontSize = 10.5.sp), color = Xd.colors.fg3,
        modifier = Modifier.padding(start = 4.dp, top = if (first) 0.dp else 10.dp, bottom = 2.dp))
}

/** A row of the menu: the frame (the controller's highlight), the title, and its control by kind. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MenuRow(
    action: InGameAction,
    value: MenuValue,
    selected: Boolean,
    /** The chip under the controller's cursor (MULTI); -1 when the row is not selected. */
    chip: Int,
    modifier: Modifier,
    onClick: () -> Unit,
    onAdjust: (Int) -> Unit,
    onChoose: (Int) -> Unit,
    onSet: (Float) -> Unit,
    onSetDone: () -> Unit,
) {
    val c = Xd.colors
    val shape = RoundedCornerShape(12.dp)
    val kind = action.kind
    // Pills, chips and the slider take their own taps; the other rows act on a tap anywhere.
    val tappable = kind == RowKind.TOGGLE || kind == RowKind.CYCLE || kind == RowKind.BUTTON
    Column(
        modifier.fillMaxWidth().heightIn(min = if (c.controller) 46.dp else 56.dp)
            // U02: the controller's highlight is the screen reader's "selected", for the row and its title.
            .semantics(mergeDescendants = true) { this.selected = selected }
            .clip(shape)
            .background(if (selected) c.acc.copy(alpha = 0.2f).compositeOver(c.solid(c.s1)) else c.s1)
            .then(if (selected) Modifier.border(2.dp, c.acc, shape) else if (!c.controller) Modifier.border(1.dp, c.line2, shape) else Modifier)
            .then(if (tappable) Modifier.clickable(enabled = value.enabled, role = Role.Button, onClick = onClick) else Modifier)
            .alpha(if (value.enabled) 1f else 0.45f)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val title = action.title()
        val danger = action == InGameAction.QUIT
        when (kind) {
            RowKind.CHOICE, RowKind.MULTI -> {
                TitleLine(title, value, danger)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    value.options.forEachIndexed { i, option ->
                        val on = if (kind == RowKind.CHOICE) i == value.selected else i in value.checked
                        OptionPill(option, on, check = kind == RowKind.MULTI, cursor = kind == RowKind.MULTI && i == chip,
                            enabled = value.enabled) { onChoose(i) }
                    }
                }
            }
            RowKind.SLIDER -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { TitleLine(title, value.copy(text = null), danger) }
                    value.text?.let { Text(it, style = XdText.mono.copy(fontSize = 12.5.sp), color = c.fg) }
                }
                Slider(value = value.fraction, onValueChange = onSet, onValueChangeFinished = onSetDone, steps = value.steps, enabled = value.enabled,
                    modifier = Modifier.fillMaxWidth().height(if (c.controller) 26.dp else 48.dp),
                    colors = SliderDefaults.colors(thumbColor = c.acc, activeTrackColor = c.acc, inactiveTrackColor = c.solid(c.s4),
                        activeTickColor = c.onAcc.copy(alpha = 0.4f), inactiveTickColor = c.fg3.copy(alpha = 0.4f)))
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    TitleLine(title, value, danger, under = kind != RowKind.CYCLE && kind != RowKind.INFO)
                    if (kind == RowKind.INFO) value.text?.let { Text(it, style = XdText.small, color = c.fg2, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                }
                when (kind) {
                    RowKind.TOGGLE -> XdSwitch(value.on, { onClick() }, enabled = value.enabled)
                    RowKind.CYCLE -> XdStepper(value.text.orEmpty(), { onAdjust(-1) }, { onAdjust(1) }, enabled = value.enabled, minLabelWidth = 72.dp)
                    RowKind.BUTTON -> if (!danger) Icon(XdIcons.chevR, null, Modifier.size(16.dp), tint = c.fg3)
                    else -> {}
                }
            }
        }
        value.note?.let { Text(it, style = XdText.note, color = c.fg3) }
    }
}

/** The title, "this game" when the value is the game's own, and (switches, buttons) the line under it. */
@Composable
private fun TitleLine(title: String, value: MenuValue, danger: Boolean, under: Boolean = false) {
    val c = Xd.colors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = XdText.label, color = if (danger) c.dangerText else c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false))
            if (value.own) Text(stringResource(R.string.menu_tag_game), style = XdText.tiny.copy(fontWeight = FontWeight.Bold), color = c.acc,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(c.acc.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
        if (under) value.text?.let { Text(it, style = XdText.small, color = c.fg3, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

/** A choice of a CHOICE row (a pill) or of a MULTI row (a chip with a check). */
@Composable
private fun OptionPill(text: String, on: Boolean, check: Boolean, cursor: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = Xd.colors
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.then(if (c.controller) Modifier.height(32.dp) else Modifier.heightIn(min = 48.dp)).clip(shape)
            .background(if (on) c.acc.copy(alpha = 0.22f).compositeOver(c.solid(c.s2)) else c.s2)
            .then(if (on || cursor) Modifier.border(if (cursor) 2.dp else 1.dp, if (cursor) c.fg else c.acc.copy(alpha = 0.6f), shape) else Modifier)
            .clickable(enabled = enabled, role = if (check) Role.Checkbox else Role.RadioButton, onClick = onClick)
            .semantics { selected = on }
            .padding(horizontal = 12.dp, vertical = if (c.controller) 0.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (check && on) Icon(XdIcons.check, null, Modifier.size(14.dp), tint = c.acc)
        Text(text, style = XdText.chip, color = if (on) c.fg else c.fg2, maxLines = 1)
    }
}

/** "Kept for Halo 3: 3 changes", over the buttons; a tap goes to the session's options. */
@Composable
private fun SavedLine(model: InGameMenuModel, short: Boolean, onOpen: () -> Unit) {
    val c = Xd.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.acc.copy(alpha = 0.1f)).clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = if (short) 5.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(XdIcons.save, null, Modifier.size(15.dp), tint = c.acc)
        Text(pluralStringResource(R.plurals.menu_saved_line, model.savedChanges, model.savedChanges, model.gameName ?: stringResource(R.string.app_name)),
            style = XdText.small, color = c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(XdIcons.chevR, null, Modifier.size(14.dp), tint = c.fg3)
    }
}

@Composable
private fun QuitConfirm(state: InGameMenuState, selectedRow: BringIntoViewRequester, onQuitChoice: (Boolean) -> Unit) {
    val c = Xd.colors
    Text(stringResource(R.string.menu_exit_question), style = XdText.h2, color = c.fg, modifier = Modifier.padding(top = 8.dp))
    Text(stringResource(R.string.menu_exit_warning), style = XdText.bodySm, color = c.fg2)
    GuestPanelOption(
        label = stringResource(R.string.menu_cancel), selected = state.selected == 0, onClick = { onQuitChoice(false) },
        modifier = Modifier.padding(top = 6.dp).then(if (state.selected == 0) Modifier.bringIntoViewRequester(selectedRow) else Modifier),
    )
    GuestPanelOption(
        label = stringResource(R.string.menu_exit_game), selected = state.selected == 1, onClick = { onQuitChoice(true) }, danger = true,
        modifier = if (state.selected == 1) Modifier.bringIntoViewRequester(selectedRow) else Modifier,
    )
}

@Composable
private fun LogPicker(state: InGameMenuState, model: InGameMenuModel, selectedRow: BringIntoViewRequester, onLogChoice: (Int) -> Unit) {
    val c = Xd.colors
    Text(stringResource(R.string.menu_logs_title), style = XdText.h2, color = c.fg)
    Text(stringResource(R.string.xd_menu_logs_note), style = XdText.note, color = c.fg3)
    val labels = listOf(stringResource(R.string.menu_logs_all)) +
        model.logSessions.map { stringResource(R.string.menu_logs_session, it.label, (it.bytes / 1024).toInt()) } +
        stringResource(R.string.menu_back)
    labels.forEachIndexed { index, label ->
        GuestPanelOption(label, selected = state.selected == index,
            modifier = if (state.selected == index) Modifier.bringIntoViewRequester(selectedRow) else Modifier,
            onClick = { onLogChoice(index) })
    }
}

@Composable
private fun MenuNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = XdText.note, color = Xd.colors.fg3, modifier = modifier)
}

private fun InGamePage.icon(): ImageVector = when (this) {
    InGamePage.GRAPHICS -> XdIcons.image
    InGamePage.SYSTEM -> XdIcons.bolt
    InGamePage.HUD -> XdIcons.hud
    InGamePage.CONTROLS -> XdIcons.gamepad
    InGamePage.SESSION -> XdIcons.play
}

@Composable
private fun InGamePage.label(): String = stringResource(
    when (this) {
        InGamePage.GRAPHICS -> R.string.menu_tab_graphics
        InGamePage.SYSTEM -> R.string.menu_tab_system
        InGamePage.HUD -> R.string.menu_tab_hud
        InGamePage.CONTROLS -> R.string.menu_tab_controls
        InGamePage.SESSION -> R.string.menu_tab_session
    },
)

/** The row's name; its value comes from the host ([MenuValue]). */
@Composable
internal fun InGameAction.title(): String = stringResource(
    when (this) {
        InGameAction.DISPLAY_MODE -> R.string.menu_t_display
        InGameAction.SCALING_EFFECT -> R.string.menu_t_scaling
        InGameAction.ANTIALIASING -> R.string.menu_t_aa
        InGameAction.SHARPNESS -> R.string.menu_t_sharpness
        InGameAction.DITHER -> R.string.menu_t_dither
        InGameAction.COLOR_FILTER -> R.string.menu_t_color_filter
        InGameAction.STRETCH -> R.string.menu_t_stretch
        InGameAction.EXTERNAL_DISPLAY -> R.string.menu_t_tv
        InGameAction.TV_MARGIN -> R.string.menu_tv_margin_title
        InGameAction.DRIVER_INFO -> R.string.menu_group_driver
        InGameAction.WINFG -> R.string.menu_t_winfg
        InGameAction.WINFG_PRESET -> R.string.menu_t_winfg_preset
        InGameAction.LSFG -> R.string.menu_t_lsfg
        InGameAction.LSFG_MULTIPLIER -> R.string.menu_t_lsfg_multiplier
        InGameAction.LSFG_TARGET -> R.string.menu_t_lsfg_target
        InGameAction.IMPORT_LSFG_DLL -> R.string.menu_import_lsfg
        InGameAction.CLEAR_LSFG_CACHE -> R.string.menu_clear_lsfg
        InGameAction.FPS_LIMIT -> R.string.menu_t_fps
        InGameAction.REFRESH_RATE -> R.string.menu_refresh_rate
        InGameAction.SUSTAINED_PERFORMANCE -> R.string.menu_t_sustained
        InGameAction.PERFORMANCE_HINTS -> R.string.menu_hints
        InGameAction.BACKGROUND_POLICY -> R.string.menu_background
        InGameAction.SMOOTH_SHADERS -> R.string.menu_t_smooth_shaders
        InGameAction.MSAA_4X_AS_2X -> R.string.menu_t_msaa_2x
        InGameAction.CUTOUT_TRANSPARENCY -> R.string.menu_t_cutout
        InGameAction.SHADING_RATE -> R.string.menu_t_shading_rate
        InGameAction.PERFORMANCE_HUD -> R.string.menu_t_hud
        InGameAction.HUD_LAYOUT -> R.string.menu_t_hud_layout
        InGameAction.HUD_STYLE -> R.string.menu_t_hud_detail
        InGameAction.HUD_METRICS -> R.string.menu_t_hud_metrics
        InGameAction.HUD_POSITION -> R.string.menu_t_hud_position
        InGameAction.HUD_LOOK -> R.string.menu_t_hud_look
        InGameAction.HUD_SIZE -> R.string.menu_t_hud_size
        InGameAction.HUD_OPACITY -> R.string.menu_t_hud_opacity
        InGameAction.HUD_COLORS -> R.string.menu_t_hud_colors
        InGameAction.TOUCH_CONTROLS -> R.string.menu_t_touch
        InGameAction.CONTROL_STYLE -> R.string.menu_t_control_style
        InGameAction.ADAPTIVE_STICKS -> R.string.menu_t_adaptive
        InGameAction.TOUCH_CAMERA -> R.string.menu_t_touch_camera
        InGameAction.EDIT_TOUCH_LAYOUT -> R.string.menu_t_edit_layout
        InGameAction.SPLIT_SCREEN -> R.string.menu_split_title
        InGameAction.CONTROLLER_RUMBLE -> R.string.menu_rumble
        InGameAction.PHONE_CONTROLLERS -> R.string.menu_phone_controllers
        InGameAction.UNBUFFERED_INPUT -> R.string.menu_unbuffered_title
        InGameAction.GYRO_CAMERA -> R.string.menu_gyro_camera
        InGameAction.GYRO_AIM -> R.string.menu_gyro_aim_title
        InGameAction.GYRO_SENSITIVITY -> R.string.menu_t_gyro_sensitivity
        InGameAction.GYRO_CALIBRATE -> R.string.menu_t_gyro_calibrate
        InGameAction.VOLUME -> R.string.menu_t_volume
        InGameAction.MUTE -> R.string.menu_t_mute
        InGameAction.PAUSE_ON_OPEN -> R.string.menu_t_pause
        InGameAction.AUTO_SAVE -> R.string.menu_t_autosave
        InGameAction.UNDO_SESSION -> R.string.menu_t_undo
        InGameAction.MAKE_GLOBAL -> R.string.menu_t_global
        InGameAction.MARK_SCENE -> R.string.menu_t_mark
        InGameAction.SHARE_LOGS -> R.string.menu_share_logs
        InGameAction.SCREENSHOT -> R.string.menu_t_screenshot
        InGameAction.RESUME -> R.string.menu_continue
        InGameAction.QUIT -> R.string.menu_exit_game
        InGameAction.MORE_OPTIONS -> R.string.menu_fewer_options
    },
)

