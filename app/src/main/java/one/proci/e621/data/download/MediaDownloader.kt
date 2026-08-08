package one.proci.e621.data.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

private val downloadHttpClient by lazy { OkHttpClient() }

/**
 * Saves a post's original media file into the device's shared Pictures/Movies collections via
 * MediaStore. No storage permission is needed: scoped storage (mandatory since API 29, and this
 * app's minSdk is 34) lets any app insert new files it owns into those collections directly.
 */
class MediaDownloader(private val context: Context) {

    suspend fun download(url: String, displayName: String, mimeType: String): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                val isVideo = mimeType.startsWith("video/")
                val collection = if (isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                val relativePath = if (isVideo) "Movies/e621" else "Pictures/e621"

                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = resolver.insert(collection, values) ?: throw IOException("Could not create media entry")

                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "e621ForAndroid/1.0")
                        .build()

                    downloadHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code})")
                        val body = response.body ?: throw IOException("Empty response body")
                        resolver.openOutputStream(uri)?.use { output ->
                            body.byteStream().use { input -> input.copyTo(output) }
                        } ?: throw IOException("Could not open output stream")
                    }
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }

                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)

                uri
            }
        }
}
