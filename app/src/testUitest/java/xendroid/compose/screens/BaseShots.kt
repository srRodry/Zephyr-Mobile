package xendroid.compose.screens

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xendroid.compose.AppContainer
import xendroid.compose.patches.GamePatchesViewModel
import xendroid.compose.settings.ConfigStore
import xendroid.compose.settings.GameSettingsViewModel
import xendroid.compose.settings.SettingsViewModel
import xendroid.compose.shots.Fixture
import xendroid.compose.shots.Phone
import xendroid.compose.shots.ShotApp
import xendroid.compose.shots.app
import xendroid.compose.shots.screen
import xendroid.compose.shots.shot
import xendroid.compose.ui.compress.GameCompressViewModel
import xendroid.compose.ui.design.InputMode
import xendroid.compose.ui.game.GameScreen
import xendroid.compose.ui.game.GameScreenLinks
import xendroid.compose.ui.library.GameLibraryScreen
import xendroid.compose.ui.library.LibraryUiState

/** Base batch "depois": the library (grid and panel, carousel) and the game sheet, as the app draws them. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShotApp::class)
class BaseShots {
    @get:Rule val compose = createComposeRule()

    private fun library(mode: InputMode, pick: String? = null) {
        val container = AppContainer(Fixture.context)
        val vm = Fixture.library(container)
        val compress = container.gameCompressViewModelFactory().create(GameCompressViewModel::class.java)
        var sheetsOpened = 0
        compose.app(mode) {
            GameLibraryScreen(vm, {}, {}, {}, {}, {}, { _, _, _, _ -> }, { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, {}, compress,
                onOpenGame = { _, _ -> sheetsOpened++ },
                gameSettings = { id -> remember(id) { container.gameSettingsViewModelFactory(id).create(GameSettingsViewModel::class.java) } })
        }
        if (pick != null) {
            // The new toolbar and shelf share a scrollable viewport: do not assume a cover is already composed.
            compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("library-showcase"))
            compose.onNodeWithTag("library-showcase").performScrollToNode(hasContentDescription(pick))
            val cover = compose.onNode(
                hasContentDescription(pick) and hasAnyAncestor(hasTestTag("library-showcase")),
                useUnmergedTree = true,
            )
            cover.performClick()
            compose.waitForIdle()
            cover.assertIsSelected()
            assertEquals("A shelf tap selects; it must not open the game sheet", 0, sheetsOpened)
            val wanted = (vm.state.value as LibraryUiState.Loaded).games.first { it.name == pick }.identityKey
            Fixture.settleUntil {
                vm.details.value?.let { it.identityKey == wanted && it.lastRun != null } == true
            }
        }
        Fixture.settle()
        compose.waitForIdle()
    }

    private fun game(mode: InputMode, section: String? = null, titleId: String = "4D5307E6") {
        val container = AppContainer(Fixture.context)
        ConfigStore(Fixture.context).editGameConfig(titleId) { h ->
            h.putInt("GPU", "framerate_limit", 30)
            h.putString("Display", "postprocess_scaling_and_sharpening", "fsr")
            h.putInt("GPU", "anisotropic_override", 4)
        }
        val vm = Fixture.library(container)
        val game = Fixture.game(vm, titleId)
        val settings = container.gameSettingsViewModelFactory(titleId).create(GameSettingsViewModel::class.java)
        val global = container.settingsViewModelFactory().create(SettingsViewModel::class.java)
        val patches = container.gamePatchesViewModelFactory(titleId).create(GamePatchesViewModel::class.java)
        val compress = container.gameCompressViewModelFactory().create(GameCompressViewModel::class.java)
        vm.loadDetails(game)
        Fixture.settle()
        compose.app(mode) {
            GameScreen(game, vm, compress, settings, global, patches, GameScreenLinks(onBack = {}), initialSection = section)
        }
        Fixture.settle(30)
        compose.waitForIdle()
    }

    @Config(qualifiers = Phone.LAND) @Test fun libraryLand() { library(InputMode.TOUCH, pick = "Forza Horizon"); compose.shot("base/depois-biblioteca-paisagem") }
    @Config(qualifiers = Phone.PORT) @Test fun libraryPortSelect() { library(InputMode.TOUCH, pick = "Forza Horizon"); compose.shot("base/depois-biblioteca-retrato-selecao") }
    @Config(qualifiers = Phone.PORT) @Test fun libraryPort() { library(InputMode.TOUCH); compose.shot("base/depois-biblioteca-retrato") }
    @Config(qualifiers = Phone.LAND) @Test fun libraryController() { library(InputMode.CONTROLLER); compose.shot("base/depois-biblioteca-controle") }
    @Config(qualifiers = Phone.PORT) @Test fun libraryControllerPort() { library(InputMode.CONTROLLER); compose.shot("base/depois-biblioteca-controle-retrato") }

    @Config(qualifiers = Phone.LAND) @Test fun sheetLand() { game(InputMode.TOUCH); compose.shot("base/depois-ficha-paisagem") }
    @Config(qualifiers = Phone.PORT) @Test fun sheetPort() { game(InputMode.TOUCH); compose.shot("base/depois-ficha-retrato") }
    @Config(qualifiers = Phone.LAND) @Test fun sheetSettings() { game(InputMode.TOUCH, section = "set:IMAGE"); compose.onNodeWithTag("game-sheet-tab:settings").assertIsSelected(); compose.onNodeWithTag("game-sheet-content:set:IMAGE").assertExists(); compose.shot("base/depois-ficha-ajustes") }
    @Config(qualifiers = Phone.LAND) @Test fun sheetPerformance() { game(InputMode.TOUCH, section = "perf"); compose.onNodeWithTag("game-sheet-tab:data").assertIsSelected(); compose.onNodeWithTag("game-sheet-content:perf").assertExists(); compose.shot("base/depois-ficha-desempenho") }
    @Config(qualifiers = Phone.LAND) @Test fun sheetContent() { game(InputMode.TOUCH, section = "cont"); compose.onNodeWithTag("game-sheet-tab:data").assertIsSelected(); compose.onNodeWithTag("game-sheet-content:cont").assertExists(); compose.shot("base/depois-ficha-conteudo") }
    @Config(qualifiers = Phone.LAND) @Test fun sheetData() { game(InputMode.TOUCH, section = "data"); compose.onNodeWithTag("game-sheet-tab:data").assertIsSelected(); compose.onNodeWithTag("game-sheet-content:data").assertExists(); compose.shot("base/depois-ficha-dados") }
    @Config(qualifiers = Phone.LAND) @Test fun sheetController() { game(InputMode.CONTROLLER); compose.shot("base/depois-ficha-controle") }
    @Config(qualifiers = Phone.LAND) @Test fun sheetControllerSettings() { game(InputMode.CONTROLLER, section = "set"); compose.shot("base/depois-ficha-controle-ajustes") }
    @Config(qualifiers = Phone.LAND) @Test fun launchWith() {
        game(InputMode.TOUCH)
        compose.onNodeWithTag("game-sheet-launch").performScrollTo().performClick()
        Fixture.settle()
        compose.screen("base/depois-iniciar-com")
    }
    @Config(qualifiers = Phone.PORT) @Test fun sheetOverviewToSettingsAndBack() {
        game(InputMode.TOUCH)
        compose.onNodeWithTag("game-sheet-link:settings").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("game-sheet-tab:settings").assertIsSelected()
        compose.onNodeWithTag("game-sheet-content:settings").assertExists()
        compose.onNodeWithTag("game-sheet-return").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("game-sheet-tab:ov").assertIsSelected()
        compose.onNodeWithTag("game-sheet-return").assertDoesNotExist()
    }

    @Config(qualifiers = Phone.LAND) @Test fun sheetPrimaryDataSections() {
        game(InputMode.TOUCH)
        compose.onNodeWithTag("game-sheet-tab:data").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("game-sheet-tab:data").assertIsSelected()
        compose.onNodeWithTag("game-sheet-content:data").assertExists()
        compose.onNodeWithTag("game-sheet-return").assertDoesNotExist()
        compose.onNodeWithTag("game-sheet-section:cont").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("game-sheet-content:cont").assertExists()
        compose.onNodeWithTag("game-sheet-section:perf").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("game-sheet-content:perf").assertExists()
        compose.onNodeWithTag("game-sheet-tab:data").assertIsSelected()
    }

    @Config(qualifiers = Phone.PORT) @Test fun sheetIdentifiersDisclosure() {
        game(InputMode.TOUCH)
        compose.onNodeWithTag("game-sheet-identifiers-values").assertDoesNotExist()
        compose.onNodeWithTag("game-sheet-identifiers").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNode(hasText("4D5307E6") and hasAnyAncestor(hasTestTag("game-sheet-identifiers-values")),
            useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("game-sheet-identifiers").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("game-sheet-identifiers-values").assertDoesNotExist()
    }

}
