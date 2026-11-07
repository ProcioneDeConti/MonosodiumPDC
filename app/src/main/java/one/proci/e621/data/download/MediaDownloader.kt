package one.proci.e621.data.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import one.proci.e621.data.api.MediaHttpClient
import java.io.IOException

/**
 * Saves a post's original media file either into a user-chosen folder (SAF tree, see
 * [one.proci.e621.data.settings.UserSettings.downloadLocationUri]) or, by default, into the
 * device's shared Pictures/Movies collections via MediaStore. The MediaStore path needs no
 * storage permission: scoped storage (mandatory since API 29, and this app's minSdk is 28) lets
 * any app insert new files it owns into those collections directly. The SAF path needs only the
 * persistable URI permission granted when the user picked the folder (see the folder picker in
 * Settings > Downloads) - no manifest permission either.
 */
class MediaDownloader(private val context: Context) {

    suspend fun download(url: String, displayName: String, mimeType: String, downloadLocationUri: String? = null): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (downloadLocationUri != null) {
                    downloadToTree(url, displayName, mimeType, Uri.parse(downloadLocationUri))
                } else {
                    downloadToMediaStore(url, displayName, mimeType)
                }
            }
        }

    /** Saves a file that's already on the device (a recovered deleted post) to the same places as [download]. */
    suspend fun saveFile(file: java.io.File, displayName: String, mimeType: String, downloadLocationUri: String? = null): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                val write: (java.io.OutputStream) -> Unit = { output -> file.inputStream().use { it.copyTo(output) } }
                if (downloadLocationUri != null) {
                    writeToTree(displayName, mimeType, Uri.parse(downloadLocationUri), write)
                } else {
                    writeToMediaStore(displayName, mimeType, write)
                }
            }
        }

    private fun fetchInto(url: String): (java.io.OutputStream) -> Unit = { output ->
        MediaHttpClient.instance.newCall(buildRequest(url)).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code})")
            response.body.byteStream().use { input -> input.copyTo(output) }
        }
    }

    private fun downloadToMediaStore(url: String, displayName: String, mimeType: String): Uri =
        writeToMediaStore(displayName, mimeType, fetchInto(url))

    private fun writeToMediaStore(displayName: String, mimeType: String, write: (java.io.OutputStream) -> Unit): Uri {
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
            resolver.openOutputStream(uri)?.use(write) ?: throw IOException("Could not open output stream")
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return uri
    }

    private fun downloadToTree(url: String, displayName: String, mimeType: String, treeUri: Uri): Uri =
        writeToTree(displayName, mimeType, treeUri, fetchInto(url))

    private fun writeToTree(displayName: String, mimeType: String, treeUri: Uri, write: (java.io.OutputStream) -> Unit): Uri {
        val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IOException("Chosen download folder is no longer accessible")
        val file = treeDoc.createFile(mimeType, displayName)
            ?: throw IOException("Could not create file in chosen download folder")

        try {
            context.contentResolver.openOutputStream(file.uri)?.use(write) ?: throw IOException("Could not open output stream")
        } catch (e: Exception) {
            file.delete()
            throw e
        }

        return file.uri
    }

    private fun buildRequest(url: String) = Request.Builder()
        .url(url)
        .build()
}
