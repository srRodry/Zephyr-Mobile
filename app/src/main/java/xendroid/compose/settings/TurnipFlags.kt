package xendroid.compose.settings

/**
 * 15i (Eden `87d4c673`, Bannerlator `1933903c`): the TU_DEBUG flags the Turnip driver reads at
 * start, chosen one by one with what each does, instead of four fixed combinations. The value is
 * still the comma-separated list `Vulkan|turnip_debug` holds; flags this build does not list stay
 * as they are. Turnip ignores flags it does not know, and proprietary drivers ignore TU_DEBUG
 * altogether. Pure, tested on the JVM.
 */
object TurnipFlags {
    /** [help] in English, like the engine's other settings (shown in Developer mode). */
    data class Flag(val name: String, val help: String)

    val KNOWN: List<Flag> = listOf(
        Flag("sysmem", "Draw straight to memory instead of in on-chip tiles (GMEM). Slower, but avoids a class of " +
            "tiled-rendering artifacts and GPU hangs. Xendroid+'s default."),
        Flag("gmem", "Always draw in on-chip tiles. Faster where it works; the opposite of sysmem."),
        Flag("nolrz", "Turn off LRZ, the early depth test. Can fix flickering or missing geometry; costs speed."),
        Flag("nolrzfc", "Turn off only LRZ's fast clear. A narrower version of nolrz."),
        Flag("noubwc", "Turn off UBWC image compression. Can fix corrupted textures or render targets; more memory traffic."),
        Flag("nomultipos", "Turn off the multi-position output optimization. Can fix stretched or flickering polygons."),
        Flag("noconcurrentresolves", "Do not resolve tiles while the next ones draw. Can fix artifacts on Adreno 7xx; slower."),
        Flag("noconcurrentunresolves", "Do not load tiles while others draw. Can fix artifacts on Adreno 7xx; slower."),
        Flag("nobin", "Disable hardware binning. Test on affected Turnip drivers and compare frame times and rendering."),
        Flag("forcebin", "Always use the binning pass in tiled rendering. For diagnosis."),
        Flag("flushall", "Flush the GPU after every command. Very slow; narrows down which draw crashes the GPU."),
        Flag("syncdraw", "Wait for every draw to finish. Very slow; for diagnosing GPU hangs."),
    )

    /** Choosing one of these takes the other off (the two rendering paths). */
    private val conflictingFlag = mapOf("sysmem" to "gmem", "gmem" to "sysmem",
        "nobin" to "forcebin", "forcebin" to "nobin")

    fun parse(raw: String): List<String> =
        raw.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() && it.matches(Regex("[a-z0-9_]{1,40}")) }.distinct()

    fun join(flags: List<String>): String = flags.distinct().joinToString(",")

    /** [raw] with [flag] turned on or off, keeping everything else (and its order). */
    fun toggle(raw: String, flag: String): String {
        val now = parse(raw)
        return if (flag in now) join(now - flag)
        else join(now.filterNot { it == conflictingFlag[flag] } + flag)
    }

    /** Flags in [raw] this build does not describe: kept, and shown as they are. */
    fun unknown(raw: String): List<String> = parse(raw).filter { name -> KNOWN.none { it.name == name } }
}
