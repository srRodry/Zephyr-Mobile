package xendroid.compose.ui.design

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xendroid.compose.R

private const val KEY = "green_theme"

@Composable
private fun themePreferences(): SharedPreferences {
    val context = LocalContext.current
    return remember(context) { context.getSharedPreferences("zephyr_appearance", Context.MODE_PRIVATE) }
}

@Composable
internal fun rememberGreenTheme(): Boolean {
    val prefs = themePreferences()
    val enabled = remember(prefs) { mutableStateOf(prefs.getBoolean(KEY, false)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY || key == null) enabled.value = prefs.getBoolean(KEY, false)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        enabled.value = prefs.getBoolean(KEY, false)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return enabled.value
}

@Composable
fun ZephyrThemeOptions() {
    val green = rememberGreenTheme()
    val prefs = themePreferences()
    Text(stringResource(R.string.zephyr_appearance), style = XdText.label)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !green, onClick = { prefs.edit().putBoolean(KEY, false).apply() },
            label = { Text(stringResource(R.string.zephyr_appearance_blue)) })
        FilterChip(selected = green, onClick = { prefs.edit().putBoolean(KEY, true).apply() },
            label = { Text(stringResource(R.string.zephyr_appearance_green)) })
    }
}
