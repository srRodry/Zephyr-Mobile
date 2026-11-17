package xendroid.compose.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xendroid.compose.settings.Setting
import xendroid.compose.settings.SettingsSchema

/** Schema-integrity checks (no emulator / JNI needed). */
class SettingsSchemaTest {

    private val all = SettingsSchema.allSettings

    // Verified inventory: 177 entries in beta2, plus the round3 texture lookup memo.
    // Includes disjoint memexport barriers and structured shader jumps from beta1.
    // Display|host_present_from_non_ui_thread is intentionally absent (forced true natively).
    @Test fun total_entry_count_is_178() {
        assertEquals(178, all.size)
        assertEquals(
            178,
            all.count { it is Setting.Bool } + all.count { it is Setting.IntRange } +
                all.count { it is Setting.ListChoice } + all.count { it is Setting.Action } + all.count { it is Setting.Text },
        )
    }

    @Test fun counts_by_type_match_verified_inventory() {
        assertEquals(124, all.count { it is Setting.Bool })        // Includes the two beta1 experiments and round3 texture memo.
        assertEquals(20, all.count { it is Setting.IntRange })     // 14 + 6 new
        assertEquals(30, all.count { it is Setting.ListChoice })   // 27 + 3 new
        assertEquals(2, all.count { it is Setting.Action })
        assertEquals(2, all.count { it is Setting.Text })          // launch_module, cl
    }

    /** The cvars the redesign shows, with the section, type and default the core defines them with. */
    @Test fun new_cvars_match_the_core_definitions() {
        fun b(key: String, def: Boolean) = assertEquals(key, def, (SettingsSchema.byKey[key] as Setting.Bool).default)
        fun i(key: String, def: Int, min: Int, max: Int) = (SettingsSchema.byKey[key] as Setting.IntRange).let {
            assertEquals(key, def, it.default); assertEquals(key, min, it.min); assertEquals(key, max, it.max)
        }
        b("GPU|texture_lookup_memo", true)
        i("Video|internal_display_resolution_x", 1280, 1, 1920)
        i("Video|internal_display_resolution_y", 720, 1, 1080)
        i("Display|postprocess_ffx_fsr_max_upsampling_passes", 1, 1, 4)
        i("Vulkan|adrenotools_turbo_reassert_seconds", 5, 0, 30)
        i("APU|volume", 100, 0, 100)
        i("Kernel|stack_size_multiplier_hack", 1, 1, 8)
        listOf("GPU|async_shader_vs_interpreter", "GPU|async_shader_skip_draws", "GPU|pipeline_storage_precreate",
            "Kernel|precise_guest_delays", "APU|apu_performance_hint", "HID|vibration", "HID|guide_button",
            "GPU|readback_resolve_sync", "GPU|precise_interpolation", "Vulkan|vulkan_allow_reverse_z",
            "Vulkan|vulkan_dynamic_rendering", "Vulkan|vulkan_avoid_geometry_shaders", "Vulkan|vulkan_depth_unorm24",
            "General|guest_crash_is_fatal").forEach { b(it, true) }
        listOf("Vulkan|vulkan_disjoint_memexport_barriers", "GPU|spirv_structured_forward_jumps",
            "GPU|depth_bias_shader_offset", "GPU|memexport_enable", "UI|achievement_notification_position_by_game",
            "Storage|mount_memory_unit").forEach { b(it, false) }
        assertEquals("0", (SettingsSchema.byKey["GPU|draw_resolution_scale_threshold"] as Setting.ListChoice).default)
        assertEquals("-1", (SettingsSchema.byKey["Kernel|console_type"] as Setting.ListChoice).default)
        val gamma = SettingsSchema.byKey["Kernel|kernel_display_gamma_power"] as Setting.ListChoice
        assertEquals("2.22222233", gamma.default)
        gamma.options.forEach { assertEquals("one '.' keeps it a TOML double: ${it.value}", 1, it.value.count { c -> c == '.' }) }
        assertEquals("", (SettingsSchema.byKey["General|launch_module"] as Setting.Text).default)
        assertEquals("", (SettingsSchema.byKey["Kernel|cl"] as Setting.Text).default)
        // The two values the lists did not offer: the custom display mode and the custom gamma.
        assertTrue((SettingsSchema.byKey["Console|internal_display_resolution"] as Setting.ListChoice).options.any { it.value == "17" })
        assertTrue((SettingsSchema.byKey["Kernel|kernel_display_gamma_type"] as Setting.ListChoice).options.any { it.value == "3" })
    }

