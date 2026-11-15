package xendroid.compose.compatibility

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xendroid.compose.settings.ConfigValueShape
import xendroid.compose.settings.Setting
import xendroid.compose.settings.SettingsSchema

/** What a profile needs before it is offered; every listed condition must hold. */
@Serializable
data class ProfileRequirements(
    /** Any of these in the GPU name ("Adreno (TM) 740"), ignoring case. */
    val gpuContains: List<String> = emptyList(),
    /** "system" or "custom": the Vulkan driver the game last ran on. */
    val driverLoader: String? = null,
    /** Any of these in the name/version of the driver the game last ran on, ignoring case. */
    val driverContains: List<String> = emptyList(),
    /** Vendor/OEM: any of these manufacturers or models (Build.MANUFACTURER, Build.MODEL). */
    val manufacturers: List<String> = emptyList(),
    val models: List<String> = emptyList(),
    val minAndroidSdk: Int? = null,
    val minAppVersionCode: Int? = null,
)

/** One test behind a profile: what was seen, on which build, GPU and driver, and when. */
@Serializable
data class ProfileEvidence(val result: String, val build: String, val gpu: String, val driver: String = "", val date: String)

@Serializable
data class SettingsProfile(
    val id: String,
    val name: String,
    val titleIds: List<String>,
    /** Why: the problem these settings address, in the player's words. */
    val reason: String,
    /** "Section|name" -> value, as the settings store them. */
    val settings: Map<String, String>,
    val requires: ProfileRequirements = ProfileRequirements(),
    val evidence: List<ProfileEvidence> = emptyList(),
)

@Serializable
data class SettingsProfileFile(val format: String, val version: Int, val profiles: List<SettingsProfile> = emptyList())

/** Where a profile came from: shipped in the app, a file on the phone, or (15b, only when the
 *  build names a community server and the player searched it) shared by another player. */
enum class ProfileSource { BUNDLED, LOCAL, COMMUNITY, EXPERIMENTAL }

data class LoadedProfile(val profile: SettingsProfile, val source: ProfileSource, val origin: String)

/** The phone as requirements see it; null = not known yet (no run recorded a driver). */
data class DeviceFacts(
    val gpu: String?,
    val driverLabel: String?,
    val driverLoader: String?,
    val manufacturer: String,
    val model: String,
    val androidSdk: Int,
    val appVersionCode: Int,
    /** The chip (Build.SOC_MODEL, Android 12+), for ranking community configs (15b). */
    val soc: String? = null,
)

/** Kept when a profile is applied: what it wrote and what each key held before, so
 *  "Restore previous" undoes exactly that and nothing the player changed since. */
@Serializable
data class AppliedProfile(
    val version: Int = 1,
    val titleId: String,
    val profileId: String,
    val profileName: String,
    val source: ProfileSource,
    val appliedAt: Long,
    /** key -> value the profile wrote. */
    val written: Map<String, String>,
    /** key -> this game's own value before (null = it followed the global settings). */
    val previous: Map<String, String?>,
)

/**
 * C05: recommended settings for a game, as a reviewed list of per-game settings. A profile
 * may only touch [ALLOWED_KEYS] with values the setting offers; it is offered only on the
 * hardware its requirements name; it is applied after a preview of every change; what the
 * player set for the game themselves is kept (their override wins); and "Restore previous"
 * puts back what was there. Profiles come from the app itself (each with the test it was
 * verified with) or from a file on the phone (a vendor's or the player's own, shown as not
 * reviewed); none from a feed. The rules are pure, so they are tested on the JVM.
 */
object SettingsProfiles {
    const val FORMAT = "xendroid-settings-profiles"
    const val VERSION = 1
    const val MAX_FILE_BYTES = 256 * 1024
    const val MAX_PROFILES_PER_FILE = 200
    const val MAX_SETTINGS = 32
    private const val MAX_LIST = 20

