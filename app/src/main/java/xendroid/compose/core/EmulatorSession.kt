package xendroid.compose.core

import android.content.Context
import android.view.Surface
import xendroid.compose.Emulator
import xendroid.compose.driver.DriverIdentity
import xendroid.emulator.Emulator as BaseEmulator

/**
 * Thin, UI-thread-affine facade over the native xenia singleton. Owns the boot-once invariant;
 * one instance per EmulatorHostActivity (one process / one core).
 *
 * Contract (caller MUST keep order; native enforces nothing):
 *   prepare() -> {setupContext, setupGamePathReal, setupLaunchArgs,
 *                 setupUriInfoListFile}                           [PRE-surface]
 *   attachSurface(s) -> bootOnce()                                [first surface only]
 *   changeSurface(w,h)                                            [surfaceChanged]
 *   attachSurface(s) (+resumeIfPaused) on later surfaceCreated    [NEVER boot again]
 *   detachSurface()                                               [surfaceDestroyed]
 *   flushGpuCaches()                                              [onPause, best-effort]
 *   keyEvent(...)                                                 [hardware input]
 */
class EmulatorSession {
    // Held until this single-shot process exits. Closing early after an asynchronous
    // quit could let a restore race the guest's final storage writes.
    @Volatile private var contentLease: xendroid.compose.archive.ContentLease? = null

    var booted: Boolean = false
        private set

    sealed interface StorageResult {
        data object Ready : StorageResult
        /** Why the game data cannot be used now: the host says it in the shown language from
         *  [reason] and [detail] (the recovery summary or the error); [english] goes to the
         *  run's record. */
        data class Unavailable(val reason: Reason, val detail: String = "", val english: String) : StorageResult {
            enum class Reason { BUSY, RECOVERY_PENDING, FAILED }
        }
    }

    /**
     * Takes the storage lease for the whole session and rolls back interrupted save
     * restores under it, before the surface exists. Waits a bounded time for a short
     * job (an automatic backup) instead of failing the launch. Call off the main thread.
     */
    suspend fun prepareStorage(timeoutMs: Long = 15_000, onWaiting: () -> Unit = {}): StorageResult {
        if (contentLease != null) return StorageResult.Ready
        val lease = try {
            xendroid.compose.archive.acquireWithRetry(timeoutMs, onBusy = onWaiting) { StorageAccess.acquire() }
        } catch (e: xendroid.compose.archive.ContentBusyException) {
            return StorageResult.Unavailable(StorageResult.Unavailable.Reason.BUSY, english =
                "Another save, profile or content operation is still using the game data. " +
                    "Wait for it to finish, then start the game again.")
        }
        val report = try {
            StorageAccess.saveStore().recoverTransactions(lease)
        } catch (e: Exception) {
            lease.close()
            throw e
        }
        if (!report.clean) {
            lease.close()
            return StorageResult.Unavailable(StorageResult.Unavailable.Reason.RECOVERY_PENDING, report.summary,
                xendroid.compose.saves.SaveRecoveryException(report).message!!)
        }
        contentLease = lease
        return StorageResult.Ready
    }

    /** Throws clearly if used before ensureLoaded(). */
    private val core: Emulator
        get() = EmulatorRuntime.emulator
            ?: error("EmulatorSession used before EmulatorRuntime.ensureLoaded()")

    // ---- PRE-surface setup (all on the main thread, in this exact order) ----

    fun setupContext(ctx: Context) = core.setup_context(ctx)

    /** An ABSOLUTE host path. The String overload routes native -> BOOT_TYPE_WITH_PATH,
     *  which mounts the right real-path device by extension. */
    fun setupGamePathReal(absPath: String) = core.setup_game_path(absPath)

    fun setupLaunchArgs(args: Array<String>) = core.setup_launch_args(args)

    fun setupUriInfoListFile(path: String) = core.setup_uri_info_list_file(path)

    /** Stash/attach the render surface. PRE-boot just stashes; POST-boot async-recreates the
     *  swapchain. Pass null to detach (synchronous GPU drain). */
    fun attachSurface(surface: Surface?) = core.setup_surface(surface)

    fun detachSurface() = core.setup_surface(null)

    fun changeSurface(width: Int, height: Int) = core.change_surface(width, height)

