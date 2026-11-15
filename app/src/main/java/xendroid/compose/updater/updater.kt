package xendroid.compose.updater

import xendroid.compose.R
import android.content.Context
import android.util.Log
import com.google.gson.annotations.SerializedName
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import xendroid.compose.BuildConfig

data class GithubAsset(
    @SerializedName("name") val name: String? = null,
    @SerializedName("size") val size: Long = 0,
    @SerializedName("browser_download_url") val downloadUrl: String? = null,
    @SerializedName("digest") val digest: String? = null,
)

data class GithubRelease(
    @SerializedName("name") val name: String? = null,
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("body") val changelog: String? = null,
    @SerializedName("html_url") val releaseUrl: String,
    @SerializedName("prerelease") val prerelease: Boolean = false,
    @SerializedName("draft") val draft: Boolean = false,
    @SerializedName("assets") val assets: List<GithubAsset>? = null,
) {
    fun toFeed() = FeedRelease(tagName, name, changelog, releaseUrl, prerelease, draft,
        assets.orEmpty().mapNotNull { a ->
            val url = a.downloadUrl?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            ReleaseAsset(a.name.orEmpty(), a.size, url, parseAssetDigest(a.digest))
        })
}

sealed class UpdateResult {
    data class Available(val release: FeedRelease) : UpdateResult()
    data class Latest(val commitHash: String) : UpdateResult()
    data class Cooldown(val remainingMillis: Long) : UpdateResult()
}

interface GithubApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun latestRelease(@Path("owner") owner: String, @Path("repo") repo: String): GithubRelease

    @GET("repos/{owner}/{repo}/releases")
    suspend fun releases(@Path("owner") owner: String, @Path("repo") repo: String,
                         @Query("per_page") perPage: Int = 10): List<GithubRelease>
}

private const val PREFS_NAME = "updater"
private const val KEY_LAST_CHECK = "last_update_check"
private const val KEY_CHANNEL = "channel"
private const val KEY_SKIPPED = "skipped_version_code"
private const val UPDATE_INTERVAL = 5 * 60 * 1000L

private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

fun getRemainingCooldown(context: Context): Long {
    val lastCheck = prefs(context).getLong(KEY_LAST_CHECK, 0L)
    return (UPDATE_INTERVAL - (System.currentTimeMillis() - lastCheck)).coerceAtLeast(0L)
}

fun shouldCheckForUpdates(context: Context): Boolean =
    System.currentTimeMillis() - prefs(context).getLong(KEY_LAST_CHECK, 0L) >= UPDATE_INTERVAL

/** When the app last looked for an update (null: never). */
fun lastUpdateCheck(context: Context): Long? = prefs(context).getLong(KEY_LAST_CHECK, 0L).takeIf { it > 0 }

fun saveLastCheck(context: Context) {
    prefs(context).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
}

fun updateChannel(context: Context): UpdateChannel =
    prefs(context).getString(KEY_CHANNEL, null)?.let { runCatching { UpdateChannel.valueOf(it) }.getOrNull() } ?: if (BuildConfig.APPLICATION_ID == "xendroid.compose.zephyr.research") UpdateChannel.PREVIEW else UpdateChannel.STABLE

fun setUpdateChannel(context: Context, channel: UpdateChannel) {
    prefs(context).edit().putString(KEY_CHANNEL, channel.name).apply()
}

/** R04: this release and older ones are not offered again; a newer one is. */
fun skipVersion(context: Context, versionCode: Int) {
    prefs(context).edit().putInt(KEY_SKIPPED, versionCode).apply()
}

private val http = OkHttpClient.Builder()
    .addInterceptor(
        // Never log response bodies: logcat is shelved into shareable diagnostics.
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
    )
    .build()

private val api = Retrofit.Builder()
    .baseUrl("https://api.github.com/")
    .client(http)
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(GithubApi::class.java)

/** The release feed for this build, or null when this package has no published channel. */
fun updateRepository(): Pair<String, String>? {
    if (BuildConfig.APPLICATION_ID !in setOf("xendroid.compose.fork", "xendroid.compose.zephyr.research")) return null
    val parts = BuildConfig.UPDATE_REPOSITORY.split('/')
    if (parts.size != 2 || parts.any { it.isBlank() }) return null
    return parts[0] to parts[1]
}

suspend fun checkForUpdates(context: Context): UpdateResult {
    val (owner, repo) = updateRepository() ?: return UpdateResult.Latest(BuildConfig.VERSION_NAME)
    val channel = updateChannel(context)
    if (channel == UpdateChannel.OFF) return UpdateResult.Latest(BuildConfig.VERSION_NAME)
    Log.d("Updater", "Checking $owner/$repo ($channel)")
    val releases = api.releases(owner, repo, perPage = 100)
        .filter { releaseMatchesPackage(it.tagName, BuildConfig.APPLICATION_ID) }
    val skipped = prefs(context).getInt(KEY_SKIPPED, 0).takeIf { it > 0 }
    val offer = chooseUpdate(releases.map { it.toFeed() }, channel, BuildConfig.VERSION_CODE,
        BuildConfig.VERSION_NAME.lowercase(), skipped)
    return offer?.let { UpdateResult.Available(it) } ?: UpdateResult.Latest(BuildConfig.VERSION_NAME)
}

