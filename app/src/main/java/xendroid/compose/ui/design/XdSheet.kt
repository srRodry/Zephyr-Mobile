package xendroid.compose.ui.design

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.delay

/**
 * The app's modal sheet: from the bottom in portrait, centred in landscape, on a dark scrim.
 * [title] and [subtitle] head it; [actions] (buttons, right-aligned) close it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun XdSheet(
    onDismiss: () -> Unit,
    title: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    wide: Boolean = false,
    dismissible: Boolean = true,
    closeButton: Boolean = true,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
            dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible,
        ),
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        XdDialogEdgeToEdge()
        val c = Xd.colors
        // Round 2: with a controller the sheet's first control has the focus as it opens, so A
        // works at once (nothing had it, and the first press only woke the focus up). A sheet
        // that gives the focus itself (the key capture) keeps it.
        val focus = androidx.compose.ui.platform.LocalFocusManager.current
        val inside = androidx.compose.runtime.remember { java.util.concurrent.atomic.AtomicBoolean(false) }
        if (c.controller) androidx.compose.runtime.LaunchedEffect(Unit) {
            androidx.compose.runtime.withFrameNanos { }
            androidx.compose.runtime.withFrameNanos { }
            if (!inside.get()) focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Next)
        }
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.58f))
                .clickable(remember = MutableInteractionSource(), indication = null) { if (dismissible) onDismiss() },
        ) {
            val landscape = maxWidth > maxHeight
            val maxW = if (wide) 660.dp else 560.dp
            val shape = if (landscape) RoundedCornerShape(20.dp) else RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
            Column(
                modifier
                    .align(if (landscape) Alignment.Center else Alignment.BottomCenter)
                    .then(if (landscape) Modifier.padding(horizontal = 24.dp) else Modifier)
                    .widthIn(max = maxW)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * if (landscape) 0.92f else 0.9f)
                    .clip(shape)
                    .background(c.solid(c.sheet))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
                    .clickable(remember = MutableInteractionSource(), indication = null) {}
                    .onFocusChanged { inside.set(it.hasFocus) }
                    // Used by touch, nothing takes the focus as the sheet opens (a text field would
                    // pop the keyboard up); with a controller the first control does.
                    .then(if (c.controller) Modifier else Modifier.noInitialFocus())
                    .then(if (landscape) Modifier else Modifier.navigationBarsPadding())
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (title != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(title, style = XdText.sheetTitle, color = c.fg, modifier = Modifier.weight(1f))
                        // With a controller, B closes: the X would only be the first stop of the focus.
                        if (closeButton && dismissible && !c.controller) XdIconButton(XdIcons.x, null, onDismiss)
                    }
                    if (subtitle != null) Text(subtitle, style = XdText.note, color = c.fg3, modifier = Modifier.padding(top = 0.dp))
                }
                content()
                if (actions != null) {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions) }
                }
            }
        }
    }
}

/**
 * Lets the window of a full-size dialog cover the system bars, as the app's own window does (edge
 * to edge); the content then keeps clear of them with the insets. Needed on Android 15+: there the
 * screen size a full-width dialog is measured against (Configuration.screenHeightDp, Compose 1.7)
 * counts the system bars, while a dialog window still fits inside them by default, so the window
 * starts under the status bar and its bottom (a sheet's or the assistant's buttons) falls off the
 * screen. Call it first thing inside the Dialog's content.
 */
@Composable
fun XdDialogEdgeToEdge() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    SideEffect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val attrs = window.attributes
            val cutout = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            if (attrs.fitInsetsTypes != 0 || attrs.layoutInDisplayCutoutMode != cutout) {
                attrs.fitInsetsTypes = 0
                attrs.layoutInDisplayCutoutMode = cutout
                window.attributes = attrs
            }
        }
    }
}

private fun Modifier.clickable(remember: MutableInteractionSource, indication: Nothing?, onClick: () -> Unit): Modifier =
    this.clickable(interactionSource = remember, indication = indication, onClick = onClick)

/** Focus moves into this group only when asked for (a tap); the window's first focus stays out. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
private fun Modifier.noInitialFocus(): Modifier =
    this.focusProperties { enter = { androidx.compose.ui.focus.FocusRequester.Cancel } }.focusGroup()

/** A choice inside a sheet: title, explanation, control at the end (the prototype's .opt). */
@Composable
fun XdSheetOption(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    control: (@Composable () -> Unit)? = null,
) {
    val c = Xd.colors
    Row(
        modifier.fillMaxWidth().clip(ZephyrPanelShape).background(c.s1)
            .border(1.dp, c.line2, ZephyrPanelShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = XdText.label, color = c.fg)
            if (subtitle != null) Text(subtitle, style = XdText.small, color = c.fg3)
        }
        control?.invoke()
    }
}

/** A menu line inside a sheet: icon, text and an optional explanation under it. */
@Composable
fun XdMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    val c = Xd.colors
    val shape = ZephyrActionShape
    Row(
        modifier.fillMaxWidth().focusRing(shape).clip(shape).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(19.dp), tint = tint ?: if (enabled) c.fg2 else c.fg3)
        Column(Modifier.weight(1f)) {
            Text(text, style = XdText.label, color = tint ?: if (enabled) c.fg else c.fg3)
            if (subtitle != null) Text(subtitle, style = XdText.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = c.fg3)
        }
    }
}

/** A short message at the bottom of the screen, optionally with "Desfazer". */
@Stable
class XdToastState {
    data class Toast(val message: String, val action: String?, val onAction: (() -> Unit)?, val id: Long)

    var current by mutableStateOf<Toast?>(null)
        private set
    private var next = 0L

    fun show(message: String, action: String? = null, onAction: (() -> Unit)? = null) {
        current = Toast(message, action, onAction, ++next)
    }

    fun dismiss() { current = null }
}

val LocalXdToast = staticCompositionLocalOf { XdToastState() }

/** Draws [state]'s toast at the bottom centre of the box it is placed in. */
@Composable
fun XdToastHost(state: XdToastState, modifier: Modifier = Modifier) {
    val toast = state.current
    LaunchedEffect(toast?.id) {
        if (toast != null) {
            delay(if (toast.action != null) 5200 else 2800)
            if (state.current?.id == toast.id) state.dismiss()
        }
    }
    val c = Xd.colors
    Box(modifier.fillMaxWidth().padding(bottom = if (c.controller) 52.dp else 16.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(toast != null, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { it / 3 }) {
            val t = toast ?: return@AnimatedVisibility
            Row(
                Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xF7161C19))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(t.message, style = XdText.bodySm, color = Color(0xFFEEF4EF), maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                if (t.action != null) Text(
                    t.action, style = XdText.labelSm.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.acc,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { t.onAction?.invoke(); state.dismiss() }.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }
}
