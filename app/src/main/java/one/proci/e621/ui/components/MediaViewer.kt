package one.proci.e621.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Size
import one.proci.e621.data.model.Post
import one.proci.e621.data.model.PostNote
import one.proci.e621.data.repository.PostActionsRepository

/** Renders a post's full media: static images and GIFs via Coil, APNG manually, video via ExoPlayer. */
@Composable
fun MediaViewer(
    post: Post,
    isActive: Boolean,
    videoLoopEnabled: Boolean,
    videoPlaybackSpeed: Float,
    videoAutoplayEnabled: Boolean,
    onTap: () -> Unit,
    onDismiss: () -> Unit,
    postActionsRepository: PostActionsRepository,
    siteName: String = "",
    modifier: Modifier = Modifier,
) {
    val url = post.playableUrl ?: post.preview.url

    // On-device Dashboard: record a view the first time this page becomes the active one.
    LaunchedEffect(post.id, isActive) {
        if (isActive) postActionsRepository.recordPostView(post, siteName)
    }

    when {
        url == null && post.isDeleted -> DeletedPostRecovery(
            post = post,
            isActive = isActive,
            videoLoopEnabled = videoLoopEnabled,
            videoPlaybackSpeed = videoPlaybackSpeed,
            videoAutoplayEnabled = videoAutoplayEnabled,
            onTap = onTap,
            onDismiss = onDismiss,
            modifier = modifier,
        )
        url == null -> Unit
        post.isVideo -> VideoPlayer(
            url = url,
            isActive = isActive,
            defaultLoopEnabled = videoLoopEnabled,
            defaultPlaybackSpeed = videoPlaybackSpeed,
            autoplayEnabled = videoAutoplayEnabled,
            onTap = onTap,
            modifier = modifier,
        )
        post.extension == "apng" -> ZoomableBox(modifier = modifier, onTap = onTap, onDismiss = onDismiss) {
            ApngImage(url = url, modifier = Modifier.fillMaxSize())
        }
        else -> {
            // View-only translation-note overlay. Only fetched for posts that actually have notes.
            var notes by remember(post.id) { mutableStateOf<List<PostNote>>(emptyList()) }
            var selectedNoteId by remember(post.id) { mutableStateOf<Long?>(null) }
            LaunchedEffect(post.id) {
                notes = if (post.hasNotes) {
                    runCatching { postActionsRepository.fetchNotes(post.id) }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            }

            Box(modifier = modifier) {
                ZoomableBox(
                    modifier = Modifier.fillMaxSize(),
                    onTap = { if (selectedNoteId != null) selectedNoteId = null else onTap() },
                    onDismiss = onDismiss,
                    imageOverlay = if (notes.isNotEmpty()) {
                        { PostNotesOverlay(post, notes, selectedNoteId) { selectedNoteId = it } }
                    } else {
                        null
                    },
                ) {
                    val context = LocalPlatformContext.current
                    val request = remember(url, post.file.width, post.file.height) {
                        ImageRequest.Builder(context)
                            .data(url)
                            .size(fullResolutionSize(post.file.width, post.file.height))
                            .build()
                    }
                    NetworkImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                val selected = notes.firstOrNull { it.id == selectedNoteId }
                if (selected != null) {
                    NoteBodyBubble(
                        note = selected,
                        index = notes.indexOf(selected),
                        onDismiss = { selectedNoteId = null },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

/**
 * Above this many pixels the decode is scaled down: an ARGB bitmap costs 4 bytes per pixel and
 * Android refuses to draw bitmaps over ~100MB, so 16MP (~64MB) leaves headroom while still
 * covering most originals at full size (a 4000x4000 image fits exactly).
 */
private const val MAX_VIEWER_PIXELS = 16_000_000L

/**
 * Decode size for the full-screen viewer. Coil normally decodes to the on-screen box, which
 * makes zooming in (up to 6x) magnify a screen-sized copy; asking for the file's real size keeps
 * zoomed detail sharp. Unknown dimensions fall back to Coil's own original-size handling.
 */
internal fun fullResolutionSize(width: Int, height: Int): Size {
    if (width <= 0 || height <= 0) return Size.ORIGINAL
    val pixels = width.toLong() * height
    if (pixels <= MAX_VIEWER_PIXELS) return Size(width, height)
    val factor = kotlin.math.sqrt(MAX_VIEWER_PIXELS.toDouble() / pixels)
    return Size((width * factor).toInt().coerceAtLeast(1), (height * factor).toInt().coerceAtLeast(1))
}
