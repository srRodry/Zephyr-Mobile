package xendroid.compose.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
import xendroid.compose.ui.controls.*
import xendroid.compose.ui.design.InputMode
import xendroid.compose.ui.keymap.KeymapViewModel

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShotApp::class)
class ControlsHubTest {
    @get:Rule val compose = createComposeRule()
    private val tools = mutableListOf<String>()
    private fun open(mode: InputMode = InputMode.TOUCH, initial: String? = null): SettingsViewModel {
        val container = AppContainer(Fixture.context)
        val global = container.settingsViewModelFactory().create(SettingsViewModel::class.java)
        val keymap = container.keymapViewModelFactory().create(KeymapViewModel::class.java)
        Fixture.settleUntil { global.ready.value || global.error.value != null }
        assertTrue(global.ready.value)
        compose.app(mode) {
            CompositionLocalProvider(LocalInputDevicesPreview provides InputDevicesNow()) {
                ControlsScreen(global, keymap, {}, ControlsLinks(
                    onKeymap = { tools += "keymap" }, onEditor = { tools += "editor" },
                    onTest = { tools += "test" }, onPhone = { tools += "phone" }), initial)
            }
        }
        Fixture.settle(); compose.waitForIdle()
        return global
    }
    private fun destination(id: String) {
        compose.onNodeWithTag("controls-hub-destination:$id").performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Config(qualifiers = Phone.PORT) @Test fun touchPanelAndBackDoNotChangeCoreSettings() {
        val vm = open(); val before = vm.values.value.mapValues { it.value.raw }
        destination(ControlsSections.TOUCH)
        compose.onNodeWithTag("controls-hub-content:touch").assertExists()
        compose.onNodeWithTag("controls-hub-back").performClick(); compose.waitForIdle()
        compose.onNodeWithTag("controls-hub-home").assertExists()
        assertEquals(before, vm.values.value.mapValues { it.value.raw })
        assertTrue(tools.isEmpty())
    }

    @Config(qualifiers = Phone.PORT) @Test fun padsMotionAndHomeAreReachableWithoutChangingValues() {
        val vm = open(); val before = vm.values.value.mapValues { it.value.raw }
        for (id in listOf(ControlsSections.PADS, ControlsSections.MOTION)) {
            destination(id)
            compose.onNodeWithTag("controls-hub-content:$id").assertExists()
            compose.onNodeWithTag("controls-hub-home-button").performClick(); compose.waitForIdle()
        }
        assertEquals(before, vm.values.value.mapValues { it.value.raw })
    }

    @Config(qualifiers = Phone.PORT) @Test fun eachExternalToolInvokesOnlyItsOriginalCallback() {
        open()
        for (id in listOf("keymap", "editor", "test", "phone")) destination("go-$id")
        assertEquals(listOf("keymap", "editor", "test", "phone"), tools)
        compose.onNodeWithTag("controls-hub-home").assertExists()
    }

    @Config(qualifiers = Phone.PORT) @Test fun touchDeepLinkBypassesLandingAndBackReturnsHome() {
        open(initial = ControlsSections.TOUCH)
        compose.onNodeWithTag("controls-hub-content:touch").assertExists()
        compose.onNodeWithTag("controls-hub-home").assertDoesNotExist()
        compose.onNodeWithTag("controls-hub-back").performClick(); compose.waitForIdle()
        compose.onNodeWithTag("controls-hub-home").assertExists()
    }

    @Config(qualifiers = "pt-rBR-w640dp-h320dp-land-night-hdpi")
    @Test fun shortLandscapeKeepsDetailedStatusAndPhonePanelAccessible() {
        open(); destination(ControlsSections.STATUS)
        compose.onNodeWithTag("controls-hub-content:input-status").assertExists()
        compose.onNodeWithTag("controls-hub-back").performClick(); compose.waitForIdle()
        destination(ControlsSections.PHONES)
        compose.onNodeWithTag("controls-hub-content:phones").assertExists()
        assertTrue(tools.isEmpty())
    }

    @Config(qualifiers = Phone.LAND) @Test fun controllerKeepsOriginalOverview() {
        open(InputMode.CONTROLLER)
        compose.onNodeWithTag("controls-touch-frame").assertDoesNotExist()
        compose.onAllNodesWithText(Fixture.context.getString(R.string.xd_ctl_sec_overview)).onFirst().assertExists()
    }
}
