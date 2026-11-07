package one.proci.e621.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import one.proci.e621.R
import one.proci.e621.data.recovery.RecoveryQueue
import one.proci.e621.data.recovery.RecoveryQueue.Status
import one.proci.e621.ui.theme.CardShape

/**
 * Floating progress card for a bulk un-delete ([RecoveryQueue]); shown over every screen while a
 * batch is queued, running, or finished but not yet dismissed. Collapsible to its header.
 */
@Composable
fun RecoveryQueuePanel(modifier: Modifier = Modifier) {
    val jobs by RecoveryQueue.jobs.collectAsState()
    val current by RecoveryQueue.current.collectAsState()
    var expanded by remember { mutableStateOf(true) }

    AnimatedVisibility(
        visible = jobs.isNotEmpty(),
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        val active = jobs.any { it.active }
        val finished = jobs.count { !it.active }
        val found = jobs.count { it.status == Status.FOUND }
        val unfinished = jobs.count { it.status == Status.NOT_FOUND || it.status == Status.FAILED || it.status == Status.CANCELLED }
        Surface(
            shape = CardShape,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (active) {
                        RainbowSpinner(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Recycling, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(
                            stringResource(if (active) R.string.recovery_queue_running else R.string.recovery_queue_done),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            if (active) {
                                stringResource(R.string.recovery_queue_progress, finished, jobs.size, found)
                            } else {
                                pluralStringResource(R.plurals.recovery_queue_found_of, jobs.size, found, jobs.size)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess, contentDescription = null)
                    }
                    if (!active) {
                        IconButton(onClick = RecoveryQueue::dismiss) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.recovery_queue_dismiss))
                        }
                    }
                }
                if (expanded) {
                    LazyColumn(modifier = Modifier.heightIn(max = 220.dp).padding(end = 10.dp)) {
                        items(jobs, key = { it.key }) { job ->
                            JobRow(job, if (job.status == Status.RUNNING) current?.let { progressText(it) } else null)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(end = 6.dp)) {
                        if (active) {
                            TextButton(onClick = RecoveryQueue::cancelAll) { Text(stringResource(R.string.recovery_queue_cancel_all)) }
                        } else if (unfinished > 0) {
                            TextButton(onClick = RecoveryQueue::retryUnfinished) { Text(stringResource(R.string.recovery_queue_retry)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobRow(job: RecoveryQueue.Job, message: String?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val (icon, tint) = when (job.status) {
        Status.QUEUED -> Icons.Filled.Schedule to muted.copy(alpha = 0.6f)
        Status.RUNNING -> null to muted
        Status.FOUND -> Icons.Filled.CheckCircle to (job.grade?.let(::gradeColor) ?: Color(0xFF86EFAC))
        Status.NOT_FOUND -> Icons.Filled.SearchOff to muted
        Status.FAILED -> Icons.Filled.Warning to MaterialTheme.colorScheme.error
        Status.CANCELLED -> Icons.Filled.RemoveCircleOutline to muted.copy(alpha = 0.6f)
    }
    val detail = when (job.status) {
        Status.QUEUED -> stringResource(R.string.recovery_job_queued)
        Status.RUNNING -> message ?: stringResource(R.string.recover_progress_starting)
        Status.FOUND -> job.grade?.let { gradeShortText(it) + " · " }.orEmpty() + pluralStringResource(R.plurals.recovery_job_files, job.count, job.count)
        Status.NOT_FOUND -> pluralStringResource(R.plurals.recovery_job_searched, job.count, job.count)
        Status.FAILED -> job.error ?: stringResource(R.string.recovery_job_failed)
        Status.CANCELLED -> stringResource(R.string.recovery_job_cancelled)
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        if (icon == null) {
            RainbowSpinner(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        }
        Text("#${job.postId}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, end = 8.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}
