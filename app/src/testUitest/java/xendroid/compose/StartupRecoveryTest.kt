package xendroid.compose

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercises the real application startup with only GPU/JNI calls substituted. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = StartupTestApplication::class)
class StartupRecoveryTest {
    private val app get() = ApplicationProvider.getApplicationContext<StartupTestApplication>()

    @After fun clearSingleton() { Emulator.get = null }

    @Test fun noVulkanStillCreatesConfigurationWithoutLoadingCore() {
        assertNull(Application.gpu_device_name_vk)
        assertEquals(0, app.loads)
        assertTrue(Application.get_default_config_file().isFile)
        assertTrue(Application.should_delay_load())
    }

    @Test fun failedGpuProbeKeepsUiStartupAvailable() {
        app.probeFailure = UnsatisfiedLinkError("probe library unavailable")
        app.onCreate()
        assertNull(Application.gpu_device_name_vk)
        assertEquals(0, app.loads)
    }

    @Test fun adrenoSixHundredRetainsDelayedLoading() {
        app.gpu = "Adreno (TM) 650"
        app.onCreate()
        assertEquals(0, app.loads)
        assertTrue(Application.should_delay_load())
    }

    @Test fun supportedGpuPreloadsAndFailedPreloadCanBeRetried() {
        app.gpu = "Adreno (TM) 740"
        app.loadFailure = UnsatisfiedLinkError("core library unavailable")
        app.onCreate()
        assertEquals(1, app.loads)
        assertNull("a failed load must not leave a usable-looking core", Emulator.get)
        app.loadFailure = null
        app.onCreate()
        assertEquals(2, app.loads)
        assertNotNull(Emulator.get)
    }

    @Test fun failedLoaderClearsSingletonAndDoesNotReplaceSuccessfulCore() {
        Emulator.get = null
        assertThrows(IllegalStateException::class.java) {
            Emulator.load_library(Runnable { throw IllegalStateException("JNI initialization failed") })
        }
        assertNull(Emulator.get)
        Emulator.load_library(Runnable {})
        val loaded = Emulator.get
        assertNotNull(loaded)
        assertThrows(RuntimeException::class.java) { Emulator.load_library(Runnable {}) }
        assertSame(loaded, Emulator.get)
    }
}

class StartupTestApplication : Application() {
    var gpu: String? = null
    var probeFailure: Throwable? = null
    var loadFailure: Throwable? = null
    var loads = 0

    override fun probeVulkanGpu(): String? {
        probeFailure?.let { throw it }
        return gpu
    }

    override fun loadNativeCore() {
        loads++
        Emulator.load_library(Runnable { loadFailure?.let { throw it } })
    }
}
