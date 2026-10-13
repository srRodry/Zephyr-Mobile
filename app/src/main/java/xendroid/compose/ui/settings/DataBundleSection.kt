package xendroid.compose.ui.settings

import xendroid.compose.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import xendroid.compose.bundle.BundleException
import xendroid.compose.bundle.DataBundle
import xendroid.compose.bundle.DataBundleIo
import xendroid.compose.bundle.ImportPlan

/**
 * L08: export the settings, touch controls, favorites, collections and compatibility notes to a file
 * the user picks, or import one after seeing exactly what it would change. [beforeImport]
 * writes pending edits before export or import; [afterImport] re-reads them after import.
 */
@Composable
fun DataBundleSection(beforeImport: () -> Unit, afterImport: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<DataBundle, ImportPlan>?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    fun run(work: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                work()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                failure = (e as? BundleException)?.let { bundleRefusalText(context, it) }
                    ?: context.getString(R.string.bundle_bad_file, e.message ?: e.javaClass.simpleName)
            } finally {
                busy = false
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) run {
            // Include pending screen edits, and stop if they cannot be saved.
            beforeImport()
            val parts = DataBundleIo.export(context, uri)
            Toast.makeText(context, context.resources.getQuantityString(R.plurals.bundle_exported, parts, parts), Toast.LENGTH_LONG).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run { pending = DataBundleIo.preview(context, uri) }
    }

    ListItem(
        headlineContent = { Text(stringResource(R.string.bundle_title)) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.bundle_note))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy, onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                        exportLauncher.launch("xendroid-settings-$stamp.zip")
                    }) { Text(stringResource(R.string.bundle_export)) }
                    OutlinedButton(enabled = !busy, onClick = {
                        importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    }) { Text(stringResource(R.string.bundle_import)) }
                }
                if (busy) LinearProgressIndicator()
            }
        },
    )

    pending?.let { (bundle, plan) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (plan.changes > 0) stringResource(R.string.bundle_confirm) else stringResource(R.string.bundle_nothing)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    plan.items.forEach { Text(planItemText(it), style = MaterialTheme.typography.bodySmall) }
                    if (plan.changes > 0) Text(stringResource(R.string.bundle_backup_note),
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            },
            confirmButton = {
                if (plan.changes > 0) TextButton(enabled = !busy, onClick = {
                    run {
                        beforeImport()
                        val backup = DataBundleIo.import(context, bundle)
                        pending = null
                        afterImport()
                        Toast.makeText(context, context.getString(R.string.bundle_imported, backup), Toast.LENGTH_LONG).show()
                    }
                }) { Text(stringResource(R.string.bundle_import)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(if (plan.changes > 0) stringResource(R.string.common_cancel) else stringResource(R.string.common_close)) } },
        )
    }

    failure?.let { message ->
        AlertDialog(
            onDismissRequest = { failure = null },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { failure = null }) { Text(stringResource(R.string.common_ok)) } },
        )
    }
}

/** U02: one line of the import preview, in the shown language. */
@Composable
private fun planItemText(item: ImportPlan.Item): String = when (item) {
    is ImportPlan.Item.Absent -> stringResource(
        if (item.part == ImportPlan.Item.Part.SETTINGS) R.string.bundle_plan_settings_absent else R.string.bundle_plan_touch_absent)
    is ImportPlan.Item.Unchanged -> stringResource(
        if (item.part == ImportPlan.Item.Part.SETTINGS) R.string.bundle_plan_settings_same else R.string.bundle_plan_touch_same)
    is ImportPlan.Item.Replaced -> item.differ?.let { pluralStringResource(R.plurals.bundle_plan_settings_replaced, it, it) }
        ?: stringResource(R.string.bundle_plan_touch_replaced)
    is ImportPlan.Item.GameSettings ->
        stringResource(R.string.bundle_plan_games, item.added, item.replaced, item.unchanged, item.kept)
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

/** U02: why a file is not taken as a bundle, in the shown language. */
private fun bundleRefusalText(context: android.content.Context, e: BundleException): String = when (e.why) {
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
