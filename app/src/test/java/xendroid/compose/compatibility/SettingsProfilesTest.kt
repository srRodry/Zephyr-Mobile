package xendroid.compose.compatibility

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xendroid.compose.compatibility.SettingsProfiles.Kind
import xendroid.compose.settings.Setting
import xendroid.compose.settings.SettingsSchema

class SettingsProfilesTest {
    @Test fun zephyrPerformanceExperimentsStayLocalAndChangeOneGraphicsVariable() {
        val text = checkNotNull(javaClass.getResource("/zephyr-performance-experiments.json")).readText()
        val local = SettingsProfiles.parse(text, ProfileSource.LOCAL, "experiments.json")
        assertTrue(local.skipped.toString(), local.skipped.isEmpty())
        assertEquals(4, local.profiles.size)
        local.profiles.forEach { loaded ->
            val profile = loaded.profile
            assertEquals(listOf("4D530AA4"), profile.titleIds)
            assertEquals(1, profile.settings.size)
            assertTrue(profile.settings.keys.none { it.startsWith("APU|") })
            assertTrue(profile.evidence.isEmpty())
            assertTrue(SettingsProfiles.problems(profile, ProfileSource.BUNDLED).isNotEmpty())
        }
        val phone = DeviceFacts("Adreno (TM) 730", "Turnip", "custom", "motorola",
            "motorola edge 30 ultra", 35, 35)
        assertTrue(local.profiles.all { SettingsProfiles.unmet(it.profile.requires, phone).isEmpty() })
        val turnip = local.profiles.first().profile
        assertTrue(SettingsProfiles.unmet(turnip.requires, phone.copy(driverLoader = "system", driverLabel = "Qualcomm")).isNotEmpty())
        // The experiment uses the existing preview and exact restore machinery.
        val plan = SettingsProfiles.plan(turnip, emptyMap(), mapOf("Vulkan|turnip_debug" to "sysmem"))
        assertEquals(mapOf("Vulkan|turnip_debug" to ""), plan.writes)
    }

    @get:Rule val folder = TemporaryFolder()

    private val adreno = DeviceFacts("Adreno (TM) 740", "Qualcomm Vulkan 0.762 · Adreno (TM) 740", "system",
        "samsung", "SM-S911B", 34, 100)

    private fun profile(
        id: String = "halo-30",
        settings: Map<String, String> = mapOf("GPU|framerate_limit" to "30", "Console|widescreen" to "true"),
        requires: String = "{}",
        evidence: String = """[{"result":"Steady 30 FPS in the first level","build":"b1","gpu":"Adreno (TM) 740","date":"2026-09-30"}]""",
    ) = """{"id":"$id","name":"Steady 30","titleIds":["4d5307e6"],"reason":"Holds 30 FPS where 60 stutters",
        |"settings":{${settings.entries.joinToString(",") { (k, v) -> "\"$k\":\"$v\"" }}},
        |"requires":$requires,"evidence":$evidence}""".trimMargin()

    private fun file(vararg profiles: String) =
        """{"format":"xendroid-settings-profiles","version":1,"profiles":[${profiles.joinToString(",")}]}"""

    @Test fun onlyAllowedSettingsWithValuesTheSettingOffers() {
        SettingsProfiles.ALLOWED_KEYS.forEach { key ->
            val s = SettingsSchema.byKey[key]
            assertTrue("$key is not a setting", s != null && s !is Setting.Action)
        }
        listOf("Vulkan|vulkan_lib_path", "Vulkan|adrenotools_force_max_clocks", "Kernel|network_enabled",
            "Logging|log_level", "General|apply_patches", "Console|user_language").forEach { key ->
            assertTrue(key, key !in SettingsProfiles.ALLOWED_KEYS)
        }
        fun problemWith(key: String, value: String) =
            SettingsProfiles.parse(file(profile(settings = mapOf(key to value))), ProfileSource.LOCAL, "f.json").skipped.single()
        assertTrue(problemWith("Vulkan|vulkan_lib_path", "/sdcard/x.so").contains("not a setting profiles may change"))
        assertTrue(problemWith("Console|widescreen", "yes").contains("true or false"))
        assertTrue(problemWith("Kernel|guest_scheduler_quantum_us", "100").contains("250 to 8000"))
        assertTrue(problemWith("GPU|framerate_limit", "50").contains("must be one of"))
        assertTrue(problemWith("APU|xma_decoder", "fake").contains("not allowed"))
        assertTrue(problemWith("GPU|no_such_thing", "1").contains("not a setting profiles may change"))
    }

