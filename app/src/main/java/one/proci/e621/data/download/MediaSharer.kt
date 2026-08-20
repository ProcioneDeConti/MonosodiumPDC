package one.proci.e621.data.download

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import one.proci.e621.data.api.MediaHttpClient
import java.io.File
import java.io.IOException

/** Downloads a post's media into the app's cache and launches the system share sheet for it. */
class MediaSharer(private val context: Context) {

    suspend fun shareFile(url: String, fileName: String, mimeType: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                val file = File(dir, fileName)

                val request = Request.Builder().url(url).build()
                MediaHttpClient.instance.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code})")
                    file.outputStream().use { output -> response.body.byteStream().use { input -> input.copyTo(output) } }
                }

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, null))
            }
        }

    fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}