    /**
     * Rendering, pacing, audio-buffering and scheduling choices that differ per game. Never
     * here: which driver library loads (vulkan_lib_path) or GPU clocks (clock policy is an
     * opt-in of its own), logging, tracing and debug layers, network, language and region,
     * storage, licences, patches and plugins, the input and output backends.
     */
    val ALLOWED_KEYS: Set<String> = setOf(
        "GPU|framerate_limit", "GPU|guest_display_refresh_cap",
        "GPU|draw_resolution_scale_x", "GPU|draw_resolution_scale_y", "GPU|draw_resolution_scaled_texture_offsets",
        "GPU|readback_resolve", "GPU|occlusion_query", "GPU|vulkan_mid_frame_submission_draws",
        "GPU|render_target_path", "GPU|half_pixel_offset", "GPU|native_2x_msaa",
        "GPU|resolve_resolution_scale_fill_half_pixel_offset", "GPU|snorm16_render_target_full_range",
        "GPU|texture_gradient_exp_bias", "GPU|texture_integer_num_format",
        "GPU|accurate_resolve_number_formats", "GPU|resolve_copy_dest_number_packing",
        "GPU|force_convert_quad_lists_to_triangle_lists", "GPU|force_convert_triangle_fans_to_lists",
        "GPU|depth_float24_round", "GPU|depth_float24_convert_in_pixel_shader", "GPU|depth_transfer_not_equal_test",
        "GPU|non_seamless_cube_map", "GPU|mrt_edram_used_range_clamp_to_min", "GPU|clear_memory_page_state",
        "GPU|execute_unclipped_draw_vs_on_cpu", "GPU|execute_unclipped_draw_vs_on_cpu_with_scissor",
        "GPU|execute_unclipped_draw_vs_on_cpu_for_psi_render_backend",
        "GPU|texture_cache_memory_limit_soft", "GPU|texture_cache_memory_limit_hard",
        "Vulkan|turnip_debug", "Vulkan|vulkan_async_skip_draws", "Vulkan|vulkan_placeholder_pipelines",
        "Vulkan|vulkan_dynamic_pipeline_state", "Vulkan|vulkan_pipeline_creation_threads",
        "Vulkan|vulkan_in_pass_resolve", "Vulkan|vulkan_resolve_to_texture_promote",
        "Vulkan|vulkan_resolve_to_texture", "Vulkan|vulkan_resolve_to_texture_serve",
        "Vulkan|vulkan_disjoint_memexport_barriers", "GPU|spirv_structured_forward_jumps",
        "GPU|texture_lookup_memo",
        "Vulkan|vulkan_pipeline_cache_save_snapshot", "Vulkan|vulkan_coalesce_float_constant_uploads",
        "Vulkan|vulkan_cache_texture_descriptors", "Vulkan|vulkan_texture_descriptor_reuse_edge",
        "Display|postprocess_scaling_and_sharpening", "Display|postprocess_antialiasing",
        "Display|present_letterbox", "Display|postprocess_dither",
        "Console|widescreen", "Console|internal_display_resolution",
        "Kernel|guest_scheduler", "Kernel|guest_scheduler_quantum_us",
        "Kernel|ignore_thread_affinities", "Kernel|ignore_thread_priorities",
        "CPU|collapse_memory_delay_spins",
        "APU|xma_decoder", "APU|use_dedicated_xma_thread", "APU|apu_pump_topup",
        "APU|apu_max_queued_frames", "APU|apu_aaudio_buffer_bursts",
    )

    /** Values a profile never picks even for an allowed key: they silence the game. */
    private val DENIED_VALUES: Map<String, Set<String>> = mapOf("APU|xma_decoder" to setOf("fake"))

    private val ID = Regex("[a-z0-9][a-z0-9._-]{0,63}")
    private val TITLE = Regex("[0-9A-F]{8}")
    private val DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val json = Json { ignoreUnknownKeys = false; isLenient = false }

