package xendroid.compose.ui.settings

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xendroid.compose.settings.SettingCatalog
import xendroid.compose.ui.design.XdSection

class SettingsHubRoutingTest {
    private fun section(id: String) = XdSection(id, id, ImageVector.Builder(
        defaultWidth = 1.dp, defaultHeight = 1.dp, viewportWidth = 1f, viewportHeight = 1f).build()) {}

    private fun originalSections(): List<XdSection> =
        (SettingCatalog.groups.map { "set:${it.name}" } + listOf(SettingsSectionIds.SUMMARY,
            SettingsSectionIds.UI, SettingsSectionIds.LANGUAGE, SettingsSectionIds.DATA, SettingsSectionIds.UPDATES,
            SettingsSectionIds.COMMUNITY, SettingsSectionIds.DIAGNOSTICS, SettingsSectionIds.ABOUT)).map(::section)

    @Test fun everyOriginalSectionIsReachableExactlyOnce() {
        val original = originalSections()
        val routed = SettingsHubCategory.entries.flatMap { settingsHubSections(it, original) }.map { it.id }
        assertEquals(original.map { it.id }.toSet(), routed.toSet())
        assertEquals(routed.size, routed.toSet().size)
    }

    @Test fun absentUpdateSectionDoesNotCreateADeadLink() {
        val sections = originalSections().filterNot { it.id == SettingsSectionIds.UPDATES }
        val system = settingsHubSections(SettingsHubCategory.SYSTEM, sections)
        assertFalse(system.any { it.id == SettingsSectionIds.UPDATES })
        assertTrue(system.any { it.id == SettingsSectionIds.LANGUAGE })
    }

    @Test fun futureSectionsRemainInAdvancedButSyntheticDestinationsDoNot() {
        val sections = originalSections() + listOf(section("app:future"), section(SettingsSectionIds.HOME), section("hub:graphics"))
        val advanced = settingsHubSections(SettingsHubCategory.ADVANCED, sections)
        assertTrue(advanced.any { it.id == "app:future" })
        assertFalse(advanced.any { it.id == SettingsSectionIds.HOME || it.id.startsWith("hub:") })
    }
}
