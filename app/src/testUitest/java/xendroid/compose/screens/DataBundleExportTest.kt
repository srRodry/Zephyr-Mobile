package xendroid.compose.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xendroid.compose.R
import xendroid.compose.bundle.DataBundles
import xendroid.compose.settings.ConfigStore
import xendroid.compose.settings.Setting
import xendroid.compose.settings.SettingsRepository
import xendroid.compose.settings.SettingsSchema
import xendroid.compose.settings.SettingsViewModel
import xendroid.compose.shots.FakeCore
import xendroid.compose.shots.Fixture
import xendroid.compose.shots.Phone
import xendroid.compose.shots.ShotApp
import xendroid.compose.shots.app
import xendroid.compose.ui.settings.DataBundleSection
import xendroid.compose.ui.settings.XdDataBundleCard

/** Actual settings edits must reach the ZIP, or the export must visibly fail. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShotApp::class, qualifiers = Phone.PORT)
class DataBundleExportTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = Fixture.context
    private val fps get() = SettingsSchema.byKey.getValue("GPU|framerate_limit") as Setting.ListChoice
    private lateinit var vm: SettingsViewModel
    private lateinit var global: File
    private lateinit var original: ByteArray
    private lateinit var output: File
    private var flushes = 0

    @Before fun setUp() {
        val store = ConfigStore(context)
        global = store.globalConfigFile()
        original = global.readBytes()
        store.editLiveConfig { it.putSetting(fps, "30") }
        vm = SettingsViewModel(SettingsRepository(store))
        Fixture.settleUntil { vm.ready.value || vm.error.value != null }
        assertTrue("settings opened", vm.ready.value)
        vm.onListChanged(fps, "60")
        assertEquals("pending edits have not reached disk yet", "30",
            FakeCore.parse(global.readText())[fps.key])
        output = File(context.cacheDir, "pending-settings-export.zip").apply { delete() }
    }

    @After fun tearDown() {
        if (global.isDirectory) global.deleteRecursively()
        vm.flush()
        global.writeBytes(original)
        output.delete()
    }

    private fun show(legacy: Boolean = false, cancelled: Boolean = false) {
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>,
                                       input: I, options: ActivityOptionsCompat?) {
                dispatchResult(requestCode, if (cancelled) Activity.RESULT_CANCELED else Activity.RESULT_OK,
                    if (cancelled) null else Intent().setData(Uri.fromFile(output)))
            }
        }
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        compose.app {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                val flush = { flushes++; vm.flushForTransfer() }
                if (legacy) DataBundleSection(beforeImport = flush, afterImport = {})
                else XdDataBundleCard(beforeImport = flush, afterImport = {})
            }
        }
        compose.onNodeWithText(context.getString(R.string.bundle_export)).performClick()
    }

    private fun expectCurrentSettings() {
        compose.waitUntil(15_000) { runCatching { output.inputStream().use(DataBundles::read) }.isSuccess }
        val exported = output.inputStream().use(DataBundles::read)
        assertEquals("latest screen edit exported", "60", FakeCore.parse(exported.globalConfig!!)[fps.key])
        assertEquals(1, flushes)
    }

    @Test fun cardExportsPendingSettings() {
        show()
        expectCurrentSettings()
    }

    @Test fun legacySectionExportsPendingSettings() {
        show(legacy = true)
        expectCurrentSettings()
    }

    @Test fun failedSaveStopsExportInsteadOfBackingUpStaleSettings() {
        // A non-file destination deterministically makes the settings save fail.
        assertTrue(global.delete())
        assertTrue(global.mkdir())
        show()
        compose.waitUntil(10_000) { vm.error.value != null }
        compose.waitForIdle()
        assertNotNull(vm.error.value)
        assertFalse("no misleading ZIP on failed save", output.exists())
        assertEquals(1, flushes)
        compose.onNodeWithText(context.getString(R.string.common_ok)).assertExists()
    }

    @Test fun cancellingTheFilePickerDoesNotFlushOrExport() {
        show(cancelled = true)
        compose.waitForIdle()
        assertEquals(0, flushes)
        assertFalse(output.exists())
        assertEquals("30", FakeCore.parse(global.readText())[fps.key])
    }
}
