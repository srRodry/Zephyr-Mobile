package xendroid.compose.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import xendroid.compose.R
import xendroid.compose.bundle.BundleException
import xendroid.compose.bundle.DataBundle
import xendroid.compose.bundle.DataBundleIo
import xendroid.compose.bundle.ImportPlan
import xendroid.compose.ui.design.LocalXdToast
import xendroid.compose.ui.design.NoteTone
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdBar
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdButtonSize
import xendroid.compose.ui.design.XdCard
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdListRow
import xendroid.compose.ui.design.XdNote
import xendroid.compose.ui.design.XdSegmented
import xendroid.compose.ui.design.XdSheet
import xendroid.compose.ui.design.XdText
import xendroid.compose.updater.UpdateChannel
import xendroid.compose.updater.setUpdateChannel
import xendroid.compose.updater.updateChannel
import xendroid.compose.updater.updateRepository

/**
 * L08 in the redesign: export the settings, touch controls, favourites, collections and ratings to
 * a file, or import one after seeing exactly what it changes (the same [DataBundleIo] as before).
 * [beforeImport] saves pending settings for both transfers and must throw if saving fails.
 */
@Composable
fun XdDataBundleCard(beforeImport: () -> Unit, afterImport: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = LocalXdToast.current
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<DataBundle, ImportPlan>?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    fun run(work: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { work() } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                failure = (e as? BundleException)?.let { bundleRefusal(context, it) }
                    ?: context.getString(R.string.bundle_bad_file, e.message ?: e.javaClass.simpleName)
            } finally { busy = false }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) run {
            // Include pending screen edits, and stop if they cannot be saved.
            beforeImport()
            val parts = DataBundleIo.export(context, uri)
            toast.show(context.resources.getQuantityString(R.plurals.bundle_exported, parts, parts))
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run { pending = DataBundleIo.preview(context, uri) }
    }
    XdCard(title = stringResource(R.string.bundle_title), icon = XdIcons.save) {
        Text(stringResource(R.string.bundle_note), style = XdText.note, color = Xd.colors.fg3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            XdButton(stringResource(R.string.bundle_export), {
                exportLauncher.launch("xendroid-settings-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".zip")
            }, size = XdButtonSize.SM, icon = XdIcons.upload, enabled = !busy)
            XdButton(stringResource(R.string.bundle_import), { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                size = XdButtonSize.SM, kind = XdButtonKind.GHOST, icon = XdIcons.download, enabled = !busy)
        }
        if (busy) XdBar(0.3f)
    }
    pending?.let { (bundle, plan) ->
        XdSheet(onDismiss = { pending = null },
            title = stringResource(if (plan.changes > 0) R.string.bundle_confirm else R.string.bundle_nothing),
            actions = {
                XdButton(stringResource(if (plan.changes > 0) R.string.common_cancel else R.string.common_close), { pending = null }, kind = XdButtonKind.GHOST)
                if (plan.changes > 0) XdButton(stringResource(R.string.bundle_import), {
                    run {
                        beforeImport()
                        val backup = DataBundleIo.import(context, bundle)
                        pending = null
                        afterImport()
                        toast.show(context.getString(R.string.bundle_imported, backup))
                    }
                }, kind = XdButtonKind.PRIMARY, enabled = !busy)
            }) {
            Column {
                plan.items.forEachIndexed { i, item -> XdListRow(planText(item), icon = planIcon(item), divider = i < plan.items.lastIndex) }
            }
            if (plan.changes > 0) XdNote(stringResource(R.string.bundle_backup_note), icon = XdIcons.info)
        }
    }
    failure?.let { message ->
        XdSheet(onDismiss = { failure = null }, title = null,
            actions = { XdButton(stringResource(R.string.common_ok), { failure = null }, kind = XdButtonKind.PRIMARY) }) {
            XdNote(message, tone = NoteTone.ERROR)
        }
    }
}

private fun partIcon(part: ImportPlan.Item.Part) = if (part == ImportPlan.Item.Part.TOUCH) XdIcons.hand else XdIcons.sliders

private fun planIcon(item: ImportPlan.Item) = when (item) {
    is ImportPlan.Item.Absent -> partIcon(item.part)
    is ImportPlan.Item.Unchanged -> partIcon(item.part)
    is ImportPlan.Item.Replaced -> partIcon(item.part)
    is ImportPlan.Item.GameSettings -> XdIcons.gamepad
    is ImportPlan.Item.Favorites -> XdIcons.star
    is ImportPlan.Item.Sort -> XdIcons.grid
    is ImportPlan.Item.Collections -> XdIcons.layers
    is ImportPlan.Item.Results -> XdIcons.shield
    ImportPlan.Item.Kept -> XdIcons.lock
}

/** One line of the import preview, in the shown language (as the older section said it). */
@Composable
private fun planText(item: ImportPlan.Item): String = when (item) {
    is ImportPlan.Item.Absent -> stringResource(
        if (item.part == ImportPlan.Item.Part.SETTINGS) R.string.bundle_plan_settings_absent else R.string.bundle_plan_touch_absent)
    is ImportPlan.Item.Unchanged -> stringResource(
        if (item.part == ImportPlan.Item.Part.SETTINGS) R.string.bundle_plan_settings_same else R.string.bundle_plan_touch_same)
    is ImportPlan.Item.Replaced -> item.differ?.let { pluralStringResource(R.plurals.bundle_plan_settings_replaced, it, it) }
        ?: stringResource(R.string.bundle_plan_touch_replaced)
    is ImportPlan.Item.GameSettings -> stringResource(R.string.bundle_plan_games, item.added, item.replaced, item.unchanged, item.kept)
    is ImportPlan.Item.Favorites -> if (item.added > 0) pluralStringResource(R.plurals.bundle_plan_favorites, item.added, item.added)
        else stringResource(R.string.bundle_plan_favorites_none)
    is ImportPlan.Item.Sort -> stringResource(R.string.bundle_plan_sort, when (item.sort) {
        "NAME_ASC" -> stringResource(R.string.lib_sort_name_asc)
        "NAME_DESC" -> stringResource(R.string.lib_sort_name_desc)
        "FORMAT" -> stringResource(R.string.lib_sort_format)
        "RECENT" -> stringResource(R.string.lib_sort_recent)
        else -> item.sort.orEmpty()
    })
    is ImportPlan.Item.Collections -> pluralStringResource(R.plurals.bundle_plan_collections, item.games, item.games, item.created)
    is ImportPlan.Item.Results -> stringResource(R.string.bundle_plan_results, item.added)
    ImportPlan.Item.Kept -> stringResource(R.string.bundle_plan_kept)
}

private fun bundleRefusal(context: android.content.Context, e: BundleException): String = when (e.why) {
    BundleException.Why.TOO_MANY_ENTRIES -> context.getString(R.string.bundle_why_too_many)
    BundleException.Why.UNEXPECTED_ENTRY -> context.getString(R.string.bundle_why_unexpected, e.detail)
    BundleException.Why.DUPLICATE_ENTRY -> context.getString(R.string.bundle_why_duplicate, e.detail)
    BundleException.Why.ENTRY_TOO_LARGE -> context.getString(R.string.bundle_why_entry_too_large, e.detail)
    BundleException.Why.TOO_LARGE -> context.getString(R.string.bundle_why_too_large)
    BundleException.Why.NO_MANIFEST -> context.getString(R.string.bundle_why_no_manifest)
    BundleException.Why.DAMAGED_MANIFEST -> context.getString(R.string.bundle_why_damaged_manifest)
    BundleException.Why.NOT_A_BUNDLE -> context.getString(R.string.bundle_why_not_bundle)
    BundleException.Why.NEWER -> context.getString(R.string.bundle_why_newer, e.detail)
    BundleException.Why.ENTRIES_MISMATCH -> context.getString(R.string.bundle_why_mismatch)
    BundleException.Why.CHECKSUM -> context.getString(R.string.bundle_why_checksum, e.detail)
    BundleException.Why.DAMAGED_LIBRARY -> context.getString(R.string.bundle_why_damaged_library)
    BundleException.Why.DAMAGED_ENTRY -> context.getString(R.string.bundle_why_damaged_entry, e.detail)
    BundleException.Why.NOT_UTF8 -> context.getString(R.string.bundle_why_not_utf8, e.detail)
    BundleException.Why.CANNOT_WRITE -> context.getString(R.string.bundle_why_cannot_write)
    BundleException.Why.CANNOT_READ -> context.getString(R.string.bundle_why_cannot_read)
    BundleException.Why.BAD_GLOBAL_TOML -> context.getString(R.string.bundle_why_bad_global_toml)
    BundleException.Why.BAD_GAME_TOML -> context.getString(R.string.bundle_why_bad_game_toml, e.detail)
    BundleException.Why.BAD_LAYOUT -> context.getString(R.string.bundle_why_bad_layout)
}

/** L07: the app's data folder in the phone's file manager (read-only while a game runs). */
@Composable
fun XdUserDataCard() {
    val context = LocalContext.current
    XdCard(title = stringResource(R.string.xd_set_userdata), icon = XdIcons.folder) {
        XdListRow(stringResource(R.string.ud_games), icon = XdIcons.gamepad, subtitle = stringResource(R.string.xd_set_userdata_games))
        XdListRow(stringResource(R.string.xd_set_userdata_saves), icon = XdIcons.user, subtitle = stringResource(R.string.xd_set_userdata_saves_sub))
        XdListRow(stringResource(R.string.ud_console_data), icon = XdIcons.box, subtitle = stringResource(R.string.xd_set_userdata_content), divider = false)
        Text(stringResource(R.string.ud_read_only_now), style = XdText.note, color = Xd.colors.fg3)
        XdButton(stringResource(R.string.xd_set_userdata_open), { xendroid.compose.ui.userdata.openUserData(context) }, size = XdButtonSize.SM,
            icon = XdIcons.folder)
    }
}

/** R02: which releases this build offers (stable, preview, off). Absent without a release feed. */
@Composable
fun XdUpdateChannelOption(shown: Boolean = updateRepository() != null) {
    if (!shown) return
    val context = LocalContext.current
    var channel by remember { mutableStateOf(updateChannel(context)) }
    xendroid.compose.ui.design.XdSheetOption(stringResource(R.string.upd_title), subtitle = when (channel) {
        UpdateChannel.STABLE -> stringResource(R.string.upd_stable_note)
        UpdateChannel.PREVIEW -> stringResource(R.string.upd_preview_note)
        UpdateChannel.OFF -> stringResource(R.string.upd_off_note)
    }) {
        XdSegmented(UpdateChannel.entries.map { it to stringResource(when (it) {
            UpdateChannel.STABLE -> R.string.upd_stable
            UpdateChannel.PREVIEW -> R.string.upd_preview
            UpdateChannel.OFF -> R.string.upd_off
        }) }, channel, { channel = it; setUpdateChannel(context, it) })
    }
}