    @Test fun aFileInAnotherFormatOrWithUnknownRulesIsSkippedWhole() {
        val unknownRequirement = profile(requires = """{"gpuContains":["Adreno"],"onlyWhenCharging":true}""")
        val parsed = SettingsProfiles.parse(file(unknownRequirement), ProfileSource.LOCAL, "new.json")
        assertTrue(parsed.profiles.isEmpty())                       // an ignored rule would offer it anywhere
        assertEquals("new.json: not a settings profile file", parsed.skipped.single())
        val v2 = file(profile()).replace("\"version\":1", "\"version\":2")
        assertTrue(SettingsProfiles.parse(v2, ProfileSource.LOCAL, "v2.json").skipped.single().contains("version 2 is not supported"))
        assertTrue(SettingsProfiles.parse("{", ProfileSource.LOCAL, "x.json").profiles.isEmpty())
        assertTrue(SettingsProfiles.parse("x".repeat(SettingsProfiles.MAX_FILE_BYTES + 1), ProfileSource.LOCAL, "big.json")
            .skipped.single().contains("larger than"))
    }

    @Test fun whatTheAppShipsNeedsItsTestAndIdsAreUnique() {
        val untested = profile(evidence = "[]")
        assertTrue(SettingsProfiles.parse(file(untested), ProfileSource.BUNDLED, "XenDroid").skipped.single()
            .contains("needs the test it was verified with"))
        assertEquals(1, SettingsProfiles.parse(file(untested), ProfileSource.LOCAL, "mine.json").profiles.size)
        val twice = SettingsProfiles.parse(file(profile(), profile()), ProfileSource.LOCAL, "two.json")
        assertEquals(1, twice.profiles.size)
        assertTrue(twice.skipped.single().contains("the same id twice"))
        // The app's own profile keeps its id; a file reusing it is skipped.
        val app = SettingsProfiles.parse(file(profile()), ProfileSource.BUNDLED, "XenDroid")
        val local = SettingsProfiles.parse(file(profile(), profile(id = "other")), ProfileSource.LOCAL, "local.json")
        val merged = SettingsProfiles.merge(listOf(app, local))
        assertEquals(listOf("halo-30" to ProfileSource.BUNDLED, "other" to ProfileSource.LOCAL),
            merged.profiles.map { it.profile.id to it.source })
        assertTrue(merged.skipped.single().contains("id already used"))
        assertEquals(listOf("4D5307E6"), merged.profiles.first().profile.titleIds)   // Title IDs upper-cased
    }

    @Test fun requirementsSayWhyAProfileIsNotForThisPhone() {
        val r = ProfileRequirements(gpuContains = listOf("Adreno"), driverLoader = "custom", manufacturers = listOf("Samsung"),
            minAndroidSdk = 35, minAppVersionCode = 100)
        val why = SettingsProfiles.unmet(r, adreno)
        assertEquals(2, why.size)
        assertTrue(why[0].contains("custom driver (the game last ran on the system one)"))
        assertTrue(why[1].contains("API level 35"))
        assertTrue(SettingsProfiles.unmet(r.copy(driverLoader = "system", minAndroidSdk = 34), adreno).isEmpty())
        val unknown = adreno.copy(gpu = null, driverLoader = null, driverLabel = null)
        assertEquals(2, SettingsProfiles.unmet(r.copy(minAndroidSdk = null), unknown).size)      // GPU and driver not known yet
        assertTrue(SettingsProfiles.unmet(ProfileRequirements(gpuContains = listOf("Mali")), adreno).single().contains("this one: Adreno"))
        assertTrue(SettingsProfiles.unmet(ProfileRequirements(models = listOf("Pixel 8")), adreno).single().contains("Pixel 8"))
        assertTrue(SettingsProfiles.unmet(ProfileRequirements(driverContains = listOf("Turnip")), adreno).single().contains("Turnip"))
        val loaded = SettingsProfiles.parse(file(profile()), ProfileSource.LOCAL, "f.json").profiles.single().profile
        assertTrue(SettingsProfiles.testedOnThisGpu(loaded, adreno))
        assertFalse(SettingsProfiles.testedOnThisGpu(loaded, adreno.copy(gpu = "Adreno (TM) 750")))
    }

    @Test fun thePreviewKeepsWhatThePlayerChoseForTheGame() {
        val p = SettingsProfiles.parse(file(profile(settings = linkedMapOf(
            "GPU|framerate_limit" to "30",                 // follows the global 60: changes
            "Console|widescreen" to "true",                // the global is already on: kept for the game
            "GPU|readback_resolve" to "fast",              // the game already has it
            "Kernel|guest_scheduler_quantum_us" to "2000", // the player chose 500 for this game
            "Vulkan|vulkan_pipeline_creation_threads" to "2",
        ))), ProfileSource.LOCAL, "f.json").profiles.single().profile
        val overrides = mapOf("GPU|readback_resolve" to "fast", "Kernel|guest_scheduler_quantum_us" to "500",
            "Vulkan|vulkan_pipeline_creation_threads" to "2.0")       // an int that round-tripped as a double
        val inherited = mapOf("GPU|framerate_limit" to "60", "Console|widescreen" to "true")
        val plan = SettingsProfiles.plan(p, overrides, inherited)
        assertEquals(listOf(Kind.CHANGE, Kind.PIN, Kind.SAME, Kind.YOURS, Kind.SAME), plan.lines.map { it.kind })
        assertEquals("60 FPS" to "30 FPS", plan.lines[0].now to plan.lines[0].target)
        // U02: the raw values travel too, for the screen to label them in its language.
        assertEquals("60" to "30", plan.lines[0].nowRaw to plan.lines[0].targetRaw)
        assertEquals("500" to "2000", plan.lines[3].now to plan.lines[3].target)
        assertEquals(mapOf("GPU|framerate_limit" to "30", "Console|widescreen" to "true"), plan.writes)
        // Every key looked at must still read the same when applying, the player's own included.
        assertEquals(mapOf("GPU|framerate_limit" to null, "Console|widescreen" to null, "GPU|readback_resolve" to "fast",
            "Kernel|guest_scheduler_quantum_us" to "500", "Vulkan|vulkan_pipeline_creation_threads" to "2.0"), plan.expected)
        // With no global value known the schema default stands in.
        assertEquals(Kind.PIN, SettingsProfiles.plan(p, emptyMap(), emptyMap()).lines[1].kind)
    }

