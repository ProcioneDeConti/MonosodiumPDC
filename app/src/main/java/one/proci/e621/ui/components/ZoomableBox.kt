package one.proci.e621.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Pinch-to-zoom + pan + double-tap-to-zoom + swipe-down-to-dismiss wrapper for image/APNG content
 * in the media viewer.
 *
 * Deliberately does NOT use [androidx.compose.foundation.gestures.detectTransformGestures]:
 * that consumes every single-finger drag unconditionally, which starves the enclosing
 * HorizontalPager of the drag events it needs to swipe to the next/previous post. Instead this
 * only consumes pan/zoom when there are 2+ fingers down or the image is already zoomed in;
 * a one-finger drag while unzoomed is left mostly untouched so the pager handles the swipe -
 * "mostly" because a downward-dominant one-finger drag while unzoomed is claimed for
 * [onDismiss] instead (see below), while a horizontal or upward one still falls through to the
 * pager exactly as before.
 *
 * [onTap] and the double-tap-to-zoom handler are combined into a single detectTapGestures call
 * deliberately: a separate detectTapGestures(onDoubleTap = ...) with no onTap still consumes
 * every single tap while it waits to see if a second tap follows, which silently swallows taps
 * meant for an outer clickable.
 *
 * Swipe-down-to-dismiss is hand-rolled inside the *same* gesture loop as the pinch/pan handling
 * (rather than a separate detectVerticalDragGestures pointerInput block) so the two can never
 * race for the same down event: detectVerticalDragGestures would unconditionally claim any
 * vertical-dominant single-finger drag the moment it crosses touch slop, including while already
 * zoomed in - which would break panning a zoomed image vertically. Folding dismiss-drag into the
 * existing "single finger, unzoomed" branch keeps that case exclusive to one or the other.
 */
@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val dismissOffsetY = remember { Animatable(0f) }
    val viewConfiguration = LocalViewConfiguration.current
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 120.dp.toPx() }
    // awaitEachGesture's block runs in a restricted suspend scope that can only call
    // AwaitPointerEventScope's own suspend members (like awaitPointerEvent) - Animatable's
    // snapTo/animateTo need a normal coroutine, hence launching them via this scope instead of
    // calling them directly.
    val scope = rememberCoroutineScope()

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
                    var dismissDragging = false
                    var directionResolved = false
                    var pendingDrag = Offset.Zero
                    val touchSlop = viewConfiguration.touchSlop
                    do {
                        val event = awaitPointerEvent()
                        val isMultitouch = event.changes.size > 1
                        if (isMultitouch || scale > 1f) {
                            if (dismissDragging) {
                                // A second finger joined mid-drag - abandon the dismiss attempt
                                // and let pinch/pan take over for the rest of this gesture.
                                dismissDragging = false
                                scope.launch { dismissOffsetY.snapTo(0f) }
                            }
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val newScale = (scale * zoomChange).coerceIn(1f, 6f)
                            offset = if (newScale <= 1f) Offset.Zero else clamp(newScale, offset + panChange * newScale)
                            scale = newScale
                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                        } else if (currentOnDismiss != null && event.changes.isNotEmpty()) {
                            val change = event.changes[0]
                            if (change.pressed) {
                                val drag = change.positionChange()
                                if (!directionResolved) {
                                    pendingDrag += drag
                                    if (pendingDrag.getDistance() > touchSlop) {
                                        directionResolved = true
                                        dismissDragging = pendingDrag.y > 0f && abs(pendingDrag.y) > abs(pendingDrag.x)
                                    }
                                } else if (dismissDragging) {
                                    change.consume()
                                    val newValue = (dismissOffsetY.value + drag.y).coerceAtLeast(0f)
                                    scope.launch { dismissOffsetY.snapTo(newValue) }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (dismissDragging) {
                        if (dismissOffsetY.value > dismissThresholdPx) {
                            currentOnDismiss?.invoke()
                        } else {
                            scope.launch { dismissOffsetY.animateTo(0f, spring()) }
                        }
                    }
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
            val dismissProgress = (dismissOffsetY.value / dismissThresholdPx).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale * (1f - dismissProgress * 0.2f),
                        scaleY = scale * (1f - dismissProgress * 0.2f),
                        translationX = offset.x,
                        translationY = offset.y + dismissOffsetY.value,
                        alpha = 1f - dismissProgress * 0.5f,
                    ),
            ) {
                content()
            }
        },
    )
}
