package xendroid.compose.screens

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xendroid.compose.Emulator
import xendroid.compose.settings.ConfigStore
import xendroid.compose.shots.FakeCore
import xendroid.compose.shots.Fixture
import xendroid.compose.shots.ShotApp
import xendroid.compose.ui.profile.ProfileManagerViewModel
import xendroid.compose.ui.profile.ProfileManagerViewModel.ListState

/** Loading the profile list must report native-load errors and allow a retry. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShotApp::class)
class ProfileLoadFailureTest {
    @After fun resetFailure() {
        FakeCore.loadFailure = null
        if (Emulator.get == null) Emulator.get = Emulator()
    }

    private fun failThenRetry(failure: Throwable) {
        val context = Fixture.context
        Emulator.get = null
        FakeCore.loadFailure = failure
        val vm = ProfileManagerViewModel(context, ConfigStore(context))
        Fixture.settleUntil { vm.listState.value is ListState.Error }
        val state = vm.listState.value
        assertTrue("load failure becomes a retryable screen error", state is ListState.Error)
        assertEquals(failure.message, (state as ListState.Error).message)

        FakeCore.loadFailure = null
        FakeCore.profiles = listOf(FakeCore.profile(Fixture.P1, "Zephyr tester"))
        val retried = vm.refresh()
        Fixture.settleUntil { retried.isCompleted }
        assertFalse("retry completes normally", retried.isCancelled)
        val loaded = vm.listState.value
        assertTrue("retry restores the list", loaded is ListState.Loaded)
        assertEquals("Zephyr tester", (loaded as ListState.Loaded).profiles.single().gamertag)
    }

    @Test fun nativeRuntimeFailureCanBeRetried() {
        failThenRetry(IllegalStateException("simulated native startup failure"))
    }

    @Test fun missingNativeLibraryCanBeRetried() {
        failThenRetry(UnsatisfiedLinkError("simulated missing JNI library"))
    }

    @Test fun cancellationIsNotShownAsAProfileError() {
        // First create the VM normally, then cancel a fresh list request at native load.
        val context = Fixture.context
        val vm = ProfileManagerViewModel(context, ConfigStore(context))
        Fixture.settleUntil { vm.listState.value !is ListState.Loading }
        Emulator.get = null
        FakeCore.loadFailure = CancellationException("screen closed")
        val cancelled = vm.refresh()
        Fixture.settleUntil { cancelled.isCompleted }
        assertTrue(cancelled.isCancelled)
        assertFalse("normal coroutine cancellation is not an error dialog", vm.listState.value is ListState.Error)
    }
}
