package xendroid.compose.ui.content

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.Date
import xendroid.compose.R
import xendroid.compose.ui.design.XdSearchField
import xendroid.compose.ui.design.XdSegmented
import xendroid.compose.core.ContentPaths
import xendroid.compose.saves.TrashedContent
import xendroid.compose.ui.content.ContentManagerViewModel.ContentEntry
import xendroid.compose.ui.content.ContentManagerViewModel.DeleteState
import xendroid.compose.ui.content.ContentManagerViewModel.GameContent
import xendroid.compose.ui.content.ContentManagerViewModel.ListState
import xendroid.compose.ui.design.LocalXdToast
import xendroid.compose.ui.design.NoteTone
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdArea
import xendroid.compose.ui.design.XdBar
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.BadgeTone
import xendroid.compose.ui.design.XdBadge
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdIconButton
import xendroid.compose.ui.design.XdButtonSize
import xendroid.compose.ui.design.XdCard
import xendroid.compose.ui.design.XdEmpty
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdListRow
import xendroid.compose.ui.design.XdNote
import xendroid.compose.ui.design.XdSection
import xendroid.compose.ui.design.XdSectionedScreen
import xendroid.compose.ui.design.XdSheet
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.XdTwoColumns
import xendroid.compose.ui.library.FolderBrowserScreen

object ContentSections {
    const val INSTALLED = "installed"
    const val DLC = "dlc"
    const val UPDATES = "updates"
    const val TRASH = "trash"
    const val INSTALL = "install"
}

/** What the content screens need from the rest of the app: games' names and covers, and
 *  opening one game's content from the Content area. */
class ContentLinks(
    val gameName: (titleId: String) -> String? = { null },
    val gameArt: (titleId: String) -> Any? = { null },
    val onOpenGame: (titleId: String, name: String) -> Unit = { _, _ -> },
)

