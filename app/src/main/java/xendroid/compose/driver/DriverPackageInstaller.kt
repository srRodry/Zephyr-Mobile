package xendroid.compose.driver

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import xendroid.compose.archive.ArchiveFiles
import xendroid.compose.archive.ArchiveLimits
import xendroid.compose.archive.ContentLease

@Serializable
private data class DriverInstalledFile(val name: String, val sha256: String, val size: Long)
@Serializable
private data class DriverInstallation(val version: Int = 1, val archiveSha256: String,
                                      val library: String, val files: List<DriverInstalledFile>)
data class InstalledDriver(val library: File, val sha256: String, val verifiedDownload: Boolean)

/** Immutable, content-addressed packages. A failed import cannot change selection or
 * an installed package; the UI selects a driver only AFTER this operation succeeds. */
class DriverPackageInstaller(private val root: File, private val androidApi: Int,
                             private val pageSize: Long) {
    private val limits = ArchiveLimits(64L * 1024 * 1024, 256L * 1024 * 1024, 128L * 1024 * 1024, 256)
    private val json = Json { ignoreUnknownKeys = true }
    private val marker = "xendroid-installation.json"

    fun install(archive: File, expectedSha256: String? = null, checkCancelled: () -> Unit = {}): InstalledDriver {
        require(archive.length() <= limits.packedBytes) { "Driver ZIP is too large" }
        val hash = ArchiveFiles.sha256(archive)
        if (!expectedSha256.isNullOrEmpty()) {
            require(expectedSha256.matches(Regex("(?i)[0-9a-f]{64}")) && hash.equals(expectedSha256, true)) { "SHA-256 verification failed" }
        }
        ContentLease.acquire(root).use {
            val existing = ArchiveFiles.resolve(root, hash)
            if (existing.isDirectory) {
                val installed = runCatching { verifiedInstallation(existing, hash) }.getOrNull()
                if (installed != null) return InstalledDriver(installed, hash, !expectedSha256.isNullOrEmpty())
            }
            val stage = Files.createTempDirectory(root.toPath(), ".install-").toFile()
            try {
                val extracted = ArchiveFiles.unpack(archive, stage, limits, checkCancelled)
                require(extracted.none { it.name == marker }) { "Reserved driver metadata" }
                val meta = File(stage, "meta.json")
                val library = if (meta.isFile) {
                    val metadata = readMetadata(meta)
                    metadata["libraryName"]?.jsonPrimitive?.contentOrNull
                        ?: error("Missing libraryName in meta.json")
                } else {
                    extracted.filter { it.extension == "so" }.singleOrNull()?.relativeTo(stage)?.invariantSeparatorsPath
                        ?: error("A driver without metadata must contain exactly one library")
                }
                val selected = ArchiveFiles.resolve(stage, library)
                require(selected.isFile && selected.extension == "so") { "Driver library missing" }
                extracted.filter { it.extension == "so" }.forEach { validateForDevice(it) }
                val manifest = DriverInstallation(archiveSha256 = hash, library = library,
                    files = extracted.map { DriverInstalledFile(it.relativeTo(stage).invariantSeparatorsPath, ArchiveFiles.sha256(it), it.length()) })
                ArchiveFiles.atomicText(File(stage, marker), json.encodeToString(manifest))
                checkCancelled()
                // A previously damaged package stays available for inspection/rollback;
                // never overwrite a binary that a guest may still have mapped.
                val destination = if (existing.exists()) ArchiveFiles.resolve(root, "$hash-${UUID.randomUUID()}") else existing
                Files.move(stage.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
                return InstalledDriver(ArchiveFiles.resolve(destination, library), hash, !expectedSha256.isNullOrEmpty())
            } finally { ArchiveFiles.deleteTree(stage) }
        }
    }

    private fun readMetadata(file: File): JsonObject {
        require(file.length() <= 64 * 1024) { "Driver metadata too large" }
        val metadata = json.parseToJsonElement(file.readText()).jsonObject
        metadata["minApi"]?.let { value ->
            val minimum = value.jsonPrimitive.contentOrNull?.toIntOrNull()
            require(minimum != null && minimum > 0) { "Invalid driver minApi" }
            require(minimum <= androidApi) { "Driver requires Android API $minimum (device API $androidApi)" }
        }
        return metadata
    }

    private fun verifiedInstallation(dir: File, hash: String): File {
        val metadata = ArchiveFiles.resolve(dir, marker)
        require(metadata.length() in 1..1024 * 1024)
        val m = json.decodeFromString<DriverInstallation>(metadata.readText())
        require(m.version == 1 && m.archiveSha256 == hash && m.files.size <= limits.entries)
        m.files.forEach {
            val f = ArchiveFiles.resolve(dir, it.name)
            require(f.isFile && f.length() == it.size && ArchiveFiles.sha256(f) == it.sha256)
        }
        File(dir, "meta.json").takeIf { it.isFile }?.let(::readMetadata)
        m.files.filter { it.name.endsWith(".so") }.forEach { validateForDevice(ArchiveFiles.resolve(dir, it.name)) }
        return ArchiveFiles.resolve(dir, m.library).also(::validateArm64Library)
    }

    private fun validateForDevice(file: File) {
        validateArm64Library(file)
        if (pageSize <= 4096L) return
        RandomAccessFile(file, "r").use { input ->
            val bytes = ByteArray(64)
            val fields = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            input.readFully(bytes)
            val offset = fields.getLong(32)
            val stride = fields.getShort(54).toInt() and 0xffff
            val count = fields.getShort(56).toInt() and 0xffff
            require(offset >= 64 && stride >= 56 && count > 0 &&
                offset <= input.length() - count.toLong() * stride) { "Invalid ELF program headers" }
            var hasLoadSegment = false
            for (index in 0 until count) {
                input.seek(offset + index.toLong() * stride)
                input.readFully(bytes, 0, 56)
                if (fields.getInt(0) != 1) continue // PT_LOAD
                hasLoadSegment = true
                val alignment = fields.getLong(48)
                require(alignment >= pageSize && (alignment and (alignment - 1)) == 0L &&
                    (fields.getLong(8) and (pageSize - 1)) == (fields.getLong(16) and (pageSize - 1))) {
                    "Driver segment is incompatible with the device page size ($pageSize bytes)"
                }
            }
            require(hasLoadSegment) { "Driver has no loadable ELF segment" }
        }
    }

    companion object {
        /** ELF64, little-endian, ET_DYN, AArch64; reject wrong ABI before loading. */
        fun validateArm64Library(file: File) {
            val h = ByteArray(64)
            require(file.isFile && file.length() >= h.size) { "Driver is not a library" }
            file.inputStream().use { input -> require(input.read(h) == h.size) }
            require(h[0] == 0x7f.toByte() && h[1] == 'E'.code.toByte() && h[2] == 'L'.code.toByte() && h[3] == 'F'.code.toByte() &&
                h[4] == 2.toByte() && h[5] == 1.toByte() && h[6] == 1.toByte() &&
                h[16] == 3.toByte() && h[17] == 0.toByte() && h[18] == 183.toByte() && h[19] == 0.toByte()) {
                "Expected an AArch64 shared Vulkan library"
            }
        }
    }
}
