package xendroid.compose.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import xendroid.compose.R

/** The app's top-level areas: the rail in touch mode, the Start menu in controller mode. */
enum class XdArea(val label: Int, val optional: Boolean = false) {
    GAMES(R.string.xd_area_games),
    COLLECTIONS(R.string.xd_area_collections, optional = true),
    CONTENT(R.string.xd_area_content),
    PROFILES(R.string.xd_area_profiles),
    CONTROLS(R.string.xd_area_controls),
    DRIVERS(R.string.xd_area_drivers, optional = true),
    SETTINGS(R.string.xd_area_settings);

    val icon: ImageVector get() = when (this) {
        GAMES -> XdIcons.grid
        COLLECTIONS -> XdIcons.layers
        CONTENT -> XdIcons.box
        PROFILES -> XdIcons.user
        CONTROLS -> XdIcons.gamepad
        DRIVERS -> XdIcons.chip
        SETTINGS -> XdIcons.gear
    }
}

/** Where the Start menu also leads, beyond the areas. */
enum class XdShortcut { DIAGNOSTICS, COMPARE, ABOUT }

/** What the frame needs from navigation; the nav host provides it. */
interface XdNavigator {
    fun area(area: XdArea)
    fun shortcut(shortcut: XdShortcut)
    /** Who plays (the guide's header); null before it is known. */
    val gamertag: String? get() = null
    /** Switch between touch and controller mode from the Start menu. */
    fun toggleInputMode() {}
}

val LocalXdNavigator = staticCompositionLocalOf<XdNavigator> {
    object : XdNavigator {
        override fun area(area: XdArea) {}
        override fun shortcut(shortcut: XdShortcut) {}
    }
}

/** True when the menus swap A and B (the Nintendo layout, U04): hints show the swapped buttons. */
val LocalSwapConfirm = staticCompositionLocalOf { false }

// ---------------------------------------------------------------- B: rail, bottom bar, top bar

@Composable
fun BRail(current: XdArea?, modifier: Modifier = Modifier) {
    val c = Xd.colors
    val nav = LocalXdNavigator.current
    // Its colour runs under a cutout or a side navigation bar; the items stay clear of them.
    Row(modifier.fillMaxHeight().background(c.s1).windowInsetsPadding(WindowInsets.safeDrawing.part(start = true))) {
        Column(
            Modifier.width(66.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.part(top = true, bottom = true))
                .padding(top = 10.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(
                Modifier.padding(bottom = 6.dp).size(36.dp).clip(RoundedCornerShape(9.dp))
                    .background(c.bg),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.zephyr_launcher_foreground),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            for (a in XdArea.entries) if (a != XdArea.SETTINGS) RailItem(a, a == current) { nav.area(a) }
            Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
            RailItem(XdArea.SETTINGS, current == XdArea.SETTINGS) { nav.area(XdArea.SETTINGS) }
        }
        VerticalDivider(thickness = 1.dp, color = c.line)
    }
}

@Composable
private fun RailItem(area: XdArea, selected: Boolean, onClick: () -> Unit) {
    val c = Xd.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.width(56.dp).focusRing(shape).clip(shape)
            .background(if (selected) c.acc.copy(alpha = 0.13f) else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick).semantics { this.selected = selected }
            .padding(top = 5.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(area.icon, null, Modifier.size(21.dp), tint = if (selected) c.acc else c.fg3)
        Text(stringResource(area.label), style = XdText.railLabel, color = if (selected) c.acc else c.fg3, maxLines = 1)
    }
}

/** The rail laid along the bottom in portrait, without the optional areas. */
@Composable
fun BBottomBar(current: XdArea?, modifier: Modifier = Modifier) {
    val c = Xd.colors
    val nav = LocalXdNavigator.current
    // Above the navigation bar (three buttons or the gesture handle), its colour running under it.
    Column(modifier.fillMaxWidth().background(c.s1).windowInsetsPadding(WindowInsets.safeDrawing.part(bottom = true, start = true, end = true))) {
        HorizontalDivider(thickness = 1.dp, color = c.line)
        Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically) {
            for (a in XdArea.entries) if (!a.optional) RailItem(a, a == current) { nav.area(a) }
        }
    }
}