    /** These keys are looked up by string with a hard cast, so a section move that changes
     *  the key must not go unnoticed. */
    @Test fun keys_referenced_by_code_resolve_to_the_right_type() {
        listOf("Console|user_language", "Console|user_country").forEach { key ->
            val s = SettingsSchema.byKey[key]
            assertNotNull("missing schema key referenced in code: $key", s)
            assertTrue(
                "$key must be a ListChoice for the profile screens",
                s is Setting.ListChoice,
            )
        }
    }

    @Test fun keys_are_unique() {
        assertEquals(all.size, SettingsSchema.byKey.size)
        assertEquals(all.size, all.map { it.key }.toSet().size)
    }

    @Test fun categories_present_in_legacy_order() {
        val expected = listOf(
            "Vulkan", "Video", "UI", "Storage", "Kernel", "Controller", "HID", "Memory", "XConfig",
            "Display", "GPU", "CPU", "Logging", "Content", "General", "APU",
        )
        assertEquals(expected, SettingsSchema.categories.map { it.title })
    }

    @Test fun removed_no_op_settings_stay_removed() {
        assertNull(SettingsSchema.byKey["Kernel|Allow_nui_initialization"])
    }

    @Test fun actions_are_the_driver_picker_and_the_log_export() {
        val actions = all.filterIsInstance<Setting.Action>().map { it.key }
        assertEquals(listOf("Vulkan|vulkan_lib_path", "Logging|dump_session_logs"), actions)
    }

    @Test fun list_defaults_are_empty_or_a_member_of_options() {
        all.filterIsInstance<Setting.ListChoice>().forEach { lc ->
            if (lc.default.isNotEmpty()) {
                assertTrue(
                    "ListChoice ${lc.key} default '${lc.default}' must resolve to an option",
                    lc.options.any { it.value == lc.default },
                )
            }
        }
    }

    @Test fun user_language_skips_10_and_maps_8_and_17_to_zh() {
        val lc = SettingsSchema.byKey["Console|user_language"] as Setting.ListChoice
        assertTrue(lc.options.none { it.value == "10" })
        assertEquals("zh", lc.options.first { it.value == "8" }.label)
        assertEquals("zh", lc.options.first { it.value == "17" }.label)
    }

    @Test fun user_country_has_107_options_skips_17_and_94_and_default_103_resolves() {
        val lc = SettingsSchema.byKey["Console|user_country"] as Setting.ListChoice
        assertEquals(107, lc.options.size)
        assertTrue(lc.options.none { it.value == "17" })
        assertTrue(lc.options.none { it.value == "94" })
        assertNotNull(lc.options.firstOrNull { it.value == "103" })
        assertEquals("103", lc.default)
    }

