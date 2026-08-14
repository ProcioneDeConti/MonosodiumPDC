package one.proci.e621.ui.components

import android.net.Uri
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Temporary speed applied while the user is holding a long-press on the video surface. */
private const val BOOST_SPEED = 2.0f
private const val SEEK_STEP_MS = 10_000L

/**
 * Video post playback: raw ExoPlayer surface plus a custom Compose control overlay
 * ([VideoPlayerControls]) - the stock Media3 [PlayerView] controller only offers play/pause and a
 * seek bar, with no loop/speed/gesture support, so this drives the [Player] instance directly.
 *
 * [defaultLoopEnabled]/[defaultPlaybackSpeed]/[autoplayEnabled] seed this video's *session-only*
 * state (see the `remember(url)` below) - in-player taps on loop/speed only ever change local
 * state for the currently open video, never the persisted default. The default itself only
 * changes via Settings > Video.
 */
@Composable
fun VideoPlayer(
    url: String,
    isActive: Boolean,
    defaultLoopEnabled: Boolean,
    defaultPlaybackSpeed: Float,
    autoplayEnabled: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewConfiguration = LocalViewConfiguration.current
    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            volume = 1f
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    // Keyed on `url`, same as `exoPlayer` above, so a newly opened video always starts from the
    // caller's current defaults rather than carrying over whatever the previous video was left on.
    var loopEnabled by remember(url) { mutableStateOf(defaultLoopEnabled) }
    var speed by remember(url) { mutableFloatStateOf(defaultPlaybackSpeed) }
    var muted by remember(url) { mutableStateOf(false) }
    var boosting by remember(url) { mutableStateOf(false) }
    var hasStartedManually by remember(url) { mutableStateOf(false) }
    var controlsVisible by remember(url) { mutableStateOf(true) }
    var isPlaying by remember(url) { mutableStateOf(false) }
    var isBuffering by remember(url) { mutableStateOf(false) }
    var positionMs by remember(url) { mutableLongStateOf(0L) }
    var durationMs by remember(url) { mutableLongStateOf(0L) }
    var scrubPositionMs by remember(url) { mutableStateOf<Long?>(null) }
    var lastInteractionTick by remember(url) { mutableLongStateOf(0L) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(loopEnabled, exoPlayer) {
        exoPlayer.repeatMode = if (loopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    LaunchedEffect(speed, boosting, exoPlayer) {
        exoPlayer.setPlaybackSpeed(if (boosting) BOOST_SPEED else speed)
    }

    LaunchedEffect(muted, exoPlayer) {
        exoPlayer.volume = if (muted) 0f else 1f
    }

    val showPlayAffordance = !autoplayEnabled && !hasStartedManually
    LaunchedEffect(isActive, showPlayAffordance, exoPlayer) {
        if (isActive && !showPlayAffordance) exoPlayer.play() else exoPlayer.pause()
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) durationMs = exoPlayer.duration.coerceAtLeast(0L)
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // ExoPlayer has no continuous Compose-friendly position Flow, so poll it - only while the
    // controls are visible (no point updating a hidden seek bar) and nothing is being scrubbed.
    LaunchedEffect(controlsVisible, exoPlayer) {
        while (controlsVisible) {
            if (scrubPositionMs == null) positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            delay(200)
        }
    }

    // Auto-hide the control bar after a few seconds of inactivity, but only while playing - a
    // paused video keeps its controls up since there's nothing else on screen to look at.
    LaunchedEffect(controlsVisible, isPlaying, lastInteractionTick) {
        if (controlsVisible && isPlaying) {
            delay(3000)
            controlsVisible = false
        }
    }

    val currentOnTap by rememberUpdatedState(onTap)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                // Combined into one detectTapGestures call deliberately, matching ZoomableBox's
                // reasoning: a bare onDoubleTap with no onTap still swallows every single tap
                // while it waits to see if a second tap follows.
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                        lastInteractionTick++
                        currentOnTap()
                    },
                    onDoubleTap = { offset ->
                        val target = if (offset.x < containerSize.width / 2f) {
                            (exoPlayer.currentPosition - SEEK_STEP_MS).coerceAtLeast(0L)
                        } else {
                            (exoPlayer.currentPosition + SEEK_STEP_MS).coerceAtMost(exoPlayer.duration.coerceAtLeast(0L))
                        }
                        exoPlayer.seekTo(target)
                        positionMs = target
                        controlsVisible = true
                        lastInteractionTick++
                    },
                )
            }
            .pointerInput(Unit) {
                // Hand-rolled hold-to-boost gesture, built from the same low-level primitives
                // ZoomableBox uses (awaitFirstDown/awaitPointerEvent) rather than Compose
                // Foundation's detectTapGestures(onLongPress = ...), which reports one event with
                // no way to detect release - and release is needed here to revert the boost.
                //
                // requireUnconsumed = true (unlike ZoomableBox's pinch/pan block, which needs
                // false to reliably catch a first finger down for multi-touch): the control
                // overlay's buttons consume their own down events first, so this only ever fires
                // for a hold on bare video, never a long-press on a control button.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val releasedBeforeLongPress = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var stillDown = true
                        while (stillDown) {
                            val event = awaitPointerEvent()
                            stillDown = event.changes.any { it.id == down.id && it.pressed }
                        }
                    } != null
                    if (!releasedBeforeLongPress) {
                        boosting = true
                        lastInteractionTick++
                        var stillDown = true
                        while (stillDown) {
                            val event = awaitPointerEvent()
                            stillDown = event.changes.any { it.id == down.id && it.pressed }
                        }
                        boosting = false
                    }
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            update = { it.player = exoPlayer },
        )

        VideoPlayerControls(
            controlsVisible = controlsVisible,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            showPlayAffordance = showPlayAffordance,
            loopEnabled = loopEnabled,
            speed = speed,
            muted = muted,
            positionMs = scrubPositionMs ?: positionMs,
            durationMs = durationMs,
            onPlayPauseClick = {
                lastInteractionTick++
                when {
                    showPlayAffordance -> hasStartedManually = true
                    exoPlayer.isPlaying -> exoPlayer.pause()
                    else -> exoPlayer.play()
                }
            },
            onLoopClick = {
                loopEnabled = !loopEnabled
                lastInteractionTick++
            },
            onSpeedSelected = {
                speed = it
                lastInteractionTick++
            },
            onMuteClick = {
                muted = !muted
                lastInteractionTick++
            },
            onSeekChange = { ms ->
                scrubPositionMs = ms
                lastInteractionTick++
            },
            onSeekFinished = {
                scrubPositionMs?.let { exoPlayer.seekTo(it) }
                scrubPositionMs = null
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