/** The bar on top of a screen: back, a small cover or icon, title and IDs, actions. */
@Composable
fun BTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleMono: Boolean = false,
    onBack: (() -> Unit)? = null,
    lead: (@Composable () -> Unit)? = null,
    art: Any? = null,
    portrait: Boolean = false,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Xd.colors
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            if (art != null) {
                AsyncImage(art, null, Modifier.matchParentSize().blur(30.dp), contentScale = ContentScale.Crop, alpha = 0.32f)
            }
            Column(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(start = 10.dp, end = 14.dp, top = 9.dp, bottom = 9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (onBack != null) XdIconButton(XdIcons.back, stringResource(R.string.xd_back), onBack)
                    if (lead != null) Box(Modifier.width(40.dp)) { lead() }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = XdText.h1, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) Text(
                            subtitle, style = if (subtitleMono) XdText.mono.copy(fontSize = 11.sp) else XdText.small,
                            color = c.fg3, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    if (actions != null && !portrait) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
                }
                if (actions != null && portrait) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
        HorizontalDivider(thickness = 1.dp, color = c.line)
    }
}

// ---------------------------------------------------------------- sections

/**
 * One section of a [XdSectionedScreen]: a line in the section list (B) or the big menu (C), and
 * the [content] shown beside it. [goTo] makes the line open another screen instead. [scroll]
 * false hands the whole space to a content that scrolls itself (a lazy list).
 */
class XdSection(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val group: String? = null,
    val badge: String? = null,
    val heading: String? = title,
    val lead: String? = null,
    val goTo: (() -> Unit)? = null,
    val play: Boolean = false,
    val scroll: Boolean = true,
    val content: (@Composable ColumnScope.() -> Unit)? = null,
)

/**
 * Round 2: in-screen jumps remember where they came from. A change of section that does not come
 * from the section list (a link such as "All settings" or "See the patches") adds a step; Back, the
 * controller's B and "‹ Back to …" over the section return to it.
 */
class XdSectionHistory internal constructor(private val stack: MutableState<List<String>>) {
    internal var onSelect: (String) -> Unit = {}
    private var last: String? = null
    private var fromList = false

    /** The section a jump came from; null when there is none to go back to. */
    val previous: String? get() = stack.value.lastOrNull()

    /** A section picked in the section list: a fresh start, nothing to go back to. */
    fun pick(id: String) {
        stack.value = emptyList()
        if (id != last) fromList = true
        onSelect(id)
    }

    fun back() {
        val target = previous ?: return
        stack.value = stack.value.dropLast(1)
        if (target != last) fromList = true
        onSelect(target)
    }

    internal fun observe(selected: String) {
        val before = last
        if (before != null && before != selected && !fromList) stack.value = (stack.value + before).takeLast(8)
        fromList = false
        last = selected
    }
}

@Composable
private fun rememberSectionHistory(selected: String, onSelect: (String) -> Unit): XdSectionHistory {
    val stack = rememberSaveable { mutableStateOf(listOf<String>()) }
    val history = remember { XdSectionHistory(stack) }
    history.onSelect = onSelect
    LaunchedEffect(selected) { history.observe(selected) }
    BackHandler(enabled = stack.value.isNotEmpty()) { history.back() }
    return history
}

/** "‹ Back to Summary" over a section reached by a link. */
@Composable
private fun BackToLink(history: XdSectionHistory, sections: List<XdSection>, modifier: Modifier = Modifier) {
    val id = history.previous ?: return
    val title = sections.firstOrNull { it.id == id }?.title ?: return
    XdLink("‹ " + stringResource(R.string.xd_back_to, title), { history.back() }, modifier.padding(bottom = 8.dp))
}

