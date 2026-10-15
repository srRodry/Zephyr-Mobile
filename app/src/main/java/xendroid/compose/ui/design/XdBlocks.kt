package xendroid.compose.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Shared structural panel shape for Zephyr 2.0: one restrained top-right cut. */
val ZephyrPanelShape: Shape = GenericShape { size, _ ->
    val cut = minOf(size.width * 0.06f, size.height * 0.18f)
    moveTo(0f, 0f)
    lineTo(size.width - cut, 0f)
    lineTo(size.width, cut)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

/** Shared interactive geometry: compact and restrained, distinct from structural panels and pills. */
val ZephyrActionShape: Shape = RoundedCornerShape(4.dp)

/** Grouped tabs and segmented controls use one shared container radius. */
val ZephyrSegmentShape: Shape = RoundedCornerShape(6.dp)

/** A structural Zephyr surface card with the shared clipped panel silhouette. */
@Composable
fun XdCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    trailing: String? = null,
    tight: Boolean = false,
    shape: Shape = ZephyrPanelShape,
    outlined: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Xd.colors
    Column(
        modifier
            .clip(shape)
            .background(c.s1)
            .then(if (outlined) Modifier.border(1.dp, c.line2, shape) else Modifier)
            .padding(horizontal = if (tight) 12.dp else 16.dp, vertical = if (tight) 10.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) XdCardHeader(title, icon, trailing)
        content()
    }
}

@Composable
fun XdCardHeader(title: String, icon: ImageVector? = null, trailing: String? = null) {
    val c = Xd.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null) Icon(icon, null, Modifier.size(15.dp), tint = c.fg3)
        Text(title.uppercase(), style = XdText.cardHead, color = c.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = trailing != null))
        if (trailing != null) Text(trailing, style = XdText.small.copy(fontSize = 12.sp), color = c.fg2, maxLines = 1)
    }
}

/** Group header inside a list ("IMAGEM 12"). */
@Composable
fun XdGroupHeader(title: String, count: Int? = null, modifier: Modifier = Modifier) {
    val c = Xd.colors
    Row(modifier.padding(start = 2.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = XdText.cardHead, color = c.fg3)
        if (count != null) Text(count.toString(), style = XdText.monoNum, color = c.fg3)
    }
}

/** Uppercase accent label above a title. */
@Composable
fun XdEyebrow(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(text.uppercase(), style = XdText.eyebrow, color = color ?: Xd.colors.acc, modifier = modifier)
}

/** Label/value pairs, values right-aligned. */
@Composable
fun XdKv(rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val c = Xd.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        for ((k, v) in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(k, style = XdText.note, color = c.fg3)
                Text(v, style = XdText.note, color = c.fg, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * A list row: leading icon or image, title (and badges), subtitle, actions at the end. The badges
 * wrap under the title when they do not fit beside it, so a long title is never squeezed;
 * [actionsBelow] (a narrow screen with wide buttons) puts the actions under the text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun XdListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    lead: (@Composable () -> Unit)? = null,
    badges: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    divider: Boolean = true,
    actionsBelow: Boolean = false,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Xd.colors
    val shape = RoundedCornerShape(12.dp)
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (c.controller) Modifier.padding(bottom = 6.dp).clip(shape).background(c.s1) else Modifier)
                .then(if (onClick != null) Modifier.focusRing(shape).clickable(role = Role.Button, onClick = onClick) else Modifier)
                .heightIn(min = rowMinHeight())
                .padding(horizontal = if (c.controller) 12.dp else 4.dp, vertical = if (c.controller) 10.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                lead != null -> Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) { lead() }
                icon != null -> Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(22.dp), tint = c.fg3)
                }
            }
            Column(Modifier.weight(1f)) {
                if (badges == null) Text(title, style = XdText.label, color = c.fg)
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = XdText.label, color = c.fg, modifier = Modifier.align(Alignment.CenterVertically))
                    badges(this)
                }
                if (subtitle != null) Text(subtitle, style = XdText.small, color = c.fg3, modifier = Modifier.padding(top = 2.dp))
                if (actions != null && actionsBelow) FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
            }
            if (actions != null && !actionsBelow) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        if (divider && !c.controller) HorizontalDivider(thickness = 1.dp, color = c.line)
    }
}

/** A rounded square holding an icon (the lead of a list row). */
@Composable
fun XdLeadIcon(icon: ImageVector, tint: Color? = null, size: Dp = 34.dp) {
    val c = Xd.colors
    Box(Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(c.s2), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.55f), tint = tint ?: c.fg2)
    }
}

enum class NoteTone { INFO, WARN, ERROR, OK, MUTED }

/** One line of explanation with an icon: info, warning, error or success. */
@Composable
fun XdNote(text: String, modifier: Modifier = Modifier, tone: NoteTone = NoteTone.MUTED, icon: ImageVector? = null) {
    val c = Xd.colors
    val color = when (tone) {
        NoteTone.WARN -> c.warn
        NoteTone.ERROR -> c.errText
        NoteTone.OK -> c.ok
        NoteTone.INFO -> c.fg2
        NoteTone.MUTED -> c.fg3
    }
    val glyph = icon ?: when (tone) {
        NoteTone.WARN -> XdIcons.warn
        NoteTone.ERROR -> XdIcons.alert
        NoteTone.OK -> XdIcons.checkCircle
        NoteTone.INFO -> XdIcons.info
        NoteTone.MUTED -> null
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (glyph != null) Icon(glyph, null, Modifier.padding(top = 1.dp).size(15.dp), tint = color)
        Text(text, style = XdText.note, color = color)
    }
}