    /** Boot the core EXACTLY ONCE (no-op on later calls). MUST be preceded by a non-null
     *  attachSurface(). Translates the checked BootException. */
    @Throws(RuntimeException::class)
    fun bootOnce() {
        if (booted) return
        // prepareStorage() must have succeeded: the guest never runs without the lease
        // or over a save tree with an unrecovered restore.
        checkNotNull(contentLease) { "Storage was not prepared before boot" }
        booted = true
        try {
            core.boot()
        } catch (e: BaseEmulator.BootException) {
            throw RuntimeException("xenia boot failed", e)
        }
    }

    fun resumeIfPaused() {
        if (core.is_paused()) core.resume()
    }

    fun pause() = core.pause()
    fun resume() = core.resume()
    fun isPaused(): Boolean = booted && core.is_paused()

    /** Best-effort GPU cache flush (onPause). Swallows everything; never throws. */
    fun flushGpuCaches() {
        if (!booted) return
        runCatching { EmulatorRuntime.emulator?.flush_gpu_caches() }
    }

    /** Single input sink for ALL controls. value = KEY_VALUE_UNUSED (-1) for digital, or a
     *  signed short magnitude for thumbsticks. Dropped until boot() has run: native key_event
     *  unconditionally derefs g_windowed_app_ref, null until the detached boot thread sets it
     *  (a controller press during the boot splash would crash). */
    fun keyEvent(keyCode: Int, pressed: Boolean, value: Int) {
        if (!booted) return
        if (keyCode in p1Held.indices) p1Held[keyCode] = pressed
        core.key_event(keyCode, pressed, value)
    }

    /** Input of a controller holding player slot P2..P4 ([slot] 1..3); P1 is [keyEvent]. */
    fun keyEventSlot(slot: Int, keyCode: Int, pressed: Boolean, value: Int) {
        if (!booted) return
        if (slot == 0) keyEvent(keyCode, pressed, value) else core.key_event_slot(slot, keyCode, pressed, value)
    }

    /** P1's guest buttons as last sent, from any source (controller, touch, phone as P1). */
    private val p1Held = BooleanArray(32)

    /** Whether P1 holds the guest button [keyCode] (e.g. LT for the gyroscope's hold-to-aim). */
    fun p1Holding(keyCode: Int): Boolean = keyCode in p1Held.indices && p1Held[keyCode]

    /** A controller took or left slot [slot] (1..3); the guest sees the pad connect or disconnect. */
    fun setSlotConnected(slot: Int, connected: Boolean, label: String) {
        if (booted) core.set_slot_connected(slot, connected, label)
    }

    /** Guest rumble per slot P1..P4: left motor shl 16 or right motor; null before boot. */
    fun rumbleState(): LongArray? = if (booted) core.rumble_state() else null

    // ---- Debug stats (UI-thread polled; reads native lock-free atomics) ----

    /** Last presented guest-frame interval in ms (0 before first present / after pause). */
    fun lastFrameTimeMs(): Double = if (booted) core.last_frame_time_ms() else 0.0

    /** Instant fps, NOT the average. */
    fun instantFps(): Double = if (booted) core.instant_fps() else 0.0