/** A button hint of the controller bar: glyphs ("A", "LB/RB", "≡"), label, and what a tap does. */
class XdHint(val buttons: String, val label: String, val onClick: (() -> Unit)? = null)

/**
 * A screen of sections, in both frames:
 * - touch (B): rail, top bar, the sections on the left (a row on top in portrait), content right;
 * - controller (C): the cover's colour behind, a big menu of the sections on the left, content
 *   right, button hints at the bottom; LB/RB move between sections, Start opens the menu.
 */
@Composable
fun XdSectionedScreen(
    title: String,
    sections: List<XdSection>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    area: XdArea? = null,
    subtitle: String? = null,
    subtitleMono: Boolean = false,
    onBack: (() -> Unit)? = null,
    lead: (@Composable () -> Unit)? = null,
    headIcon: ImageVector? = null,
    art: Any? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    hints: List<XdHint>? = null,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    /** Controller mode: pad buttons the screen handles itself (Y favourites…); true = handled. */
    onPad: ((PadButton) -> Boolean)? = null,
) {
    val real = sections.filter { it.goTo == null && it.content != null }
    val current = real.firstOrNull { it.id == selected } ?: real.firstOrNull()
    val history = rememberSectionHistory(selected, onSelect)
    if (Xd.controller) {
        CSectioned(title, sections, current, history, modifier, subtitle, onBack, lead, headIcon, art, actions, hints, overlay, onPad)
        return
    }
    val c = Xd.colors
    BoxWithConstraints(modifier.fillMaxSize().background(c.bg)) {
        val portrait = maxHeight > maxWidth
        if (portrait) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).windowInsetsPadding(WindowInsets.safeDrawing.part(top = true))) {
                    BTopBar(title, subtitle = subtitle, subtitleMono = subtitleMono, onBack = onBack, lead = lead, art = art, portrait = true, actions = actions)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (s in sections) SecNavItem(s, s.id == current?.id, history::pick, compact = true)
                    }
                    HorizontalDivider(thickness = 1.dp, color = c.line)
                    SectionBody(current, Modifier.weight(1f)) { BackToLink(history, sections) }
                }
                BBottomBar(area)
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                BRail(area)
                Column(Modifier.weight(1f).windowInsetsPadding(WindowInsets.safeDrawing.part(top = true, end = true))) {
                    BTopBar(title, subtitle = subtitle, subtitleMono = subtitleMono, onBack = onBack, lead = lead, art = art, actions = actions)
                    Row(Modifier.weight(1f)) {
                        Column(Modifier.width(186.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            var lastGroup: String? = null
                            for (s in sections) {
                                if (s.group != null && s.group != lastGroup) {
                                    Text(s.group.uppercase(), style = XdText.cardHead.copy(fontSize = 10.sp), color = c.fg3,
                                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = if (lastGroup == null) 4.dp else 10.dp, bottom = 4.dp))
                                    lastGroup = s.group
                                }
                                SecNavItem(s, s.id == current?.id, history::pick, compact = false)
                            }
                        }
                        VerticalDivider(thickness = 1.dp, color = c.line)
                        SectionBody(current, Modifier.weight(1f)) { BackToLink(history, sections) }
                    }
                }
            }
        }
        overlay?.invoke(this)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SecNavItem(s: XdSection, selected: Boolean, onSelect: (String) -> Unit, compact: Boolean) {
    val c = Xd.colors
    val shape = RoundedCornerShape(9.dp)
    // Round 2: a section chosen by a link scrolls the list (a row in portrait) to it.
    val inView = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) { if (selected) { withFrameNanos { }; inView.bringIntoView() } }
    Row(
        Modifier.bringIntoViewRequester(inView).then(if (compact) Modifier else Modifier.fillMaxWidth()).focusRing(shape).heightIn(min = 33.dp).clip(shape)
            .background(if (selected) c.s3 else Color.Transparent)
            .clickable(role = Role.Tab) { s.goTo?.invoke() ?: onSelect(s.id) }
            .semantics { this.selected = selected }
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(s.icon, null, Modifier.size(17.dp), tint = if (selected) c.acc else c.fg3)
        Text(s.title, style = XdText.labelSm, color = if (selected) c.fg else c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = if (compact) Modifier else Modifier.weight(1f))
        when {
            s.goTo != null -> Icon(XdIcons.chevR, null, Modifier.size(14.dp), tint = c.fg3)
            s.badge != null -> Text(s.badge, style = XdText.monoNum.copy(fontSize = 10.5.sp), color = c.acc)
        }
    }
}

