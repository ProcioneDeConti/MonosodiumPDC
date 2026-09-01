package one.proci.e621.data.download

import android.content.Context
import android.net.Uri
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DownloadState { QUEUED, ACTIVE, DONE, ERROR }

data class DownloadJob(
    val id: Long,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val postId: Long,
    val thumbnailUrl: String?,
    val state: DownloadState = DownloadState.QUEUED,
    val error: String? = null,
    val resultUri: Uri? = null,
)

/**
 * Session-only (in-memory) download queue with concurrency 2 - every download in the app routes
 * through here so there's one place to see progress, retry, and clear. Mirrors the desktop app's
 * `downloadsStore`. Not persisted: a queue is a "right now" thing.
 */
class DownloadQueue(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val downloadLocationUri: () -> String?,
    private val onDownloadComplete: (() -> Unit)? = null,
) {
    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()

    val activeOrQueuedCount: Int
        get() = _jobs.value.count { it.state == DownloadState.QUEUED || it.state == DownloadState.ACTIVE }

    private val nextId = AtomicLong(1)
    private val work = Channel<Long>(Channel.UNLIMITED)

    init {
        repeat(CONCURRENCY) {
            scope.launch {
                for (jobId in work) runJob(jobId)
            }
        }
    }

    fun enqueue(url: String, fileName: String, mimeType: String, postId: Long, thumbnailUrl: String?) {
        // De-dupe: don't re-queue a file that's already queued/active/done in this session.
        if (_jobs.value.any { it.fileName == fileName && it.state != DownloadState.ERROR }) return
        val job = DownloadJob(nextId.getAndIncrement(), url, fileName, mimeType, postId, thumbnailUrl)
        _jobs.update { it + job }
        work.trySend(job.id)
    }

    fun retry(id: Long) {
        _jobs.update { list -> list.map { if (it.id == id) it.copy(state = DownloadState.QUEUED, error = null) else it } }
        work.trySend(id)
    }

    fun remove(id: Long) = _jobs.update { list -> list.filterNot { it.id == id } }

    fun clearFinished() = _jobs.update { list ->
        list.filterNot { it.state == DownloadState.DONE || it.state == DownloadState.ERROR }
    }

    fun clearAll() = _jobs.update { list -> list.filter { it.state == DownloadState.ACTIVE } }

    private suspend fun runJob(id: Long) {
        val job = _jobs.value.firstOrNull { it.id == id && it.state == DownloadState.QUEUED } ?: return
        setState(id) { it.copy(state = DownloadState.ACTIVE) }
        val result = MediaDownloader(appContext).download(job.url, job.fileName, job.mimeType, downloadLocationUri())
        result.fold(
            onSuccess = { uri ->
                setState(id) { it.copy(state = DownloadState.DONE, resultUri = uri) }
                onDownloadComplete?.invoke()
            },
            onFailure = { e -> setState(id) { it.copy(state = DownloadState.ERROR, error = e.message ?: e.toString()) } },
        )
    }

    private fun setState(id: Long, transform: (DownloadJob) -> DownloadJob) {
        _jobs.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private companion object {
        const val CONCURRENCY = 2
    }
}
