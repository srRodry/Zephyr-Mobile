package xendroid.compose.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xendroid.compose.R
import xendroid.compose.compatibility.CompatCatalog
import xendroid.compose.data.Game
import xendroid.compose.data.GameCollections
import xendroid.compose.data.GameFormat
import xendroid.compose.patches.GamePatchesViewModel
import xendroid.compose.sessions.RunPerformance
import xendroid.compose.sessions.RunState
import xendroid.compose.sessions.SessionRun
import xendroid.compose.ui.design.ChartCaption
import xendroid.compose.ui.design.FpsChart
import xendroid.compose.ui.design.FrameTimeChart
import xendroid.compose.ui.design.GameCover
import xendroid.compose.ui.design.NoteTone
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdButtonSize
import androidx.compose.foundation.layout.heightIn
import xendroid.compose.ui.design.XdCard
import xendroid.compose.ui.design.XdEyebrow
import xendroid.compose.ui.design.XdGroupHeader
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdKpi
import xendroid.compose.ui.design.XdKv
import xendroid.compose.ui.design.XdLink
import xendroid.compose.ui.design.XdListRow
import xendroid.compose.ui.design.XdNote
import xendroid.compose.ui.design.XdPath
import xendroid.compose.ui.design.XdSheet
import xendroid.compose.ui.design.focusRing
import xendroid.compose.ui.design.XdStatusPill
import xendroid.compose.ui.design.XdSwitch
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.XdTwoColumns
import xendroid.compose.ui.library.CoverArt
import xendroid.compose.ui.library.GameActions
import xendroid.compose.ui.library.GameLibraryViewModel
import xendroid.compose.ui.library.LibraryPlane
import xendroid.compose.ui.library.LibraryData
import xendroid.compose.ui.library.RunTimelineDialog
import xendroid.compose.ui.library.compatShortText
import xendroid.compose.ui.library.discBadge
import xendroid.compose.ui.library.formatLabel
import xendroid.compose.ui.library.formatSize
import xendroid.compose.ui.library.playTime
import xendroid.compose.ui.library.playedAgo
import xendroid.compose.ui.library.tone

/**
 * The cards of the game sheet: the hero (controller "Play"), the overview, performance with the
 * last run's charts and timeline, patches and content, saves and data, compatibility. Built per
 * composition from what the screen already collected.
 */