@Composable
private fun SectionBody(section: XdSection?, modifier: Modifier, top: @Composable () -> Unit = {}) {
    val c = Xd.colors
    if (section == null) { Box(modifier); return }
    val head: @Composable ColumnScope.() -> Unit = {
        top()
        if (section.heading != null) Text(section.heading, style = XdText.h2, color = c.fg, modifier = Modifier.padding(bottom = 10.dp))
        if (section.lead != null) Text(section.lead, style = XdText.bodySm, color = c.fg3, modifier = Modifier.readable().padding(bottom = 12.dp))
    }
    if (section.scroll) {
        val scroll = rememberSaveable(section.id, saver = androidx.compose.foundation.ScrollState.Saver) { androidx.compose.foundation.ScrollState(0) }
        Column(modifier.fillMaxHeight().verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 26.dp)) {
            head()
            section.content?.invoke(this)
        }
    } else {
        Column(modifier.fillMaxHeight().padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
            head()
            section.content?.invoke(this)
        }
    }
}

/** A screen without sections, in both frames. */
@Composable
fun XdSingleScreen(
    title: String,
    modifier: Modifier = Modifier,
    area: XdArea? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    headIcon: ImageVector = XdIcons.gear,
    actions: (@Composable RowScope.() -> Unit)? = null,
    hints: List<XdHint>? = null,
    scroll: Boolean = true,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    /** Controller mode: pad buttons the screen handles itself; true = handled. */
    onPad: ((PadButton) -> Boolean)? = null,
    /** False for a modal flow (the folder browser): no rail or bottom bar to wander off mid-way. */
    showNav: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Xd.colors
    if (Xd.controller) {
        CFrame(modifier, art = null, hints = hints ?: defaultHints(sections = false), onPad = onPad, overlay = overlay) { _ ->
            Column(Modifier.fillMaxSize()) {
                CHead(title, subtitle, onBack, lead = null, headIcon = headIcon, actions = actions)
                val body = Modifier.weight(1f).fillMaxWidth()
                if (scroll) Column(body.verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 18.dp), content = content)
                else Column(body.padding(start = 22.dp, end = 22.dp, top = 8.dp), content = content)
            }
        }
        return
    }
    BoxWithConstraints(modifier.fillMaxSize().background(c.bg)) {
        val portrait = maxHeight > maxWidth
        val body: @Composable (Modifier) -> Unit = { m ->
            Column(m.windowInsetsPadding(WindowInsets.safeDrawing.part(top = true, end = !portrait))) {
                BTopBar(title, subtitle = subtitle, onBack = onBack, portrait = portrait, actions = actions)
                val inner = Modifier.weight(1f).fillMaxWidth()
                if (scroll) Column(inner.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp), content = content)
                else Column(inner.padding(start = 16.dp, end = 16.dp, top = 14.dp), content = content)
            }
        }
        when {
            !showNav -> body(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.part(bottom = true, start = !portrait)))
            portrait -> Column(Modifier.fillMaxSize()) { body(Modifier.weight(1f)); BBottomBar(area) }
            else -> Row(Modifier.fillMaxSize()) { BRail(area); body(Modifier.weight(1f)) }
        }
        overlay?.invoke(this)
    }
}

