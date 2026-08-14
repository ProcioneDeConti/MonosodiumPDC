package one.proci.e621.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import one.proci.e621.data.model.Post

/** Renders a post's full media: static images and GIFs via Coil, APNG manually, video via ExoPlayer. */
@Composable
fun MediaViewer(
    post: Post,
    isActive: Boolean,
    videoLoopEnabled: Boolean,
    videoPlaybackSpeed: Float,
    videoAutoplayEnabled: Boolean,
    onTap: () -> Unit,
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
        post.extension == "apng" -> ZoomableBox(modifier = modifier, onTap = onTap) {
            ApngImage(url = url, modifier = Modifier.fillMaxSize())
        }
        else -> ZoomableBox(modifier = modifier, onTap = onTap) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
