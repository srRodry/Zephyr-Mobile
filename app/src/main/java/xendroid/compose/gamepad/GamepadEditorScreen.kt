package xendroid.compose.gamepad

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import xendroid.compose.R
import xendroid.compose.ui.controls.TouchOptionRows
import xendroid.compose.ui.design.CHints
import xendroid.compose.ui.design.LocalSwapConfirm
import xendroid.compose.ui.design.LocalXdToast
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdButtonSize
import xendroid.compose.ui.design.XdChip
import xendroid.compose.ui.design.XdHint
import xendroid.compose.ui.design.XdIconButton
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdSegmented
import xendroid.compose.ui.design.XdSheet
import xendroid.compose.ui.design.XdStepper
import xendroid.compose.ui.design.XdSwitch
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.XdToastHost
import xendroid.compose.ui.design.XdToastState
import xendroid.compose.ui.design.zephyrBackdrop
import xendroid.compose.ui.design.zephyrSurface

/** Snap grid: cells along the SHORTER screen edge. The longer edge gets proportionally more
 *  cells of the same pixel pitch, so the cells are square. Higher = smaller (finer) cells. */
const val EDITOR_GRID_STEPS = 20

/** Rounds a fraction to the [EDITOR_GRID_STEPS] grid (a visible, coarse grid so "Snap"
 *  actually aligns controls). steps<=0 is identity. */
