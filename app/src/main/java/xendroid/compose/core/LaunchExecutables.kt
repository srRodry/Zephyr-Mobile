package xendroid.compose.core

import java.io.File

/** Only real sibling XEX files of an extracted game, keeping exact filename case. */
fun launchExecutables(launchPath: String): List<String>? {
    val file = File(launchPath)
    if (!file.extension.equals("xex", ignoreCase = true)) return null
    return file.parentFile?.listFiles()?.filter {
        it.isFile && it.extension.equals("xex", ignoreCase = true)
    }?.map { it.name }?.sortedWith(String.CASE_INSENSITIVE_ORDER)
}
