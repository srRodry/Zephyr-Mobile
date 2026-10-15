package xendroid.compose.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xendroid.compose.AppContainer
import xendroid.compose.R
import xendroid.compose.settings.SettingsViewModel
import xendroid.compose.shots.Fixture
import xendroid.compose.shots.Phone
import xendroid.compose.shots.ShotApp
import xendroid.compose.shots.app
import xendroid.compose.ui.design.InputMode
import xendroid.compose.ui.settings.SettingsScreen

/** Real SettingsScreen, view model and section history; only the guest backend is the test fixture. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShotApp::class)
class SettingsHubTest {
    @get:Rule val compose = createComposeRule()

    private fun open(mode: InputMode = InputMode.TOUCH, initial: String? = null): SettingsViewModel {
        val container = AppContainer(Fixture.context)
        val vm = container.settingsViewModelFactory().create(SettingsViewModel::class.java)
        Fixture.settleUntil { vm.ready.value || vm.error.value != null }
        assertTrue("Settings loaded", vm.ready.value)
        compose.app(mode) { SettingsScreen(vm, {}, initialSection = initial) }
        Fixture.settle()
        compose.waitForIdle()
        return vm
    }

    @Config(qualifiers = Phone.PORT) @Test fun graphicsImageAndBackPreserveAllValues() {
        val vm = open()
        val before = vm.values.value.mapValues { it.value.raw }
        compose.onNodeWithTag("settings-hub-category:graphics").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings-hub-section:set:IMAGE").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings-hub-content:set:IMAGE").assertExists()
        compose.onNodeWithTag("settings-hub-back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings-hub-section:set:DRIVER").assertExists()
        compose.onNodeWithTag("settings-hub-back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings-hub-home").assertExists()
        assertEquals(before, vm.values.value.mapValues { it.value.raw })
    }

    @Config(qualifiers = Phone.LAND) @Test fun systemLanguageAndHomeRemainReachable() {
        open()
        compose.onNodeWithTag("settings-hub-category:system").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings-hub-section:app:lang").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings-hub-content:app:lang").assertExists()
        compose.onNodeWithTag("settings-hub-home-button").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings-hub-home").assertExists()
    }

    @Config(qualifiers = Phone.PORT) @Test fun existingDataDeepLinkDoesNotOpenTheHubFirst() {
        open(initial = "app:data")
        compose.onNodeWithTag("settings-hub-content:app:data").assertExists()
        compose.onNodeWithTag("settings-hub-home").assertDoesNotExist()
        compose.onNodeWithTag("settings-hub-back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings-hub-home").assertExists()
    }

    @Config(qualifiers = Phone.PORT) @Test fun searchAndClearDoNotChangeSettings() {
        val vm = open()
        val before = vm.values.value.mapValues { it.value.raw }
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("settings-hub-search")),
            useUnmergedTree = true).performTextInput("anisotropic")
        compose.waitForIdle()
        compose.onNodeWithTag("settings-hub-search-results").assertExists()
        compose.onNodeWithTag("settings-hub-category:graphics").assertDoesNotExist()
        compose.onNodeWithTag("settings-hub-clear-search").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings-hub-home").assertExists()
        assertEquals(before, vm.values.value.mapValues { it.value.raw })
    }

    @Config(qualifiers = "pt-rBR-w640dp-h320dp-land-night-hdpi")
    @Test fun shortLandscapeKeepsAdvancedSummaryAccessible() {
        open()
        compose.onNodeWithTag("settings-hub-category:advanced").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings-hub-section:resumo").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings-hub-content:resumo").assertExists()
        compose.onNodeWithTag("settings-hub-home-button").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings-hub-category:storage").performScrollTo().assertExists()
    }

    @Config(qualifiers = Phone.LAND) @Test fun controllerKeepsTheOriginalSummary() {
        open(InputMode.CONTROLLER)
        compose.onNodeWithTag("settings-touch-frame").assertDoesNotExist()
        compose.onAllNodesWithText(Fixture.context.getString(R.string.xd_set_summary)).onFirst().assertExists()
    }
}