// ---------------------------------------------------------------- C: controller frame

/** The deep cover colour, the blurred art and the top/bottom shades behind controller screens. */
@Composable
fun CBackground(art: Any?, modifier: Modifier = Modifier) {
    val c = Xd.colors
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF070908), Color(0xFF020303))))) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(c.dyn.copy(alpha = 0.62f), Color.Transparent), radius = 1400f)))
        if (art != null) AsyncImage(art, null, Modifier.fillMaxSize().blur(48.dp), contentScale = ContentScale.Crop, alpha = 0.45f)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.42f), 0.26f to Color.Transparent, 0.58f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f))))
    }
}

/** Default hints of a controller screen. */
@Composable
fun defaultHints(sections: Boolean, onMenu: (() -> Unit)? = null): List<XdHint> {
    val swap = LocalSwapConfirm.current
    val list = mutableListOf(
        XdHint(if (swap) "B" else "A", stringResource(R.string.xd_hint_select)),
        XdHint(if (swap) "A" else "B", stringResource(R.string.xd_hint_back)),
    )
    if (sections) list += XdHint("LB/RB", stringResource(R.string.xd_hint_sections))
    list += XdHint("LT/RT", stringResource(R.string.xd_hint_page))
    list += XdHint("≡", stringResource(R.string.xd_hint_menu), onMenu)
    return list
}

/** Rows LT/RT move the focus by: about a screen of list rows. */
private const val PAGE_STEPS = 6

/** The hints bar along the bottom of controller screens. */
@Composable
fun CHints(hints: List<XdHint>, modifier: Modifier = Modifier, trailing: String? = null, scrim: Boolean = true) {
    val c = Xd.colors
    Row(
        modifier.fillMaxWidth().height(42.dp)
            // [scrim] false: inside a panel, which has its own background and padding.
            .then(if (scrim) Modifier.background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))) else Modifier)
            .horizontalScroll(rememberScrollState()).padding(horizontal = if (scrim) 20.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        for (h in hints) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).then(if (h.onClick != null) Modifier.clickable(onClick = h.onClick) else Modifier),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                for (b in h.buttons.split('/')) ButtonGlyph(b)
                Text(h.label, style = XdText.labelSm.copy(fontSize = 12.5.sp), color = c.fg2, maxLines = 1)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Text(trailing, style = XdText.small, color = c.fg3, maxLines = 1)
        }
    }
}

