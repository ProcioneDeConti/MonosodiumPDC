package one.proci.e621.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/**
 * Pinch-to-zoom + pan + double-tap-to-zoom wrapper for image/APNG content in the media viewer.
 *
 * Deliberately does NOT use [androidx.compose.foundation.gestures.detectTransformGestures]:
 * that consumes every single-finger drag unconditionally, which starves the enclosing
 * HorizontalPager of the drag events it needs to swipe to the next/previous post. Instead this
 * only consumes pan/zoom when there are 2+ fingers down or the image is already zoomed in;
 * a one-finger drag while unzoomed is left untouched so the pager handles the swipe.
 *
 * [onTap] and the double-tap-to-zoom handler are combined into a single detectTapGestures call
 * deliberately: a separate detectTapGestures(onDoubleTap = ...) with no onTap still consumes
 * every single tap while it waits to see if a second tap follows, which silently swallows taps
 * meant for an outer clickable.
 */
@Composable
fun ZoomableBox(modifier: Modifier = Modifier, onTap: (() -> Unit)? = null, content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val currentOnTap by rememberUpdatedState(onTap)

    fun clamp(newScale: Float, rawOffset: Offset): Offset {
        val maxX = (containerSize.width * (newScale - 1) / 2f).coerceAtLeast(0f)
        val maxY = (containerSize.height * (newScale - 1) / 2f).coerceAtLeast(0f)
        return Offset(rawOffset.x.coerceIn(-maxX, maxX), rawOffset.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val isMultitouch = event.changes.size > 1
                        if (isMultitouch || scale > 1f) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val newScale = (scale * zoomChange).coerceIn(1f, 6f)
                            offset = if (newScale <= 1f) Offset.Zero else clamp(newScale, offset + panChange * newScale)
                            scale = newScale
                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnTap?.invoke() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 3f
                        }
                    },
                )
            },
        content = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            ) {
                content()
            }
        },
    )
}
