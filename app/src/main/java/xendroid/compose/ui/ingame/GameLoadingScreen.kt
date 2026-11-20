package xendroid.compose.ui.ingame

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import xendroid.compose.R
import xendroid.compose.ui.design.CBackground
import xendroid.compose.ui.design.CHints
import xendroid.compose.ui.design.GameCover
import xendroid.compose.ui.design.LocalSwapConfirm
import xendroid.compose.ui.design.WithCoverColors
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdBadge
import xendroid.compose.ui.design.XdBar
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdButtonSize
import xendroid.compose.ui.design.XdEyebrow
import xendroid.compose.ui.design.XdHint
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdNote
import xendroid.compose.ui.design.ZephyrOrbitMark
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.rememberCoverColors

/** What the loading screen says about the launch besides its stage: who plays and with what. */
data class LoadingDetails(
    val profile: String? = null,
    val titleId: String? = null,
    /** Driver, FPS limit, resolution, patches, own settings: a few words each. */
    val badges: List<String> = emptyList(),
    /** The game's own settings in effect (an accent badge). */
    val ownSettings: Int = 0,
    /** Started with "Start with…": this launch has options of its own. */
    val withOptions: Boolean = false,
)

/**
 * 15e + batch 2: what the screen shows until the game's first frame. The cover's colour and the
 * blurred cover behind; the cover, the name and who signs in; the four stages of the start with
 * the time each took ([BootStatus], the core's own counters); a bar that moves a step per stage;
 * past [BOOT_STILL_WORKING_SECONDS] why the first start is slow; and what the game starts with.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameLoadingScreen(
    status: BootStatus,
    art: File?,
    name: String?,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
    details: LoadingDetails? = null,
) {
    val colors = rememberCoverColors(art)
    // The second each stage was first seen, to time the finished ones.
    val started = remember { mutableStateMapOf<BootStatus.Stage, Long>() }
    LaunchedEffect(status.stage) { if (status.stage !in started) started[status.stage] = status.seconds }
    // Only the game's 64 px icon: drawn as the composed cover the library shows.
    val smart = remember(art) {
        art?.let { f ->
            runCatching { BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(f.path, it) }.outWidth }
                .getOrDefault(0) in 1..160
        } ?: false
    }
    WithCoverColors(colors?.dyn, colors?.accent) {
        val c = Xd.colors
        Box(modifier.fillMaxSize().background(Color.Black)) {
            CBackground(art)
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val portrait = maxHeight > maxWidth
                val cover: @Composable (Dp) -> Unit = { w ->
                    if (art != null) Box(Modifier.width(w)) { GameCover(art, name.orEmpty(), smart, radius = 12.dp) }
                }
                val body: @Composable ColumnScope.() -> Unit = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ZephyrOrbitMark(30.dp)
                        XdEyebrow(stringResource(R.string.xd_boot_opening, status.seconds))
                    }
                    if (!name.isNullOrBlank()) Text(name, style = XdText.hero, color = c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val who = listOfNotNull(details?.profile?.let { stringResource(R.string.xd_boot_signs_in, it) },
                        details?.titleId?.let { stringResource(R.string.xd_boot_title, it) })
                    if (who.isNotEmpty()) Text(who.joinToString(" · "), style = XdText.bodySm, color = c.fg2)
                    Steps(status, started, Modifier.padding(top = 10.dp))
                    XdBar(bootProgress(status.stage), Modifier.widthIn(max = 520.dp).padding(top = 12.dp))
                    if (status.seconds >= BOOT_STILL_WORKING_SECONDS) {
                        XdNote(stringResource(R.string.boot_still_working), modifier = Modifier.widthIn(max = 520.dp).padding(top = 8.dp))
                    }
                    if (details != null && (details.badges.isNotEmpty() || details.ownSettings > 0 || details.withOptions)) {
                        FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            details.badges.forEachIndexed { i, b -> XdBadge(b, icon = if (i == 0) XdIcons.chip else null) }
                            if (details.ownSettings > 0) XdBadge(pluralStringResource(R.plurals.xd_boot_own_settings, details.ownSettings, details.ownSettings),
                                tone = xendroid.compose.ui.design.BadgeTone.ACCENT)
                            if (details.withOptions) XdBadge(stringResource(R.string.xd_boot_with_options), tone = xendroid.compose.ui.design.BadgeTone.ACCENT,
                                icon = XdIcons.sliders)
                        }
                    }
                }
                val scroll = rememberScrollState()
                if (portrait) {
                    Column(Modifier.align(Alignment.Center).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 26.dp, vertical = 70.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        cover(150.dp)
                        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = body)
                    }
                } else {
                    Row(Modifier.align(Alignment.Center).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 56.dp, vertical = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                        cover(176.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), content = body)
                    }
                }
            }
            if (onCancel != null) {
                if (Xd.controller) {
                    val swap = LocalSwapConfirm.current
                    Box(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing)) {
                        CHints(listOf(XdHint(if (swap) "A" else "B", stringResource(R.string.xd_back), onCancel)))
                    }
                } else {
                    Box(Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(14.dp)) {
                        XdButton(stringResource(R.string.xd_back), onCancel, kind = XdButtonKind.GHOST, size = XdButtonSize.SM, icon = XdIcons.back)
                    }
                }
            }
        }
    }
}

/** The four stages, each done (with the time it took), going on now, or still to come. */
@Composable
private fun Steps(status: BootStatus, started: Map<BootStatus.Stage, Long>, modifier: Modifier = Modifier) {
    val c = Xd.colors
    Column(modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (stage in BootStatus.Stage.entries) {
            val done = stage.ordinal < status.stage.ordinal
            val now = stage == status.stage
            val text = when (stage) {
                BootStatus.Stage.EMULATOR -> stringResource(R.string.xd_boot_step_emulator)
                BootStatus.Stage.GAME -> stringResource(R.string.xd_boot_step_game)
                BootStatus.Stage.GRAPHICS -> if (done || now) pluralStringResource(R.plurals.xd_boot_step_graphics_n,
                    status.pipelines.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), status.pipelines)
                    else stringResource(R.string.xd_boot_step_graphics)
                BootStatus.Stage.FIRST_FRAME -> stringResource(R.string.xd_boot_step_first_frame)
            }
            val took = if (done) {
                val from = started[stage] ?: 0L
                val to = BootStatus.Stage.entries.drop(stage.ordinal + 1).firstNotNullOfOrNull { started[it] } ?: status.seconds
                stringResource(R.string.xd_boot_seconds, (to - from).coerceAtLeast(0))
            } else null
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(when { done -> XdIcons.checkCircle; now -> XdIcons.refresh; else -> XdIcons.clock }, null, Modifier.size(20.dp),
                    tint = when { done -> c.acc; now -> c.fg; else -> c.fg3 })
                Text(text, style = XdText.body.copy(fontSize = 15.sp), color = if (done || now) c.fg else c.fg3, modifier = Modifier.weight(1f, fill = false))
                if (took != null) Text(took, style = XdText.monoSm, color = c.fg3)
            }
        }
    }
}
