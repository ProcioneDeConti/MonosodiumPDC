package one.proci.e621.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import one.proci.e621.data.util.VideoPlaybackSpeeds

/**
 * Overlay controls layered on top of the raw ExoPlayer surface in [VideoPlayer]: a bottom bar
 * (seek/time, play-pause, loop, speed, mute), a buffering spinner, and a large centered play
 * affordance for the "autoplay is off, tap to start" state.
 */
@Composable
fun VideoPlayerControls(
    controlsVisible: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    showPlayAffordance: Boolean,
    loopEnabled: Boolean,
    speed: Float,
    muted: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPauseClick: () -> Unit,
    onLoopClick: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onMuteClick: () -> Unit,
    onSeekChange: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (isBuffering && !showPlayAffordance) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).size(48.dp),
            )
        }

        if (showPlayAffordance) {
            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && !showPlayAffordance,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPlayPauseClick) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                        )
                    }
                    Text(formatVideoTime(positionMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = positionMs.toFloat().coerceIn(0f, durationMs.coerceAtLeast(1L).toFloat()),
                        onValueChange = { onSeekChange(it.toLong()) },
                        onValueChangeFinished = onSeekFinished,
                        valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    )
                    Text(formatVideoTime(durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onLoopClick) {
                        Icon(
                            Icons.Filled.Loop,
                            contentDescription = "Loop",
                            tint = if (loopEnabled) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                    SpeedButton(speed = speed, onSpeedSelected = onSpeedSelected)
                    IconButton(onClick = onMuteClick) {
                        Icon(
                            if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (muted) "Unmute" else "Mute",
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedButton(speed: Float, onSpeedSelected: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.width(64.dp)) {
            Text(formatVideoSpeed(speed), color = Color.White)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (index in 0..VideoPlaybackSpeeds.lastIndex) {
                val option = VideoPlaybackSpeeds.speedForIndex(index)
                DropdownMenuItem(
                    text = { Text(formatVideoSpeed(option)) },
                    onClick = {
                        onSpeedSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun formatVideoTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatVideoSpeed(speed: Float): String =
    if (speed == speed.toLong().toFloat()) "${speed.toLong()}x" else "${speed}x"
