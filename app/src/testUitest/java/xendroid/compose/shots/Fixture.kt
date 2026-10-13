package xendroid.compose.shots

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.robolectric.shadows.ShadowLooper
import xendroid.compose.AppContainer
import xendroid.compose.compatibility.CompatStatus
import xendroid.compose.compatibility.CompatibilityStore
import xendroid.compose.data.CoverStore
import xendroid.compose.data.Game
import xendroid.compose.data.GameCollection
import xendroid.compose.data.IconCache
import xendroid.compose.driver.DriverIdentity
import xendroid.compose.sessions.RunEvent
import xendroid.compose.sessions.RunEventLog
import xendroid.compose.sessions.RunPerformance
import xendroid.compose.sessions.RunState
import xendroid.compose.sessions.SessionRunStore
import xendroid.compose.sessions.TitleActivity
import xendroid.compose.ui.library.FirstRunStore
import xendroid.compose.ui.library.GameLibraryViewModel
import xendroid.compose.ui.library.LibraryUiState

/**
 * The example library of the "depois" prints, through the app's own view models: the games of
 * [SampleLibrary] with their art, play history, favourites, collections, the player's ratings,
 * two profiles, and recorded sessions with performance numbers and a timeline.
 */
object Fixture {
    val context: Context get() = ApplicationProvider.getApplicationContext()

    const val P1 = "E03000000A1B2C3D"
    const val P2 = "E03000000F9E8D7C"

    /** Drains the main looper while background work (IO) lands. */
    fun settle(rounds: Int = 20) = repeat(rounds) { ShadowLooper.idleMainLooper(); Thread.sleep(15) }

    /** Runs [block] off the main thread (DataStore must never be awaited on it) and settles until it is done. */
    fun io(block: suspend () -> Unit) {
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { block() }
        settleUntil(400) { job.isCompleted }
    }