class GameCards(
    private val game: Game,
    private val data: LibraryData,
    private val info: GameLibraryViewModel.GameDetails?,
    private val art: CoverArt,
    private val actions: GameActions,
    private val library: GameLibraryViewModel,
    private val patches: GamePatchesViewModel?,
    private val patchState: GamePatchesViewModel.UiState,
    private val links: GameScreenLinks,
    private val activeProfile: String?,
    private val onSection: (String) -> Unit,
    private val quick: @Composable () -> Unit,
    private val changedCount: Int,
) {
    private val run: SessionRun? get() = info?.lastRun
    private val perf: RunPerformance? get() = run?.performance?.takeIf { it.sampledSeconds > 0 }

    /** Controller "Play": the big cover, name, rating, play time, last session and Play. */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun Hero(onPlay: () -> Unit) {
        val c = Xd.colors
        val played = data.played(game)
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp)) {
            Box(Modifier.width(150.dp)) {
                GameCover(art.model, game.name, art.smart, favorite = data.favorite(game), discLabel = discBadge(game), radius = 12.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                XdEyebrow(listOfNotNull(formatLabel(game.format), game.titleId).joinToString(" · "))
                Text(game.name, style = XdText.hero, color = c.fg, maxLines = 3, overflow = TextOverflow.Ellipsis)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    XdStatusPill(compatShortText(data.status(game)), data.status(game).tone(), Modifier.align(Alignment.CenterVertically))
                    Text(if (played != null) listOfNotNull(playTime(played), playedAgo(played)).joinToString(" · ")
                        else stringResource(R.string.xd_lib_never_played), style = XdText.bodySm, color = c.fg2,
                        modifier = Modifier.align(Alignment.CenterVertically))
                }
                perf?.let { p ->
                    val median = p.fpsPercentile(0.5)
                    val low = p.fpsPercentile(0.05)
                    if (median != null && low != null) Text(stringResource(R.string.xd_game_hero_last, median, low), style = XdText.bodySm, color = c.fg2)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(top = 6.dp)) {
                    XdButton(stringResource(R.string.lib_play), onPlay, kind = XdButtonKind.PRIMARY, size = XdButtonSize.LG,
                        icon = XdIcons.play, enabled = !actions.preparing)
                    activeProfile?.let { Text(stringResource(R.string.lib_signs_in_as, it), style = XdText.small, color = c.fg3) }
                }
            }
        }
    }

    /** Touch "Overview": last session, quick settings, compatibility, patches and content. */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun Overview() {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth > 620.dp
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LibraryPlane(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        GameSheetLink(stringResource(R.string.xd_game_sec_all), GameSections.SETTINGS, XdIcons.sliders, onSection)
                        GameSheetLink(stringResource(R.string.xd_game_sec_content), GameSections.CONTENT, XdIcons.patch, onSection)
                        GameSheetLink(stringResource(R.string.xd_game_sec_data), GameSections.DATA, XdIcons.save, onSection)
                        GameSheetLink(stringResource(R.string.xd_game_sec_perf), GameSections.PERF, XdIcons.chart, onSection)
                    }
                }
                LastSessionCard(full = false)
                XdTwoColumns(wide,
                    left = {
                        XdCard(title = stringResource(R.string.xd_game_sec_quick), icon = XdIcons.sliders,
                            trailing = pluralStringResource(R.plurals.xd_set_changed_count, changedCount, changedCount)) {
                            quick()
                            XdLink(stringResource(R.string.xd_lib_all_settings), { onSection(GameSections.SETTINGS) })
                        }
                        PatchesCard(limit = 3)
                    },
                    right = {
                        XdCard(title = stringResource(R.string.xd_game_sec_compat), icon = XdIcons.shield) { CompatibilityBody() }
                        XdCard(title = stringResource(R.string.xd_area_content), icon = XdIcons.box) { ContentFacts() }
                    },
                )
            }
        }
    }

    /**
     * "Performance": one run in full, its timeline, the settings it ran with and what to do with
     * it. Round 2: the newest run with numbers (a session that died early has none), or the one
     * picked from the list of the game's sessions.
     */
    @Composable
    fun Performance() {
        val runs = info?.runs.orEmpty()
        var picked by rememberSaveable(game.identityKey) { mutableStateOf<String?>(null) }
        val chosen = runs.firstOrNull { it.runId == picked } ?: runs.firstOrNull { it.hasNumbers() } ?: run
        val lastEvents = info?.lastRunEvents
        val events by produceState<xendroid.compose.sessions.RunEventLog?>(null, chosen?.runId, lastEvents) {
            value = when {
                chosen == null -> null
                chosen.runId == run?.runId && lastEvents != null -> lastEvents
                else -> withContext(Dispatchers.IO) {
                    runCatching { xendroid.compose.sessions.SessionRuns.store().events(chosen.runId) }.getOrNull()
                }
            }
        }
        var choosing by remember { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (chosen != null && runs.size > 1) RunPicker(chosen, runs.size) { choosing = true }
            if (chosen != null && picked == null && chosen.runId != run?.runId) XdNote(stringResource(R.string.xd_perf_session_latest_with_numbers))
            SessionCard(chosen, full = true)
            Timeline(events)
            if (chosen != null) SettingsInEffect(chosen)
            XdCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val r = chosen
                    XdButton(stringResource(R.string.xd_perf_share), {
                        if (r != null) actions.showReport(xendroid.compose.sessions.RunReports.build(r, events,
                            info?.compatibility?.reports.orEmpty(),
                            xendroid.compose.sessions.SessionRuns.reportDevice(xendroid.compose.BuildConfig.VERSION_NAME),
                            System.currentTimeMillis()))
                    }, size = XdButtonSize.SM, icon = XdIcons.share, enabled = r != null)
                    XdButton(stringResource(R.string.xd_guide_compare), links.onCompare, size = XdButtonSize.SM, kind = XdButtonKind.GHOST)
                    XdButton(stringResource(R.string.xd_perf_logs), links.onDiagnostics, size = XdButtonSize.SM, kind = XdButtonKind.GHOST)
                }
            }
        }
        if (choosing) RunsSheet(runs, chosen, onPick = { picked = it.runId; choosing = false }, onDismiss = { choosing = false })
    }

    /** "Patches and content": every patch, the title update and DLC, and the file. */
    @Composable
    fun Content() {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PatchesCard(limit = null)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                XdTwoColumns(maxWidth > 620.dp,
                    left = {
                        XdCard(title = stringResource(R.string.xd_game_tu_dlc), icon = XdIcons.box) {
                            ContentFacts(withCache = false)
                            XdButton(stringResource(R.string.lib_manage_content), links.onContent, size = XdButtonSize.SM, enabled = info?.titleId != null)
                        }
                    },
                    right = {
                        XdCard(title = stringResource(R.string.xd_game_file), icon = XdIcons.disc) {
                            XdPath(game.launchUri)
                            XdKv(listOfNotNull(
                                stringResource(R.string.xd_game_format) to formatLabel(game.format),
                                if (game.isMultiDisc) stringResource(R.string.xd_game_discs) to stringResource(R.string.lib_disc_of, game.discNumber, game.discCount) else null,
                            ))
                        }
                    },
                )
            }
        }
    }

    /** "Saves and data": saves, shader cache, cover, collections, compression, shortcut, sessions. */
    @Composable
    fun Data() {
        val context = LocalContext.current
        val custom = remember(game.identityKey, art) { !art.smart || library.hasCustomCover(game) }
        XdCard {
            XdListRow(stringResource(R.string.lib_saves), icon = XdIcons.save, subtitle = stringResource(R.string.xd_data_saves_sub)) {
                XdButton(stringResource(R.string.xd_data_saves_open), links.onSaves, size = XdButtonSize.SM, enabled = info?.titleId != null)
            }
            val cache = info?.shaderCache
            XdListRow(stringResource(R.string.xd_data_cache), icon = XdIcons.layers,
                subtitle = when {
                    cache == null -> "—"
                    cache.first == 0 -> stringResource(R.string.lib_shader_cache_none)
                    else -> pluralStringResource(R.plurals.lib_shader_cache_files, cache.first,
                        android.text.format.Formatter.formatShortFileSize(context, cache.second), cache.first)
                }) {
                XdButton(stringResource(R.string.xd_data_clear), { cache?.let { actions.clearShaderCache(game, it.second) } },
                    size = XdButtonSize.SM, kind = XdButtonKind.GHOST, icon = XdIcons.trash, enabled = (cache?.first ?: 0) > 0)
            }
            if (xendroid.compose.data.CoverStore.normalize(game.titleId) != null) {
                XdListRow(stringResource(R.string.xd_data_cover), icon = XdIcons.image,
                    subtitle = stringResource(if (custom) R.string.xd_data_cover_custom else R.string.xd_data_cover_own)) {
                    if (custom) XdButton(stringResource(R.string.xd_data_cover_reset), { actions.ownIcon(game) }, size = XdButtonSize.SM, kind = XdButtonKind.GHOST)
                    XdButton(stringResource(R.string.lib_change_cover), { actions.pickCover(game) }, size = XdButtonSize.SM, kind = XdButtonKind.GHOST)
                }
            }
            val names = GameCollections.namesOf(data.collections, game.identityKey)
            XdListRow(stringResource(R.string.lib_collections), icon = XdIcons.layers,
                subtitle = names.joinToString(", ").ifEmpty { stringResource(R.string.lib_no_collection) }) {
                XdButton(stringResource(R.string.xd_data_edit), { actions.editCollections(game) }, size = XdButtonSize.SM, kind = XdButtonKind.GHOST)
            }
            if (game.format == GameFormat.ISO) XdListRow(stringResource(R.string.lib_compress), icon = XdIcons.zip,
                subtitle = stringResource(R.string.xd_data_compress_sub)) {
                XdButton(stringResource(R.string.xd_data_compress), { actions.compress(game) }, size = XdButtonSize.SM, kind = XdButtonKind.GHOST)
            }
            if (actions.canShortcut) XdListRow(stringResource(R.string.lib_shortcut), icon = XdIcons.link,
                subtitle = stringResource(R.string.xd_data_shortcut_sub)) {
                XdButton(stringResource(R.string.xd_data_create), { actions.shortcut(game) }, size = XdButtonSize.SM, kind = XdButtonKind.GHOST)
            }
            val runs = data.played(game)?.runs ?: 0
            XdListRow(stringResource(R.string.lib_sessions), icon = XdIcons.timeline, divider = false,
                subtitle = if (runs > 0) pluralStringResource(R.plurals.xd_data_runs, runs, runs) else stringResource(R.string.xd_data_no_runs)) {
                XdButton(stringResource(R.string.xd_data_open), links.onDiagnostics, size = XdButtonSize.SM, kind = XdButtonKind.GHOST)
            }
        }
    }

    /** Controller "Compatibility". */
    @Composable
    fun Compatibility() {
        XdCard { CompatibilityBody() }
    }

    // ------------------------------------------------------------------ pieces

    @Composable
    private fun LastSessionCard(full: Boolean) {
        val played = data.played(game)
        val trailing = if (played != null) stringResource(R.string.xd_game_ov_trailing, playedAgo(played).orEmpty(), playTime(played).orEmpty())
        else stringResource(R.string.xd_lib_never_played)
        XdCard(title = stringResource(R.string.xd_lib_last_session), icon = XdIcons.chart, trailing = trailing) {
            PerfBody(full, run)
            if (!full && perf != null) XdLink(stringResource(R.string.xd_game_ov_perf_link), { onSection(GameSections.PERF) })
        }
    }

    /** Performance's card: the last session as the overview shows it, or the one picked, by its date. */
    @Composable
    private fun SessionCard(r: SessionRun?, full: Boolean) {
        if (r == null || r.runId == run?.runId) { LastSessionCard(full); return }
        XdCard(title = runWhen(r), icon = XdIcons.chart, trailing = r.playedMs?.let { xendroid.compose.sessions.formatPlayTime(it) }) {
            PerfBody(full, r)
        }
    }

    /** The session shown, its date, length and FPS, and how many there are: a tap lists them. */
    @Composable
    private fun RunPicker(r: SessionRun, count: Int, onClick: () -> Unit) {
        val c = Xd.colors
        val shape = RoundedCornerShape(12.dp)
        Row(
            Modifier.fillMaxWidth().focusRing(shape).clip(shape).background(c.s1).clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(XdIcons.timeline, null, Modifier.size(18.dp), tint = c.acc)
            Column(Modifier.weight(1f)) {
                Text(listOfNotNull(runWhen(r), r.playedMs?.let { xendroid.compose.sessions.formatPlayTime(it) }, runFps(r)).joinToString(" · "),
                    style = XdText.label, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(pluralStringResource(R.plurals.xd_perf_sessions_count, count, count) + " · " + stringResource(R.string.xd_perf_session_change),
                    style = XdText.tiny, color = c.fg3)
            }
            Icon(XdIcons.chevD, null, Modifier.size(16.dp), tint = c.fg2)
        }
    }

    /** The game's sessions, newest first: when, how it ended, its numbers and driver. */
    @Composable
    private fun RunsSheet(runs: List<SessionRun>, chosen: SessionRun?, onPick: (SessionRun) -> Unit, onDismiss: () -> Unit) {
        XdSheet(onDismiss = onDismiss, title = stringResource(R.string.xd_perf_sessions_title, game.name),
            subtitle = stringResource(R.string.xd_perf_sessions_sub)) {
            Column {
                runs.forEachIndexed { i, r ->
                    val numbers = r.performance?.takeIf { it.sampledSeconds > 0 }?.let { p ->
                        val median = p.fpsPercentile(0.5); val low = p.fpsPercentile(0.05); val p99 = p.frameTimeUpperMs(0.99)
                        if (median != null && low != null) stringResource(R.string.xd_perf_session_numbers, median, low,
                            p99?.let { if (it >= RunPerformance.FRAME_TIME_OPEN_BUCKET) "$it+" else "$it" } ?: "—") else null
                    } ?: stringResource(R.string.xd_perf_session_no_numbers)
                    XdListRow(runWhen(r), icon = if (r.runId == chosen?.runId) XdIcons.check else when (r.state) {
                            RunState.FAILED -> XdIcons.xCircle
                            RunState.INTERRUPTED -> XdIcons.alert
                            else -> XdIcons.timeline
                        },
                        subtitle = listOfNotNull(runEnding(r), numbers, r.driver?.label).joinToString(" · "),
                        divider = i < runs.lastIndex, onClick = { onPick(r) })
                }
            }
        }
    }

    /** "Today, 21:40", "Yesterday, 18:02", "3 Oct, 21:40". */
    @Composable
    private fun runWhen(r: SessionRun): String {
        val context = LocalContext.current
        return android.text.format.DateUtils.getRelativeDateTimeString(context, r.runningAt ?: r.startedAt,
            android.text.format.DateUtils.DAY_IN_MILLIS, android.text.format.DateUtils.WEEK_IN_MILLIS,
            android.text.format.DateUtils.FORMAT_ABBREV_ALL).toString()
    }

    private fun runFps(r: SessionRun): String? = r.performance?.takeIf { it.sampledSeconds > 0 }?.fpsPercentile(0.5)?.let { "$it FPS" }

    private fun SessionRun.hasNumbers(): Boolean = (performance?.sampledSeconds ?: 0) > 0

    /** The settings away from the defaults as [r] booted (C06), now on the sheet. */
    @Composable
    private fun SettingsInEffect(r: SessionRun) {
        val c = Xd.colors
        val lines = r.changedSettings
        XdCard(title = stringResource(R.string.xd_perf_settings_title), icon = XdIcons.sliders, trailing = lines?.size?.takeIf { it > 0 }?.toString()) {
            when {
                lines == null -> Text(stringResource(R.string.xd_perf_settings_unknown), style = XdText.note, color = c.fg3)
                lines.isEmpty() -> Text(stringResource(R.string.xd_perf_settings_none), style = XdText.note, color = c.fg3)
                else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (line in lines) Text(line, style = XdText.monoSm, color = c.fg2)
                }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun PerfBody(full: Boolean, r: SessionRun?) {
        val c = Xd.colors
        val p = r?.performance?.takeIf { it.sampledSeconds > 0 }
        when {
            r == null && data.played(game) == null -> Text(stringResource(R.string.xd_perf_none_yet), style = XdText.bodySm, color = c.fg3)
            r == null -> Text(stringResource(R.string.xd_game_loading), style = XdText.bodySm, color = c.fg3)
            p == null -> {
                Text(runEnding(r), style = XdText.bodySm, color = c.fg2)
                XdNote(stringResource(R.string.xd_perf_no_numbers))
            }
            else -> {
                val median = p.fpsPercentile(0.5)
                val low = p.fpsPercentile(0.05)
                val p99 = p.frameTimeUpperMs(0.99)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val perRow = if (maxWidth > 520.dp) 4 else 2
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = perRow) {
                        val kpi = Modifier.weight(1f)
                        XdKpi(median?.toString() ?: "—", "FPS", stringResource(R.string.xd_perf_median), kpi)
                        XdKpi(low?.toString() ?: "—", "FPS", stringResource(R.string.xd_perf_low), kpi)
                        XdKpi(p99?.let { if (it >= RunPerformance.FRAME_TIME_OPEN_BUCKET) "$it+" else "$it" } ?: "—", "ms", stringResource(R.string.xd_perf_p99), kpi)
                        XdKpi(p.firstFrameSeconds?.toString() ?: "—", "s", stringResource(R.string.xd_perf_first), kpi)
                    }
                }
                FpsChart(p.fpsHistogram, median, low, p.fpsLimits.lastOrNull()?.takeIf { it > 0 },
                    stringResource(R.string.xd_perf_median_label), stringResource(R.string.xd_perf_low_label))
                ChartCaption(stringResource(R.string.xd_perf_fps_caption, p.sampledSeconds))
                if (full) {
                    val ft50 = p.frameTimeUpperMs(0.5)
                    if (p.frames > 0) {
                        FrameTimeChart(p.frameTimeHistogramMs, ft50, p99)
                        ChartCaption(stringResource(R.string.xd_perf_ft_caption, ft50 ?: 0))
                    }
                    XdKv(perfFacts(r, p))
                    Text(runEnding(r), style = XdText.note, color = c.fg3)
                    xendroid.compose.sessions.describeFrameGeneration(p)?.let { XdNote(stringResource(R.string.xd_perf_fg, it), icon = XdIcons.spark) }
                }
            }
        }
        if (full && r != null) r.nativeBacktrace?.let { crash ->
            XdNote(stringResource(R.string.xd_perf_crash, xendroid.compose.sessions.describeNativeCrash(crash)), tone = NoteTone.ERROR)
        }
    }

    @Composable
    private fun perfFacts(r: SessionRun, p: RunPerformance): List<Pair<String, String>> = buildList {
        p.pipelineCreations?.takeIf { it > 0 }?.let { n ->
            add(stringResource(R.string.xd_perf_pipelines) to stringResource(R.string.xd_perf_pipelines_value, "%,d".format(n),
                "%.1f".format((p.pipelineCreationMs ?: 0L) / 1000.0)))
        }
        val backend = p.audioBackend
        val blocks = p.audioBlocks
        val concealed = p.audioConcealedBlocks
        if (backend != null && blocks != null && concealed != null) {
            add(stringResource(R.string.xd_perf_audio, backend) to when {
                blocks == 0L -> stringResource(R.string.xd_perf_audio_silent)
                concealed == 0L -> stringResource(R.string.xd_perf_audio_ok, "%,d".format(blocks))
                else -> stringResource(R.string.xd_perf_audio_bad, "%,d".format(concealed), "%,d".format(blocks))
            })
        }
        val start = p.batteryStartC
        val max = p.batteryMaxC
        if (start != null && max != null) {
            add(stringResource(R.string.xd_perf_battery) to stringResource(R.string.xd_perf_battery_value, "%.0f".format(start), "%.0f".format(max),
                p.batteryEndC?.let { "%.0f".format(it) } ?: "—"))
        }
        if (p.fpsLimits.isNotEmpty() || p.displayHz.isNotEmpty()) {
            val unlimited = stringResource(R.string.xd_perf_unlimited)
            val limits = p.fpsLimits.joinToString(" → ") { if (it == 0) unlimited else "$it FPS" }
            val hz = p.displayHz.joinToString(" → ") { "$it Hz" }
            add(stringResource(R.string.xd_perf_pacing) to listOf(limits, hz).filter { it.isNotEmpty() }.joinToString(" · "))
        }
        r.driver?.let { add(stringResource(R.string.xd_perf_driver) to it.label) }
        if (r.runId == run?.runId) info?.lastProfile?.let { add(stringResource(R.string.xd_perf_profile) to it) }
    }

    @Composable
    private fun runEnding(r: SessionRun): String {
        val played = r.playedMs?.let { xendroid.compose.sessions.formatPlayTime(it) }
        return when (r.state) {
            RunState.ENDED -> if (r.titleId != null && r.runningAt != null) stringResource(R.string.xd_run_ended, played ?: "—")
                else stringResource(R.string.xd_run_closed_early)
            RunState.FAILED -> stringResource(R.string.xd_run_failed, played ?: "—", r.endReason ?: "?")
            RunState.INTERRUPTED -> stringResource(R.string.xd_run_interrupted, played ?: "—", r.endReason ?: "?")
            else -> stringResource(R.string.xd_run_open)
        }
    }

    @Composable
    private fun Timeline(events: xendroid.compose.sessions.RunEventLog? = info?.lastRunEvents) {
        val log = events?.takeIf { it.events.isNotEmpty() } ?: return
        val c = Xd.colors
        var all by remember { mutableStateOf(false) }
        XdCard(title = stringResource(R.string.lib_timeline), icon = XdIcons.timeline,
            trailing = pluralStringResource(R.plurals.lib_timeline_note, log.events.size, log.events.size)) {
            val shown = log.events.take(14)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (e in shown) {
                    val line = xendroid.compose.sessions.describeEvent(e)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(line.substringBefore(' '), style = XdText.monoSm, color = c.fg3, modifier = Modifier.width(56.dp))
                        Text(line.substringAfter(' '), style = XdText.note, color = c.fg2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (log.events.size > shown.size || log.dropped > 0) XdLink(stringResource(R.string.xd_perf_timeline_all), { all = true })
        }
        if (all) RunTimelineDialog(log, onDismiss = { all = false })
    }

    @Composable
    private fun PatchesCard(limit: Int?) {
        val c = Xd.colors
        val loaded = patchState as? GamePatchesViewModel.UiState.Loaded
        // Round 2: by version. Once a run here recorded the game's hashes, the files for that
        // version come first, then the others (with the TU their name or comments state), then
        // those that state none; until then, in the files' order.
        val match = { f: xendroid.compose.patches.PatchFile -> loaded?.versions?.get(f.fileName) ?: xendroid.compose.patches.PatchVersion.Match.UNKNOWN }
        val groups: List<Pair<xendroid.compose.patches.PatchVersion.Match?, List<xendroid.compose.patches.PatchFile>>> =
            if (loaded?.versionKnown == true) xendroid.compose.patches.PatchVersion.Match.entries
                .map { m -> m to loaded.files.filter { match(it) == m } }.filter { it.second.isNotEmpty() }
            else listOf(null to loaded?.files.orEmpty())
        val entries = groups.flatMap { (_, files) -> files.flatMap { f -> f.entries.map { f to it } } }
        val on = entries.count { it.second.isEnabled }
        XdCard(title = stringResource(if (limit == null) R.string.xd_game_patches_title else R.string.xd_game_patches), icon = XdIcons.patch,
            trailing = if (entries.isNotEmpty()) stringResource(R.string.xd_game_patches_on, on, entries.size) else null) {
            when (val s = patchState) {
                is GamePatchesViewModel.UiState.Error -> XdNote(s.message, tone = NoteTone.ERROR)
                GamePatchesViewModel.UiState.Loading -> {}
                else -> if (entries.isEmpty()) Text(stringResource(if (patches == null) R.string.xd_game_no_title else R.string.xd_game_patches_none),
                    style = XdText.note, color = c.fg3)
            }
            if (limit == null && entries.isNotEmpty()) Text(stringResource(R.string.xd_game_patches_note), style = XdText.note, color = c.fg3)
            if (limit == null && loaded != null && !loaded.versionKnown && entries.isNotEmpty()) {
                Text(stringResource(R.string.pt_version_unknown), style = XdText.note, color = c.fg3)
            }
            val many = (loaded?.files?.size ?: 0) > 1
            val row: @Composable (xendroid.compose.patches.PatchFile, xendroid.compose.patches.PatchEntry, Boolean) -> Unit = { file, entry, label ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, style = XdText.labelSm, color = c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val sub = listOfNotNull(if (label) fileLabel(file) else null, entry.desc?.takeIf { limit == null && it.isNotBlank() })
                        if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = XdText.tiny, color = c.fg3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    XdSwitch(entry.isEnabled, { patches?.toggle(file, entry, it) }, contentDescription = entry.name)
                }
                HorizontalDivider(thickness = 1.dp, color = c.line)
            }
            Column {
                if (limit != null) {
                    for ((file, entry) in entries.take(limit)) row(file, entry, many)
                } else for ((m, files) in groups) {
                    if (m != null && (groups.size > 1 || m != xendroid.compose.patches.PatchVersion.Match.YOURS)) {
                        XdGroupHeader(stringResource(when (m) {
                            xendroid.compose.patches.PatchVersion.Match.YOURS -> R.string.xd_game_patches_yours
                            xendroid.compose.patches.PatchVersion.Match.OTHER -> R.string.xd_game_patches_others
                            xendroid.compose.patches.PatchVersion.Match.UNKNOWN -> R.string.xd_game_patches_any
                        }), files.sumOf { it.entries.size })
                        if (m == xendroid.compose.patches.PatchVersion.Match.OTHER) {
                            Text(stringResource(R.string.xd_game_patches_others_note), style = XdText.note, color = c.fg3)
                        }
                    }
                    for (file in files) {
                        if (many) Text(fileLabel(file), style = XdText.small, color = c.fg2, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                        for (entry in file.entries) row(file, entry, false)
                    }
                }
            }
            if (loaded != null && loaded.conflicts.isNotEmpty() && limit == null) {
                XdNote(pluralStringResource(R.plurals.xd_game_patch_conflicts, loaded.conflicts.size, loaded.conflicts.size), tone = NoteTone.WARN)
            }
            if (limit != null && entries.size > limit) XdLink(stringResource(R.string.xd_game_patches_all, entries.size), { onSection(GameSections.CONTENT) })
            if (limit == null && patches != null) XdButton(stringResource(R.string.xd_game_patches_manage), links.onPatches, size = XdButtonSize.SM,
                kind = XdButtonKind.GHOST, icon = XdIcons.patch)
        }
    }

    /** A patch file as the sheet names it: its variant ("Undertow (TU2)") and, when the name does
     *  not say it, the version its comments state. */
    @Composable
    private fun fileLabel(file: xendroid.compose.patches.PatchFile): String {
        val label = if (file.mine) stringResource(R.string.pt_added_by_you, file.variantLabel) else file.variantLabel
        val version = file.versionLabel?.takeIf { v -> !label.replace(" ", "").contains(v.replace(" ", ""), ignoreCase = true) }
        return listOfNotNull(label, version).joinToString(" · ")
    }

    @Composable
    private fun ContentFacts(withCache: Boolean = true) {
        val context = LocalContext.current
        val none = stringResource(R.string.xd_game_none)
        XdKv(listOfNotNull(
            stringResource(R.string.xd_game_tu) to (info?.updates?.let { list ->
                if (list.isEmpty()) none
                else list.joinToString(", ") { n -> info.updateVersions[n]?.let { v -> "$n (${context.getString(R.string.xd_game_version_of, v)})" } ?: n }
            } ?: "—"),
            stringResource(R.string.xd_game_dlc) to (info?.dlcCount?.let { if (it == 0) none else it.toString() } ?: "—"),
            if (withCache) stringResource(R.string.xd_data_cache) to (info?.shaderCache?.let { (files, bytes) ->
                if (files == 0) none else android.text.format.Formatter.formatShortFileSize(context, bytes)
            } ?: "—") else null,
        ))
    }

    @Composable
    private fun CompatibilityBody() {
        val c = Xd.colors
        val latest = info?.compatibility?.latest
        val status = latest?.status ?: data.status(game)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            XdStatusPill(compatShortText(status), status.tone())
            latest?.let { Text(java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(it.createdAt)),
                style = XdText.small, color = c.fg3) }
        }
        if (latest != null) {
            if (latest.note.isNotBlank()) Text(latest.note, style = XdText.bodySm, color = c.fg)
            Text(listOfNotNull(stringResource(R.string.xd_compat_build, latest.build), latest.gpu, latest.driverLabel,
                latest.mediaId?.let { stringResource(R.string.lib_media_short, it) }, latest.disc?.let { stringResource(R.string.lib_disc_short, it) })
                .joinToString(" · "), style = XdText.note, color = c.fg3)
        } else Text(stringResource(R.string.xd_compat_not_rated), style = XdText.note, color = c.fg3)
        XdButton(stringResource(if (latest != null) R.string.xd_compat_rate_again else R.string.xd_compat_rate), { actions.rate(game) },
            size = XdButtonSize.SM, enabled = info?.titleId != null)
        Text(stringResource(R.string.lib_rate_note), style = XdText.small, color = c.fg3)
        info?.catalog?.let { CatalogBlock(it) }
    }

    /** C04: what the signed catalog says about the game, one line per build/GPU/driver. */
    @Composable
    private fun CatalogBlock(catalog: GameLibraryViewModel.CatalogView) {
        val c = Xd.colors
        HorizontalDivider(thickness = 1.dp, color = c.line)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.lib_catalog), style = XdText.label, color = c.fg, modifier = Modifier.weight(1f))
            XdButton(stringResource(if (catalog.refreshing) R.string.lib_catalog_downloading else R.string.common_refresh),
                { library.refreshCatalog(game) }, size = XdButtonSize.SM, kind = XdButtonKind.GHOST, icon = XdIcons.refresh, enabled = !catalog.refreshing)
        }
        val copy = catalog.copy
        if (copy == null) Text(stringResource(R.string.lib_catalog_none), style = XdText.note, color = c.fg3)
        else {
            Text(stringResource(R.string.lib_catalog_copy, copy.payload.sequence,
                java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(copy.fetchedAt))) +
                if (copy.freshness == CompatCatalog.Freshness.STALE) stringResource(R.string.lib_catalog_stale) else "",
                style = XdText.note, color = c.fg3)
            if (catalog.results.isEmpty()) Text(stringResource(R.string.lib_catalog_empty), style = XdText.note, color = c.fg3)
            catalog.results.take(4).forEach { setup ->
                Text((if (setup.thisSetup) stringResource(R.string.lib_catalog_this) else stringResource(R.string.lib_catalog_setup, setup.build, setup.gpu)) +
                    (if (setup.driver.isNotEmpty()) " · ${setup.driver}" else "") + ": ${setup.summary} (${setup.latestDate})",
                    style = XdText.note, color = c.fg2)
            }
            if (catalog.results.size > 4) Text(pluralStringResource(R.plurals.lib_catalog_more, catalog.results.size - 4, catalog.results.size - 4),
                style = XdText.note, color = c.fg3)
            if (catalog.results.any { !it.thisSetup }) Text(stringResource(R.string.lib_catalog_note), style = XdText.small, color = c.fg3)
        }
        catalog.message?.let { XdNote(it, tone = NoteTone.INFO) }
    }
}
