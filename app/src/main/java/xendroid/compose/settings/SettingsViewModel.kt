package xendroid.compose.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import xendroid.compose.core.EmulatorRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repo: SettingsRepository,
    /** Lote 1: the games with values of their own (null: not shown). */
    private val gameIndex: GameOverridesIndex? = null,
) : ViewModel(), SettingsHost {

    val categories: List<SettingsCategory> = SettingsSchema.categories
    override val isCustomDriverSupported: Boolean get() = repo.isCustomDriverSupported

    private val _values = MutableStateFlow<Map<String, SettingValue>>(emptyMap())
    val values: StateFlow<Map<String, SettingValue>> = _values.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    /** Title ID to its own values, for "N games use another value" and the summary. */
    private val _gameValues = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val gameValues: StateFlow<Map<String, Map<String, String>>> = _gameValues.asStateFlow()

    init { load() }

    /** Reads the games' own values again (after one of them changed). */
    fun reloadGameValues() {
        val index = gameIndex ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { index.read() }.onSuccess { _gameValues.value = it }
                .onFailure { Log.w("SettingsViewModel", "Reading the games' own settings failed", it) }
        }
    }

    /** Single off-main load path (shared by init + onResume): ensureLoaded() can sleep +
     *  System.loadLibrary on delay-load devices (Adreno 5xx/6xx) where Application.onCreate
     *  skips the eager load, so the native Config calls must wait on it OFF the main thread.
     *  The repo is @Synchronized throughout, so concurrent load/flush/driver ops cannot
     *  corrupt the native handle. */
    private fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                EmulatorRuntime.ensureLoaded()
                repo.ensureOpen()
                reloadAll()
                _ready.value = true
                _error.value = null
            }.onFailure { _ready.value = false; fail(it) }
        }
        reloadGameValues()
    }

    private fun reloadAll() {
        _values.value = SettingsSchema.allSettings.associate { it.key to repo.valueOf(it) }
    }

    private fun refreshKey(s: Setting) {
        _values.value = _values.value.toMutableMap().apply { put(s.key, repo.valueOf(s)) }
    }

    private fun change(s: Setting, edit: () -> Unit) {
        runCatching { edit(); refreshKey(s) }.onFailure { fail(it) }
    }
    override fun onBoolChanged(s: Setting.Bool, v: Boolean) = change(s) { repo.setBool(s, v) }
    override fun onIntChanged(s: Setting.IntRange, v: Int) = change(s) { repo.setInt(s, v) }
    override fun onListChanged(s: Setting.ListChoice, value: String) = change(s) { repo.setListValue(s, value) }
    /** Custom driver picker writes the installed .so path ("" clears -> system driver).
     *  Persisted durably OFF the screen handle (the SAF picker pauses the screen, nulling
     *  the handle), then the snapshot is refreshed. Runs off the main thread. */
    override fun onDriverPathChanged(s: Setting.Action, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repo.persistDriverPath(value)
                repo.ensureOpen()
                reloadAll()
            }.onFailure { fail(it) }
        }
    }

    // Snapshot-backed reads: rows recompose often; avoid a JNI crossing per read.
    private fun raw(s: Setting): String? = _values.value[s.key]?.raw

    override fun currentBool(s: Setting.Bool) = ConfigValueShape.parseBool(raw(s), s.default)
    override fun currentInt(s: Setting.IntRange) = ConfigValueShape.parseInt(raw(s), s.default)
    override fun currentListValue(s: Setting.ListChoice) =
        ConfigValueShape.listOption(s.options.map { it.value }, raw(s)) ?: s.default
    override fun currentDriverPath(s: Setting.Action) = raw(s) ?: ""
    override fun currentText(s: Setting.Text) = raw(s) ?: s.default
    override fun onTextChanged(s: Setting.Text, value: String) = change(s) { repo.setRawString(s, value) }

    /** The value "Back to default" writes. */
    fun defaultRaw(s: Setting): String = repo.defaultRaw(s)

    /** Writes [s]'s default back (the redesign's "Back to default"). */
    fun resetToDefault(s: Setting) = setRaw(s, defaultRaw(s))

    /** Synchronous durable write; I/O-free when nothing was edited. */
    fun flush() {
        runCatching { repo.flushAndClose() }.onFailure { fail(it) }
    }

    /** Transfers must stop on a failed save; lifecycle flush still reports errors in place. */
    fun flushForTransfer() {
        try {
            repo.flushAndClose()
        } catch (e: Exception) {
            fail(e)
            throw e
        }
    }

    fun clearError() { _error.value = null }

    private fun fail(cause: Throwable) {
        Log.w("SettingsViewModel", "Config edit failed; keeping previous file", cause)
        _error.value = "Could not read or save the configuration. The existing file was kept. Fix invalid TOML or storage access, then retry."
    }

    /** Re-open the handle after a pause-flush and refresh snapshots. Call on resume. */
    fun onResume() = load()

    override fun onCleared() { runCatching { repo.close() }.onFailure { fail(it) } }
}