/** A controller button as on the pad: A green, B red, X blue, Y yellow; shoulders and menus grey. */
@Composable
fun ButtonGlyph(button: String, size: Dp = 22.dp) {
    val (bg, fg) = when (button) {
        "A" -> Color(0xFF3F9E1B) to Color.White
        "B" -> Color(0xFFD8352A) to Color.White
        "X" -> Color(0xFF1D7FD1) to Color.White
        "Y" -> Color(0xFFE0B015) to Color(0xFF1D1600)
        else -> Color.White.copy(alpha = 0.2f) to Color.White
    }
    val round = button.length == 1 && button[0].isLetter()
    Box(
        Modifier.heightIn(min = size).widthIn(min = size).clip(if (round) CircleShape else RoundedCornerShape(6.dp)).background(bg)
            .padding(horizontal = if (round) 0.dp else 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(button, style = XdText.tiny.copy(fontWeight = FontWeight.ExtraBold, fontSize = if (round) 11.sp else 10.sp), color = fg, maxLines = 1)
    }
}

/** Head of a controller screen: back, a cover or icon, title and subtitle, actions. */
@Composable
fun CHead(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    lead: (@Composable () -> Unit)?,
    headIcon: ImageVector?,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Xd.colors
    Row(modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        if (onBack != null) XdIconButton(XdIcons.back, stringResource(R.string.xd_back), onBack)
        when {
            lead != null -> Box(Modifier.width(44.dp)) { lead() }
            headIcon != null -> Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.s2), contentAlignment = Alignment.Center) {
                Icon(headIcon, null, Modifier.size(22.dp), tint = c.acc)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = XdText.h1.copy(fontSize = 19.sp), color = c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = XdText.small, color = c.fg3, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (actions != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/**
 * The controller frame: background, [content], hints, the Start menu (guide) and the pad keys:
 * Start toggles the guide, LB/RB call [onShoulder] (-1/+1).
 */
@Composable
fun CFrame(
    modifier: Modifier = Modifier,
    art: Any?,
    hints: List<XdHint>,
    onShoulder: ((Int) -> Unit)? = null,
    onPad: ((PadButton) -> Boolean)? = null,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.(openGuide: () -> Unit) -> Unit,
) {
    var guide by remember { mutableStateOf(false) }
    val openGuide = { guide = true }
    val withMenu = hints.map { if (it.buttons == "≡" && it.onClick == null) XdHint(it.buttons, it.label, openGuide) else it }
    val focus = LocalFocusManager.current
    BoxWithConstraints(
        modifier.fillMaxSize()
            .onPreviewKeyEvent { e ->
                when (val b = padButtonOf(e)) {
                    null -> false
                    else -> when {
                        onPad?.invoke(b) == true -> true
                        b == PadButton.START -> { guide = !guide; true }
                        b == PadButton.LB && onShoulder != null -> { onShoulder(-1); true }
                        b == PadButton.RB && onShoulder != null -> { onShoulder(1); true }
                        // Round 2: LT/RT go a page up or down, several rows at once (the list follows).
                        b == PadButton.LT -> { repeat(PAGE_STEPS) { focus.moveFocus(FocusDirection.Up) }; true }
                        b == PadButton.RT -> { repeat(PAGE_STEPS) { focus.moveFocus(FocusDirection.Down) }; true }
                        else -> false
                    }
                }
            },
    ) {
        CBackground(art)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.part(top = true, end = true, start = true))) {
            Box(Modifier.weight(1f).fillMaxWidth()) { content(openGuide) }
            CHints(withMenu)
        }
        overlay?.invoke(this)
    }
    if (guide) CGuide(onDismiss = { guide = false })
}

@Composable
private fun CSectioned(
    title: String,
    sections: List<XdSection>,
    current: XdSection?,
    history: XdSectionHistory,
    modifier: Modifier,
    subtitle: String?,
    onBack: (() -> Unit)?,
    lead: (@Composable () -> Unit)?,
    headIcon: ImageVector?,
    art: Any?,
    actions: (@Composable RowScope.() -> Unit)?,
    hints: List<XdHint>?,
    overlay: (@Composable BoxScope.() -> Unit)?,
    onPad: ((PadButton) -> Boolean)?,
) {
    val c = Xd.colors
    val onSelect: (String) -> Unit = history::pick
    val real = sections.filter { it.goTo == null && it.content != null }
    val shoulder: (Int) -> Unit = { d ->
        val i = real.indexOfFirst { it.id == current?.id }
        if (real.isNotEmpty()) onSelect(real[(i + d + real.size) % real.size].id)
    }
    val requesters = remember(sections.map { it.id }) { sections.associate { it.id to FocusRequester() } }
    CFrame(modifier, art, hints ?: defaultHints(sections = true), onShoulder = { d ->
        shoulder(d)
    }, onPad = onPad, overlay = overlay) { _ ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val portrait = maxHeight > maxWidth
            val menuItem: @Composable (XdSection) -> Unit = { s -> CMenuItem(s, s.id == current?.id, onSelect, requesters.getValue(s.id), portrait) }
            if (portrait) {
                Column(Modifier.fillMaxSize()) {
                    CHead(title, subtitle, onBack, lead, headIcon, actions = actions)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)) { for (s in sections) menuItem(s) }
                    CPanel(current, Modifier.weight(1f), portrait = true) { BackToLink(history, sections) }
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(276.dp).fillMaxHeight()) {
                        CHead(title, subtitle, onBack, lead, headIcon)
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 10.dp, top = 2.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            var lastGroup: String? = null
                            for (s in sections) {
                                if (s.group != null && s.group != lastGroup) {
                                    Text(s.group.uppercase(), style = XdText.cardHead.copy(fontSize = 10.sp), color = c.fg3,
                                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 2.dp))
                                    lastGroup = s.group
                                }
                                menuItem(s)
                            }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        if (actions != null) Row(Modifier.fillMaxWidth().padding(top = 12.dp, end = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically, content = actions)
                        CPanel(current, Modifier.weight(1f), portrait = false) { BackToLink(history, sections) }
                    }
                }
            }
        }
    }
    // The section's menu line takes the focus, so the pad works at once (A, LB/RB, Start).
    LaunchedEffect(current?.id) { current?.id?.let { runCatching { requesters[it]?.requestFocus() } } }
}

