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
    modifier: Modifier = Modifier,
) {
    val url = post.playableUrl ?: post.preview.url

    when {
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
                    NetworkImage(
                        model = url,
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