enum class BadgeTone { NEUTRAL, OK, WARN, ERROR, ACCENT, NEW }

@Composable
fun XdBadge(text: String, modifier: Modifier = Modifier, tone: BadgeTone = BadgeTone.NEUTRAL, icon: ImageVector? = null) {
    val c = Xd.colors
    val (bg, fg) = when (tone) {
        BadgeTone.NEUTRAL -> c.s3 to c.fg2
        BadgeTone.OK -> c.playable.copy(alpha = 0.16f) to c.playable
        BadgeTone.WARN -> c.ingame.copy(alpha = 0.16f) to c.ingame
        BadgeTone.ERROR -> c.nothing.copy(alpha = 0.18f) to c.errText
        BadgeTone.ACCENT -> c.acc.copy(alpha = 0.18f) to c.acc
        BadgeTone.NEW -> c.exposed.copy(alpha = 0.17f) to c.exposed
    }
    Row(
        modifier.clip(RoundedCornerShape(if (tone == BadgeTone.NEW) 5.dp else 7.dp)).background(bg)
            .padding(horizontal = if (tone == BadgeTone.NEW) 6.dp else 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(12.dp), tint = fg)
        Text(
            text,
            style = if (tone == BadgeTone.NEW) XdText.monoSm.copy(fontSize = 9.5.sp, letterSpacing = 0.07.em, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else XdText.tiny.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            color = fg, maxLines = 1,
        )
    }
}

/** The compatibility pill: coloured dot and the rating's name. */
@Composable
fun XdStatusPill(text: String, tone: CompatTone, modifier: Modifier = Modifier) {
    val c = Xd.colors
    val color = c.status(tone)
    Row(
        modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f)).padding(start = 8.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(text, style = XdText.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = color, maxLines = 1)
    }
}

@Composable
fun XdDot(tone: CompatTone, size: Dp = 7.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Xd.colors.status(tone)))
}

/** A thin progress bar; [warn] paints it yellow. */
@Composable
fun XdBar(progress: Float, modifier: Modifier = Modifier, warn: Boolean = false, height: Dp = 6.dp) {
    val c = Xd.colors
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(c.s3)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50))
            .background(if (warn) c.warn else c.acc))
    }
}

/** Nothing to show: a centred line and an optional action. */
@Composable
fun XdEmpty(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text, style = XdText.bodySm, color = Xd.colors.fg3, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

/** A big number with its unit and a caption (performance tiles). */
@Composable
fun XdKpi(value: String, unit: String, caption: String, modifier: Modifier = Modifier) {
    val c = Xd.colors
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(c.s2).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(buildAnnotatedString {
            append(value)
            withStyle(SpanStyle(fontSize = 13.sp, color = c.fg2, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)) { append(" $unit") }
        }, style = XdText.kpi, color = c.fg, maxLines = 1)
        Text(caption, style = XdText.tiny, color = c.fg3, modifier = Modifier.padding(top = 3.dp))
    }
}

/** IDs in small mono boxes (Title ID, Media ID…), selectable for copying. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun XdIdRow(ids: List<String>, modifier: Modifier = Modifier) {
    val c = Xd.colors
    SelectionContainer(modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (id in ids) Text(id, style = XdText.mono, color = c.fg2,
                modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(Color.White.copy(alpha = 0.07f)).padding(horizontal = 8.dp, vertical = 6.dp))
        }
    }
}

/** A file path in mono, selectable. */
@Composable
fun XdPath(path: String, modifier: Modifier = Modifier) {
    SelectionContainer(modifier) { Text(path, style = XdText.mono, color = Xd.colors.fg3) }
}

/** TOML with its comments, tables, keys and values coloured, selectable. */
@Composable
fun XdToml(text: String, modifier: Modifier = Modifier) {
    val styled = buildAnnotatedString {
        text.lines().forEachIndexed { i, line ->
            if (i > 0) append('\n')
            when {
                line.startsWith("#") -> withStyle(SpanStyle(color = Color(0xFF6F8178))) { append(line) }
                line.startsWith("[") && line.endsWith("]") -> withStyle(SpanStyle(color = Color(0xFFE9C86A))) { append(line) }
                " = " in line -> {
                    val k = line.substringBefore(" = ")
                    withStyle(SpanStyle(color = Color(0xFF9FD3FF))) { append(k) }
                    append(" = ")
                    withStyle(SpanStyle(color = Color(0xFF9BE37F))) { append(line.substringAfter(" = ")) }
                }
                else -> append(line)
            }
        }
    }
    SelectionContainer(modifier) {
        Text(styled, style = XdText.mono.copy(fontSize = 12.5.sp, lineHeight = 20.sp), color = Color(0xFFD7E2DB),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF0A0E0C))
                .padding(horizontal = 16.dp, vertical = 14.dp))
    }
}

/** A vertical gap of [h]. */
@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

/** The flexible gap of a row. */
@Composable
fun RowScope.Push() = Spacer(Modifier.weight(1f))

/** Two columns on a wide screen, one on a narrow one (cards of the game sheet). */
@Composable
fun XdTwoColumns(
    wide: Boolean,
    modifier: Modifier = Modifier,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) {
    if (wide) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), content = left)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), content = right)
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) { left(); right() }
    }
}

/** Width limit for running text, as the prototype's 66–70ch. */
fun Modifier.readable(): Modifier = this.widthIn(max = 620.dp)
