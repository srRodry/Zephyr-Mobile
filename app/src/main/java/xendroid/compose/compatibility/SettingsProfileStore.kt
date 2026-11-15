package xendroid.compose.compatibility

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlinx.serialization.json.Json
import xendroid.compose.archive.ArchiveFiles
import xendroid.compose.archive.withDirectoryLock

/**
 * C05 sources and bookkeeping, plain files (tested on the JVM):
 *  - [bundled]: the profiles shipped in the app (an asset), each with the test it was verified with;
 *  - [localDir]: `settings-profiles/` in the user data the Files app shows, where a vendor or the
 *    player may put a profile file; shown as not reviewed by XenDroid. Nothing is downloaded.
 *  - [recordsDir]: private, what each applied profile wrote and replaced, for "Restore previous".
 */
class SettingsProfileStore(
    private val bundled: () -> String?,
    private val localDir: File,
    private val recordsDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val experimental: () -> String? = { null },
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val titlePattern = Regex("[0-9A-F]{8}")

    /** Every valid profile (the app's first) and why anything else was skipped. */
    fun load(): SettingsProfiles.Parsed {
        val app = bundled()?.let { SettingsProfiles.parse(it, ProfileSource.BUNDLED, "Xendroid+") }
            ?: SettingsProfiles.Parsed(emptyList(), emptyList())
        val local = localFiles().map { file ->
            if (file.length() > SettingsProfiles.MAX_FILE_BYTES) {
                SettingsProfiles.Parsed(emptyList(), listOf("${file.name}: larger than 256 KB"))
            } else {
                runCatching { SettingsProfiles.parse(file.readText(), ProfileSource.LOCAL, file.name) }
                    .getOrElse { SettingsProfiles.Parsed(emptyList(), listOf("${file.name}: could not be read")) }
            }
        }
        val experiments = experimental()?.let {
            SettingsProfiles.parse(it, ProfileSource.EXPERIMENTAL, "Zephyr beta: experimental")
        } ?: SettingsProfiles.Parsed(emptyList(), emptyList())
        return SettingsProfiles.merge(listOf(app, experiments) + local)
    }

    /** At most [MAX_LOCAL_FILES] plain `.json` files (no links, no hidden ones), by name. */
    private fun localFiles(): List<File> = localDir.listFiles().orEmpty()
        .filter { f ->
            f.name.endsWith(".json", ignoreCase = true) && !f.name.startsWith(".") &&
                Files.isRegularFile(f.toPath(), LinkOption.NOFOLLOW_LINKS)
        }
        .sortedBy { it.name.lowercase() }
        .take(MAX_LOCAL_FILES)

    fun forTitle(all: List<LoadedProfile>, titleId: String): List<LoadedProfile> =
        all.filter { titleId.uppercase() in it.profile.titleIds }

    fun applied(titleId: String): AppliedProfile? {
        val file = record(titleId)
        return withDirectoryLock(recordsDir) {
            runCatching {
                if (!file.isFile || file.length() > 64 * 1024) null
                else json.decodeFromString(AppliedProfile.serializer(), file.readText())
                    .takeIf { it.titleId == titleId.uppercase() }
            }.getOrNull()
        }
    }

    /** Kept before the settings are written: if the write never lands, restoring finds no key
     *  holding the profile's value and changes nothing. */
    fun recordApplied(titleId: String, loaded: LoadedProfile, plan: SettingsProfiles.Plan): AppliedProfile {
        val written = plan.writes.mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
        val record = AppliedProfile(titleId = titleId.uppercase(), profileId = loaded.profile.id,
            profileName = loaded.profile.name, source = loaded.source, appliedAt = clock(),
            written = written, previous = written.keys.associateWith { plan.expected[it] })
        val file = record(titleId)
        withDirectoryLock(recordsDir) { ArchiveFiles.atomicText(file, json.encodeToString(AppliedProfile.serializer(), record)) }
        return record
    }

    fun clearApplied(titleId: String) {
        val file = record(titleId)
        withDirectoryLock(recordsDir) { file.delete() }
    }

    private fun record(titleId: String): File {
        val title = titleId.uppercase()
        require(titlePattern.matches(title) && title != "00000000") { "Invalid Title ID" }
        return File(recordsDir, "$title.json")
    }

    companion object {
        const val MAX_LOCAL_FILES = 20
        /** The folder (in the user data) where a profile file may be put. */
        const val LOCAL_FOLDER = "settings-profiles"
        /** The asset with the app's own profiles. */
        const val ASSET = "settings-profiles/recommended.json"
        const val EXPERIMENTAL_ASSET = "settings-profiles/experimental-beta.json"
    }
}
