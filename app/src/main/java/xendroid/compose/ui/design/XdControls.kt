package xendroid.compose.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A 2 dp ring in the accent while the element has key/controller focus (never on touch). */
fun Modifier.focusRing(shape: Shape, color: Color? = null, width: Dp = 2.dp): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val ring = color ?: Xd.colors.acc
    val w = if (Xd.controller) width + 1.dp else width
    this
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .then(if (focused) Modifier.border(w, ring, shape) else Modifier)
}

enum class XdButtonKind { PRIMARY, SECONDARY, GHOST, DANGER }
enum class XdButtonSize { SM, MD, LG }

/** Zephyr 2.0 actions use a restrained rectangular radius; chips remain deliberately pill-shaped. */
internal val LocalXdButtonShape = staticCompositionLocalOf<Shape> { ZephyrActionShape }

@Composable
fun XdButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: XdButtonKind = XdButtonKind.SECONDARY,
    size: XdButtonSize = XdButtonSize.MD,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = Xd.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val (bg, fg) = when (kind) {
        XdButtonKind.PRIMARY -> c.action to c.onAction
        XdButtonKind.SECONDARY -> (if (pressed) c.s4 else c.s3) to c.fg
        XdButtonKind.GHOST -> (if (pressed) c.s2 else Color.Transparent) to c.fg
        XdButtonKind.DANGER -> c.nothing.copy(alpha = 0.22f) to c.dangerText
    }
    val height = when (size) {
        XdButtonSize.SM -> if (c.controller) 36.dp else 32.dp
        XdButtonSize.MD -> if (c.controller) 42.dp else 38.dp
        XdButtonSize.LG -> if (c.controller) 48.dp else 44.dp
    }
    val pad = when (size) { XdButtonSize.SM -> 12.dp; XdButtonSize.MD -> 15.dp; XdButtonSize.LG -> 20.dp }
    val style = when (size) {
        XdButtonSize.SM -> XdText.button.copy(fontSize = 12.5.sp)
        XdButtonSize.MD -> XdText.button
        XdButtonSize.LG -> XdText.button.copy(fontSize = 15.sp)
    }
    val shape = LocalXdButtonShape.current
    Row(
        modifier
            // An accent ring would vanish on the accent fill of a primary button.
            .focusRing(shape, color = if (kind == XdButtonKind.PRIMARY) c.fg else null)
            .defaultMinSize(minHeight = height)
            .height(height)
            .clip(shape)
            .background(bg)
            .then(if (kind == XdButtonKind.GHOST) Modifier.border(1.dp, c.line2, shape) else Modifier)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.42f)
            .padding(horizontal = pad),
        horizontalArrangement = Arrangement.spacedBy(if (size == XdButtonSize.SM) 6.dp else 8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(if (size == XdButtonSize.SM) 16.dp else 18.dp), tint = fg)
        Text(text, style = style, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Round icon button (48 dp touch target); [on] paints it the favourite yellow. */
@Composable
fun XdIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    on: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 48.dp,
    tint: Color? = null,
) {
    val c = Xd.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .focusRing(CircleShape)
            .size(size)
            .clip(CircleShape)
            .background(if (pressed) c.s2 else Color.Transparent)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .alpha(if (enabled) 1f else 0.42f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint ?: if (on) Color(0xFFF3CF55) else c.fg2)
    }
}

/** Filter chip: label, an optional count, pressed state in the accent. */
@Composable
fun XdChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    icon: ImageVector? = null,
) {
    val c = Xd.colors
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .focusRing(shape)
            .height(if (c.controller) 34.dp else 30.dp)
            .clip(shape)
            .background(if (selected) c.acc.copy(alpha = 0.2f).compositeOver(c.solid(c.s2)) else c.s2)
            .then(if (selected) Modifier.border(1.dp, c.acc.copy(alpha = 0.55f), shape) else Modifier)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(15.dp), tint = if (selected) c.acc else c.fg3)
        Text(text, style = XdText.chip, color = if (selected) c.fg else c.fg2, maxLines = 1)
        if (count != null) Text(count.toString(), style = XdText.monoNum, color = if (selected) c.acc else c.fg3)
    }
}

