package xendroid.compose.core

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class PresenterPerformanceHintsTest {
    @Before fun noPlatformHintService() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .setSystemService(Context.PERFORMANCE_HINT_SERVICE, null)
    }

    private fun hints() = PresenterPerformanceHints(ApplicationProvider.getApplicationContext<Application>())

    @Test fun disabledHintsNeverReadNativeWorkOrFpsLimit() {
        hints().use { h ->
            h.update({ error("native work should not be read") }, { error("target should not be read") }, true)
            assertEquals("Presenter ADPF · Off", h.status)
        }
    }

    @Test fun suspendedHintsNeverReadNativeWorkOrFpsLimit() {
        hints().use { h ->
            h.requested = true
            h.update({ error("native work should not be read") }, { error("target should not be read") }, false)
            assertEquals("Presenter ADPF · requested (suspended in menu/background)", h.status)
        }
    }

    @Test @Config(sdk = [30]) fun unsupportedApiNeverReadsNativeWorkOrFpsLimit() {
        hints().use { h ->
            h.requested = true
            h.update({ error("native work should not be read") }, { error("target should not be read") }, true)
            assertEquals("Presenter ADPF · Android 12+ required", h.status)
        }
    }

    @Test fun requestedForegroundHintsReadEachInputOnce() {
        var workReads = 0
        var targetReads = 0
        hints().use { h ->
            h.requested = true
            h.update({ workReads++; longArrayOf(0, 0, 0) }, { targetReads++; 33_333_333L }, true)
            assertEquals(1, workReads)
            assertEquals(1, targetReads)
            assertEquals("Presenter ADPF · waiting for native thread", h.status)
        }
    }
}
