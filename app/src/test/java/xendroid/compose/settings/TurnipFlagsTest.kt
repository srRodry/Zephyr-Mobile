package xendroid.compose.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnipFlagsTest {
    @Test fun flagsOneByOneInTheSameList() {
        assertEquals("sysmem,nolrz", TurnipFlags.toggle("sysmem", "nolrz"))
        assertEquals("sysmem", TurnipFlags.toggle("sysmem,nolrz", "nolrz"))
        assertEquals("", TurnipFlags.toggle("sysmem", "sysmem"))
        assertEquals("nolrz", TurnipFlags.toggle("", "nolrz"))
        assertEquals("sysmem,nobin", TurnipFlags.toggle("sysmem,forcebin", "nobin"))
        assertEquals("gmem,forcebin", TurnipFlags.toggle("gmem,nobin", "forcebin"))
        // sysmem and gmem are the two rendering paths: choosing one takes the other off.
        assertEquals("nolrz,gmem", TurnipFlags.toggle("sysmem,nolrz", "gmem"))
        assertEquals("sysmem", TurnipFlags.toggle("gmem", "sysmem"))
    }

    @Test fun whatThisBuildDoesNotKnowStays() {
        assertEquals(listOf("push_consts_per_stage", "rast_order"), TurnipFlags.unknown("sysmem,push_consts_per_stage, rast_order"))
        assertEquals("push_consts_per_stage,rast_order,noubwc", TurnipFlags.toggle("sysmem,push_consts_per_stage,rast_order", "sysmem")
            .let { TurnipFlags.toggle(it, "noubwc") })
        // Junk never reaches the driver's environment.
        assertEquals(listOf("sysmem"), TurnipFlags.parse(" SYSMEM , ,sysmem,x y,$(rm)"))
    }

    @Test fun everyListedFlagHasHelpAndTheShippedPresetsAreCombinations() {
        assertTrue(TurnipFlags.KNOWN.all { it.help.length in 20..200 })
        val presets = (SettingsSchema.byKey.getValue("Vulkan|turnip_debug") as Setting.ListChoice).options.map { it.value }
        presets.forEach { preset -> assertTrue(preset, TurnipFlags.unknown(preset).isEmpty()) }
        assertTrue("Logging|log_mask" in SettingsSchema.byKey)
    }
}