    /** A line of a preview. [now] and [target] are labels ("On", "30 FPS"). */
    enum class Kind {
        /** The game will use [Line.target] instead of [Line.now]. */
        CHANGE,
        /** Already [Line.target] through the global settings; now kept for this game. */
        PIN,
        /** This game already has [Line.target]; nothing to write. */
        SAME,
        /** The player set this for the game themselves: kept as [Line.now]. */
        YOURS,
    }

    /** [now]/[target]: English labels (reports, tests); [nowRaw]/[targetRaw]: the values, for the
     *  screen to label in its language (U02); [targetGlobal]: the target is the global value. */
    data class Line(val key: String, val title: String, val kind: Kind, val now: String, val target: String,
                    val nowRaw: String = "", val targetRaw: String = "", val targetGlobal: Boolean = false)

    /**
     * [expected]: this game's own value of every key the plan looked at (null = follows the
     * global settings); applying refuses when the file no longer says so. [writes]: key ->
     * value to write, null = remove (follow the global settings again).
     */
    data class Plan(val lines: List<Line>, val expected: Map<String, String?>, val writes: Map<String, String?>)

    data class Parsed(val profiles: List<LoadedProfile>, val skipped: List<String>)

    /** Reads one file of profiles; whatever breaks a rule is skipped with the reason. */
    fun parse(text: String, source: ProfileSource, origin: String): Parsed {
        if (text.length > MAX_FILE_BYTES) return Parsed(emptyList(), listOf("$origin: larger than 256 KB"))
        val file = runCatching { json.decodeFromString(SettingsProfileFile.serializer(), text) }.getOrElse {
            return Parsed(emptyList(), listOf("$origin: not a settings profile file"))
        }
        if (file.format != FORMAT || file.version != VERSION) {
            return Parsed(emptyList(), listOf("$origin: format \"${file.format.take(40)}\" version ${file.version} is not supported"))
        }
        if (file.profiles.size > MAX_PROFILES_PER_FILE) {
            return Parsed(emptyList(), listOf("$origin: more than $MAX_PROFILES_PER_FILE profiles"))
        }
        val loaded = ArrayList<LoadedProfile>()
        val skipped = ArrayList<String>()
        val ids = HashSet<String>()
        for (profile in file.profiles) {
            val problem = problems(profile, source).firstOrNull()
            when {
                problem != null -> skipped += "$origin: ${profile.id.take(64)}: $problem"
                !ids.add(profile.id) -> skipped += "$origin: ${profile.id}: the same id twice"
                else -> loaded += LoadedProfile(profile.copy(titleIds = profile.titleIds.map { it.uppercase() }), source, origin)
            }
        }
        return Parsed(loaded, skipped)
    }

    /** Merges sources in the order given (the app's own first): a later profile reusing an id is skipped. */
    fun merge(parts: List<Parsed>): Parsed {
        val ids = HashSet<String>()
        val loaded = ArrayList<LoadedProfile>()
        val skipped = ArrayList<String>()
        for (part in parts) {
            skipped += part.skipped
            for (p in part.profiles) {
                if (ids.add(p.profile.id)) loaded += p else skipped += "${p.origin}: ${p.profile.id}: id already used by another profile"
            }
        }
        return Parsed(loaded, skipped)
    }

