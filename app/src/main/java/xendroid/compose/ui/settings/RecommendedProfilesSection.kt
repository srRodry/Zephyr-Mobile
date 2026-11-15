package xendroid.compose.ui.settings

import androidx.compose.ui.res.pluralStringResource
import xendroid.compose.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xendroid.compose.compatibility.DeviceFacts
import xendroid.compose.compatibility.LoadedProfile
import xendroid.compose.compatibility.ProfileSource
import xendroid.compose.compatibility.SettingsProfiles
import xendroid.compose.compatibility.SettingsProfiles.Kind
import xendroid.compose.settings.GameSettingsViewModel.ProfilePreview
import xendroid.compose.settings.GameSettingsViewModel.ProfilesState
import xendroid.compose.settings.ConfigValueShape
import xendroid.compose.settings.Setting
import xendroid.compose.settings.SettingsSchema

/** C05: the game's recommended settings, where they come from and why, and the way back. */
@Composable
fun RecommendedProfilesCard(
    state: ProfilesState,
    onPreview: (LoadedProfile) -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.prof_title), style = MaterialTheme.typography.titleSmall)
            state.applied?.let { applied ->
                val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(applied.appliedAt))
                Text(pluralStringResource(R.plurals.prof_applied, applied.written.size, applied.profileName, date, applied.written.size),
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onRestore) { Text(stringResource(R.string.prof_restore_previous)) }
            }
            state.offered.forEach { profile -> ProfileBlock(profile, state.facts, enabled = state.applied == null, onPreview) }
            if (state.notHere.isNotEmpty()) {
                Text(stringResource(R.string.prof_not_here), style = MaterialTheme.typography.bodySmall, color = muted)
                state.notHere.forEach { (profile, why) ->
                    Text("• ${profile.profile.name}: ${why.joinToString("; ")}", style = MaterialTheme.typography.bodySmall, color = muted)
                }
            }
            if (state.skipped.isNotEmpty()) {
                Text(stringResource(R.string.prof_skipped, state.skipped.take(3).joinToString("; ")) +
                    (if (state.skipped.size > 3) " " + stringResource(R.string.prof_skipped_more, state.skipped.size - 3) else ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ProfileBlock(loaded: LoadedProfile, facts: DeviceFacts?, enabled: Boolean, onPreview: (LoadedProfile) -> Unit) {
    val profile = loaded.profile
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(profile.name, style = MaterialTheme.typography.bodyLarge)
        Text(when (loaded.source) {
            ProfileSource.EXPERIMENTAL -> stringResource(R.string.zephyr_perf_experimental)
            ProfileSource.BUNDLED -> stringResource(R.string.prof_from_app)
            ProfileSource.LOCAL -> stringResource(R.string.prof_from_file, loaded.origin)
            ProfileSource.COMMUNITY -> stringResource(R.string.prof_from_community, loaded.origin)
        }, style = MaterialTheme.typography.bodySmall, color = muted)
        Text(profile.reason, style = MaterialTheme.typography.bodySmall)
        profile.evidence.firstOrNull()?.let { test ->
            val elsewhere = facts != null && !SettingsProfiles.testedOnThisGpu(profile, facts)
            Text(stringResource(R.string.prof_tested, test.result, test.gpu + if (test.driver.isNotBlank()) ", ${test.driver}" else "", test.build, test.date) +
                (if (elsewhere) stringResource(R.string.prof_other_gpu) else ""),
                style = MaterialTheme.typography.bodySmall, color = muted)
        }
        TextButton(onClick = { onPreview(loaded) }, enabled = enabled) { Text(stringResource(R.string.prof_preview)) }
    }
}

/** Every setting the plan touches, before anything is written. */
@Composable
fun ProfilePreviewDialog(preview: ProfilePreview, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val writes = preview.plan.writes.size
    val restore = preview is ProfilePreview.Restore
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(when (preview) {
                is ProfilePreview.Apply -> stringResource(R.string.prof_apply_title, preview.profile.profile.name)
                is ProfilePreview.Restore -> stringResource(R.string.prof_restore_title)
            })
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                preview.plan.lines.forEach { line ->
                    val setting = SettingsSchema.byKey[line.key]
                    val title = setting?.let { settingTitle(it) } ?: line.title
                    val now = setting?.let { valueText(it, line.nowRaw) } ?: line.now
                    val target = if (setting == null) line.target else valueText(setting, line.targetRaw)
                        .let { if (line.targetGlobal) stringResource(R.string.prof_global_value, it) else it }
                    Text(when (line.kind) {
                        Kind.CHANGE -> stringResource(R.string.prof_line_change, title, now, target)
                        Kind.PIN -> stringResource(R.string.prof_line_pin, title, target)
                        Kind.SAME -> stringResource(R.string.prof_line_same, title, target)
                        Kind.YOURS -> if (restore) stringResource(R.string.prof_line_yours_restore, title, now)
                            else stringResource(R.string.prof_line_yours, title, now, target)
                    }, style = MaterialTheme.typography.bodySmall)
                }
                Text(if (restore) stringResource(R.string.prof_restore_note)
                    else stringResource(R.string.prof_apply_note),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = restore || writes > 0) {
                Text(when {
                    restore -> stringResource(R.string.prof_restore)
                    writes == 0 -> stringResource(R.string.prof_nothing)
                    else -> pluralStringResource(R.plurals.prof_apply_n, writes, writes)
                })
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** U02: a setting's value as the preview shows it, in the shown language. */
@Composable
internal fun valueText(s: Setting, raw: String): String = when (s) {
    is Setting.Bool -> stringResource(if (ConfigValueShape.parseBool(raw, s.default)) R.string.prof_on else R.string.prof_off)
    is Setting.ListChoice -> s.options.firstOrNull { it.value == raw }?.let { optionLabel(s, it.value, it.label) }
        ?: raw.ifEmpty { stringResource(R.string.prof_default) }
    else -> SettingsProfiles.label(s, raw)
}