fun snapFrac(f: Float, steps: Int = EDITOR_GRID_STEPS): Float {
    if (steps <= 0) return f
    return (f * steps).roundToInt() / steps.toFloat()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun GamepadEditorScreen(controller: GamepadController, onDone: () -> Unit, inGame: Boolean = false,
                        titleId: String? = null, gameName: String? = null) {
    // 15l: the controls show the size they have over the game, so the app's UI scale stays out.
    xendroid.compose.ui.theme.NaturalSize { GamepadEditorContent(controller, onDone, inGame, titleId, gameName) }
}

/**
 * The touch layout editor: the controls over a dark frame (the paused game in a game), the grid,
 * the chosen control's panel (size, dead zone, shown or not) and a toolbar that folds away.
 * Every change can be undone; nothing is kept until "Save and exit". With a controller: LB/RB pick
 * a control, the D-pad moves it a cell, A opens its panel, X the layouts, Y the general options,
 * Start saves and Select folds the toolbar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GamepadEditorContent(controller: GamepadController, onDone: () -> Unit, inGame: Boolean,
                                 titleId: String?, gameName: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = remember { XdToastState() }
    val c = Xd.colors
    val persisted by controller.config.collectAsState(initial = GamepadConfigDto())
    var working by remember(persisted) { mutableStateOf(persisted) }
    val history = remember(persisted) { EditHistory<GamepadConfigDto>() }
    var canUndo by remember(persisted) { mutableStateOf(false) }
    var canRedo by remember(persisted) { mutableStateOf(false) }
    val startLandscape = LocalConfiguration.current.orientation != Configuration.ORIENTATION_PORTRAIT
    var landscape by remember { mutableStateOf(startLandscape) }
    var selected by remember { mutableStateOf<ControlId?>(null) }
    var snap by remember { mutableStateOf(true) }
    // 15r: move and resize the selected control's group (face buttons, trigger + bumper…) together.
    var grouped by remember { mutableStateOf(false) }
    var globalsOpen by remember { mutableStateOf(false) }
    var collapsed by remember { mutableStateOf(false) }
    var layoutsOpen by remember { mutableStateOf(false) }
    // U06: with a game running, edits can go to that game's own layout instead of the shared one.
    val perGame = titleId != null && working.hasOwnLayout(titleId, landscape)
    val editScope = if (perGame) titleId else null
    val base = remember(working, landscape, editScope) { controller.controlsFor(working, landscape, editScope) }
    // Un-snapped live fraction of the control being dragged. The drag accumulates raw deltas
    // here (NEVER snapped per frame) so it tracks the finger 1:1; snapFrac is applied once on
    // drag-end. Reset explicitly on drag-end. Null when no drag is active.
    var dragRaw by remember { mutableStateOf<Pair<ControlId, Offset>?>(null) }
    var editorSize by remember { mutableStateOf(IntSize.Zero) }
    // Square snap grid: per-axis cell counts from the actual screen size (EDITOR_GRID_STEPS on the
    // shorter edge, same pixel pitch on the longer edge), so the cells are square in any orientation.
    val (stepsX, stepsY) = remember(editorSize) {
        val w = editorSize.width; val h = editorSize.height
        if (w == 0 || h == 0) EDITOR_GRID_STEPS to EDITOR_GRID_STEPS
        else {
            val cell = minOf(w, h).toFloat() / EDITOR_GRID_STEPS
            (w / cell).roundToInt().coerceAtLeast(2) to (h / cell).roundToInt().coerceAtLeast(2)
        }
    }

    val view = LocalView.current
    val activity = remember(view) { view.context.findActivity() }
    // Snapshot the orientation BEFORE the editor forces it (remember{} runs during composition,
    // before the effects below), so on exit we RESTORE it rather than hard-locking landscape --
    // otherwise the library would stay landscape even when the device is held in portrait.
    val enterOrientation = remember {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    // The Landscape/Portrait toggle FORCES the screen orientation, so each layout previews the way
    // it appears in-game. MainActivity declares orientation in configChanges, so this rotates
    // WITHOUT recreating the activity (no lost edits).
    LaunchedEffect(landscape, activity) {
        activity?.requestedOrientation = if (landscape)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    // Edge-to-edge immersive while editing, so the overlay matches the fullscreen game exactly
    // (true WYSIWYG) and a control can be placed at the very top/bottom edge. Orientation + bars
    // restored on exit.
    DisposableEffect(Unit) {
        val insets = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val prevBehavior = insets?.systemBarsBehavior
        insets?.let {
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.requestedOrientation = enterOrientation
            insets?.let {
                if (inGame) it.hide(WindowInsetsCompat.Type.systemBars())
                else it.show(WindowInsetsCompat.Type.systemBars())
                if (prevBehavior != null) it.systemBarsBehavior = prevBehavior
            }
        }
    }

    /** A change that can be undone. */
    fun commit(next: GamepadConfigDto) {
        if (next == working) return
        history.push(working)
        canUndo = history.canUndo
        canRedo = history.canRedo
        working = next
    }
    fun undo() {
        history.undo(working)?.let { working = it }
        canUndo = history.canUndo
        canRedo = history.canRedo
    }
    fun redo() {
        history.redo(working)?.let { working = it }
        canUndo = history.canUndo
        canRedo = history.canRedo
    }
    fun layoutWith(cfg: GamepadConfigDto, transform: (List<OnScreenControl>) -> List<OnScreenControl>): GamepadConfigDto =
        // U06: controls and fields this build does not know stay in the stored layout.
        cfg.withLayout(editScope, landscape, transform(base).toDto().preserving(cfg.layoutFor(editScope, landscape)))
    fun mutateControls(transform: (List<OnScreenControl>) -> List<OnScreenControl>) = commit(layoutWith(working, transform))
    fun resize(id: ControlId, s: Float) = mutateControls { list ->
        if (grouped) ControlGroups.resize(list, id, s)
        else list.map { if (it.id == id) it.withLayout(s = s.coerceIn(ControlGroups.SCALES.start, ControlGroups.SCALES.endInclusive)) else it }
    }
    fun place(id: ControlId, x: Float, y: Float) = mutateControls { list ->
        if (grouped) ControlGroups.move(list, id, x, y) else list.map { if (it.id == id) it.withLayout(x = x, y = y) else it }
    }
    /** One grid cell in a direction (the D-pad of a controller). */
    fun nudge(dx: Int, dy: Int) {
        val id = selected ?: base.firstOrNull()?.id?.also { selected = it } ?: return
        val control = base.firstOrNull { it.id == id } ?: return
        place(id,
            (snapFrac(control.xFraction, stepsX) + dx / stepsX.toFloat()).coerceIn(1f / stepsX, (stepsX - 1f) / stepsX),
            (snapFrac(control.yFraction, stepsY) + dy / stepsY.toFloat()).coerceIn(1f / stepsY, (stepsY - 1f) / stepsY))
    }
    fun pickNext(step: Int) {
        if (base.isEmpty()) return
        val i = base.indexOfFirst { it.id == selected }
        selected = base[if (i < 0) 0 else (i + step + base.size) % base.size].id
    }
    fun save() {
        scope.launch {
            try { controller.save(working); onDone() }
            catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                toast.show(context.getString(R.string.ge_save_failed))
            }
        }
    }

    val controllerMode = c.controller
    val rootFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    var panelFocused by remember { mutableStateOf(false) }
    var barFocused by remember { mutableStateOf(false) }
    val inUi = panelFocused || barFocused
    LaunchedEffect(controllerMode) { if (controllerMode) runCatching { rootFocus.requestFocus() } }
    BackHandler {
        if (inUi) runCatching { rootFocus.requestFocus() } else onDone()
    }

    CompositionLocalProvider(LocalXdToast provides toast) {
    // A dark frame stands for the game (in a game, the paused frame shows through it).
    Box(
        Modifier.fillMaxSize().then(if (inGame) Modifier.background(Color(0xFF06160F).copy(alpha = .52f))
            else Modifier.zephyrBackdrop(animated = false))
            .onSizeChanged { editorSize = it }
            .focusRequester(rootFocus)
            .focusable(enabled = controllerMode)
            .onPreviewKeyEvent { e ->
                if (!controllerMode || e.type != KeyEventType.KeyDown || layoutsOpen || globalsOpen) return@onPreviewKeyEvent false
                when (e.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_BUTTON_L1 -> { pickNext(-1); runCatching { rootFocus.requestFocus() }; true }
                    KeyEvent.KEYCODE_BUTTON_R1 -> { pickNext(1); runCatching { rootFocus.requestFocus() }; true }
                    KeyEvent.KEYCODE_BUTTON_X -> { layoutsOpen = true; true }
                    KeyEvent.KEYCODE_BUTTON_Y -> { globalsOpen = true; true }
                    KeyEvent.KEYCODE_BUTTON_START -> { save(); true }
                    KeyEvent.KEYCODE_BUTTON_SELECT -> { collapsed = !collapsed; true }
                    KeyEvent.KEYCODE_DPAD_UP -> if (inUi) false else { nudge(0, -1); true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> if (inUi) false else { nudge(0, 1); true }
                    KeyEvent.KEYCODE_DPAD_LEFT -> if (inUi) false else { nudge(-1, 0); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> if (inUi) false else { nudge(1, 0); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A -> if (inUi) false else {
                        if (selected == null) pickNext(1)
                        runCatching { panelFocus.requestFocus() }
                        true
                    }
                    else -> false
                }
            },
    ) {
        // Full-screen, edge-to-edge WYSIWYG overlay: controls can be dragged ANYWHERE on the
        // screen (no top-bar inset). Pass the square-grid cell counts only while Snap is on.
        GamepadOverlay(
            controls = base, opacity = working.globals.opacity.coerceAtLeast(0.5f), modifier = Modifier.fillMaxSize(),
            onKeyEvent = { _, _, _ -> }, editMode = true, style = ControlStyle.parse(working.globals.style),
            gridStepsX = if (snap) stepsX else 0, gridStepsY = if (snap) stepsY else 0,
            selectedId = selected, onSelect = { selected = it },
            onTranslate = { id, dx, dy ->
                // Seed the raw accumulator from the control's CURRENT committed fraction on the
                // first delta of this drag (the state before it is what Undo brings back), then
                // advance it by the raw finger delta. Never snap here -- snapping the running value
                // re-rounds sub-half-step deltas back to the same grid cell and freezes the control.
                val first = dragRaw?.first != id
                val cur = dragRaw?.takeIf { it.first == id }?.second
                    ?: base.firstOrNull { it.id == id }?.let { Offset(it.xFraction, it.yFraction) }
                    ?: return@GamepadOverlay
                if (first) {
                    history.push(working)
                    canUndo = history.canUndo
                    canRedo = history.canRedo
                }
                val nx = (cur.x + dx).coerceIn(0.02f, 0.98f)
                val ny = (cur.y + dy).coerceIn(0.02f, 0.98f)
                dragRaw = id to Offset(nx, ny)
                working = layoutWith(working) { list ->
                    if (grouped) ControlGroups.move(list, id, nx, ny)
                    else list.map { if (it.id == id) it.withLayout(x = nx, y = ny) else it }
                }
            },
            onDragEnd = { id ->
                // Commit the grid-snap once, on release, when Snap is on.
                val raw = dragRaw?.takeIf { it.first == id }?.second
                dragRaw = null
                if (snap && raw != null) {
                    // Snap to the nearest IN-BOUNDS square-grid line: real grid lines, fully on-screen.
                    val nx = snapFrac(raw.x, stepsX).coerceIn(1f / stepsX, (stepsX - 1f) / stepsX)
                    val ny = snapFrac(raw.y, stepsY).coerceIn(1f / stepsY, (stepsY - 1f) / stepsY)
                    working = layoutWith(working) { list ->
                        // 15r: the group follows the dragged control onto the grid, keeping its shape.
                        if (grouped) ControlGroups.move(list, id, nx, ny)
                        else list.map { if (it.id == id) it.withLayout(x = nx, y = ny) else it }
                    }
                }
            },
            onScale = { id, f -> base.firstOrNull { it.id == id }?.let { resize(id, it.scale * f) } },
        )

        val chosen = base.firstOrNull { it.id == selected }
        if (chosen == null) {
            Text(stringResource(if (controllerMode) R.string.xd_te_tip_pad else R.string.xd_te_tip_touch), style = XdText.small, color = c.fg2,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp).clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 14.dp, vertical = 7.dp))
        } else {
            val names = if (grouped) ControlGroups.of(chosen.id).filter { it != chosen.id }.map { controlName(it) } else emptyList()
            ControlPanel(chosen, names, panelFocus,
                onSize = { d -> resize(chosen.id, ((chosen.scale * 100).roundToInt() + d) / 100f) },
                onDeadZone = { d ->
                    val now = chosen.deadZoneOrNull ?: return@ControlPanel
                    mutateControls { list -> list.map { if (it.id == chosen.id) it.withLayout(deadZone = ((now * 100).roundToInt() + d) / 100f) else it } }
                },
                onVisible = { mutateControls { list -> list.map { if (it.id == chosen.id) it.withLayout(vis = !it.visible) else it } } },
                onClose = { selected = null; if (controllerMode) runCatching { rootFocus.requestFocus() } },
                // Between the triggers, where a layout has the least to cover.
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)
                    .onFocusChanged { panelFocused = it.hasFocus },
            )
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (collapsed) {
                Row(
                    Modifier.padding(10.dp).clip(RoundedCornerShape(50)).background(c.solid(c.sheet).copy(alpha = 0.92f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                        .clickable(role = Role.Button) { collapsed = false }.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(XdIcons.move, null, Modifier.size(16.dp), tint = c.acc)
                    Text(stringResource(R.string.xd_te_tools), style = XdText.labelSm, color = c.fg)
                }
            } else {
                FlowRow(
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp).widthIn(max = 980.dp)
                        .zephyrSurface(RoundedCornerShape(16.dp))
                        .onFocusChanged { barFocused = it.hasFocus }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val mid = Modifier.align(Alignment.CenterVertically)
                    XdSegmented(listOf(true to stringResource(R.string.ge_landscape), false to stringResource(R.string.ge_portrait)), landscape,
                        { landscape = it }, mid, compact = true)
                    XdChip(stringResource(R.string.ge_snap), snap, { snap = !snap }, mid)
                    XdChip(stringResource(R.string.ge_group), grouped, { grouped = !grouped }, mid)
                    if (titleId != null) XdChip(gameName?.let { stringResource(R.string.xd_te_only_game, it) } ?: stringResource(R.string.ge_this_game), perGame, {
                        // On: the game's own layout starts from what it plays with now; off: back to the shared one.
                        commit(if (perGame) working.withoutOwnLayout(titleId, landscape)
                            else working.withLayout(titleId, landscape, base.toDto().preserving(working.layoutFor(null, landscape))))
                    }, mid)
                    Bar(mid)
                    XdIconButton(XdIcons.reset, stringResource(R.string.xd_undo), ::undo, mid, enabled = canUndo, size = 34.dp)
                    XdButton(stringResource(R.string.zephyr_editor_redo), ::redo, modifier = mid,
                        enabled = canRedo, kind = XdButtonKind.GHOST, size = XdButtonSize.SM)
                    XdButton(stringResource(R.string.lp_title), { layoutsOpen = true }, mid, kind = XdButtonKind.GHOST, size = XdButtonSize.SM)
                    XdButton(stringResource(R.string.ge_globals), { globalsOpen = true }, mid, kind = XdButtonKind.GHOST, size = XdButtonSize.SM)
                    XdButton(stringResource(R.string.ge_reset), {
                        commit(working.withLayout(editScope, landscape, defaultLayout(landscape).toDto().preserving(working.layoutFor(editScope, landscape))))
                        toast.show(context.getString(R.string.xd_te_reset_done))
                    }, mid, kind = XdButtonKind.GHOST, size = XdButtonSize.SM)
                    XdIconButton(XdIcons.chevD, stringResource(R.string.xd_te_collapse), { collapsed = true }, mid, size = 34.dp)
                    Bar(mid)
                    XdButton(stringResource(R.string.common_cancel), onDone, mid, kind = XdButtonKind.GHOST, size = XdButtonSize.SM)
                    XdButton(stringResource(R.string.ge_save_quit), ::save, mid, kind = XdButtonKind.PRIMARY, size = XdButtonSize.SM)
                }
            }
            if (controllerMode) {
                val swap = LocalSwapConfirm.current
                CHints(listOf(
                    XdHint("LB/RB", stringResource(R.string.xd_te_hint_pick)),
                    XdHint(if (swap) "B" else "A", stringResource(R.string.xd_te_hint_settings)),
                    XdHint("X", stringResource(R.string.lp_title), { layoutsOpen = true }),
                    XdHint("Y", stringResource(R.string.ge_globals), { globalsOpen = true }),
                    XdHint("⧉", stringResource(R.string.xd_te_hint_tools), { collapsed = !collapsed }),
                    XdHint("≡", stringResource(R.string.ge_save_quit), ::save),
                    XdHint(if (swap) "A" else "B", stringResource(R.string.common_cancel), onDone),
                ))
            }
        }
        XdToastHost(toast, Modifier.align(Alignment.TopCenter).padding(top = 52.dp))

        if (layoutsOpen) {
            LayoutPresetsDialog(config = working, editScope = editScope, landscape = landscape,
                onChange = ::commit, onMessage = { toast.show(it) }, onDismiss = { layoutsOpen = false })
        }
        if (globalsOpen) {
            XdSheet(onDismiss = { globalsOpen = false }, title = stringResource(R.string.ge_globals), wide = true,
                subtitle = stringResource(R.string.xd_te_globals_sub),
                actions = { XdButton(stringResource(R.string.xd_done), { globalsOpen = false }, kind = XdButtonKind.PRIMARY) }) {
                TouchOptionRows(working.globals, onGlobals = { change -> commit(working.copy(globals = change(working.globals))) })
            }
        }
    }
    }
}