/**
 * Segmented control (two to six short options); [accent] fills the chosen one in the accent.
 * [counts], one per option, show after the labels ("Essential 24").
 */
@Composable
fun <T> XdSegmented(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
    counts: List<Int>? = null,
) {
    val c = Xd.colors
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.s2)
            .padding(3.dp)
            .alpha(if (enabled) 1f else 0.4f),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, (value, label) ->
            val on = value == selected
            val shape = ZephyrActionShape
            Box(
                Modifier
                    .focusRing(shape)
                    .height(if (compact) 26.dp else if (c.controller) 34.dp else 30.dp)
                    .clip(shape)
                    .background(if (on) (if (accent) c.acc else c.s4) else Color.Transparent)
                    .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(value) }
                    .semantics { this.selected = on }
                    .padding(horizontal = if (compact) 8.dp else 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        label, style = XdText.chip.copy(fontSize = if (compact) 12.sp else 12.5.sp),
                        color = if (on) (if (accent) c.onAcc else c.fg) else c.fg2, maxLines = 1,
                    )
                    counts?.getOrNull(i)?.let { n ->
                        Text(n.toString(), style = XdText.monoNum, maxLines = 1,
                            color = if (on) (if (accent) c.onAcc else c.acc) else c.fg3)
                    }
                }
            }
        }
    }
}

/** On/off switch (46×28) with the app's colours. */
@Composable
fun XdSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val c = Xd.colors
    val track by animateColorAsState(if (checked) c.acc else c.s4, label = "track")
    val thumb by animateColorAsState(if (checked) c.onAcc else Color(0xFFCFD8D2), label = "thumb")
    val x by animateDpAsState(if (checked) 21.dp else 3.dp, label = "x")
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .focusRing(shape)
            .size(46.dp, 28.dp)
            .clip(shape)
            .background(track)
            .then(
                if (onCheckedChange != null) Modifier.toggleable(checked, enabled = enabled, role = Role.Switch) { onCheckedChange(it) }
                else Modifier
            )
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        Box(Modifier.offset(x = x, y = 3.dp).size(22.dp).clip(CircleShape).background(thumb))
    }
}

/** The search box of the library and the settings lists. */
@Composable
fun XdSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    height: Dp = 38.dp,
) {
    val c = Xd.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .height(height)
            .clip(shape)
            .background(c.s2)
            .then(if (focused) Modifier.border(1.5.dp, c.acc, shape) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(XdIcons.search, null, Modifier.size(17.dp), tint = c.fg3)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = XdText.body, color = c.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = XdText.body.copy(color = c.fg),
                cursorBrush = SolidColor(c.acc),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            )
        }
        if (value.isNotEmpty()) {
            Icon(XdIcons.x, null, Modifier.size(16.dp).clip(CircleShape).clickable { onValueChange("") }, tint = c.fg3)
        }
    }
}

/** A one-line text input (config values, names); [mono] for paths and arguments. */
@Composable
fun XdTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    mono: Boolean = true,
    enabled: Boolean = true,
    width: Dp = 200.dp,
) {
    val c = Xd.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val style = if (mono) XdText.mono.copy(fontSize = 13.sp) else XdText.body
    Box(
        modifier
            .widthIn(max = width)
            .height(34.dp)
            .clip(shape)
            .background(c.s3)
            .then(if (focused) Modifier.border(1.5.dp, c.acc, shape) else Modifier)
            .padding(horizontal = 11.dp)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, style = style, color = c.fg3, maxLines = 1)
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true, enabled = enabled,
            textStyle = style.copy(color = c.fg), cursorBrush = SolidColor(c.acc),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        )
    }
}

