package xendroid.compose.core

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchExecutablesTest {
    @Test fun extractedGameOffersOnlySiblingExecutablesWithExactCase() {
        val root = Files.createTempDirectory("xex-picker").toFile()
        try {
            for (name in listOf("Default.xex", "AO4.Main.xex", "AO4.Splash.XEX", "Engine.BuildInfo.xex", "NxeArt")) {
                File(root, name).writeText("fixture")
            }
            File(root, "folder.xex").mkdir()
            File(root, "Data").mkdir()
            File(root, "Data/nested.xex").writeText("fixture")
            assertEquals(listOf("AO4.Main.xex", "AO4.Splash.XEX", "Default.xex", "Engine.BuildInfo.xex"),
                launchExecutables(File(root, "Default.xex").path))
        } finally { root.deleteRecursively() }
    }

    @Test fun packagedGameDoesNotOfferUnrelatedHostExecutables() {
        assertNull(launchExecutables("/games/disc.iso"))
        assertNull(launchExecutables("/games/disc.zar"))
    }
}