@Composable
private fun Bar(modifier: Modifier = Modifier) = Box(modifier.width(1.dp).height(22.dp).background(Color.White.copy(alpha = 0.12f)))

/** The chosen control: its size, its dead zone (d-pad and sticks) and whether it is shown. */
@Composable
private fun ControlPanel(
    control: OnScreenControl,
    group: List<String>,
    firstFocus: FocusRequester,
    onSize: (Int) -> Unit,
    onDeadZone: (Int) -> Unit,
    onVisible: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val c = Xd.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.width(250.dp).zephyrSurface(shape)
            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(controlName(control.id), style = XdText.label, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            XdIconButton(XdIcons.x, stringResource(R.string.xd_close), onClose, size = 34.dp)
        }
        val size = (control.scale * 100).roundToInt()
        PanelRow(stringResource(R.string.ge_size)) {
            XdStepper("$size%", { onSize(-10) }, { onSize(10) }, canPrevious = size > 50, canNext = size < 300,
                minLabelWidth = 52.dp, modifier = Modifier.focusRequester(firstFocus))
        }
        val range = when (control) {
            is OnScreenControl.Dpad -> OnScreenControl.Dpad.DEAD_ZONES
            is OnScreenControl.AnalogStick -> OnScreenControl.AnalogStick.DEAD_ZONES
            else -> null
        }
        val dz = control.deadZoneOrNull
        if (range != null && dz != null) {
            val now = (dz * 100).roundToInt()
            PanelRow(stringResource(R.string.ge_dead_zone)) {
                XdStepper("$now%", { onDeadZone(-5) }, { onDeadZone(5) },
                    canPrevious = now > (range.start * 100).roundToInt(), canNext = now < (range.endInclusive * 100).roundToInt(), minLabelWidth = 52.dp)
            }
        }
        PanelRow(stringResource(R.string.xd_te_visible)) { XdSwitch(control.visible, { onVisible() }) }
        if (group.isNotEmpty()) Text(stringResource(R.string.xd_te_group_note, group.joinToString(", ")), style = XdText.small, color = c.fg3,
            modifier = Modifier.padding(end = 6.dp))
    }
}

