package xendroid.compose.ui.ingame

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xendroid.compose.shots.Phone
import xendroid.compose.shots.ShotApp
import xendroid.compose.shots.app
import xendroid.compose.ui.design.InputMode

/** Actual menu composition and action routing; no guest execution or device measurements. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShotApp::class)
class TouchGameGuideTest {
    @get:Rule val compose = createComposeRule()

    private class Host {
        var state by mutableStateOf(InGameMenuState(open = true))
        var action: InGameAction? = null
        var quitChoice: Boolean? = null
    }

    private fun menu(mode: InputMode = InputMode.TOUCH): Host {
        val host = Host()
        compose.app(mode) {
            InGameMenu(host.state, InGameMenuModel(gameName = "Jogo de teste", paused = true),
                onPage = { host.state = host.state.copy(page = it) },
                onSelect = { host.state = host.state.select(it) },
                onAction = {
                    host.action = it
                    if (it == InGameAction.QUIT) host.state = host.state.copy(confirmingQuit = true, confirmationSelection = 0)
                },
                onAdjust = { _, _ -> }, onChoose = { _, _ -> }, onSet = { _, _ -> }, onLogChoice = {},
                onQuitChoice = { host.quitChoice = it; host.state = host.state.copy(confirmingQuit = false) })
        }
        return host
    }

    @Config(qualifiers = Phone.LAND) @Test fun controlsReturnToGuideAndResumeUsesExistingAction() {
        val host = menu()
        compose.onNodeWithTag("game-guide-root").assertExists()
        compose.onNodeWithTag("game-guide-controls").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(InGamePage.CONTROLS, host.state.page)
        compose.onNodeWithTag("game-guide-root").assertDoesNotExist()
        compose.onNodeWithTag("game-guide-tools").assertExists()
        compose.onNodeWithTag("game-guide-back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("game-guide-resume").performScrollTo().performClick()
        assertEquals(InGameAction.RESUME, host.action)
    }

    @Config(qualifiers = Phone.PORT) @Test fun settingsOpenGraphicsAndReturnToPortraitGuide() {
        val host = menu()
        compose.onNodeWithTag("game-guide-settings").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(InGamePage.GRAPHICS, host.state.page)
        compose.onNodeWithTag("game-guide-back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("game-guide-root").assertExists()
    }

    @Config(qualifiers = "pt-rBR-w640dp-h320dp-land-night-hdpi")
    @Test fun shortLandscapeQuitStillRequiresConfirmationAndCanBeCancelled() {
        val host = menu()
        compose.onNodeWithTag("game-guide-quit").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(host.state.confirmingQuit)
        compose.onNodeWithTag("game-guide-dialog").assertExists()
        compose.onNodeWithTag("game-guide-root").assertDoesNotExist()
        compose.onNodeWithTag("game-guide-quit-cancel").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(false, host.quitChoice)
        compose.onNodeWithTag("game-guide-root").assertExists()
        compose.onNodeWithTag("game-guide-quit").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("game-guide-quit-confirm").performScrollTo().performClick()
        assertEquals(true, host.quitChoice)
    }

    @Config(qualifiers = Phone.LAND) @Test fun controllerKeepsItsDirectCategoryMenu() {
        menu(InputMode.CONTROLLER)
        compose.onNodeWithTag("game-guide-root").assertDoesNotExist()
        compose.onNodeWithTag("game-guide-tools").assertExists()
    }
}
