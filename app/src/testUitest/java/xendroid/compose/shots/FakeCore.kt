package xendroid.compose.shots

import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import xendroid.compose.Emulator

/**
 * The parts of the native core the screens reach, faked on the JVM (Robolectric shadows):
 * the TOML config tables, the profiles and the installed content. Values follow the core's own
 * conventions: entries are read and written as "Section|name" with the value as text, and a
 * text's shape decides its TOML type (true/false, a whole number, a number with one '.').
 */
object FakeCore {
    /** Open config tables by handle. */
    internal val tables = HashMap<Long, LinkedHashMap<String, String>>()
    internal var nextHandle = 1L

    /** What list_profiles returns: xuid, gamertag, language, country. */
    var profiles: List<Emulator.ProfileInfo> = emptyList()
    /** Simulates recoverable native-load failures without loading a game. */
    var loadFailure: Throwable? = null

    /** Installed content by "<TITLEID>:<type>". */
    val content = HashMap<String, List<Emulator.ContentItem>>()

    /** What content_header reads from a package, by its file name. */
    val headers = HashMap<String, Emulator.ContentInfo>()

    var deviceInfo = "Adreno (TM) 740 · Qualcomm 819.0 · Android 14"

    fun profile(xuid: String, gamertag: String, language: Int = 9, country: Int = 13) = Emulator.ProfileInfo().apply {
        this.xuid = xuid; this.gamertag = gamertag; this.language = language; this.country = country; hasAvatar = false
    }

    fun item(dir: String, name: String, size: Long) = Emulator.ContentItem().apply { pkgDir = dir; displayName = name; this.size = size }

    fun header(titleId: String, type: Int, name: String, size: Long) = Emulator.ContentInfo().apply {
        this.titleId = titleId.toLong(16).toInt(); contentType = type; contentSize = size; displayName = name
    }

    fun reset() {
        tables.clear(); nextHandle = 1L; profiles = emptyList(); loadFailure = null; content.clear(); headers.clear()
    }

    // ---- TOML (only what the config files use: tables, key = value, comments) ----

    fun parse(text: String): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        var section = ""
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("[") && line.endsWith("]")) { section = line.substring(1, line.length - 1).trim(); continue }
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim()
            var value = line.substring(eq + 1).trim()
            value = if (value.startsWith("\"")) {
                val end = value.indexOf('"', 1)
                if (end > 0) value.substring(1, end) else value.trim('"')
            } else value.substringBefore('#').trim()
            out["$section|$key"] = value
        }
        return out
    }

    fun serialize(table: Map<String, String>): String {
        val bySection = table.entries.groupBy({ it.key.substringBefore('|') }, { it.key.substringAfter('|') to it.value })
        return buildString {
            for ((section, entries) in bySection.toSortedMap()) {
                append('[').append(section).append("]\n")
                for ((k, v) in entries.sortedBy { it.first }) append(k).append(" = ").append(tomlValue(v)).append('\n')
                append('\n')
            }
        }
    }

    private fun tomlValue(v: String): String = when {
        v == "true" || v == "false" -> v
        v.matches(Regex("-?\\d+")) -> v
        v.matches(Regex("-?\\d+\\.\\d+")) -> v
        else -> "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}

@Implements(xendroid.emulator.Emulator.Config::class)
class ShadowNativeConfig {
    @Implementation
    fun native_open_config(text: String): Long {
        val h = FakeCore.nextHandle++
        FakeCore.tables[h] = FakeCore.parse(text)
        return h
    }

    @Implementation
    fun native_open_config_file(path: String): Long =
        native_open_config(java.io.File(path).takeIf { it.isFile }?.readText() ?: "")

    @Implementation
    fun native_load_config_entry(handle: Long, tag: String): String? = FakeCore.tables[handle]?.get(tag)

    @Implementation
    fun native_load_config_entry_ty_arr(handle: Long, tag: String): Array<String>? =
        FakeCore.tables[handle]?.get(tag)?.let { arrayOf(it) }

    @Implementation
    fun native_save_config_entry(handle: Long, tag: String, value: String) {
        FakeCore.tables.getOrPut(handle) { LinkedHashMap() }[tag] = value
    }

    @Implementation
    fun native_save_config_entry_ty_arr(handle: Long, tag: String, value: Array<String>) {
        native_save_config_entry(handle, tag, value.firstOrNull().orEmpty())
    }

    @Implementation
    fun native_remove_config_entry(handle: Long, tag: String) {
        FakeCore.tables[handle]?.remove(tag)
    }

    @Implementation
    fun native_config_empty(handle: Long): Boolean = FakeCore.tables[handle].isNullOrEmpty()

    @Implementation
    fun native_close_config(handle: Long): String = FakeCore.serialize(FakeCore.tables.remove(handle).orEmpty())

    @Implementation
    fun native_close_config_file(handle: Long, path: String) {
        java.io.File(path).writeText(native_close_config(handle))
    }

    @Implementation
    fun native_free_config(handle: Long) {
        FakeCore.tables.remove(handle)
    }
}

@Implements(Emulator::class)
class ShadowCoreEmulator {
    companion object {
        /** The core "loads" without a native library. */
        @JvmStatic
        @Implementation
        fun load_library() {
            FakeCore.loadFailure?.let { throw it }
            if (Emulator.get == null) Emulator.get = Emulator()
        }
    }

    @Implementation
    fun list_profiles(contentRoot: String): Array<Emulator.ProfileInfo> = FakeCore.profiles.toTypedArray()

    @Implementation
    fun list_content(contentRoot: String, titleId: String, contentType: Int): Array<Emulator.ContentItem> =
        FakeCore.content["${titleId.uppercase()}:$contentType"].orEmpty().toTypedArray()

    @Implementation
    fun content_header(srcPath: String): Emulator.ContentInfo? = FakeCore.headers[java.io.File(srcPath).name]

    @Implementation
    fun simple_device_info(): String = FakeCore.deviceInfo

    @Implementation
    fun audio_volume(): Int = 100
}