@Composable
private fun PanelRow(label: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = XdText.bodySm, color = Xd.colors.fg2, modifier = Modifier.weight(1f))
        control()
    }
}

/** A touch control's name as the player knows it. */
@Composable
internal fun controlName(id: ControlId): String = when (id) {
    ControlId.DPAD -> stringResource(R.string.xd_te_ctl_dpad)
    ControlId.BACK -> stringResource(R.string.xd_te_ctl_back)
    ControlId.START -> stringResource(R.string.xd_te_ctl_start)
    ControlId.LEFT_STICK -> stringResource(R.string.xd_te_ctl_ls)
    ControlId.RIGHT_STICK -> stringResource(R.string.xd_te_ctl_rs)
    ControlId.LS_CLICK -> stringResource(R.string.xd_te_ctl_l3)
    ControlId.RS_CLICK -> stringResource(R.string.xd_te_ctl_r3)
    else -> id.name
}

/** 15c: the name of a split-screen mode, for the editor and the in-game menu. */
@androidx.annotation.StringRes
fun splitScreenLabel(mode: SplitScreenMode): Int = when (mode) {
    SplitScreenMode.OFF -> R.string.ge_split_off
    SplitScreenMode.TABLETOP -> R.string.ge_split_tabletop
    SplitScreenMode.ALWAYS -> R.string.ge_split_always
}
