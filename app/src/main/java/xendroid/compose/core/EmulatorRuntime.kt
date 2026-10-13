package xendroid.compose.core

import android.util.Log
import xendroid.compose.Application
import xendroid.compose.Emulator
import xendroid.compose.settings.ConfigMigrations
import xendroid.compose.settings.ConfigStore

/**
 * Thin Kotlin facade over the once-per-process native load + GPU probe.
 * The probe itself already ran in Application.onCreate (cached in
 * Application.gpu_device_name_vk); on Adreno 830 libe.so is loaded eagerly there.
 * ensureLoaded() makes the "already loaded" case a no-op and only triggers the
 * lazy load on delay-load devices (Adreno 5xx/6xx) that skipped the eager path.
 */
object EmulatorRuntime {
    private const val TAG = "EmulatorRuntime"

    /** Cached Vulkan device name, or null when Vulkan is unavailable. */
    val gpuDeviceName: String? get() = Application.gpu_device_name_vk

    /** True iff the device exposes a Vulkan physical device. Hard gate for boot. */
    val supportsVulkan: Boolean get() = gpuDeviceName != null

    /** Idempotent. Loads libe.so exactly once; swallows the "already loaded"
     *  RuntimeException (the eager Application path may have loaded it first). */
    @Synchronized
    fun ensureLoaded() {
        if (Emulator.get == null) {
            try {
                Emulator.load_library()
            } catch (e: RuntimeException) {
                // "Emulator already loaded" lost a race; get is now non-null -> fine.
                if (Emulator.get == null) throw e
                Log.w(TAG, "load_library raced; using existing singleton", e)
            }
        }
        applyCoreDefaultUpdatesOnce()
    }

    private val coreDefaultUpdates = SuccessfulInitialization()

    /** Once per process, before the core boots or the settings read the live config: the core's
     *  own default changes written to it ([ConfigMigrations]). */
    private fun applyCoreDefaultUpdatesOnce() {
        runCatching {
            coreDefaultUpdates.run {
                ConfigStore(Application.ctx).applyCoreDefaultUpdates(
                    ConfigMigrations.parse(xendroid.emulator.Emulator.Config.native_config_updates()))
            }
        }.onFailure { Log.w(TAG, "Applying the core's default updates to the config failed", it) }
    }

    /** The native singleton once loaded; null before ensureLoaded() on delay devices. */
    val emulator: Emulator? get() = Emulator.get
}
