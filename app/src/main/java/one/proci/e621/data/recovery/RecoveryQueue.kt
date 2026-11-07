package one.proci.e621.data.recovery

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.proci.e621.R
import one.proci.e621.data.model.Post
import one.proci.e621.data.settings.Site

/**
 * Bulk un-delete (ported from the desktop's `recoveryQueueStore`): a session-only queue of deleted
 * posts to search for, one post at a time through the same [SourceRecovery.recover] as the viewer's
 * button, so the cache, grid tiles and per-post reports all apply. One at a time on purpose: a
 * single recovery already searches three sites in parallel. Capped at [MAX_BATCH] queued/running;
 * posts with a recovered copy are skipped, ones searched before without result are tried again.
 */
object RecoveryQueue {

    const val MAX_BATCH = 15
    private const val CHANNEL_ID = "recovery"
    private const val NOTIFICATION_ID = 6210

    enum class Status { QUEUED, RUNNING, FOUND, NOT_FOUND, FAILED, CANCELLED }

    data class Job(
        val site: Site,
        val postId: Long,
        val status: Status,
        /** Best grade found (status FOUND). */
        val grade: Grade? = null,
        /** Files found (FOUND) or places searched (NOT_FOUND). */
        val count: Int = 0,
        val error: String? = null,
    ) {
        val key get() = "${site.name}:$postId"
        val active get() = status == Status.QUEUED || status == Status.RUNNING
    }

    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()

    /** Latest status of the post being searched. */
    private val _current = MutableStateFlow<SourceRecovery.Progress?>(null)
    val current: StateFlow<SourceRecovery.Progress?> = _current.asStateFlow()

    private lateinit var appContext: Context
    private lateinit var scope: CoroutineScope
    private val posts = HashMap<String, Post>()
    private var pump: kotlinx.coroutines.Job? = null
    private var running: kotlinx.coroutines.Job? = null

    fun init(context: Context, scope: CoroutineScope) {
        appContext = context.applicationContext
        this.scope = scope
    }

    private fun keyOf(site: Site, id: Long) = "${site.name}:$id"

    private fun hasCopy(site: Site, id: Long) = RecoveredStore.thumbs.value[site]?.get(id)?.hasCopy == true

    /** Which of [list] a bulk run would take: deleted, something to search with, no recovered copy yet. */
    fun recoverable(site: Site, list: List<Post>): List<Post> =
        list.filter { it.isDeleted && !hasCopy(site, it.id) && SourceRecovery.canSearch(it) }

    sealed interface EnqueueResult {
        data class Added(val count: Int) : EnqueueResult
        data object NothingToDo : EnqueueResult
        data object TooMany : EnqueueResult
    }

    /** Adds the recoverable posts among [list], refusing the lot if the queue would go over [MAX_BATCH]. */
    fun enqueue(site: Site, list: List<Post>): EnqueueResult {
        val pending = _jobs.value.filter { it.active }
        val fresh = recoverable(site, list).filter { p -> pending.none { it.key == keyOf(site, p.id) } }
        if (fresh.isEmpty()) return EnqueueResult.NothingToDo
        if (pending.size + fresh.size > MAX_BATCH) return EnqueueResult.TooMany
        synchronized(posts) { fresh.forEach { posts[keyOf(site, it.id)] = it } }
        val keys = fresh.map { keyOf(site, it.id) }.toSet()
        _jobs.update { jobs -> jobs.filter { it.key !in keys } + fresh.map { Job(site, it.id, Status.QUEUED) } }
        startPump()
        return EnqueueResult.Added(fresh.size)
    }

    private fun update(key: String, transform: (Job) -> Job) =
        _jobs.update { jobs -> jobs.map { if (it.key == key) transform(it) else it } }

    private fun startPump() {
        if (pump?.isActive == true) return
        pump = scope.launch {
            var found = 0
            var total = 0
            while (true) {
                val job = _jobs.value.firstOrNull { it.status == Status.QUEUED } ?: break
                val post = synchronized(posts) { posts[job.key] }
                if (post == null) {
                    update(job.key) { it.copy(status = Status.FAILED, error = "post data missing") }
                    continue
                }
                total++
                update(job.key) { it.copy(status = Status.RUNNING) }
                _current.value = null
                val run = launch {
                    try {
                        val set = SourceRecovery.recover(job.site, post) { _current.value = it }
                        found++
                        update(job.key) { it.copy(status = Status.FOUND, grade = set.items.firstOrNull()?.grade, count = set.items.size) }
                    } catch (e: RecoveryFailed) {
                        val looked = e.attempts.count { it.stage != Attempt.Stage.SKIPPED && it.stage != Attempt.Stage.LIMIT }
                        update(job.key) { it.copy(status = Status.NOT_FOUND, count = looked) }
                    } catch (e: CancellationException) {
                        update(job.key) { it.copy(status = Status.CANCELLED) }
                        throw e
                    } catch (e: Exception) {
                        update(job.key) { it.copy(status = Status.FAILED, error = e.message?.lineSequence()?.firstOrNull()) }
                    }
                }
                running = run
                run.join()
                running = null
            }
            _current.value = null
            if (total > 0) notifyFinished(found, total)
        }
    }

    /** Stops the batch: queued posts are dropped, the running one is cancelled. */
    fun cancelAll() {
        _jobs.update { jobs -> jobs.map { if (it.status == Status.QUEUED) it.copy(status = Status.CANCELLED) else it } }
        running?.cancel()
    }

    /** Re-queues every post that ended without a copy (or was cancelled). */
    fun retryUnfinished() {
        _jobs.update { jobs ->
            jobs.map {
                if (it.status == Status.NOT_FOUND || it.status == Status.FAILED || it.status == Status.CANCELLED) {
                    it.copy(status = Status.QUEUED, error = null, count = 0)
                } else it
            }
        }
        startPump()
    }

    /** Hides the progress card; only once nothing is queued or running. */
    fun dismiss() {
        if (_jobs.value.any { it.active }) return
        synchronized(posts) { posts.clear() }
        _jobs.value = emptyList()
    }

    private fun appInForeground(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }

    /** A notification when a batch ends, only if the app isn't on screen (the card says it otherwise). */
    private fun notifyFinished(found: Int, total: Int) {
        if (appInForeground()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, appContext.getString(R.string.recovery_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val res = appContext.resources
        val text = if (found == 0) {
            res.getQuantityString(R.plurals.recovery_notify_none, total, total)
        } else {
            res.getQuantityString(R.plurals.recovery_notify_found, total, found, total)
        }
        val launch = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
        val tap = launch?.let { PendingIntent.getActivity(appContext, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_recovery_notification)
            .setContentTitle(appContext.getString(R.string.recovery_notify_title))
            .setContentText(text)
            .setAutoCancel(true)
            .apply { if (tap != null) setContentIntent(tap) }
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }
}
