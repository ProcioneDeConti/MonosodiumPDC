package one.proci.e621.ui.screens.downloads

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import one.proci.e621.R
import one.proci.e621.data.download.DownloadJob
import one.proci.e621.data.download.DownloadState
import one.proci.e621.ui.components.NetworkImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    jobs: List<DownloadJob>,
    onBack: () -> Unit,
    onRetry: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onClearFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (jobs.any { it.state == DownloadState.DONE || it.state == DownloadState.ERROR }) {
                        TextButton(onClick = onClearFinished) { Text(stringResource(R.string.downloads_clear_finished)) }
                    }
                },
            )
        },
    ) { padding ->
        if (jobs.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.downloads_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(jobs, key = { it.id }) { job ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        NetworkImage(
                            model = job.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp).padding(end = 10.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(job.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(
                                when (job.state) {
                                    DownloadState.QUEUED -> stringResource(R.string.downloads_state_queued)
                                    DownloadState.ACTIVE -> stringResource(R.string.downloads_state_active)
                                    DownloadState.DONE -> stringResource(R.string.downloads_state_done)
                                    DownloadState.ERROR -> job.error ?: stringResource(R.string.downloads_state_error)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (job.state == DownloadState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        when (job.state) {
                            DownloadState.ACTIVE -> CircularProgressIndicator(Modifier.size(20.dp))
                            DownloadState.ERROR -> IconButton(onClick = { onRetry(job.id) }) {
                                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.downloads_retry))
                            }
                            DownloadState.DONE -> {
                                job.resultUri?.let { uri ->
                                    IconButton(onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uri, job.mimeType)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                },
                                            )
                                        }
                                    }) {
                                        Icon(Icons.Filled.FolderOpen, contentDescription = stringResource(R.string.downloads_open))
                                    }
                                }
                            }
                            else -> Unit
                        }
                        if (job.state != DownloadState.ACTIVE) {
                            IconButton(onClick = { onRemove(job.id) }) {
                                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_clear))
                            }
                        }
                    }
                }
            }
        }
    }
}