    @Test fun restoringPutsBackOnlyWhatTheProfileWroteAndNobodyChanged() {
        val applied = AppliedProfile(titleId = "4D5307E6", profileId = "halo-30", profileName = "Steady 30",
            source = ProfileSource.LOCAL, appliedAt = 1L,
            written = mapOf("GPU|framerate_limit" to "30", "Console|widescreen" to "true", "GPU|occlusion_query" to "fake"),
            previous = mapOf("GPU|framerate_limit" to null, "Console|widescreen" to null, "GPU|occlusion_query" to null))
        val overrides = mapOf("GPU|framerate_limit" to "30", "Console|widescreen" to "false")   // occlusion_query: removed since
        val plan = SettingsProfiles.restorePlan(applied, overrides, mapOf("GPU|framerate_limit" to "60"))
        assertEquals(listOf(Kind.CHANGE, Kind.YOURS, Kind.YOURS), plan.lines.map { it.kind })
        assertEquals(mapOf("GPU|framerate_limit" to null), plan.writes)          // null: follow the global again
        assertEquals("30 FPS" to "60 FPS (global)", plan.lines[0].now to plan.lines[0].target)
        assertEquals(Triple("30", "60", true), Triple(plan.lines[0].nowRaw, plan.lines[0].targetRaw, plan.lines[0].targetGlobal))
        assertEquals("Off", plan.lines[1].now)
        assertEquals("false", plan.lines[1].nowRaw)
        assertEquals(mapOf("GPU|framerate_limit" to "30", "Console|widescreen" to "false", "GPU|occlusion_query" to null), plan.expected)
    }

    @Test fun theStoreReadsTheAppsListAndPlainFilesAndKeepsWhatWasApplied() {
        val local = folder.newFolder("settings-profiles")
        File(local, "vendor.json").writeText(file(profile(id = "vendor", evidence = "[]")))
        File(local, "notes.txt").writeText("not a profile")
        File(local, ".hidden.json").writeText(file(profile(id = "hidden")))
        File(local, "broken.json").writeText("{")
        Files.createSymbolicLink(File(local, "link.json").toPath(), File(local, "vendor.json").toPath())
        val records = File(folder.root, "applied")
        val store = SettingsProfileStore({ file(profile()) }, local, records, clock = { 42L })
        val loaded = store.load()
        assertEquals(listOf("halo-30", "vendor"), loaded.profiles.map { it.profile.id })
        assertEquals(listOf("broken.json: not a settings profile file"), loaded.skipped)
        assertEquals(2, store.forTitle(loaded.profiles, "4d5307e6").size)
        assertTrue(store.forTitle(loaded.profiles, "41560817").isEmpty())

        assertNull(store.applied("4D5307E6"))
        val plan = SettingsProfiles.plan(loaded.profiles.first().profile, emptyMap(), mapOf("GPU|framerate_limit" to "60"))
        val record = store.recordApplied("4d5307e6", loaded.profiles.first(), plan)
        assertEquals(record, store.applied("4D5307E6"))
        assertEquals(mapOf("GPU|framerate_limit" to "30", "Console|widescreen" to "true"), record.written)
        assertEquals(mapOf("GPU|framerate_limit" to null, "Console|widescreen" to null), record.previous)
        assertEquals(42L, record.appliedAt)
        store.clearApplied("4D5307E6")
        assertNull(store.applied("4D5307E6"))
        // No local folder at all is no profile files, not an error.
        assertTrue(SettingsProfileStore({ null }, File(folder.root, "absent"), records).load().let { it.profiles.isEmpty() && it.skipped.isEmpty() })
    }

    @Test fun theAppShipsNoProfileWithoutATest() {
        val shipped = File("src/main/assets/${SettingsProfileStore.ASSET}").readText()
        val parsed = SettingsProfiles.parse(shipped, ProfileSource.BUNDLED, "XenDroid")
        assertTrue(parsed.skipped.isEmpty())
        parsed.profiles.forEach { assertTrue(it.profile.evidence.isNotEmpty()) }
    }
}