/** A drop-down choice: the value in a box, the options in a menu. */
@Composable
fun <T> XdSelect(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
    maxWidth: Dp = 220.dp,
) {
    val c = Xd.colors
    var open by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == selected }?.second ?: placeholder ?: selected?.toString().orEmpty()
    val shape = RoundedCornerShape(10.dp)
    Box(modifier) {
        Row(
            Modifier
                .focusRing(shape)
                .widthIn(max = maxWidth)
                .height(if (c.controller) 38.dp else 34.dp)
                .clip(shape)
                .background(c.s3)
                .clickable(enabled = enabled, role = Role.DropdownList) { open = true }
                .alpha(if (enabled) 1f else 0.4f)
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(label, style = XdText.labelSm, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false))
            Icon(XdIcons.chevD, null, Modifier.size(14.dp), tint = c.fg2)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.background(c.solid(c.sheet))) {
            for ((value, text) in options) {
                DropdownMenuItem(
                    text = { Text(text, style = XdText.body, color = if (value == selected) c.acc else c.fg) },
                    onClick = { open = false; onSelect(value) },
                    trailingIcon = if (value == selected) ({ Icon(XdIcons.check, null, Modifier.size(16.dp), tint = c.acc) }) else null,
                )
            }
        }
    }
}

/** ◀ value ▶ for a short numeric range (or the controller rows). */
@Composable
fun XdStepper(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    canPrevious: Boolean = true,
    canNext: Boolean = true,
    minLabelWidth: Dp = 64.dp,
) {
    val c = Xd.colors
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).background(c.s3).padding(2.dp).alpha(if (enabled) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        StepArrow(XdIcons.chevL, enabled && canPrevious, onPrevious)
        Text(label, style = XdText.labelSm, color = c.fg, textAlign = TextAlign.Center, maxLines = 1,
            modifier = Modifier.widthIn(min = minLabelWidth))
        StepArrow(XdIcons.chevR, enabled && canNext, onNext)
    }
}

@Composable
private fun StepArrow(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val c = Xd.colors
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.focusRing(shape).size(30.dp).clip(shape).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.35f),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(16.dp), tint = c.fg2) }
}

/** A slider with its value written beside it. */
@Composable
fun XdSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    width: Dp = 150.dp,
) {
    val c = Xd.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Slider(
            value = value, onValueChange = onValueChange, valueRange = range, steps = steps, enabled = enabled,
            onValueChangeFinished = onValueChangeFinished, modifier = Modifier.width(width),
            colors = SliderDefaults.colors(
                thumbColor = c.acc, activeTrackColor = c.acc, inactiveTrackColor = c.solid(c.s4),
                activeTickColor = c.onAcc.copy(alpha = 0.4f), inactiveTickColor = c.fg3.copy(alpha = 0.4f),
            ),
        )
        Text(label, style = XdText.mono.copy(fontSize = 12.5.sp), color = c.fg, textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 46.dp))
    }
}

/** A horizontally scrolling row of chips (the library filters). */
@Composable
fun XdChipRow(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(0.dp), content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A thin vertical separator between chip groups. */
@Composable
fun XdChipSeparator() {
    Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(20.dp).background(Xd.colors.line2))
}

/** A text link in the accent ("Ver detalhes", "Limpar filtros"). */
@Composable
fun XdLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: TextStyle = XdText.labelSm) {
    val c = Xd.colors
    val shape = RoundedCornerShape(6.dp)
    Text(
        text, style = style, color = c.acc,
        modifier = modifier.focusRing(shape).clip(shape).clickable(role = Role.Button, onClick = onClick).padding(vertical = 2.dp),
    )
}

/** Content colour for a block, as Material's [LocalContentColor]. */
@Composable
fun WithContentColor(color: Color, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalContentColor provides color, content = content)

/** Minimum height of a list row: larger touch targets in controller mode. */
@Composable
fun rowMinHeight(): Dp = if (Xd.controller) 54.dp else 44.dp
