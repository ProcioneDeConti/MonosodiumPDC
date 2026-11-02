package one.proci.e621.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import one.proci.e621.R
import one.proci.e621.data.model.Post
import one.proci.e621.data.recovery.SourceRecovery
import one.proci.e621.data.recovery.SourceRecovery.Grade
import one.proci.e621.data.recovery.SourceRecovery.Note
import one.proci.e621.data.recovery.SourceRecovery.Progress

/**
 * Shown in place of a deleted post's media. e621 drops the file but keeps the sources, so a
 * manual "Try to load from source" fetches the image from them ([SourceRecovery]) - strictly
 * opt-in, nothing touches a third-party site until tapped. Every image found is graded against
 * the original; the best is shown, with a strip to pick another when there are several.
 */
@Composable
fun DeletedPostRecovery(
    post: Post,
    onTap: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var candidates by remember(post.id) { mutableStateOf(SourceRecovery.cached(post.id)) }
    var selected by remember(post.id) { mutableIntStateOf(0) }
    var job by remember(post.id) { mutableStateOf<Job?>(null) }
    var status by remember(post.id) { mutableStateOf<Progress?>(null) }
    var failures by remember(post.id) { mutableStateOf<List<String>>(emptyList()) }
    var error by remember(post.id) { mutableStateOf<SourceRecovery.RecoveryFailed?>(null) }
    val hasWebSource = remember(post.id) { SourceRecovery.webSources(post).isNotEmpty() }
    val loading = job?.isActive == true

    val found = candidates
    if (found != null && found.isNotEmpty()) {
        val current = found[selected.coerceIn(0, found.lastIndex)]
        Box(modifier = modifier) {
            ZoomableBox(modifier = Modifier.fillMaxSize(), onTap = onTap, onDismiss = onDismiss) {
                val context = LocalPlatformContext.current
                val request = remember(current) {
                    ImageRequest.Builder(context).data(current.bytes).memoryCacheKey("recovered:${post.id}:${current.imageUrl}").build()
                }
                AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GradeBadge(current, modifier = Modifier.padding(horizontal = 12.dp))
                if (found.size > 1) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(found) { i, c ->
                            CandidateThumb(c, selected = i == selected, onClick = { selected = i })
                        }
                    }
                }
            }
        }
        return
    }

    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Original's shape: tall images fill the height first, wide ones the width, and the box
        // only takes what's left after the text and button below.
        val ratio = if (post.file.width > 0 && post.file.height > 0) post.file.width.toFloat() / post.file.height else 1f
        DeletedGhost(
            post = post,
            modifier = Modifier
                .weight(1f, fill = false)
                .aspectRatio(ratio.coerceIn(0.3f, 3f), matchHeightConstraintsFirst = ratio < 1f),
        )
        Text(stringResource(R.string.deleted_post_message), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyLarge)
        if (hasWebSource) {
            Button(
                enabled = !loading,
                onClick = {
                    error = null
                    failures = emptyList()
                    job = scope.launch {
                        try {
                            val result = SourceRecovery.recover(post) { p ->
                                status = p
                                if (p is Progress.Failed) failures = failures + "${p.host}: ${p.reason}"
                            }
                            selected = 0
                            candidates = result
                        } catch (e: SourceRecovery.RecoveryFailed) {
                            error = e
                        } finally {
                            status = null
                        }
                    }
                },
            ) {
                if (loading) {
                    RainbowSpinner(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Text(
                    stringResource(if (error != null) R.string.recover_retry else R.string.recover_button),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (!loading && error == null) {
                Text(
                    stringResource(R.string.recover_explainer),
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Text(stringResource(R.string.recover_no_sources), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
        }
        status?.let { p ->
            Text(progressText(p), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
        if (loading) {
            failures.takeLast(4).forEach {
                Text(it, color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }
        error?.let { e ->
            Text(
                stringResource(if (e.noSources) R.string.recover_no_sources else R.string.recover_none),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            e.details.take(6).forEach {
                Text(it, color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 3)
            }
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
private fun progressText(p: Progress): String = when (p) {
    is Progress.Fetching -> stringResource(R.string.recover_progress_fetching, p.host, p.index, p.total)
    is Progress.Downloading -> if (p.total > 1) {
        stringResource(R.string.recover_progress_downloading_n, p.host, p.index, p.total)
    } else {
        stringResource(R.string.recover_progress_downloading, p.host)
    }
    is Progress.Comparing -> stringResource(R.string.recover_progress_comparing, p.host)
    is Progress.Failed -> "${p.host}: ${p.reason}"
    Progress.Archive -> stringResource(R.string.recover_progress_archive)
}

private fun gradeColor(grade: Grade) = when (grade) {
    Grade.EXACT -> Color(0xFF6EE7A0)
    Grade.SAME_DIMENSIONS -> Color(0xFF8CC8FF)
    Grade.RESCALED -> Color(0xFFFFD27A)
    Grade.MISMATCH -> Color(0xFFFF8A80)
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
private fun noteText(n: Note): String = when (n) {
    is Note.Format -> stringResource(R.string.recover_note_format, n.got, n.want)
    is Note.Dimensions -> stringResource(R.string.recover_note_dims, n.width, n.height, n.wantWidth, n.wantHeight)
    Note.SmallerFile -> stringResource(R.string.recover_note_smaller)
    Note.LargerFile -> stringResource(R.string.recover_note_larger)
    Note.ShapeDiffers -> stringResource(R.string.recover_note_shape)
}

@Composable
private fun GradeBadge(image: SourceRecovery.RecoveredImage, modifier: Modifier = Modifier) {
    val host = remember(image) { android.net.Uri.parse(image.sourceUrl).host?.removePrefix("www.") ?: image.sourceUrl }
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
            Text(
                image.notes.map { noteText(it) }.joinToString("; "),
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CandidateThumb(image: SourceRecovery.RecoveredImage, selected: Boolean, onClick: () -> Unit) {
    val context = LocalPlatformContext.current
    val request = remember(image) { ImageRequest.Builder(context).data(image.bytes).size(256).build() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (selected) Modifier.border(2.dp, gradeColor(image.grade), RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(64.dp))
        Text(gradeTitle(image.grade), color = gradeColor(image.grade), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text("${image.width}×${image.height}", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
    }
}
