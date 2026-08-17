package one.proci.e621.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

/**
 * Thin wrapper around Coil's [AsyncImage] that overlays a small, unobtrusive indeterminate bar
 * along the top edge while a fetch is actually in flight (not for cache hits, which resolve
 * before the first frame is even drawn) - without this, a post on a slow connection is just blank
 * space until it either pops in or fails, which reads as broken rather than "still working on it."
 */
@Composable
fun NetworkImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    var loading by remember(model) { mutableStateOf(false) }
    Box(modifier = modifier) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = contentScale,
            onState = { state -> loading = state is AsyncImagePainter.State.Loading },
            modifier = Modifier.fillMaxSize(),
        )
        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(2.dp),
                color = Color.White.copy(alpha = 0.85f),
                trackColor = Color.White.copy(alpha = 0.15f),
            )
        }
    }
}