@Composable
private fun CMenuItem(s: XdSection, selected: Boolean, onSelect: (String) -> Unit, requester: FocusRequester, portrait: Boolean) {
    val c = Xd.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .then(if (portrait) Modifier else Modifier.fillMaxWidth())
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused && s.goTo == null && !selected) onSelect(s.id)
            }
            .scale(if (focused) 1.02f else 1f)
            .heightIn(min = if (portrait) 38.dp else 42.dp)
            .clip(shape)
            .background(when { focused -> c.acc; selected -> Color.White.copy(alpha = 0.11f); else -> Color.Transparent })
            .clickable(role = Role.Tab) { s.goTo?.invoke() ?: onSelect(s.id) }
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val fg = when { focused -> c.onAcc; selected -> c.fg; else -> c.fg2 }
        Icon(s.icon, null, Modifier.size(19.dp), tint = when { focused -> c.onAcc; selected || s.play -> c.acc; else -> c.fg3 })
        Text(s.title, style = XdText.label.copy(fontSize = if (portrait) 13.5.sp else 14.5.sp), color = fg, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = if (portrait) Modifier else Modifier.weight(1f))
        when {
            s.goTo != null -> Icon(XdIcons.chevR, null, Modifier.size(14.dp), tint = if (focused) c.onAcc else c.fg3)
            s.badge != null -> Text(s.badge, style = XdText.monoNum, color = if (focused) c.onAcc else c.acc)
        }
    }
}

@Composable
private fun CPanel(section: XdSection?, modifier: Modifier, portrait: Boolean, top: @Composable () -> Unit = {}) {
    val c = Xd.colors
    if (section == null) { Box(modifier); return }
    val pad = if (portrait) Modifier.padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 18.dp)
    else Modifier.padding(start = 10.dp, end = 22.dp, top = 16.dp, bottom = 18.dp)
    val head: @Composable ColumnScope.() -> Unit = {
        top()
        if (section.heading != null) Text(section.heading, style = XdText.h2c, color = c.fg, modifier = Modifier.padding(bottom = 12.dp))
        if (section.lead != null) Text(section.lead, style = XdText.bodySm, color = c.fg3, modifier = Modifier.readable().padding(bottom = 12.dp))
    }
    if (section.scroll) {
        val scroll = rememberSaveable(section.id, saver = androidx.compose.foundation.ScrollState.Saver) { androidx.compose.foundation.ScrollState(0) }
        Column(modifier.fillMaxSize().verticalScroll(scroll).then(pad)) { head(); section.content?.invoke(this) }
    } else Column(modifier.fillMaxSize().then(pad)) { head(); section.content?.invoke(this) }
}

