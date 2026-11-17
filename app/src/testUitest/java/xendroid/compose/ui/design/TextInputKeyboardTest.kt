package xendroid.compose.ui.design

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xendroid.compose.shots.ShotApp
import xendroid.compose.shots.app

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShotApp::class)
class TextInputKeyboardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun touchRequestsKeyboardAndAllowsEditingInControllerMode() {
        var shows = 0
        val keyboard = object : SoftwareKeyboardController {
            override fun show() { shows++ }
            override fun hide() {}
        }
        compose.app(InputMode.CONTROLLER) {
            var value by remember { mutableStateOf("") }
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                XdTextInput(value, { value = it }, modifier = Modifier.testTag("launch-input"))
            }
        }
        compose.onNodeWithTag("launch-input").performTouchInput { click(center) }
        compose.onNode(hasSetTextAction()).assertIsFocused().performTextInput("AO4.Main.xex")
        compose.onNode(hasSetTextAction()).assertTextEquals("AO4.Main.xex")
        compose.runOnIdle { assertTrue("Touch must explicitly request the software keyboard", shows > 0) }
    }
}