/**
 * Lote 5: the Content area (every game's DLC and title updates, by game and with sizes; the trash
 * with its quota as a bar; installing, with the packages found in Downloads) and, opened from a
 * game, that game's content: DLC, title updates, its trash and installing for it. Removing sends
 * a package to the trash, restorable until it is deleted for good there; nothing goes by itself.
 * [installer]: the Content area installs any package (a whole game goes to the library); one
 * game's screen installs that game's DLC and title updates only.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContentScreen(
    vm: ContentManagerViewModel,
    onBack: () -> Unit,
    installer: InstallContentViewModel? = null,
    titleId: String? = null,
    gameName: String = "",
    art: Any? = null,
    links: ContentLinks = ContentLinks(),
    initialSection: String? = null,
) {
    val context = LocalContext.current
    val toast = LocalXdToast.current
    val c = Xd.colors
    val listState by vm.listState.collectAsStateWithLifecycle()
    val vmState by vm.state.collectAsStateWithLifecycle()
    val deleteState by vm.deleteState.collectAsStateWithLifecycle()
    val found by vm.found.collectAsStateWithLifecycle()
    val folders by vm.folderCounts.collectAsStateWithLifecycle()
    val free by vm.freeBytes.collectAsStateWithLifecycle()
    val installerState = installer?.state?.collectAsStateWithLifecycle()?.value ?: ContentInstallState.Idle
    var picking by remember { mutableStateOf(false) }
    var addingFolder by remember { mutableStateOf(false) }
    // An install ends on a sheet to read; a removal or a restore on a toast.
    var installing by remember { mutableStateOf(false) }
    val all = vm.allGames
    var section by rememberSaveable { mutableStateOf(initialSection ?: if (all) ContentSections.INSTALLED else ContentSections.DLC) }
    val busy = vmState is ContentInstallState.Busy || installerState is ContentInstallState.Busy

    fun install(path: String) {
        installing = true
        if (installer != null) installer.install(path) else vm.install(path)
    }

    // What the installer put in place shows once it is done.
    LaunchedEffect(installerState) {
        if (installerState is ContentInstallState.Done) { vm.refresh(); vm.lookForPackages() }
    }
    if (picking) {
        FolderBrowserScreen(onFileChosen = { path -> picking = false; install(path) }, onCancel = { picking = false })
        return
    }
    if (addingFolder) {
        FolderBrowserScreen(onFolderChosen = { path -> addingFolder = false; vm.addFolder(path) }, onCancel = { addingFolder = false },
            title = stringResource(R.string.xd_cm_folder_pick_title), hint = stringResource(R.string.xd_cm_folder_pick_hint))
        return
    }

    val loaded = listState as? ListState.Loaded
    val name: (String) -> String = { title -> links.gameName(title) ?: context.getString(R.string.xd_cm_title_n, title) }
    val size: (Long) -> String = { Formatter.formatShortFileSize(context, it) }
    val trash = XdSection(ContentSections.TRASH, stringResource(R.string.cm_tab_trash), XdIcons.trash,
        badge = loaded?.trashed?.size?.takeIf { it > 0 }?.toString()) {
        Listed(listState) { s ->
            TrashBody(s, all, name, size, busy, onEmpty = vm::requestEmptyTrash, onRestore = vm::restore, onPurge = vm::requestPurge)
        }
    }
    val installSection = XdSection(ContentSections.INSTALL, stringResource(R.string.xd_cm_sec_install), XdIcons.plus,
        heading = stringResource(R.string.xd_cm_install_heading)) {
        LaunchedEffect(Unit) { if (vm.found.value == null) vm.lookForPackages() }
        InstallBody(all, found, folders, free, busy, name, size, onPick = { picking = true }, onInstall = { install(it.path) },
            onLookAgain = vm::lookForPackages, onAddFolder = { addingFolder = true }, onRemoveFolder = vm::removeFolder)
    }
    val sections = if (all) listOf(
        XdSection(ContentSections.INSTALLED, stringResource(R.string.xd_cm_sec_installed), XdIcons.box,
            badge = loaded?.let { (it.dlc.size + it.updates.size).takeIf { n -> n > 0 }?.toString() },
            heading = stringResource(R.string.xd_cm_installed_heading)) {
            Listed(listState) { s ->
                InstalledAll(s, name, links.gameArt, size, busy, onOpen = { links.onOpenGame(it.titleId, name(it.titleId)) },
                    onRemove = vm::requestDelete, onInstall = { section = ContentSections.INSTALL })
            }
        },
        trash,
        installSection,
    ) else listOf(
        XdSection(ContentSections.DLC, "DLC", XdIcons.box, badge = loaded?.dlc?.size?.takeIf { it > 0 }?.toString()) {
            Listed(listState) { s -> PackageCard(s.dlc, stringResource(R.string.cm_no_dlc), size, busy, vm::requestDelete) }
        },
        XdSection(ContentSections.UPDATES, stringResource(R.string.cm_tab_updates), XdIcons.download,
            badge = loaded?.updates?.size?.takeIf { it > 0 }?.toString(), heading = stringResource(R.string.xd_cm_updates_heading)) {
            Listed(listState) { s -> PackageCard(s.updates, stringResource(R.string.cm_no_tu), size, busy, vm::requestDelete) }
        },
        trash,
        installSection,
    )
    val packages = loaded?.let { it.dlc.size + it.updates.size } ?: 0
    val subtitle = when {
        loaded == null -> null
        all -> stringResource(R.string.xd_cm_sub_all, pluralStringResource(R.plurals.xd_cm_packages, packages, packages),
            size(loaded.trashUsed), size(loaded.trashQuota))
        else -> stringResource(R.string.xd_cm_sub_game, titleId.orEmpty(), loaded.dlc.size,
            stringResource(if (loaded.updates.isNotEmpty()) R.string.xd_cm_with_tu else R.string.xd_cm_no_tu))
    }
    XdSectionedScreen(
        title = if (!all && gameName.isNotBlank()) stringResource(R.string.cm_title_game, gameName) else stringResource(R.string.cm_title),
        sections = sections, selected = section, onSelect = { section = it },
        area = if (all) XdArea.CONTENT else null,
        subtitle = subtitle,
        onBack = if (busy) null else onBack,
        headIcon = XdIcons.box,
        art = art,
        lead = if (all) null else ({ Cover(art, 40.dp) }),
        actions = if (all && section != ContentSections.INSTALL) ({
            XdButton(stringResource(R.string.xd_cm_install), { section = ContentSections.INSTALL }, size = XdButtonSize.SM, icon = XdIcons.plus)
        }) else null,
    )

    val shown = if (installerState !is ContentInstallState.Idle) installerState else vmState
    val dismiss: () -> Unit = { vm.dismiss(); installer?.dismiss(); installing = false }
    if (shown is ContentInstallState.Done && !installing) LaunchedEffect(shown) { toast.show(shown.message); dismiss() }
    else ContentInstallDialogs(shown, onDismiss = dismiss, onConfirmOverwrite = { path, display ->
        if (installerState is ContentInstallState.ConfirmOverwrite) installer?.confirmOverwrite(path, display) else vm.confirmOverwrite(path, display)
    })

    when (val d = deleteState) {
        is DeleteState.Confirm -> XdSheet(onDismiss = vm::dismiss, title = stringResource(R.string.cm_remove_title), actions = {
            XdButton(stringResource(R.string.common_cancel), vm::dismiss, kind = XdButtonKind.GHOST)
            XdButton(stringResource(R.string.pf_trash), { vm.delete(d.item) }, kind = XdButtonKind.DANGER)
        }) { Text(stringResource(R.string.cm_remove_text, d.item.displayName), style = XdText.body, color = c.fg2) }
        is DeleteState.TrashFull -> XdSheet(onDismiss = vm::dismiss, title = stringResource(R.string.cm_trash_full), actions = {
            XdButton(stringResource(R.string.common_cancel), vm::dismiss, kind = XdButtonKind.GHOST)
            XdButton(stringResource(R.string.cm_empty_trash), vm::requestEmptyTrash, kind = XdButtonKind.GHOST)
            XdButton(stringResource(R.string.cm_delete_for_good), { vm.deleteForGood(d.item) }, kind = XdButtonKind.DANGER)
        }) { Text(stringResource(R.string.cm_trash_full_text, size(d.used), size(d.quota), d.item.displayName), style = XdText.body, color = c.fg2) }
        is DeleteState.ConfirmPurge -> XdSheet(onDismiss = vm::dismiss, title = stringResource(R.string.cm_delete_for_good_title), actions = {
            XdButton(stringResource(R.string.common_cancel), vm::dismiss, kind = XdButtonKind.GHOST)
            XdButton(stringResource(R.string.common_delete), { vm.purge(d.entry) }, kind = XdButtonKind.DANGER)
        }) { Text(stringResource(R.string.cm_delete_for_good_text, d.entry.displayName, size(d.entry.bytes)), style = XdText.body, color = c.fg2) }
        DeleteState.ConfirmEmptyTrash -> XdSheet(onDismiss = vm::dismiss, title = stringResource(R.string.cm_empty_title), actions = {
            XdButton(stringResource(R.string.common_cancel), vm::dismiss, kind = XdButtonKind.GHOST)
            XdButton(stringResource(R.string.cm_empty_trash), { vm.purge(null) }, kind = XdButtonKind.DANGER)
        }) { Text(stringResource(R.string.cm_empty_text), style = XdText.body, color = c.fg2) }
        DeleteState.Idle -> Unit
    }
}

/** The list once read; a line while it is read, the reason when it could not be. */
@Composable
private fun Listed(state: ListState, content: @Composable (ListState.Loaded) -> Unit) {
    when (state) {
        ListState.Loading -> XdEmpty(stringResource(R.string.xd_cm_loading))
        is ListState.Error -> XdNote(state.message, tone = NoteTone.ERROR)
        is ListState.Loaded -> content(state)
    }
}

