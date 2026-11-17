package xendroid.compose.updater

/**
 * Release identity read from a tag. CI tags releases `XenDroid-v<versionCode>-<sha>`;
 * older tags were `XenDroid-<sha>` and carry no ordering information.
 */
data class ReleaseTag(val versionCode: Int?, val commit: String)

fun parseReleaseTag(tag: String): ReleaseTag? {
    val body = tag.trim().removePrefix("Zephyr-Research-").removePrefix("XenDroid-")
    Regex("v([0-9]{1,9})-([0-9a-fA-F]{7,40})").matchEntire(body)?.let {
        return ReleaseTag(it.groupValues[1].toInt(), it.groupValues[2].lowercase())
    }
    Regex("([0-9a-fA-F]{7,40})").matchEntire(body)?.let { return ReleaseTag(null, it.groupValues[1].lowercase()) }
    return null
}

/**
 * Only an older build of the SAME channel is offered an update: the published feed
 * holds the regular `.fork` package, which cannot replace a debug or `.fork.opt`
 * test install anyway. Without a version code on both sides nothing is offered —
 * a different commit is not necessarily a newer one.
 */
fun isUpdateFor(release: ReleaseTag, installedVersionCode: Int, installedCommit: String): Boolean {
    val remote = release.versionCode ?: return false
    if (installedVersionCode <= 1) return false   // local build without CI numbering
    return remote > installedVersionCode && !installedCommit.startsWith(release.commit)
}

/** Keep Research APKs separate from the legacy regular-package feed. */
fun releaseMatchesPackage(tag: String, applicationId: String): Boolean = when (applicationId) {
    "xendroid.compose.zephyr.research" -> tag.startsWith("Zephyr-Research-v")
    "xendroid.compose.fork" -> tag.startsWith("XenDroid-v")
    else -> false
}
