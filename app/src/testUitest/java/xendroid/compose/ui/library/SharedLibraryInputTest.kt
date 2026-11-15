package xendroid.compose.ui.library

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xendroid.compose.data.Game
import xendroid.compose.data.GameFormat
import xendroid.compose.shots.Phone
import xendroid.compose.shots.ShotApp
import xendroid.compose.shots.app
import xendroid.compose.ui.design.InputMode

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShotApp::class, qualifiers = Phone.LAND)
class SharedLibraryInputTest {
    @get:Rule val compose = createComposeRule()
    private val games = listOf(
        Game("/games/forza.iso", "Forza Horizon", GameFormat.ISO),
        Game("/games/halo.iso", "Halo 3", GameFormat.ISO),
    )
    private var libraryGames = games
    private var preparing by mutableStateOf(false)
    private val played = mutableListOf<Game>()
    private val favorites = mutableListOf<Game>()

    private fun open() {
        compose.app(InputMode.CONTROLLER) {
            var selected by remember { mutableStateOf<Game?>(null) }
            LibraryTouch(
                data = LibraryData(libraryGames, emptySet(), emptyMap(), emptyMap(), emptyList(), LibrarySort.NAME_ASC),
                filter = LibraryFilter.All, onFilter = {}, query = "", onQuery = {},
                density = CoverDensity.MEDIUM, onDensity = {}, onSort = {},
                selected = selected, onSelect = { selected = it }, artOf = { CoverArt("", false) },
                onOpen = {}, onFavorite = { favorites += it }, onPlay = { played += it },
                preparing = preparing, onMenu = {}, gridState = rememberLazyGridState(), notices = {}, detail = {},
            )
        }
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { keyDown(key); keyUp(key) }
        compose.waitForIdle()
    }

    @Test fun gamepadSelectionAndConfirmUseTheSharedStage() {
        open()
        compose.onNodeWithTag("library-console").assertExists()
        press(Key.DirectionRight)
        press(Key.Enter)
        assertEquals(listOf(games[1]), played)
        press(Key.ButtonY)
        assertEquals(listOf(games[1]), favorites)
        press(Key.DirectionRight)
        press(Key.Enter)
        assertEquals(listOf(games[1], games[0]), played)
    }

    @Test fun confirmCannotLaunchWhilePreparationIsActive() {
        preparing = true
        open()
        press(Key.Enter)
        assertEquals(emptyList<Game>(), played)
        compose.runOnIdle { preparing = false }
        compose.waitForIdle()
        press(Key.Enter)
        assertEquals(listOf(games[0]), played)
    }

    @Test fun circularSwipeKeepsOneSelectionAndDoesNotLaunch() {
        libraryGames = games + Game("/games/rdr.iso", "Red Dead Redemption", GameFormat.ISO)
        open()
        compose.onNodeWithTag("library-coverflow").performTouchInput { swipeLeft(durationMillis = 700) }
        compose.waitForIdle()
        compose.onAllNodes(isSelected() and hasAnyAncestor(hasTestTag("library-coverflow")),
            useUnmergedTree = true).assertCountEquals(1)
        assertEquals(emptyList<Game>(), played)
        fun selectedTitle() = compose.onNode(isSelected() and hasAnyAncestor(hasTestTag("library-coverflow")),
            useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        val beforeCircle = selectedTitle()
        // A complete circle returns to the same title; the next step advances again.
        repeat(3) { compose.onNodeWithTag("library-next").performClick(); compose.waitForIdle() }
        assertEquals(beforeCircle, selectedTitle())
        compose.onNodeWithTag("library-next").performClick(); compose.waitForIdle()
        org.junit.Assert.assertNotEquals(beforeCircle, selectedTitle())
    }

    @Test fun singleGameHasOneAccessibleCoverAndSwipeDoesNotLaunch() {
        libraryGames = listOf(games.first())
        open()
        compose.onNodeWithTag("library-coverflow").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onAllNodes(isSelected() and hasAnyAncestor(hasTestTag("library-coverflow")),
            useUnmergedTree = true).assertCountEquals(1)
        assertEquals(emptyList<Game>(), played)
    }
}
