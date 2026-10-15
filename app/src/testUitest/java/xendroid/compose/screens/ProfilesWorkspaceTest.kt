package xendroid.compose.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
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
import xendroid.compose.shots.Phone
import xendroid.compose.shots.ShotApp
import xendroid.compose.shots.app
import xendroid.compose.ui.design.InputMode
import xendroid.compose.ui.profile.ProfileManagerViewModel.ProfileEntry
import xendroid.compose.ui.profile.TouchProfiles

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShotApp::class)
class ProfilesWorkspaceTest {
    @get:Rule val compose = createComposeRule()
    private val a = ProfileEntry("E030000000000001", "Principal", 1, 103, false, true)
    private val b = ProfileEntry("E030000000000002", "Corrida", 1, 103, false, false)
    private val calls = mutableListOf<Pair<String, String>>()
    private fun open(profiles: () -> List<ProfileEntry> = { listOf(a, b) }) {
        compose.app(InputMode.TOUCH) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                TouchProfiles(profiles(), listOf(a.xuid, null, null, null), emptyMap(),
                    onActive = { calls += "activate" to it.xuid }, onEdit = { calls += "edit" to it.xuid },
                    onSaves = { calls += "saves" to it.xuid }, onTrash = { calls += "trash" to it.xuid },
                    onDetails = { calls += "details" to it.xuid })
            }
        }
    }
    private fun choose(id: String) {
        compose.onNodeWithTag("profiles-selector").performScrollToNode(hasTestTag("profile-select:$id"))
        compose.onNodeWithTag("profile-select:$id").performClick()
        compose.waitForIdle()
    }

    @Config(qualifiers = Phone.PORT) @Test fun inspectionDoesNotActivateAndExplicitActivationTargetsInspectedId() {
        open()
        compose.onNodeWithTag("profile-select:${a.xuid}").assertIsSelected()
        choose(b.xuid)
        compose.onNodeWithTag("profile-select:${b.xuid}").assertIsSelected()
        compose.onNodeWithTag("profile-inspected:${b.xuid}").assertExists()
        assertTrue(calls.isEmpty())
        compose.onNodeWithTag("profile-activate").performScrollTo().performClick()
        assertEquals(listOf("activate" to b.xuid), calls)
    }

    @Config(qualifiers = Phone.PORT) @Test fun actionsUseInspectedIdentityWithoutChangingActiveProfile() {
        open(); choose(b.xuid)
        for (action in listOf("edit", "details", "trash")) {
            compose.onNodeWithTag("profile-$action").performScrollTo().performClick()
        }
        assertEquals(listOf("edit" to b.xuid, "details" to b.xuid, "trash" to b.xuid), calls)
        assertTrue(a.isActive)
    }

    @Config(qualifiers = Phone.PORT) @Test fun removedInspectionFallsBackToActiveThenEmptyWithoutActions() {
        val state = mutableStateOf(listOf(a, b))
        open { state.value }; choose(b.xuid)
        compose.runOnIdle { state.value = listOf(a) }
        compose.onNodeWithTag("profile-inspected:${a.xuid}").assertExists()
        compose.onNodeWithTag("profile-activate").assertDoesNotExist()
        compose.runOnIdle { state.value = emptyList() }
        compose.onNodeWithTag("profiles-selector").assertDoesNotExist()
        compose.onNodeWithTag("profile-edit").assertDoesNotExist()
        assertTrue(calls.isEmpty())
    }

    @Config(qualifiers = Phone.PORT) @Test fun noActiveProfileUsesFirstForInspectionOnly() {
        open { listOf(b) }
        compose.onNodeWithTag("profile-inspected:${b.xuid}").assertExists()
        compose.onNodeWithTag("profile-activate").assertExists()
        assertTrue(calls.isEmpty())
    }

    @Config(qualifiers = "pt-rBR-w640dp-h320dp-land-night-hdpi")
    @Test fun shortLandscapeCanSelectLastAvatarAndReachActions() {
        val many = (1..12).map { b.copy(xuid = "E0300000000000%02X".format(it), gamertag = "Perfil local $it") }
        open { many }; choose(many.last().xuid)
        compose.onNodeWithTag("profile-details").performScrollTo().performClick()
        assertEquals(listOf("details" to many.last().xuid), calls)
    }

    @Config(qualifiers = Phone.PORT) @Test fun unavailableSaveSummaryNeverEnablesSaveAction() {
        open()
        compose.onNodeWithTag("profile-saves").performScrollTo().assertIsNotEnabled()
        assertTrue(calls.isEmpty())
    }
}