    /** Every rule a profile breaks; empty = it may be offered. */
    fun problems(p: SettingsProfile, source: ProfileSource): List<String> = buildList {
        if (!ID.matches(p.id)) add("id must be 1-64 characters of a-z, 0-9, '.', '_' or '-'")
        if (p.name.isBlank() || p.name.length > 60) add("name must be 1-60 characters")
        if (p.reason.isBlank() || p.reason.length > 500) add("reason must be 1-500 characters")
        if (p.titleIds.isEmpty() || p.titleIds.size > 50 ||
            p.titleIds.any { !TITLE.matches(it.uppercase()) || it == "00000000" }) add("titleIds must list 1-50 Title IDs")
        if (p.settings.isEmpty() || p.settings.size > MAX_SETTINGS) add("settings must have 1-$MAX_SETTINGS entries")
        p.settings.forEach { (key, value) -> valueProblem(key, value)?.let(::add) }
        val r = p.requires
        if (r.driverLoader != null && r.driverLoader != "system" && r.driverLoader != "custom") {
            add("requires.driverLoader must be \"system\" or \"custom\"")
        }
        if (listOf(r.gpuContains, r.driverContains, r.manufacturers, r.models)
                .any { list -> list.size > MAX_LIST || list.any { it.isBlank() || it.length > 64 } }) {
            add("requirement lists take at most $MAX_LIST entries of 1-64 characters")
        }
        if ((r.minAndroidSdk ?: 21) !in 21..99 || (r.minAppVersionCode ?: 1) < 1) add("minimum versions are out of range")
        if (p.evidence.size > MAX_LIST || p.evidence.any { e ->
                listOf(e.result, e.build, e.gpu).any { it.isBlank() || it.length > 200 } || e.driver.length > 200 || !DATE.matches(e.date)
            }) add("each test needs a result, build, GPU and date (YYYY-MM-DD), up to 200 characters")
        // What the app ships is a recommendation only with the test that verified it.
        if (source == ProfileSource.BUNDLED && p.evidence.isEmpty()) add("a profile shipped with the app needs the test it was verified with")
    }

    /** Why [key] = [value] may not be in a profile; null = it may (15b shares by the same rule). */
    fun valueProblem(key: String, value: String): String? {
        val s = SettingsSchema.byKey[key]
        if (key !in ALLOWED_KEYS || s == null || s is Setting.Action || s is Setting.Text) return "$key is not a setting profiles may change"
        if (value in DENIED_VALUES[key].orEmpty()) return "$key = $value is not allowed"
        return when (s) {
            is Setting.Bool -> if (value == "true" || value == "false") null else "$key must be true or false"
            is Setting.IntRange -> if (value.toIntOrNull()?.let { it in s.min..s.max } == true) null
                else "$key must be a whole number from ${s.min} to ${s.max}"
            is Setting.ListChoice -> if (s.options.any { it.value == value }) null
                else "$key must be one of ${s.options.joinToString { it.value }}"
            is Setting.Action, is Setting.Text -> "$key is not a setting profiles may change"
        }
    }

    /** Why [r] rules this phone out; empty = it may be offered here. */
    fun unmet(r: ProfileRequirements, f: DeviceFacts): List<String> = buildList {
        if (r.gpuContains.isNotEmpty()) {
            val gpu = f.gpu
            when {
                gpu == null -> add("for ${r.gpuContains.joinToString(" or ")} GPUs; this phone's GPU is not known yet")
                r.gpuContains.none { gpu.contains(it, ignoreCase = true) } -> add("for ${r.gpuContains.joinToString(" or ")} GPUs (this one: $gpu)")
            }
        }
        r.driverLoader?.let { want ->
            val loader = f.driverLoader
            when {
                loader.isNullOrEmpty() -> add("for the $want driver; start the game once so its driver is known")
                !loader.equals(want, ignoreCase = true) -> add("for the $want driver (the game last ran on the $loader one)")
            }
        }
        if (r.driverContains.isNotEmpty()) {
            val driver = f.driverLabel
            when {
                driver == null -> add("for ${r.driverContains.joinToString(" or ")} drivers; start the game once so its driver is known")
                r.driverContains.none { driver.contains(it, ignoreCase = true) } -> add("for ${r.driverContains.joinToString(" or ")} drivers (the game last ran on $driver)")
            }
        }
        if (r.manufacturers.isNotEmpty() && r.manufacturers.none { it.equals(f.manufacturer, ignoreCase = true) }) {
            add("for ${r.manufacturers.joinToString(" or ")} phones")
        }
        if (r.models.isNotEmpty() && r.models.none { it.equals(f.model, ignoreCase = true) }) add("for the ${r.models.joinToString(" or ")} model")
        r.minAndroidSdk?.let { if (f.androidSdk < it) add("needs Android API level $it or newer") }
        r.minAppVersionCode?.let { if (f.appVersionCode < it) add("needs a newer Xendroid+ build") }
    }

