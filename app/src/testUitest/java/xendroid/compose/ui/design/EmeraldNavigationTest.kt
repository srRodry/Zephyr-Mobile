package xendroid.compose.ui.design

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xendroid.compose.shots.Phone
import xendroid.compose.shots.ShotApp
import xendroid.compose.shots.app
import xendroid.compose.shots.shot

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShotApp::class)
class EmeraldNavigationTest {
    @get:Rule val compose = createComposeRule()

    private fun screen(fontScale: Float = 1f, mode: InputMode = InputMode.TOUCH) {
        compose.app(mode) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                XdSingleScreen("Sistema", area = XdArea.SETTINGS) {
                    XdCard(Modifier.fillMaxWidth(), title = "Configurações e drivers") {
                        Text("Vulkan · configuração por jogo", style = XdText.body, color = Xd.colors.fg)
                        XdButton("Gerenciar drivers", {}, kind = XdButtonKind.PRIMARY)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test @Config(qualifiers = Phone.PORT)
    fun portraitKeepsNavigationAndActionVisible() {
        screen()
        compose.onNodeWithTag("zephyr-navigation-portrait").assertIsDisplayed()
        compose.onNodeWithTag("zephyr-navigation-landscape").assertDoesNotExist()
        compose.onNodeWithText("Gerenciar drivers").assertIsDisplayed().performClick()
        compose.shot("emerald/sistema-retrato")
    }

    @Test @Config(qualifiers = Phone.LAND)
    fun landscapeUsesItsOwnRailRatherThanStretchingPortrait() {
        screen()
        compose.onNodeWithTag("zephyr-navigation-landscape").assertIsDisplayed()
        compose.onNodeWithTag("zephyr-navigation-portrait").assertDoesNotExist()
        compose.onNodeWithText("Gerenciar drivers").assertIsDisplayed()
        compose.shot("emerald/sistema-paisagem")
    }

    @Test @Config(qualifiers = Phone.PORT)
    fun enlargedTextKeepsPrimaryActionReachable() {
        screen(1.4f)
        compose.onNode(hasText("Jogos") and hasAnyAncestor(hasTestTag("zephyr-navigation-portrait")),
            useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Gerenciar drivers").assertIsDisplayed().performClick()
        compose.shot("emerald/sistema-fonte-ampliada")
    }

    @Test @Config(qualifiers = Phone.PORT)
    fun controllerPortraitRetainsSharedNavigationAndAction() {
        screen(mode = InputMode.CONTROLLER)
        compose.onNodeWithTag("zephyr-navigation-portrait").assertIsDisplayed()
        compose.onNodeWithTag("zephyr-navigation-landscape").assertDoesNotExist()
        compose.onNodeWithText("Gerenciar drivers").assertIsDisplayed().performClick()
        compose.shot("emerald/sistema-controle-retrato")
    }

    @Test @Config(qualifiers = Phone.LAND)
    fun controllerLandscapeRetainsSharedNavigationAndAction() {
        screen(mode = InputMode.CONTROLLER)
        compose.onNodeWithTag("zephyr-navigation-landscape").assertIsDisplayed()
        compose.onNodeWithTag("zephyr-navigation-portrait").assertDoesNotExist()
        compose.onNodeWithText("Gerenciar drivers").assertIsDisplayed().performClick()
        compose.shot("emerald/sistema-controle-paisagem")
    }
}