/** The Start menu of controller mode: who plays, the areas, and switching to touch mode. */
@Composable
fun CGuide(onDismiss: () -> Unit) {
    val nav = LocalXdNavigator.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        XdDialogEdgeToEdge()
        val c = Xd.colors
        val first = remember { FocusRequester() }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.58f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .onPreviewKeyEvent { e -> if (padButtonOf(e) == PadButton.START) { onDismiss(); true } else false },
        ) {
            Column(
                Modifier.fillMaxHeight().widthIn(max = 320.dp).fillMaxWidth(0.86f)
                    .background(Brush.verticalGradient(listOf(Color(0xFF151B18), Color(0xFF0D110F))))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .windowInsetsPadding(WindowInsets.safeDrawing.part(top = true, start = true, bottom = true))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(c.s3), contentAlignment = Alignment.Center) {
                        Icon(XdIcons.user, null, Modifier.size(22.dp), tint = c.fg2)
                    }
                    Column {
                        Text(nav.gamertag ?: stringResource(R.string.xd_guide_no_profile), style = XdText.h1.copy(fontSize = 19.sp), color = c.fg)
                        Text(stringResource(R.string.xd_guide_active), style = XdText.small, color = c.fg3)
                    }
                }
                val items = listOf(
                    Triple(XdIcons.grid, R.string.xd_guide_library) { nav.area(XdArea.GAMES) },
                    Triple(XdIcons.box, R.string.xd_area_content) { nav.area(XdArea.CONTENT) },
                    Triple(XdIcons.user, R.string.xd_area_profiles) { nav.area(XdArea.PROFILES) },
                    Triple(XdIcons.gamepad, R.string.xd_area_controls) { nav.area(XdArea.CONTROLS) },
                    Triple(XdIcons.chip, R.string.xd_area_drivers) { nav.area(XdArea.DRIVERS) },
                    Triple(XdIcons.gear, R.string.xd_guide_settings) { nav.area(XdArea.SETTINGS) },
                    Triple(XdIcons.bug, R.string.xd_guide_diagnostics) { nav.shortcut(XdShortcut.DIAGNOSTICS) },
                    Triple(XdIcons.ab, R.string.xd_guide_compare) { nav.shortcut(XdShortcut.COMPARE) },
                    Triple(XdIcons.info, R.string.xd_guide_about) { nav.shortcut(XdShortcut.ABOUT) },
                )
                items.forEachIndexed { i, (icon, label, go) ->
                    GuideItem(icon, stringResource(label), if (i == 0) Modifier.focusRequester(first) else Modifier) { onDismiss(); go() }
                }
                Spacer(Modifier.height(12.dp))
                GuideItem(XdIcons.hand, stringResource(R.string.xd_guide_use_touch)) { onDismiss(); nav.toggleInputMode() }
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}

@Composable
private fun GuideItem(icon: ImageVector, text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Xd.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.scale(if (focused) 1.02f else 1f).heightIn(min = 42.dp).clip(shape)
            .background(if (focused) c.acc else Color.Transparent).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = if (focused) c.onAcc else c.fg3)
        Text(text, style = XdText.label.copy(fontSize = 14.5.sp), color = if (focused) c.onAcc else c.fg2)
    }
}

/** Insets helper: only the given sides of these insets. */
fun WindowInsets.part(top: Boolean = false, bottom: Boolean = false, start: Boolean = false, end: Boolean = false): WindowInsets {
    var acc: WindowInsetsSides? = null
    fun add(s: WindowInsetsSides) { acc = acc?.plus(s) ?: s }
    if (top) add(WindowInsetsSides.Top)
    if (bottom) add(WindowInsetsSides.Bottom)
    if (start) add(WindowInsetsSides.Start)
    if (end) add(WindowInsetsSides.End)
    return acc?.let { this.only(it) } ?: WindowInsets(0, 0, 0, 0)
}

/** Provides [navigator] and the A/B swap to the frames below. */
@Composable
fun ProvideXdNavigation(navigator: XdNavigator, swapConfirm: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalXdNavigator provides navigator, LocalSwapConfirm provides swapConfirm, content = content)
}