    /** True when one of the tests behind [p] ran on this very GPU. */
    fun testedOnThisGpu(p: SettingsProfile, f: DeviceFacts): Boolean =
        f.gpu != null && p.evidence.any { it.gpu.trim().equals(f.gpu.trim(), ignoreCase = true) }

    /**
     * What applying [p] does to a game whose own settings are [overrides] (key -> raw value,
     * only the keys set for this game) and whose global values are [inherited].
     */
    fun plan(p: SettingsProfile, overrides: Map<String, String>, inherited: Map<String, String?>): Plan {
        val lines = ArrayList<Line>()
        val expected = LinkedHashMap<String, String?>()
        val writes = LinkedHashMap<String, String?>()
        for ((key, target) in p.settings) {
            val s = SettingsSchema.byKey[key] ?: continue
            val mine = overrides[key]
            expected[key] = mine
            val global = inherited[key] ?: defaultRaw(s)
            val kind = when {
                mine == null && same(s, global, target) -> Kind.PIN
                mine == null -> Kind.CHANGE
                same(s, mine, target) -> Kind.SAME
                else -> Kind.YOURS
            }
            if (kind == Kind.CHANGE || kind == Kind.PIN) writes[key] = canonical(s, target)
            lines += Line(key, s.title, kind, label(s, mine ?: global), label(s, target), mine ?: global, target)
        }
        return Plan(lines, expected, writes)
    }

    /** Undoing [applied]: each key still holding what the profile wrote goes back to what it
     *  was; a key the player changed since is theirs and stays. */
    fun restorePlan(applied: AppliedProfile, overrides: Map<String, String>, inherited: Map<String, String?>): Plan {
        val lines = ArrayList<Line>()
        val expected = LinkedHashMap<String, String?>()
        val writes = LinkedHashMap<String, String?>()
        for ((key, wrote) in applied.written) {
            val s = SettingsSchema.byKey[key] ?: continue
            val mine = overrides[key]
            val global = inherited[key] ?: defaultRaw(s)
            val before = applied.previous[key]
            val beforeLabel = before?.let { label(s, it) } ?: "${label(s, global)} (global)"
            expected[key] = mine
            if (mine != null && same(s, mine, wrote)) {
                writes[key] = before
                lines += Line(key, s.title, Kind.CHANGE, label(s, mine), beforeLabel, mine, before ?: global, before == null)
            } else {
                lines += Line(key, s.title, Kind.YOURS, label(s, mine ?: global), beforeLabel, mine ?: global, before ?: global, before == null)
            }
        }
        return Plan(lines, expected, writes)
    }

    /** "On", "30 FPS", "1280x720": what the settings screen shows for [raw]. */
    fun label(s: Setting, raw: String): String = when (s) {
        is Setting.Bool -> if (ConfigValueShape.parseBool(raw, s.default)) "On" else "Off"
        is Setting.IntRange -> ConfigValueShape.parseInt(raw, s.default).toString()
        is Setting.ListChoice -> s.options.firstOrNull { it.value == raw }?.label ?: raw.ifEmpty { "default" }
        is Setting.Action -> raw
        is Setting.Text -> raw
    }

    private fun same(s: Setting, a: String, b: String): Boolean = canonical(s, a) == canonical(s, b)

    private fun canonical(s: Setting, raw: String): String = when (s) {
        is Setting.Bool -> ConfigValueShape.bool(ConfigValueShape.parseBool(raw, s.default))
        is Setting.IntRange -> ConfigValueShape.int(ConfigValueShape.parseInt(raw, s.default))
        else -> raw
    }

    private fun defaultRaw(s: Setting): String = when (s) {
        is Setting.Bool -> ConfigValueShape.bool(s.default)
        is Setting.IntRange -> ConfigValueShape.int(s.default)
        is Setting.ListChoice -> s.default
        is Setting.Action -> s.default
        is Setting.Text -> s.default
    }
}
