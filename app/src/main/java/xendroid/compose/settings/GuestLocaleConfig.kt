package xendroid.compose.settings

import android.content.Context
import org.tomlj.Toml
import org.tomlj.TomlTable

/** Persists onboarding's two console settings without loading the emulation core. */
object GuestLocaleConfig {
    fun save(context: Context, language: String?, country: String?) {
        val store = ConfigStore(context)
        ConfigFileTransaction.update(store.globalConfigFile()) { existing ->
            edit(existing ?: store.defaultTemplateText(), language?.toLong(), country?.toLong())
        }
    }

    internal fun edit(text: String, language: Long?, country: Long?): String {
        if (language == null && country == null) return text
        val parsed = Toml.parse(text)
        require(!parsed.hasErrors()) { parsed.errors().first().toString() }
        val console = parsed.get(listOf("Console"))
        require(console == null || console is TomlTable) { "Console must be a TOML table" }
        val current = console as? TomlTable ?: Toml.parse("")
        val changes = buildMap<String, Any> {
            if (language != null) put("user_language", language)
            if (country != null) put("user_country", country)
        }
        if (changes.all { (key, value) -> current.get(listOf(key)) == value }) return text
        return EditedTable(parsed, mapOf("Console" to EditedTable(current, changes))).serialize()
    }

    // The TOML serializer walks entries, including nested tables and arrays.
    private class EditedTable(source: TomlTable, changes: Map<String, Any>) : TomlTable by source {
        private val entries = source.entrySet().associate { it.key to it.value }.toMutableMap().apply {
            putAll(changes)
        }

        override fun entrySet(): MutableSet<MutableMap.MutableEntry<String, Any>> = entries.entries

        fun serialize(): String = StringBuilder().also { super<TomlTable>.toToml(it) }.toString()
    }
}
