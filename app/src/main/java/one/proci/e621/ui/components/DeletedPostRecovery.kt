package one.proci.e621.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.proci.e621.E621Application
import one.proci.e621.R
import one.proci.e621.data.download.MediaDownloader
import one.proci.e621.data.model.Post
import one.proci.e621.data.recovery.Grade
import one.proci.e621.data.recovery.Note
import one.proci.e621.data.recovery.RecoveredImage
import one.proci.e621.data.recovery.RecoveredSet
import one.proci.e621.data.recovery.RecoveredStore
import one.proci.e621.data.recovery.RecoveryFailed
import one.proci.e621.data.recovery.SourceRecovery
import one.proci.e621.data.recovery.SourceRecovery.Progress

/**
 * Shown in place of a deleted post's media (ported from the desktop's DeletedPostRecovery). e621
 * drops the file but keeps the sources and the md5, so a manual button searches for a copy
 * ([SourceRecovery]) - strictly opt-in, nothing touches a third-party site until tapped. Results
 * are cached on disk ([RecoveredStore]), so a post recovered before opens straight from the cache
 * and shows in the grid. With several files found, a strip lists them (best first) and the pick is
 * remembered. Leaving the post mid-search cancels it (the search runs in this composable's scope).
 */
@Composable
fun DeletedPostRecovery(
    post: Post,
    isActive: Boolean,
    videoLoopEnabled: Boolean,
    videoPlaybackSpeed: Float,
    videoAutoplayEnabled: Boolean,
    onTap: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val site by RecoveredStore.currentSite.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var set by remember(post.id, site) { mutableStateOf<RecoveredSet?>(null) }
    var job by remember(post.id) { mutableStateOf<Job?>(null) }
    var status by remember(post.id) { mutableStateOf<Progress?>(null) }
    var log by remember(post.id) { mutableStateOf<List<Progress>>(emptyList()) }
    // A finished search that found nothing (attempts + whether the post had no sources at all).
    var failure by remember(post.id) { mutableStateOf<RecoveryFailed?>(null) }
    var showReport by remember(post.id) { mutableStateOf(false) }
    var saveState by remember(post.id) { mutableStateOf(SaveState.IDLE) }
    val loading = job?.isActive == true
    val hasWebSource = remember(post.id) { SourceRecovery.webSources(post).isNotEmpty() }
    val canSearch = remember(post.id) { SourceRecovery.canSearch(post) }

    // A cached recovery opens immediately; a cached miss shows its report again.
    LaunchedEffect(post.id, site) {
        val cached = RecoveredStore.load(site, post.id) ?: return@LaunchedEffect
        set = cached
        if (cached.items.isEmpty() && cached.report != null) failure = RecoveryFailed(false, cached.report.attempts)
    }
    LaunchedEffect(saveState) {
        if (saveState == SaveState.DONE || saveState == SaveState.FAILED) {
            delay(1600)
            saveState = SaveState.IDLE
        }
    }

    fun recover() {
        failure = null
        log = emptyList()
        showReport = false
        job = scope.launch {
            try {
                set = SourceRecovery.recover(site, post) { p ->
                    status = p
                    if (p is Progress.Scored || p is Progress.Failed) log = log + p
                }
            } catch (e: RecoveryFailed) {
                failure = e
                if (set?.items.isNullOrEmpty()) set = RecoveredSet(notFoundAt = System.currentTimeMillis() / 1000)
            } finally {
                status = null
            }
        }
    }

    val candidates = set?.items.orEmpty()
    val current = set?.picked
    if (current != null) {
        val file = RecoveredStore.fileOf(site, post.id, current)
        Box(modifier = modifier) {
            if (current.isVideo) {
                VideoPlayer(
                    url = Uri.fromFile(file).toString(),
                    isActive = isActive,
                    defaultLoopEnabled = videoLoopEnabled,
                    defaultPlaybackSpeed = videoPlaybackSpeed,
                    autoplayEnabled = videoAutoplayEnabled,
                    onTap = onTap,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ZoomableBox(modifier = Modifier.fillMaxSize(), onTap = onTap, onDismiss = onDismiss) {
                    val platformContext = LocalPlatformContext.current
                    val request = remember(file) { ImageRequest.Builder(platformContext).data(file).memoryCacheKey("recovered:${file.path}").build() }
                    AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }
            if (showReport && !loading) {
                RecoveryReportCard(
                    set = set,
                    failed = null,
                    onClose = { showReport = false },
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
            }
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GradeBadge(current, index = candidates.indexOf(current), total = candidates.size, modifier = Modifier.padding(horizontal = 12.dp))
                if (loading) ProgressPanel(status, log, modifier = Modifier.padding(horizontal = 12.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (!loading) {
                        OverlayButton(Icons.AutoMirrored.Filled.ListAlt, stringResource(R.string.recover_report), selected = showReport) { showReport = !showReport }
                    }
                    if (loading) {
                        OverlayButton(null, stringResource(R.string.recover_cancel), spinner = true) { job?.cancel() }
                    } else if (canSearch) {
                        OverlayButton(Icons.Filled.Refresh, stringResource(R.string.recover_search_again)) { recover() }
                    }
                    OverlayButton(
                        Icons.Filled.Download,
                        stringResource(
                            when (saveState) {
                                SaveState.DONE -> R.string.recover_saved
                                SaveState.FAILED -> R.string.recover_save_failed
                                else -> R.string.recover_download
                            },
                        ),
                        tint = when (saveState) {
                            SaveState.DONE -> Color(0xFF86EFAC)
                            SaveState.FAILED -> Color(0xFFFCA5A5)
                            else -> Color.White
                        },
                        enabled = saveState != SaveState.SAVING,
                    ) {
                        saveState = SaveState.SAVING
                        scope.launch {
                            val app = context.applicationContext as E621Application
                            val result = MediaDownloader(context).saveFile(
                                file,
                                "${site.name.lowercase()}_${post.id}_recovered.${current.ext}",
                                current.mime,
                                app.userPreferences.settingsState.value.downloadLocationUri,
                            )
                            saveState = if (result.isSuccess) SaveState.DONE else SaveState.FAILED
                        }
                    }
                    OverlayButton(Icons.Filled.Delete, stringResource(R.string.recover_remove), enabled = !loading) {
                        scope.launch {
                            RecoveredStore.forget(site, post.id)
                            set = null
                            failure = null
                            showReport = false
                        }
                    }
                }
                if (candidates.size > 1) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(candidates, key = { _, c -> c.file }) { _, c ->
                            CandidateThumb(RecoveredStore.fileOf(site, post.id, c), c, selected = c == current) {
                                set = set?.copy(selected = c.file)
                                scope.launch { RecoveredStore.select(site, post.id, c.file) }
                            }
                        }
                    }
                } else {
                    androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                }
            }
        }
        return
    }

    val notFoundAt = set?.notFoundAt
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (failure == null && !loading) {
            // Original's shape: e621 keeps the dimensions.
            val ratio = if (post.file.width > 0 && post.file.height > 0) post.file.width.toFloat() / post.file.height else 1f
            DeletedGhost(
                post = post,
                modifier = Modifier
                    .fillMaxWidth(if (ratio < 1f) 0.6f else 0.9f)
                    .aspectRatio(ratio.coerceIn(0.5f, 2.5f)),
            )
        }
        Text(stringResource(R.string.deleted_post_message), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyLarge)
        if (notFoundAt != null && !loading && failure == null) {
            Text(
                stringResource(R.string.recover_searched_before, formatSearchedOn(notFoundAt)),
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
        if (canSearch) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !loading, onClick = ::recover) {
                    if (loading) {
                        RainbowSpinner(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        stringResource(
                            when {
                                loading -> R.string.recover_recovering
                                notFoundAt != null || failure != null -> R.string.recover_search_again
                                hasWebSource -> R.string.recover_button
                                else -> R.string.recover_button_md5
                            },
                        ),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (loading) {
                    OutlinedButton(onClick = { job?.cancel() }) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.recover_cancel), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (!loading && failure == null) {
                Text(
                    stringResource(if (hasWebSource) R.string.recover_explainer else R.string.recover_explainer_md5),
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Text(stringResource(R.string.recover_no_sources), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        if (loading) ProgressPanel(status, log)
        failure?.let { f ->
            if (!loading) RecoveryReportCard(set = null, failed = f.attempts, noSources = f.noSources)
        }
    }
}

private enum class SaveState { IDLE, SAVING, DONE, FAILED }

@Composable
private fun OverlayButton(
    icon: ImageVector?,
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    spinner: Boolean = false,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        if (spinner) {
            RainbowSpinner(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint.copy(alpha = if (enabled) 0.9f else 0.4f), modifier = Modifier.size(16.dp))
        }
        Text(
            label,
            color = tint.copy(alpha = if (enabled) 0.9f else 0.4f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun ProgressPanel(status: Progress?, log: List<Progress>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            status?.let { progressText(it) } ?: stringResource(R.string.recover_progress_starting),
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        log.takeLast(5).forEach {
            Text(progressText(it), color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

/** Artist tags that aren't really artists, left out of the ghost card's credit line. */
private val NON_ARTIST_TAGS = setOf("conditional_dnp", "sound_warning", "unknown_artist", "anonymous_artist", "epilepsy_warning", "avoid_posting")

/**
 * Placeholder for a deleted post's missing file: a dashed frame at the original's aspect ratio
 * (e621 keeps the dimensions) with a faded icon. The full version, in the viewer, also credits the
 * artist and gives the original's size and format; [compact] is the grid-tile version, icon only.
 */
@Composable
fun DeletedGhost(post: Post, modifier: Modifier = Modifier, compact: Boolean = false, color: Color = Color.White) {
    val dash = remember { androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(18f, 12f)) }
    Column(
        modifier = modifier.then(
            if (compact) Modifier
            else Modifier.drawBehind {
                drawRoundRect(
                    color = color.copy(alpha = 0.3f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), pathEffect = dash),
                )
            },
        ).padding(if (compact) 4.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.HideImage,
            contentDescription = stringResource(R.string.status_deleted),
            tint = color.copy(alpha = if (compact) 0.45f else 0.35f),
            modifier = Modifier.size(if (compact) 28.dp else 64.dp),
        )
        if (compact) return@Column
        val artists = remember(post.id) { post.tags.artist.filter { it !in NON_ARTIST_TAGS } }
        if (artists.isNotEmpty()) {
            Text(
                stringResource(R.string.deleted_ghost_by, artists.joinToString(", ") { it.replace('_', ' ') }),
                color = color.copy(alpha = 0.7f),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
        val details = buildList {
            if (post.file.width > 0 && post.file.height > 0) add("${post.file.width}×${post.file.height}")
            if (post.file.ext.isNotBlank()) add(post.file.ext.uppercase())
        }
        if (details.isNotEmpty()) {
            Text(details.joinToString(" · "), color = color.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun progressText(p: Progress): String = when (p) {
    is Progress.Fetching -> stringResource(R.string.recover_progress_fetching, p.host, p.index, p.total)
    is Progress.Mirror -> stringResource(R.string.recover_progress_mirror, p.name)
    is Progress.Downloading -> if (p.total > 1) {
        stringResource(R.string.recover_progress_downloading_n, p.host, p.index, p.total)
    } else {
        stringResource(R.string.recover_progress_downloading, p.host)
    }
    is Progress.Comparing -> stringResource(R.string.recover_progress_comparing, p.host)
    is Progress.Scored -> if (p.grade == null) {
        stringResource(R.string.recover_progress_unusable, p.host)
    } else {
        val what = if (p.width > 0) "${p.width}×${p.height} ${p.ext}" else p.ext
        stringResource(R.string.recover_progress_scored, what, p.host, gradeShortText(p.grade).lowercase())
    }
    is Progress.Failed -> "${p.host}: ${p.reason}"
    is Progress.Archive -> stringResource(R.string.recover_progress_archive_host, p.host)
    is Progress.ArchiveCapture -> stringResource(R.string.recover_progress_capture, p.timestamp, p.host)
    Progress.TimeLimit -> stringResource(R.string.recover_progress_time_limit)
}

fun gradeColor(grade: Grade) = when (grade) {
    Grade.EXACT -> Color(0xFF86EFAC)
    Grade.SAME_DIMENSIONS -> Color(0xFFBEF264)
    Grade.RESCALED -> Color(0xFFFCD34D)
    Grade.MISMATCH -> Color(0xFFFCA5A5)
}

@Composable
private fun gradeTitle(grade: Grade) = stringResource(
    when (grade) {
        Grade.EXACT -> R.string.recover_grade_exact
        Grade.SAME_DIMENSIONS -> R.string.recover_grade_same
        Grade.RESCALED -> R.string.recover_grade_rescaled
        Grade.MISMATCH -> R.string.recover_grade_mismatch
    },
)

@Composable
private fun noteText(n: Note): String = when (n.kind) {
    Note.Kind.FORMAT -> stringResource(R.string.recover_note_format, n.got, n.want)
    Note.Kind.DIMENSIONS -> stringResource(R.string.recover_note_dims, n.width, n.height, n.wantWidth, n.wantHeight)
    Note.Kind.SMALLER_FILE -> stringResource(R.string.recover_note_smaller)
    Note.Kind.LARGER_FILE -> stringResource(R.string.recover_note_larger)
    Note.Kind.SHAPE_DIFFERS -> stringResource(R.string.recover_note_shape)
    Note.Kind.STILL_OF_VIDEO -> stringResource(R.string.recover_note_still)
    Note.Kind.VIDEO_UNCHECKED -> stringResource(R.string.recover_note_video)
}

@Composable
private fun GradeBadge(image: RecoveredImage, index: Int, total: Int, modifier: Modifier = Modifier) {
    val host = remember(image) { Uri.parse(image.sourceUrl).host?.removePrefix("www.") ?: image.sourceUrl }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(gradeTitle(image.grade), color = gradeColor(image.grade), style = MaterialTheme.typography.labelLarge)
        Text(
            stringResource(if (image.viaArchive) R.string.recover_from_archive else R.string.recover_from, host),
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodySmall,
        )
        if (image.notes.isNotEmpty()) {
            Text(image.notes.map { noteText(it) }.joinToString("; "), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        }
        if (total > 1) {
            Text(stringResource(R.string.recover_file_n_of, index + 1, total), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CandidateThumb(file: java.io.File, image: RecoveredImage, selected: Boolean, onClick: () -> Unit) {
    val context = LocalPlatformContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (selected) Modifier.background(Color.White.copy(alpha = 0.15f)).border(2.dp, gradeColor(image.grade), RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        if (image.isVideo) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Movie, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(32.dp))
            }
        } else {
            val request = remember(file) { ImageRequest.Builder(context).data(file).size(256).build() }
            AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(64.dp))
        }
        Text(gradeShortText(image.grade), color = gradeColor(image.grade), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(
            if (image.isVideo) image.ext else "${image.width}×${image.height}",
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
