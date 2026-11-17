package xendroid.compose.ui.library

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import xendroid.compose.R
import xendroid.compose.compatibility.CompatStatus
import xendroid.compose.data.Game
import xendroid.compose.data.GameCollection
import xendroid.compose.data.GameFormat
import xendroid.compose.data.isFavorite
import xendroid.compose.sessions.TitleActivity

/** The library as the screens draw it: every game, and what is known about each. */
@Immutable
data class LibraryData(
    val games: List<Game>,
    val favorites: Set<String>,
    val activity: Map<String, TitleActivity>,
    val compat: Map<String, CompatStatus>,
    val collections: List<GameCollection>,
    val sort: LibrarySort,
) {
    fun favorite(game: Game) = isFavorite(game, favorites)
    fun played(game: Game): TitleActivity? = game.titleId?.uppercase()?.let { activity[it] }
    fun status(game: Game): CompatStatus? = game.titleId?.uppercase()?.let { compat[it] }

    /** [games] through [filter] and [query], in the chosen order. */
    fun shown(filter: LibraryFilter, query: String): List<Game> {
        val q = query.trim()
        val members = (filter as? LibraryFilter.Collection)?.let { f -> collections.firstOrNull { it.name == f.name } }?.members?.toHashSet()
        val list = games.filter { game ->
            when (filter) {
                LibraryFilter.All -> true
                LibraryFilter.Favorites -> favorite(game)
                is LibraryFilter.Collection -> members != null && game.identityKey in members
                is LibraryFilter.Format -> game.format == filter.format
                LibraryFilter.Recent -> played(game) != null
            } && (q.isEmpty() || game.name.contains(q, ignoreCase = true) || game.titleId?.contains(q, ignoreCase = true) == true)
        }
        return when {
            filter == LibraryFilter.Recent -> sortByRecent(list, activity)
            else -> sorted(list, sort)
        }
    }

    /** Counts need no sorted copy of the library (chips are recomposed frequently). */
    fun count(filter: LibraryFilter): Int = when (filter) {
        LibraryFilter.All -> games.size
        LibraryFilter.Favorites -> games.count { favorite(it) }
        LibraryFilter.Recent -> games.count { played(it) != null }
        is LibraryFilter.Format -> games.count { it.format == filter.format }
        is LibraryFilter.Collection -> {
            val members = collections.firstOrNull { it.name == filter.name }?.members?.toHashSet()
            if (members == null) 0 else games.count { it.identityKey in members }
        }
    }

    fun sorted(list: List<Game>, sort: LibrarySort): List<Game> = when (sort) {
        LibrarySort.NAME_ASC -> list.sortedBy { it.name.lowercase() }
        LibrarySort.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
        LibrarySort.FORMAT -> list.sortedWith(compareBy({ it.format.name }, { it.name.lowercase() }))
        LibrarySort.RECENT -> sortByRecent(list, activity)
    }

    /** The formats present, in a fixed order (the format chips). */
    val formats: List<GameFormat> get() = GameFormat.entries.filter { f -> games.any { it.format == f } }
}

/** What the library lists: everything, the favorites, one collection, one format, or recents (controller tabs). */
sealed interface LibraryFilter {
    data object All : LibraryFilter
    data object Favorites : LibraryFilter
    data object Recent : LibraryFilter
    data class Collection(val name: String) : LibraryFilter
    data class Format(val format: GameFormat) : LibraryFilter

    /** Kept across recreation as text. */
    val key: String get() = when (this) {
        All -> "all"; Favorites -> "fav"; Recent -> "recent"
        is Collection -> "col:$name"; is Format -> "fmt:${format.name}"
    }

    companion object {
        fun parse(key: String?): LibraryFilter = when {
            key == "fav" -> Favorites
            key == "recent" -> Recent
            key?.startsWith("col:") == true -> Collection(key.removePrefix("col:"))
            key?.startsWith("fmt:") == true -> GameFormat.entries.firstOrNull { it.name == key.removePrefix("fmt:") }?.let { Format(it) } ?: All
            else -> All
        }
    }
}

/** Cover size of the touch grid: small, medium, large (minimum column width in dp). */
enum class CoverDensity(val minWidth: Int) { SMALL(86), MEDIUM(110), LARGE(144) }

/** The grid's cover size, an app preference. */
object CoverDensityStore {
    private const val PREFS = "ui_look"
    private const val KEY = "cover_density"
    fun read(context: Context): CoverDensity =
        CoverDensity.entries.firstOrNull { it.name == context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) } ?: CoverDensity.MEDIUM
    fun write(context: Context, density: CoverDensity) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, density.name) }
}

/** A format as the chips and the sheet name it. */
@Composable
fun formatLabel(format: GameFormat): String = when (format) {
    GameFormat.ISO -> "ISO"
    GameFormat.ZAR -> "ZAR"
    GameFormat.GOD -> "GOD"
    GameFormat.XEX_FOLDER -> stringResource(R.string.xd_fmt_xex)
    GameFormat.STFS -> "XBLA"
}

/** "2 h ago", "yesterday": when the game was last played, in the shown language. */
@Composable
fun playedAgo(played: TitleActivity?): String? {
    played ?: return null
    LocalContext.current
    return DateUtils.getRelativeTimeSpanString(played.lastPlayedAt, System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
}

/** "14 h 32 min": total play time. */
fun playTime(played: TitleActivity?): String? = played?.let { xendroid.compose.sessions.formatPlayTime(it.playedMs) }

/** The short disc badge on a cover ("Disc 1/3"). */
@Composable
fun discBadge(game: Game): String? = if (game.isMultiDisc) stringResource(R.string.xd_lib_disc_badge, game.discNumber, game.discCount) else null