/** Downloads the release's APK, verified against its published size and SHA-256. */
suspend fun downloadUpdate(context: Context, release: FeedRelease, onProgress: (Float) -> Unit): File =
    withContext(Dispatchers.IO) {
        val asset = apkAsset(release) ?: throw UpdateDownloadException(UpdateDownloadException.Reason.NO_APK)
        val sha = asset.sha256 ?: throw UpdateDownloadException(UpdateDownloadException.Reason.NO_SHA256)
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }   // one update at a time
        http.newCall(Request.Builder().url(asset.url).build()).execute().use { response ->
            if (!response.isSuccessful) throw UpdateDownloadException(UpdateDownloadException.Reason.HTTP, response.code.toLong())
            val body = response.body ?: throw UpdateDownloadException(UpdateDownloadException.Reason.EMPTY)
            downloadVerified(body.byteStream(), asset.size, sha, File(dir, "update.apk")) { bytes ->
                onProgress(bytes.toFloat() / asset.size)
            }
        }
    }

/** U02: why the download cannot be used, in the shown language. */
internal fun downloadFailureText(context: Context, e: UpdateDownloadException): String = when (e.reason) {
    UpdateDownloadException.Reason.NO_APK -> context.getString(R.string.upd_err_no_apk)
    UpdateDownloadException.Reason.NO_SHA256 -> context.getString(R.string.upd_err_no_sha256)
    UpdateDownloadException.Reason.HTTP -> context.getString(R.string.upd_err_http, e.args[0])
    UpdateDownloadException.Reason.EMPTY -> context.getString(R.string.upd_err_empty)
    UpdateDownloadException.Reason.UNEXPECTED_SIZE -> context.getString(R.string.upd_err_size, e.args[0])
    UpdateDownloadException.Reason.CANCELLED -> context.getString(R.string.upd_err_cancelled)
    UpdateDownloadException.Reason.TOO_LARGE -> context.getString(R.string.upd_err_too_large)
    UpdateDownloadException.Reason.STOPPED_EARLY -> context.getString(R.string.upd_err_stopped_early, e.args[0], e.args[1])
    UpdateDownloadException.Reason.MISMATCH -> context.getString(R.string.upd_err_mismatch)
    UpdateDownloadException.Reason.NOT_KEPT -> context.getString(R.string.upd_err_not_kept)
}

/**
 * What the update card lists for a release, one line each. Notes written by
 * tools/release_notes.py carry a short summary per language in hidden
 * `<!-- update-summary:<language>` blocks: the app's language is picked, then the same
 * language in another region, then English. Other notes (GitHub's generated "What's
 * Changed") are read up to their Full Changelog line, as text.
 */
fun updateNotes(text: String, language: String): List<String> {
    val summaries = Regex("<!--\\s*update-summary:([A-Za-z-]+)\\s*\\n(.*?)-->", RegexOption.DOT_MATCHES_ALL)
        .findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
    val base = language.substringBefore('-')
    val summary = summaries[language]
        ?: summaries.entries.firstOrNull { it.key.substringBefore('-').equals(base, ignoreCase = true) }?.value
        ?: summaries["en"]
        ?: summaries.values.firstOrNull()
    return (summary ?: cleanChangelog(text)).lines()
        .map { it.trim().removePrefix("•").trim().removePrefix("* ").removePrefix("- ").trim() }
        .filter { it.isNotEmpty() }
}

fun cleanChangelog(text: String): String {
    return text
        .replace(Regex("(?s)\\*\\*Full Changelog\\*\\*:.*"), "")
        .replace(Regex("(?s)<!--.*?-->|<details>.*?</details>"), "")
        // GitHub's generated lines: "* Title by @author in <pull request URL>" is the title.
        .replace(Regex("(?m)^\\* (.+) by @[\\w-]+ in https?://\\S+$"), "* $1")
        .replace(Regex("(?m)^\\* @[\\w-]+ made their first contribution.*$"), "")
        // Headings, HTML, tables and images say nothing on a card.
        .replace(Regex("(?m)^\\s*(#|<|\\||!\\[).*$"), "")
        .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), "")
        .replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "$1")
        .replace(Regex("https?://\\S+"), "")
        .replace(Regex("(?m)^\\s*>\\s?"), "")
        .replace("**", "").replace("`", "")
        .replace(Regex("\\* "), "• ")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
