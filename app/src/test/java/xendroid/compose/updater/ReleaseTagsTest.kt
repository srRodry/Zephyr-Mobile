package xendroid.compose.updater

import org.junit.Assert.*
import org.junit.Test

class ReleaseTagsTest {
    @Test fun parsesNumberedAndLegacyTags() {
        assertEquals(ReleaseTag(42, "1a2b3c4d"), parseReleaseTag("XenDroid-v42-1A2B3C4D"))
        assertEquals(ReleaseTag(null, "1a2b3c4d"), parseReleaseTag("XenDroid-1a2b3c4d"))
        assertNull(parseReleaseTag("XenDroid-latest"))
        assertNull(parseReleaseTag("v1.0"))
    }

    @Test fun onlyAStrictlyNewerNumberedBuildIsAnUpdate() {
        val release = ReleaseTag(42, "1a2b3c4d")
        assertTrue(isUpdateFor(release, installedVersionCode = 41, installedCommit = "99999999"))
        assertFalse(isUpdateFor(release, installedVersionCode = 42, installedCommit = "99999999"))
        assertFalse(isUpdateFor(release, installedVersionCode = 43, installedCommit = "99999999"))
        assertFalse("same commit", isUpdateFor(release, installedVersionCode = 41, installedCommit = "1a2b3c4d"))
    }

    @Test fun unorderedBuildsAreNeverOfferedAnUpdate() {
        // A legacy tag has no version code; a local build is numbered 1.
        assertFalse(isUpdateFor(ReleaseTag(null, "1a2b3c4d"), installedVersionCode = 41, installedCommit = "99999999"))
        assertFalse(isUpdateFor(ReleaseTag(42, "1a2b3c4d"), installedVersionCode = 1, installedCommit = "unknown"))
    }

    @Test fun researchTagsAreNumberedAndPackageIsolated() {
        assertEquals(ReleaseTag(100013, "1a2b3c4d"),
            parseReleaseTag("Zephyr-Research-v100013-1A2B3C4D"))
        assertTrue(releaseMatchesPackage("Zephyr-Research-v100013-1a2b3c4d", "xendroid.compose.zephyr.research"))
        assertFalse(releaseMatchesPackage("XenDroid-v100014-1a2b3c4d", "xendroid.compose.zephyr.research"))
        assertFalse(releaseMatchesPackage("Zephyr-Research-v100013-1a2b3c4d", "xendroid.compose.fork"))
        assertFalse(releaseMatchesPackage("Zephyr-Research-v100013-1a2b3c4d", "xendroid.compose.debug"))
        assertFalse(releaseMatchesPackage("Zephyr-Research-v100013-1a2b3c4d", "xendroid.compose.uitest"))
        assertTrue(releaseMatchesPackage("XenDroid-v42-1a2b3c4d", "xendroid.compose.fork"))
    }
}
