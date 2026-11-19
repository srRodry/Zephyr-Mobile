package xendroid.compose.ui.game

import android.widget.Toast
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xendroid.compose.R
import xendroid.compose.core.SessionLogs
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdIcons

@Composable
internal fun DownloadGameDiagnostics(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    XdButton(
        stringResource(if (busy) R.string.set_logs_exporting else R.string.zephyr_game_download_diagnostics),
        onClick = {
            if (!busy) {
                busy = true
                scope.launch {
                    try {
                        val name = withContext(Dispatchers.IO) {
                            SessionLogs.downloadDiagnostics(context.applicationContext)
                        }
                        val message = if (name == null) context.getString(R.string.set_logs_none)
                        else context.getString(R.string.set_logs_exported, name)
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        Toast.makeText(context, R.string.zephyr_game_download_diagnostics_failed, Toast.LENGTH_LONG).show()
                    } finally {
                        busy = false
                    }
                }
            }
        },
        modifier = modifier.heightIn(min = 48.dp).testTag("game-download-diagnostics"),
        icon = XdIcons.download,
        enabled = !busy,
    )
}