private enum class InstalledContentFilter { ALL, UPDATES, DLC }

/** Every game's content, a card per game. */
@Composable
private fun InstalledAll(
    s: ListState.Loaded,
    name: (String) -> String,
    art: (String) -> Any?,
    size: (Long) -> String,
    busy: Boolean,
    onOpen: (GameContent) -> Unit,
    onRemove: (ContentEntry) -> Unit,
    onInstall: () -> Unit,
) {
    val c = Xd.colors
    if (s.games.isEmpty()) {
        XdEmpty(stringResource(R.string.xd_cm_none_all)) {
            XdButton(stringResource(R.string.xd_cm_install), onInstall, size = XdButtonSize.SM, icon = XdIcons.plus)
        }
        return
    }
    val packages = s.dlc.size + s.updates.size
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(InstalledContentFilter.ALL) }
    val matching = remember(s.games, query, filter, name) {
        val needle = query.trim()
        s.games.sortedBy { name(it.titleId).lowercase() }.mapNotNull { game ->
            val candidates = when (filter) {
                InstalledContentFilter.ALL -> game.updates + game.dlc
                InstalledContentFilter.UPDATES -> game.updates
                InstalledContentFilter.DLC -> game.dlc
            }
            val matchesTitle = needle.isBlank() || name(game.titleId).contains(needle, ignoreCase = true) ||
                game.titleId.contains(needle, ignoreCase = true)
            val items = if (matchesTitle) candidates else candidates.filter { it.displayName.contains(needle, ignoreCase = true) }
            if (items.isEmpty()) null else game to items
        }
    }
    BoxWithConstraints {
        val narrow = maxWidth < 480.dp
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            XdNote(stringResource(R.string.xd_cm_all_note, pluralStringResource(R.plurals.xd_cm_packages, packages, packages),
                size(s.games.sumOf { it.bytes }), pluralStringResource(R.plurals.xd_cm_games, s.games.size, s.games.size)))
            XdSearchField(query, { query = it }, stringResource(R.string.zephyr_content_search), Modifier.fillMaxWidth())
            XdSegmented(listOf(
                InstalledContentFilter.ALL to stringResource(R.string.zephyr_content_all),
                InstalledContentFilter.UPDATES to stringResource(R.string.cm_tab_updates),
                InstalledContentFilter.DLC to "DLC",
            ), filter, { filter = it }, Modifier.fillMaxWidth())
            if (matching.isEmpty()) XdEmpty(stringResource(R.string.zephyr_content_no_results))
            matching.forEach { (g, items) ->
                XdCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Cover(art(g.titleId), 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(name(g.titleId), style = XdText.label, color = c.fg)
                            Text(stringResource(R.string.xd_cm_game_line, g.dlc.size,
                                g.updates.firstOrNull()?.displayName ?: stringResource(R.string.xd_cm_no_tu), size(g.bytes)),
                                style = XdText.small, color = c.fg3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        XdButton(stringResource(R.string.xd_open), { onOpen(g) }, kind = XdButtonKind.GHOST, size = XdButtonSize.SM)
                    }
                    Column {
                        items.forEachIndexed { i, e -> PackageRow(e, divider = i < items.lastIndex, narrow, size, busy, onRemove) }
                    }
                }
            }
        }
    }
}