    fun averageFps(): Double = if (booted) core.average_fps() else 0.0
    fun hostPresentSubmissionCount(): Long =
        if (booted) core.host_present_submission_count() else 0L
    fun activeTitleId(): String? = if (booted) core.active_title_id() else null
    fun presentationState(): PresentationState = if (booted) PresentationState.decode(core.presentation_state()) else PresentationState()
    fun setPresentationMode(mode: Int) { if (booted) core.set_presentation_mode(mode) }
    fun setScalingEffect(effect: Int) { if (booted) core.set_scaling_effect(effect) }
    /** The in-game menu's GPU options ([GpuLiveOption]), from the core's next frame. */
    fun setLiveOption(option: GpuLiveOption, value: Int) { if (booted) core.set_live_option(option.ordinal, value) }
    fun setColorFilter(mode: Int) { if (booted) core.set_color_filter(mode) }
    /** The in-game menu's Image options (negative: the Settings value), from the next frame. */
    fun setImageTuning(t: ImageTuning) {
        if (booted) core.set_image_tuning(t.antialiasing, t.casSharpness, t.fsrSharpnessReduction, t.dither)
    }
    fun activeGpuLabel(): String = if (booted) core.active_gpu_label().orEmpty() else ""
    /** Identity of the Vulkan driver actually loaded (see [DriverIdentity]); null before the presenter starts. */
    fun activeDriverIdentity(): DriverIdentity? =
        if (booted) DriverIdentity.parse(core.active_driver_identity().orEmpty()) else null
    /** L10: the active title's module hashes as its patches were matched (main executable
     *  first), 16 hex digits each; empty before a title loads. */
    fun moduleHashes(): List<String> =
        if (booted) core.module_hashes()?.map { "%016X".format(it) }.orEmpty() else emptyList()
    /** C06: the settings away from the core's defaults as this run booted (game config
     *  applied; no profiles, storage or paths), one line each; null before boot. */
    fun changedSettings(): List<String>? = if (booted) core.changed_settings()?.filterNotNull() else null
    /** Cumulative guest frame-time counts per 1 ms bucket; null before boot. */
    fun guestFrameTimeHistogram(): LongArray? = if (booted) core.guest_frame_time_histogram() else null
    /** GPU time per timed frame-generation pass (0.25 ms buckets) + untimed passes last; null before boot. */
    fun frameGenerationGpuHistogram(): LongArray? = if (booted) core.frame_generation_gpu_histogram() else null
    /** PRE-boot, optional test diagnostics; older native libraries may lack the hook. */
    fun setJitCompileDiagnostics(enabled: Boolean): Boolean =
        runCatching { core.set_jit_compile_diagnostics(enabled); true }.getOrDefault(false)
    /** {pipeline creations, ns, in flight}; optional JIT suffix, see RunPerformanceAccumulator. */
    fun shaderCompileStats(): LongArray? = if (booted) core.shader_compile_stats() else null
    /** {backend, blocks played, blocks concealed, device xruns} since the process started; null before boot. */
    fun audioRunStats(): LongArray? = if (booted) core.audio_run_stats() else null
    /** PRE-boot: where the core leaves a fatal error's message before aborting. */
    fun setFatalReportPath(path: String?) = core.set_fatal_report_path(path)
    fun presenterWork(): LongArray = if (booted) core.presenter_work() else longArrayOf(0, 0, 0)
    fun setFrameGeneration(enabled: Boolean, preset: Int, hz: Float) { if (booted) core.set_frame_generation(enabled, preset, hz) }
    fun setLsfg(enabled: Boolean, cache: String, hz: Float, multiplier: Int = 2) {
        if (booted) core.set_lsfg(enabled, cache, hz, multiplier.coerceIn(2, 4))
    }
    fun audioVolume(): Int = if (booted) core.audio_volume() else 100
    fun setAudioVolume(percent: Int) { if (booted) core.set_audio_volume(percent.coerceIn(0, 100)) }

/** Effective show_debug_overlay (global + per-game override). The override lands on the
 * detached boot thread, so callers must POLL this after boot. */
fun showDebugOverlayEnabled(): Boolean =
    if (booted) core.show_debug_overlay_enabled() else false

fun showTouchOverlayEnabled(): Boolean =
    if (booted) core.show_touch_overlay_enabled() else true

fun setShowTouchOverlay(value: Boolean) {
    if (booted) core.set_show_touch_overlay(value)
}
fun setFpsLimit(value: Int) {
    if (booted) core.set_framerate_limit(value)
}

fun fpsLimit(): Int {
    return if (booted) core.get_framerate_limit() else 60
}
/** FPS limit. */
/** A pending guest text prompt, or null. Holds a dispatch thread until [keyboardSubmit]
 * answers, so every shown panel must be answered. */

    fun keyboardRequest(): Emulator.KeyboardRequest? =
        if (booted) core.keyboard_request() else null

    fun keyboardSubmit(id: Long, accepted: Boolean, text: String) {
        if (booted) core.keyboard_submit(id, accepted, text)
    }

    fun keyboardCancelAll() {
        if (booted) core.keyboard_cancel_all()
    }

    /** A pending guest message box, or null. Blocks a guest thread until [messageBoxSubmit]
     *  answers it, so every shown panel must be answered. */
    fun messageBoxRequest(): Emulator.MessageBoxRequest? =
        if (booted) core.msgbox_request() else null

    fun messageBoxSubmit(id: Long, button: Int) {
        if (booted) core.msgbox_submit(id, button)
    }

    fun messageBoxCancelAll() {
        if (booted) core.msgbox_cancel_all()
    }

    /** Blocks a guest thread until [discSubmit] answers it. */
    fun discRequest(): Emulator.DiscSwapRequest? =
        if (booted) core.disc_request() else null

    fun discSubmit(id: Long, accepted: Boolean, path: String) {
        if (booted) core.disc_submit(id, accepted, path)
    }

    fun discCancelAll() {
        if (booted) core.disc_cancel_all()
    }

    /** Before boot: the guest can ask at any point after. */
    fun discSetKnown(labels: List<String>, paths: List<String>) {
        core.disc_set_known(labels.toTypedArray(), paths.toTypedArray())
    }
}
