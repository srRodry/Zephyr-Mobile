package xendroid.compose.driver

import android.content.Context
import android.net.Uri
import android.os.Build
import android.system.Os
import android.system.OsConstants
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import xendroid.compose.Application

object DriverPackageIO {
    private fun installer() = DriverPackageInstaller(Application.get_custom_driver_dir(),
        Build.VERSION.SDK_INT, Os.sysconf(OsConstants._SC_PAGESIZE))

    suspend fun import(context: Context, uri: Uri): InstalledDriver = withContext(Dispatchers.IO) {
        val coroutine = currentCoroutineContext()
        val temporary = File.createTempFile("driver-import-", ".zip", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open driver ZIP")
            input.use { source -> temporary.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var bytes = 0L
                while (true) {
                    coroutine.ensureActive()
                    val n = source.read(buffer)
                    if (n < 0) break
                    bytes += n
                    require(bytes <= 64L * 1024 * 1024) { "Driver ZIP exceeds 64 MB" }
                    output.write(buffer, 0, n)
                }
            } }
            installer().install(temporary) { coroutine.ensureActive() }
        } finally { temporary.delete() }
    }

    suspend fun install(file: File, expectedSha256: String?): InstalledDriver = withContext(Dispatchers.IO) {
        val coroutine = currentCoroutineContext()
        installer().install(file, expectedSha256) { coroutine.ensureActive() }
    }
}