/** One game's DLC or title updates. */
@Composable
private fun PackageCard(items: List<ContentEntry>, empty: String, size: (Long) -> String, busy: Boolean, onRemove: (ContentEntry) -> Unit) {
    if (items.isEmpty()) {
        XdEmpty(empty)
        return
    }
    BoxWithConstraints {
        val narrow = maxWidth < 480.dp
        XdCard(Modifier.fillMaxWidth()) {
            Column { items.forEachIndexed { i, e -> PackageRow(e, divider = i < items.lastIndex, narrow, size, busy, onRemove) } }
        }
    }
}

@Composable
private fun PackageRow(e: ContentEntry, divider: Boolean, narrow: Boolean, size: (Long) -> String, busy: Boolean, onRemove: (ContentEntry) -> Unit) {
    ItemRow(e.displayName, listOfNotNull(typeText(e.contentType), e.version?.let { stringResource(R.string.xd_game_version_of, it) }, size(e.size))
        .joinToString(" · "), iconOf(e.contentType), divider, narrow) {
        XdButton(stringResource(R.string.common_remove), { onRemove(e) }, kind = XdButtonKind.GHOST, size = XdButtonSize.SM, icon = XdIcons.trash,
            enabled = !busy)
    }
}

/** The trash: how full it is (every game's), then its packages to restore or delete for good. */
@Composable
private fun TrashBody(
    s: ListState.Loaded,
    all: Boolean,
    name: (String) -> String,
    size: (Long) -> String,
    busy: Boolean,
    onEmpty: () -> Unit,
    onRestore: (TrashedContent) -> Unit,
    onPurge: (TrashedContent) -> Unit,
) {
    val c = Xd.colors
    BoxWithConstraints {
        val narrow = maxWidth < 520.dp
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            XdCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.cm_trash_usage, size(s.trashUsed), size(s.trashQuota)), style = XdText.label, color = c.fg,
                        modifier = Modifier.weight(1f))
                    XdButton(stringResource(R.string.cm_empty), onEmpty, kind = XdButtonKind.GHOST, size = XdButtonSize.SM,
                        enabled = s.trashUsed > 0 && !busy)
                }
                val used = if (s.trashQuota > 0) s.trashUsed.toFloat() / s.trashQuota else 0f
                XdBar(used, warn = used > 0.8f)
                Text(stringResource(R.string.cm_trash_note) + " " + stringResource(R.string.xd_cm_trash_all_games), style = XdText.note, color = c.fg3)
            }
            if (s.trashed.isEmpty()) XdEmpty(stringResource(if (all) R.string.xd_cm_trash_empty else R.string.cm_trash_none))
            else XdCard(Modifier.fillMaxWidth()) {
                Column {
                    s.trashed.forEachIndexed { i, t ->
                        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t.deletedAt))
                        val line = stringResource(R.string.cm_trash_entry, typeText(t.contentType), size(t.bytes), date)
                        ItemRow(t.displayName, if (all) line + " · " + name(t.titleId) else line, iconOf(t.contentType), i < s.trashed.lastIndex, narrow) {
                            XdButton(stringResource(R.string.prof_restore), { onRestore(t) }, size = XdButtonSize.SM, enabled = !busy)
                            XdButton(stringResource(R.string.cm_delete_for_good), { onPurge(t) }, kind = XdButtonKind.GHOST, size = XdButtonSize.SM,
                                enabled = !busy)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Installing: the steps and the file picker; round 2, the content folders (searched with every
 * folder inside them, besides Downloads) and the packages found there, the installed ones marked.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InstallBody(
    all: Boolean,
    found: List<FoundPackage>?,
    folders: List<ContentManagerViewModel.FolderCount>,
    free: Long?,
    busy: Boolean,
    name: (String) -> String,
    size: (Long) -> String,
    onPick: () -> Unit,
    onInstall: (FoundPackage) -> Unit,
    onLookAgain: () -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
) {
    val c = Xd.colors
    BoxWithConstraints {
        val wide = maxWidth > 640.dp
        val narrow = maxWidth < 480.dp
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            XdTwoColumns(wide, left = {
                XdCard(Modifier.fillMaxWidth(), title = stringResource(R.string.xd_cm_install_card), icon = XdIcons.plus) {
                    Steps(listOf(stringResource(R.string.xd_cm_step1),
                        stringResource(if (all) R.string.xd_cm_step2 else R.string.xd_cm_step2_game),
                        stringResource(if (all) R.string.xd_cm_step3 else R.string.xd_cm_step3_game)))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        XdButton(stringResource(R.string.xd_cm_pick_file), onPick, kind = XdButtonKind.PRIMARY, size = XdButtonSize.SM,
                            icon = XdIcons.folder, enabled = !busy)
                        if (free != null) Text(stringResource(R.string.xd_cm_free, size(free)), style = XdText.note, color = c.fg3,
                            modifier = Modifier.align(Alignment.CenterVertically))
                    }
                }
                XdCard(Modifier.fillMaxWidth(), title = stringResource(R.string.xd_cm_folders_title), icon = XdIcons.folder) {
                    Text(stringResource(R.string.xd_cm_folders_note), style = XdText.note, color = c.fg3)
                    Column {
                        val inDownloads = found?.count { it.folder == null }
                        XdListRow(stringResource(R.string.xd_cm_downloads), icon = XdIcons.download, divider = folders.isNotEmpty(),
                            subtitle = inDownloads?.let { pluralStringResource(R.plurals.xd_cm_folder_packages, it, it) })
                        folders.forEachIndexed { i, f ->
                            XdListRow(java.io.File(f.path).name.ifEmpty { f.path }, icon = XdIcons.folder, divider = i < folders.lastIndex,
                                subtitle = listOf(xendroid.compose.ui.library.displayPath(f.path),
                                    if (f.readable) pluralStringResource(R.plurals.xd_cm_folder_packages, f.packages, f.packages)
                                    else stringResource(R.string.xd_cm_folder_unreadable)).joinToString(" · ")) {
                                XdIconButton(XdIcons.x, stringResource(R.string.common_remove), { onRemoveFolder(f.path) })
                            }
                        }
                    }
                    XdButton(stringResource(R.string.xd_cm_folder_add), onAddFolder, size = XdButtonSize.SM, icon = XdIcons.plus, enabled = !busy)
                }
            }, right = {
                XdCard(Modifier.fillMaxWidth(), title = stringResource(R.string.xd_cm_found_title), icon = XdIcons.box,
                    trailing = found?.size?.takeIf { it > 0 }?.toString()) {
                    when {
                        found == null -> Text(stringResource(R.string.xd_cm_looking_folders), style = XdText.note, color = c.fg3)
                        found.isEmpty() -> Text(stringResource(if (all) R.string.xd_cm_found_none else R.string.xd_cm_found_none_game),
                            style = XdText.note, color = c.fg3)
                        else -> Column {
                            // Not yet installed first; among them, the newest.
                            val ordered = found.sortedWith(compareBy<FoundPackage> { it.installed }.thenByDescending { it.modified })
                            ordered.forEachIndexed { i, f ->
                                ItemRow(f.fileName, describe(f, name, size), iconOf(f.contentType), i < ordered.lastIndex, narrow || wide,
                                    badge = if (f.installed) stringResource(R.string.xd_cm_installed_badge) else null) {
                                    XdButton(stringResource(if (f.installed) R.string.xd_cm_install_again else R.string.xd_cm_install), { onInstall(f) },
                                        size = XdButtonSize.SM, kind = if (f.installed) XdButtonKind.GHOST else XdButtonKind.SECONDARY, enabled = !busy)
                                }
                            }
                        }
                    }
                    XdButton(stringResource(R.string.xd_cm_look_again), onLookAgain, kind = XdButtonKind.GHOST, size = XdButtonSize.SM,
                        icon = XdIcons.refresh, enabled = found != null && !busy)
                }
            })
            if (!all) XdNote(stringResource(R.string.xd_cm_install_game_note))
        }
    }
}

