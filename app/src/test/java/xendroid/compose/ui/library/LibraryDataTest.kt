package xendroid.compose.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test
import xendroid.compose.compatibility.CompatStatus
import xendroid.compose.data.Game
import xendroid.compose.data.GameCollection
import xendroid.compose.data.GameFormat
import xendroid.compose.sessions.TitleActivity

/** The library's filters, search and orders (the chips, the controller tabs). */
class LibraryDataTest {
    private val halo = Game("/g/halo3.iso", "Halo 3", GameFormat.ISO, titleId = "4D5307E6")
    private val forza = Game("/g/forza.zar", "Forza Horizon", GameFormat.ZAR, titleId = "4D5309C9")
    private val geo = Game("/g/geo", "Geometry Wars", GameFormat.STFS, titleId = "584108FF")
    private val noId = Game("/g/demo/default.xex", "Demo", GameFormat.XEX_FOLDER)
    private val games = listOf(halo, forza, geo, noId)

    private fun data(sort: LibrarySort = LibrarySort.NAME_ASC) = LibraryData(
        games = games,
        favorites = setOf(forza.identityKey, "/g/geo"),
        activity = mapOf(
            "4D5307E6" to TitleActivity("4D5307E6", lastPlayedAt = 100, playedMs = 1_000, runs = 1),
            "4D5309C9" to TitleActivity("4D5309C9", lastPlayedAt = 300, playedMs = 9_000, runs = 4),
        ),
        compat = mapOf("4D5307E6" to CompatStatus.PLAYABLE),
        collections = listOf(GameCollection("Racing", listOf(forza.identityKey))),
        sort = sort,
    )

    @Test fun filters() {
        val d = data()
        assertEquals(listOf("Demo", "Forza Horizon", "Geometry Wars", "Halo 3"), d.shown(LibraryFilter.All, "").map { it.name })
        // A favourite saved by launch path before identity keys existed still counts.
        assertEquals(listOf("Forza Horizon", "Geometry Wars"), d.shown(LibraryFilter.Favorites, "").map { it.name })
        assertEquals(listOf("Forza Horizon"), d.shown(LibraryFilter.Collection("Racing"), "").map { it.name })
        assertEquals(emptyList<Game>(), d.shown(LibraryFilter.Collection("Gone"), ""))
        assertEquals(listOf("Halo 3"), d.shown(LibraryFilter.Format(GameFormat.ISO), "").map { it.name })
        assertEquals(listOf("Forza Horizon", "Halo 3"), d.shown(LibraryFilter.Recent, "").map { it.name })
        assertEquals(2, d.count(LibraryFilter.Favorites))
    }

    @Test fun counts_match_visible_entries_for_every_filter_and_sort() {
        val filters = listOf(LibraryFilter.All, LibraryFilter.Favorites, LibraryFilter.Recent,
            LibraryFilter.Collection("Racing"), LibraryFilter.Collection("Gone")) +
            GameFormat.entries.map { LibraryFilter.Format(it) }
        for (sort in LibrarySort.entries) {
            val d = data(sort)
            for (filter in filters) assertEquals(d.shown(filter, "").size, d.count(filter))
            val empty = d.copy(games = emptyList())
            for (filter in filters) assertEquals(0, empty.count(filter))
        }
    }

    @Test fun search_by_name_or_title_id() {
        val d = data()
        assertEquals(listOf("Halo 3"), d.shown(LibraryFilter.All, "halo").map { it.name })
        assertEquals(listOf("Forza Horizon"), d.shown(LibraryFilter.All, "4d5309").map { it.name })
        assertEquals(listOf("Geometry Wars"), d.shown(LibraryFilter.Favorites, " wars ").map { it.name })
    }

    @Test fun orders() {
        assertEquals(listOf("Halo 3", "Geometry Wars", "Forza Horizon", "Demo"), data(LibrarySort.NAME_DESC).shown(LibraryFilter.All, "").map { it.name })
        assertEquals(listOf("Forza Horizon", "Halo 3", "Demo", "Geometry Wars"), data(LibrarySort.RECENT).shown(LibraryFilter.All, "").map { it.name })
        assertEquals(setOf(GameFormat.ISO, GameFormat.ZAR, GameFormat.XEX_FOLDER, GameFormat.STFS), data().formats.toSet())
    }

    @Test fun what_is_known_about_a_game() {
        val d = data()
        assertEquals(CompatStatus.PLAYABLE, d.status(halo))
        assertEquals(null, d.status(noId))
        assertEquals(4, d.played(forza)?.runs)
        assertEquals(null, d.played(noId))
    }

    @Test fun filter_keys_survive_recreation() {
        for (f in listOf(LibraryFilter.All, LibraryFilter.Favorites, LibraryFilter.Recent, LibraryFilter.Collection("Racing: 2"),
            LibraryFilter.Format(GameFormat.GOD))) {
            assertEquals(f, LibraryFilter.parse(f.key))
        }
        assertEquals(LibraryFilter.All, LibraryFilter.parse(null))
        assertEquals(LibraryFilter.All, LibraryFilter.parse("fmt:NOPE"))
    }

    @Test fun controller_tabs_are_recents_favourites_everything_then_collections() {
        assertEquals(listOf(LibraryFilter.Recent, LibraryFilter.Favorites, LibraryFilter.All, LibraryFilter.Collection("Racing")),
            controllerTabs(data()))
    }
}
