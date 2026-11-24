package xendroid.compose.driver

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xendroid.compose.archive.ArchiveFiles

class DriverPackageInstallerTest {
    @get:Rule val folder = TemporaryFolder()
    private fun elf(marker: Byte): ByteArray = ByteArray(128).apply {
        this[0] = 0x7f; this[1] = 'E'.code.toByte(); this[2] = 'L'.code.toByte(); this[3] = 'F'.code.toByte()
        this[4] = 2; this[5] = 1; this[6] = 1; this[16] = 3; this[18] = 183.toByte(); this[80] = marker
    }
    private fun zip(bytes: ByteArray, libraryName: String = "libvulkan.so", path: String = "libvulkan.so", minApi: Int? = null): File = folder.newFile().also { f ->
        ZipOutputStream(f.outputStream()).use {
            it.putNextEntry(ZipEntry("meta.json")); it.write(("{\"libraryName\":\"$libraryName\"" + (minApi?.let { api -> ",\"minApi\":$api" } ?: "") + "}").toByteArray()); it.closeEntry()
            it.putNextEntry(ZipEntry(path)); it.write(bytes); it.closeEntry()
        }
    }
    @Test fun importsAreImmutableAndSameArchiveIsReusedOnlyIfVerified() {
        val installer = DriverPackageInstaller(folder.newFolder(), 34, 4096)
        val archive = zip(elf(1))
        val first = installer.install(archive, ArchiveFiles.sha256(archive))
        assertTrue(first.verifiedDownload)
        assertEquals(first.library, installer.install(archive).library)
        val second = installer.install(zip(elf(2)))
        assertNotEquals(first.library.parent, second.library.parent)
        assertArrayEquals(elf(1), first.library.readBytes())
    }
    @Test fun wrongAbiDigestAndUnsafeMetadataCannotInstall() {
        for (archive in listOf(zip(elf(1).apply { this[18] = 62 }), zip(elf(1), "../libvulkan.so"))) {
            val root = folder.newFolder()
            assertThrows(IllegalArgumentException::class.java) { DriverPackageInstaller(root, 34, 4096).install(archive) }
            assertFalse(root.listFiles()!!.any { it.isDirectory })
        }
        assertThrows(IllegalArgumentException::class.java) { DriverPackageInstaller(folder.newFolder(), 34, 4096).install(zip(elf(1)), "0".repeat(64)) }
    }
    @Test fun minimumApiAppliesToFreshAndPreviouslyImportedPackages() {
        val root = folder.newFolder()
        val archive = zip(elf(1), minApi = 34)
        val older = DriverPackageInstaller(root, 33, 4096)
        assertThrows(IllegalArgumentException::class.java) { older.install(archive) }
        val installed = DriverPackageInstaller(root, 34, 4096).install(archive)
        assertThrows(IllegalArgumentException::class.java) { older.install(archive) }
        assertArrayEquals(elf(1), installed.library.readBytes())
    }

    @Test fun largePageDevicesValidateLoadSegmentsOnFreshAndReusedImports() {
        fun library(alignment: Long, address: Long) = elf(1).also { bytes ->
            val h = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            h.putLong(32, 64)
            h.putShort(54, 56)
            h.putShort(56, 1)
            h.putInt(64, 1)
            h.putLong(72, 0)
            h.putLong(80, address)
            h.putLong(112, alignment)
        }
        val root = folder.newFolder()
        val installer = DriverPackageInstaller(root, 34, 16384)
        val small = zip(library(4096, 0))
        DriverPackageInstaller(root, 34, 4096).install(small)
        assertThrows(IllegalArgumentException::class.java) { installer.install(small) }
        assertThrows(IllegalArgumentException::class.java) { installer.install(zip(library(16384, 4096))) }
        assertTrue(installer.install(zip(library(16384, 0))).library.isFile)
    }

    @Test fun interruptedExtractionLeavesInstalledDriverUntouched() {
        val root = folder.newFolder()
        val installer = DriverPackageInstaller(root, 34, 4096)
        val previous = installer.install(zip(elf(1)))
        var calls = 0
        assertThrows(java.io.IOException::class.java) {
            installer.install(zip(elf(2))) { if (++calls == 3) throw java.io.IOException("cancel") }
        }
        assertArrayEquals(elf(1), previous.library.readBytes())
        assertFalse(root.listFiles()!!.any { it.name.startsWith(".install-") })
    }
}