/** Numbered steps. */
@Composable
private fun Steps(steps: List<String>) {
    val c = Xd.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { i, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(c.s3), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = XdText.monoNum, color = c.fg2)
                }
                Text(step, style = XdText.bodySm, color = c.fg2, modifier = Modifier.weight(1f).padding(top = 1.dp))
            }
        }
    }
}

/**
 * A row of a list with its actions: at the end on a wide screen ([XdListRow]); under the text on
 * a narrow one, where two buttons would leave the name no room.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    divider: Boolean,
    stacked: Boolean,
    badge: String? = null,
    actions: @Composable () -> Unit,
) {
    val c = Xd.colors
    val badges: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? =
        badge?.let { text -> { XdBadge(text, tone = BadgeTone.OK, modifier = Modifier.align(Alignment.CenterVertically)) } }
    if (!stacked || c.controller) {
        XdListRow(title, subtitle = subtitle, icon = icon, divider = divider, badges = badges) { actions() }
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 11.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp), tint = c.fg3) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    if (badge == null) Text(title, style = XdText.label, color = c.fg)
                    else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = XdText.label, color = c.fg, modifier = Modifier.align(Alignment.CenterVertically))
                        XdBadge(badge, tone = BadgeTone.OK, modifier = Modifier.align(Alignment.CenterVertically))
                    }
                    Text(subtitle, style = XdText.small, color = c.fg3, modifier = Modifier.padding(top = 2.dp))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
            }
        }
        if (divider) HorizontalDivider(thickness = 1.dp, color = c.line)
    }
}

/** A game's cover, or a disc where it has none. */
@Composable
private fun Cover(art: Any?, width: Dp) {
    val c = Xd.colors
    val shape = RoundedCornerShape(7.dp)
    if (art != null) AsyncImage(art, null, Modifier.width(width).aspectRatio(0.75f).clip(shape), contentScale = ContentScale.Crop)
    else Box(Modifier.width(width).aspectRatio(0.75f).clip(shape).background(c.s2), contentAlignment = Alignment.Center) {
        Icon(XdIcons.disc, null, Modifier.size(width * 0.45f), tint = c.fg3)
    }
}