    /** Settles until [done] (background work landed), at most [rounds] times. */
    fun settleUntil(rounds: Int = 300, done: () -> Boolean) {
        var n = 0
        while (!done() && n++ < rounds) settle(1)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> flow(vm: Any, field: String): MutableStateFlow<T> =
        vm.javaClass.getDeclaredField(field).apply { isAccessible = true }.get(vm) as MutableStateFlow<T>

    private fun games(): List<Pair<SampleGame, Game>> = SampleLibrary.games.map { s ->
        val name = IconCache(context.cacheDir).write(s.path, SampleArt.iconPng(s))
        if (s.customCover) CoverStore(File(context.filesDir, "covers")).setCustom(s.titleId, SampleArt.coverPng(s))
        s to s.game(name)
    }

    val driver = DriverIdentity(vendorId = "5143", deviceId = "43050a01", driverVersion = "25.3.0", api = "1.3.296", driverId = 18,
        driverName = "Turnip Mesa", driverInfo = "25.3.0", gpu = "Adreno (TM) 740", uuid = "c0ffee", loader = "custom",
        library = "libvulkan_freedreno.so")

    /** Seconds by FPS around [target] (a 30 FPS game that drops a little in heavy scenes). */
    private fun fps(target: Int, seconds: Int): List<Int> = List(target + 31) { fps ->
        when (fps) {
            target -> seconds * 70 / 100
            target - 1 -> seconds * 12 / 100
            target - 2 -> seconds * 7 / 100
            in target - 6..target - 3 -> seconds * 2 / 100
            in target - 12..target - 7 -> seconds / 200
            else -> 0
        }
    }

    /** Frames by frame time (ms), centred on the frame budget of [target] FPS. */
    private fun frameTimes(target: Int, frames: Long): List<Long> {
        val budget = 1000 / target
        return List(RunPerformance.FRAME_TIME_OPEN_BUCKET + 1) { ms ->
            when (ms) {
                budget -> frames * 62 / 100
                budget - 1, budget + 1 -> frames * 14 / 100
                budget + 2 -> frames * 4 / 100
                in budget + 3..budget + 9 -> frames / 120
                in budget + 10..budget + 25 -> frames / 1000
                else -> 0
            }
        }
    }

    /** One finished run of [titleId], [minutesAgo] ago, lasting [minutes], with its numbers. */
    private fun run(store: () -> SessionRunStore, clock: LongArray, titleId: String, path: String, minutesAgo: Long, minutes: Long,
                    target: Int, events: List<RunEvent>) {
        val now = System.currentTimeMillis()
        clock[0] = now - (minutesAgo + minutes) * 60_000
        val begun = store().begin("library", path, "v412", 4242)
        clock[0] += 9_000
        store().running(begun.runId, titleId, driver, P1)
        val seconds = (minutes * 60).toInt()
        val perf = RunPerformance(fpsHistogram = fps(target, seconds), idleSeconds = 30, presentSubmissions = seconds * target.toLong(),
            batteryStartC = 32.5f, batteryMaxC = 41.2f, batteryEndC = 39.0f, frameTimeHistogramMs = frameTimes(target, seconds * target.toLong()),
            pipelineCreations = 1830, pipelineCreationMs = 6400, firstFrameSeconds = 7, audioBackend = "AAudio",
            audioBlocks = seconds * 94L, audioConcealedBlocks = 0, audioDeviceXruns = 0, fpsLimits = listOf(target), displayHz = listOf(120),
            guestRefreshCap = true)
        clock[0] = now - minutesAgo * 60_000
        store().finish(begun.runId, RunState.ENDED, "exited from the menu", perf)
        store().saveEvents(begun.runId, RunEventLog(events = events))
    }

    private fun sessions() {
        val clock = longArrayOf(System.currentTimeMillis())
        val root = File(xendroid.compose.Application.get_internal_data_dir(), "session-runs")
        val store = { SessionRunStore(root, clock = { clock[0] }) }
        val halo = listOf(
            RunEvent(0, "boot", "run started"), RunEvent(7_200, "first frame"), RunEvent(192_000, "controller", "connected: P1"),
            RunEvent(725_000, "stall", "pipelines: 9 in 1.8 s"), RunEvent(1_840_000, "thermal", "moderate"),
            RunEvent(2_402_000, "pause", "game menu"), RunEvent(2_431_000, "resume"), RunEvent(3_159_000, "exit", "from the menu"),
        )
        run(store, clock, "4D5307E6", SampleLibrary.games[0].path, minutesAgo = 140, minutes = 53, target = 30, events = halo)
        run(store, clock, "4D5309C9", SampleLibrary.games[1].path, minutesAgo = 26 * 60, minutes = 41, target = 30,
            events = listOf(RunEvent(0, "boot", "run started"), RunEvent(9_000, "first frame"), RunEvent(2_460_000, "exit", "from the menu")))
    }

    private fun ratings() {
        val store = CompatibilityStore(File(xendroid.compose.Application.get_internal_data_dir(), "compatibility"))
        store.report("4D5307E6", CompatStatus.PLAYABLE, "Campanha inteira a 30 FPS com escala 1x; cutscenes sem travar.", "v412 · 7bb3409",
            "Adreno (TM) 740", driver, "7A1C3E52", 0)
        store.report("4D5309C9", CompatStatus.IN_GAME, "Corre bem, mas as sombras piscam em algumas pistas.", "v412 · 7bb3409", "Adreno (TM) 740", driver)
        store.report("5454082B", CompatStatus.IN_GAME, "Cidades pesadas caem para 22 FPS.", "v410", "Adreno (TM) 740", driver)
        store.report("544307D5", CompatStatus.PLAYABLE, "", "v412", "Adreno (TM) 740", driver)
        store.report("584108FF", CompatStatus.PLAYABLE, "", "v412", "Adreno (TM) 740", driver)
        store.report("545407F2", CompatStatus.INTRO, "Para no menu principal.", "v409", "Adreno (TM) 740", driver)
        store.report("4D5307DF", CompatStatus.BOOTS, "Tela preta depois do logo.", "v405", "Adreno (TM) 740", null)
        store.report("4E4D0859", CompatStatus.NOTHING, "", "v411", "Adreno (TM) 740", null)
    }

    /** The library view model over the sample games, as after a scan. */
    fun library(container: AppContainer): GameLibraryViewModel {
        FirstRunStore.markDone(context)
        FakeCore.profiles = listOf(FakeCore.profile(P1, "ChefeMaster117"), FakeCore.profile(P2, "Arbiter"))
        sessions()
        ratings()
        val pairs = games()
        val vm = container.libraryViewModelFactory().create(GameLibraryViewModel::class.java)
        // This fixture injects a synthetic library below. Finish cancelling the
        // constructor's real scan first, so it cannot overwrite that state later
        // or leave an indeterminate progress indicator in Compose assertions.
        val initialScan = vm.javaClass.getDeclaredField("refreshJob").apply { isAccessible = true }
            .get(vm) as? kotlinx.coroutines.Job
        vm.stopScan()
        settleUntil { initialScan?.isCompleted != false }
        check(initialScan?.isCompleted != false && !vm.isRefreshing.value && vm.scanProgress.value == null) {
            "Initial library scan did not stop before fixture state was installed"
        }
        val now = System.currentTimeMillis()
        flow<Map<String, TitleActivity>>(vm, "_activity").value = pairs.filter { it.first.lastRank > 0 }.associate { (s, _) ->
            s.titleId to TitleActivity(s.titleId, now - when (s.lastRank) { 1 -> 140L * 60_000; 2 -> 26L * 3_600_000; else -> s.lastRank * 36L * 3_600_000 },
                s.playedMinutes * 60_000L, s.runs)
        }
        flow<Map<String, CompatStatus>>(vm, "_compat").value = mapOf(
            "4D5307E6" to CompatStatus.PLAYABLE, "4D5309C9" to CompatStatus.IN_GAME, "5454082B" to CompatStatus.IN_GAME,
            "544307D5" to CompatStatus.PLAYABLE, "584108FF" to CompatStatus.PLAYABLE, "545407F2" to CompatStatus.INTRO,
            "4D5307DF" to CompatStatus.BOOTS, "4E4D0859" to CompatStatus.NOTHING)
        flow<String?>(vm, "_activeProfile").value = "ChefeMaster117"
        // Never block the main thread on DataStore: edit off it and keep the looper turning.
        val edit = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            // Set, never toggled: the preferences may outlive one test.
            xendroid.compose.data.PreferencesStore(context).addFavorites(pairs.filter { it.first.favorite }.map { it.second.identityKey }.toSet())
            vm.editCollections { _ ->
                SampleLibrary.collections.map { name -> GameCollection(name, pairs.filter { name in it.first.collections }.map { it.second.identityKey }) }
            }
        }
        var rounds = 0
        while (!edit.isCompleted && rounds++ < 200) settle(1)
        flow<LibraryUiState>(vm, "_state").value = LibraryUiState.Loaded(pairs.map { it.second })
        settle()
        return vm
    }

    fun game(vm: GameLibraryViewModel, titleId: String): Game =
        (vm.state.value as LibraryUiState.Loaded).games.first { it.titleId == titleId }
}
