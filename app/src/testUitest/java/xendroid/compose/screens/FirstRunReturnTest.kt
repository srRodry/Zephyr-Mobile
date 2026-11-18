package xendroid.compose.screens

import android.app.AppOpsManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows
import xendroid.compose.AppContainer
import xendroid.compose.R
import xendroid.compose.shots.Fixture
import xendroid.compose.shots.Phone
import xendroid.compose.shots.ShotApp
import xendroid.compose.shots.app
import xendroid.compose.ui.compress.GameCompressViewModel
import xendroid.compose.ui.design.InputMode
import xendroid.compose.ui.library.FirstRunStore
import xendroid.compose.ui.library.GameLibraryScreen

/**
 * Device feedback, round 2: tapping Games brought the setup assistant back. Its state was
 * declared after the folder browser's early return (so it started over, at step 1, open), and
 * About's request was a counter never reset (so every return to the library reopened it).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShotApp::class, qualifiers = Phone.LAND)
class FirstRunReturnTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = Fixture.context
    private fun text(id: Int) = context.getString(id)
    private fun shown(id: Int) = compose.onAllNodesWithText(text(id)).fetchSemanticsNodes().isNotEmpty()

    private fun freshInstall() {
        context.getSharedPreferences("first_run", Context.MODE_PRIVATE).edit().clear().commit()
        // All files access granted, so "Add folder" opens the app's own folder browser.
        val appOps = context.getSystemService(AppOpsManager::class.java)
        Shadows.shadowOf(appOps).setMode("android:manage_external_storage", context.applicationInfo.uid, context.packageName,
            AppOpsManager.MODE_ALLOWED)
    }

    @Test fun assistantKeepsItsStepAcrossTheFolderBrowser() {
        val container = AppContainer(context)
        val vm = Fixture.library(container)
        val compress = container.gameCompressViewModelFactory().create(GameCompressViewModel::class.java)
        freshInstall()
        compose.app(InputMode.TOUCH) {
            GameLibraryScreen(vm, {}, {}, {}, {}, {}, { _, _, _, _ -> }, { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, {}, compress)
        }
        compose.waitForIdle()
        assertTrue("the assistant opens on a fresh install", shown(R.string.fr_welcome))
        compose.onAllNodesWithText(text(R.string.xd_fr_continue)).onFirst().performClick()
        compose.waitForIdle()
        assertTrue(shown(R.string.fr_your_games))
        // "Add folder" opens the folder browser in place of the library and the assistant.
        compose.onAllNodesWithText(text(R.string.xd_fr_add_folder)).onFirst().performClick()
        compose.waitForIdle()
        assertTrue("the browser is open", !shown(R.string.fr_your_games))
        compose.onAllNodesWithContentDescription(text(R.string.xd_back)).onFirst().performClick()
        // The assistant is rendered in a separate Dialog window. Robolectric's
        // immediate idle barrier can complete before that window is attached;
        // await the observable step, but still fail on a real lost state.
        compose.waitUntil(timeoutMillis = 5_000) { shown(R.string.fr_your_games) }
        assertTrue("back on the same step", shown(R.string.fr_your_games))
        assertFalse("return must not restart the wizard", shown(R.string.fr_welcome))
    }

    @Test fun aboutsRequestOpensTheAssistantOnce() {
        val container = AppContainer(context)
        val vm = Fixture.library(container)
        val compress = container.gameCompressViewModelFactory().create(GameCompressViewModel::class.java)
        var requested by mutableStateOf(true)
        var visit by mutableIntStateOf(0)
        compose.app(InputMode.TOUCH) {
            // A new key is the library composed again, as after coming back from another area.
            key(visit) {
                GameLibraryScreen(vm, {}, {}, {}, {}, {}, { _, _, _, _ -> }, { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, {}, compress,
                    assistantRequested = requested, onAssistantHandled = { requested = false })
            }
        }
        compose.waitForIdle()
        assertTrue("About's request opens it", shown(R.string.fr_welcome))
        assertEquals(false, requested)
        compose.onAllNodesWithText(text(R.string.fr_skip)).onFirst().performClick()
        compose.waitForIdle()
        assertTrue(FirstRunStore.done(context))
        visit++
        compose.waitForIdle()
        assertTrue("coming back to Games does not open it again", !shown(R.string.fr_welcome))
    }
}