private fun iconOf(contentType: Int): ImageVector = when {
    contentType == ContentPaths.TU_CONTENT_TYPE -> XdIcons.download
    contentType == ContentPaths.PROFILE_CONTENT_TYPE -> XdIcons.user
    ContentPaths.isLaunchableGameType(contentType) -> XdIcons.gamepad
    else -> XdIcons.box
}

/** A package's kind in the shown language. */
@Composable
private fun typeText(contentType: Int): String = when {
    contentType == ContentPaths.DLC_CONTENT_TYPE -> "DLC"
    contentType == ContentPaths.TU_CONTENT_TYPE -> stringResource(R.string.cm_type_tu)
    contentType == ContentPaths.PROFILE_CONTENT_TYPE -> stringResource(R.string.xd_cm_type_profile)
    contentType == 0x00000001 -> stringResource(R.string.xd_cm_type_save)
    ContentPaths.isLaunchableGameType(contentType) -> stringResource(R.string.xd_cm_type_game)
    else -> stringResource(R.string.xd_cm_type_other)
}

/** A found package as the list says it: kind, game, the name inside it, size. */
@Composable
private fun describe(f: FoundPackage, name: (String) -> String, size: (Long) -> String): String {
    val where = f.folder?.let { stringResource(R.string.xd_cm_in_folder, java.io.File(it).name.ifEmpty { it }) }
    return when {
        f.isGame -> listOfNotNull(f.displayName.takeIf { it != f.fileName }, stringResource(R.string.xd_cm_found_game, size(f.size)), where)
        else -> listOfNotNull(typeText(f.contentType), f.version?.let { stringResource(R.string.xd_game_version_of, it) }, f.titleId?.let(name),
            f.displayName.takeIf { it != f.fileName }, size(f.size), where)
    }.joinToString(" · ")
}