    @Test fun int_ranges_match_verified_xml() {
        fun ir(key: String) = SettingsSchema.byKey[key] as Setting.IntRange
        ir("Memory|mmap_address_high").let {
            assertEquals(2, it.min); assertEquals(63, it.max); assertEquals(8, it.default)
        }
        ir("GPU|texture_cache_memory_limit_soft").let {
            // min == the real TOML default (384); a higher floor would silently coerce the
            // default upward.
            assertEquals(384, it.min); assertEquals(4096, it.max); assertEquals(384, it.default)
        }
        ir("GPU|texture_cache_memory_limit_hard").let {
            assertEquals(512, it.min); assertEquals(4096, it.max); assertEquals(768, it.default)
        }
        ir("General|time_scalar").let {
            assertEquals(1, it.min); assertEquals(8, it.max)
        }
        ir("Console|xmp_default_volume").let {
            assertEquals(0, it.min); assertEquals(100, it.max)
        }
        ir("APU|apu_max_queued_frames").let {
            assertEquals(4, it.min); assertEquals(64, it.max)
        }
    }

    /** Lists of TOML doubles: the native side stores a value with exactly one '.' as a
     *  double, so every option must have one, or the cvar would be written as an int. */
    @Test fun double_lists_keep_one_dot_in_every_option() {
        listOf("HID|left_stick_deadzone_percentage", "HID|right_stick_deadzone_percentage",
            "Display|postprocess_ffx_cas_additional_sharpness", "Display|postprocess_ffx_fsr_sharpness_reduction")
            .map { SettingsSchema.byKey[it] as Setting.ListChoice }
            .forEach { s -> s.options.forEach { assertEquals("${s.key} ${it.value}", 1, it.value.count { c -> c == '.' }) } }
    }

    /** vulkan_texture_cache reads -1 (no override) and 0..5 (off, 1x..16x). */
    @Test fun anisotropic_override_offers_the_core_values() {
        val s = SettingsSchema.byKey["GPU|anisotropic_override"] as Setting.ListChoice
        assertEquals(listOf("-1", "0", "1", "2", "3", "4", "5"), s.options.map { it.value })
        assertEquals("-1", s.default)
        assertTrue(s.key in SettingsSchema.playerKeys)
    }

    /** 14j: the core's effects by their cvar names (emulator_window.cc), and the dither that
     *  reduces banding reachable without Developer mode. */
    @Test fun scaling_offers_the_core_effects_and_players_can_reduce_banding() {
        val s = SettingsSchema.byKey["Display|postprocess_scaling_and_sharpening"] as Setting.ListChoice
        assertEquals(listOf("bilinear", "cas", "fsr", "sgsr", "lanczos", "crt"), s.options.map { it.value })
        assertTrue("Display|postprocess_dither" in SettingsSchema.playerKeys)
        assertEquals(false, (SettingsSchema.byKey["Display|postprocess_dither"] as Setting.Bool).default)
    }

    /** L02: Player mode shows exactly the curated keys, all real settings, none twice. */
    @Test fun player_mode_shows_only_the_curated_settings() {
        val player = SettingsSchema.categoriesFor(UiMode.PLAYER)
        assertEquals(1, player.size)
        val keys = player.single().settings.map { it.key }
        assertEquals(SettingsSchema.playerKeys, keys)
        assertEquals(keys.toSet().size, keys.size)
        assertEquals(SettingsSchema.categories, SettingsSchema.categoriesFor(UiMode.DEVELOPER))
        // Engine internals and experiments stay in Developer.
        listOf("Vulkan|vulkan_validation", "CPU|validate_hir", "Kernel|guest_scheduler", "GPU|readback_resolve")
            .forEach { assert(it !in keys) { "$it should be Developer-only" } }
        assertEquals(UiMode.PLAYER, UiMode.parse("PLAYER"))
        assertEquals(null, UiMode.parse("player"))
    }

    /** Every IntRange default must be in [min, max], else the slider silently coerces the
     *  persisted default to a different value (the texture-cache bug). */
    @Test fun int_range_defaults_within_bounds() {
        SettingsSchema.allSettings.filterIsInstance<Setting.IntRange>().forEach {
            assert(it.default in it.min..it.max) {
                "${it.key}: default ${it.default} outside [${it.min}, ${it.max}]"
            }
            assert(it.min <= it.max) { "${it.key}: min ${it.min} > max ${it.max}" }
        }
    }
}
