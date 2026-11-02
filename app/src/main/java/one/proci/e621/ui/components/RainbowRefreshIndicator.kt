package one.proci.e621.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Distinct flag-style ROYGBIV stripes - shared with the easter egg's [one.proci.e621.ui.screens.grid.PostGridScreen] background. */
val RainbowStripeColors = listOf(
    Color(0xFFFF0000), Color(0xFFFF7F00), Color(0xFFFFFF00), Color(0xFF00FF00),
    Color(0xFF00BFFF), Color(0xFF4B0082), Color(0xFF8F00FF),
)

/**
 * Replaces Material3's default pull-to-refresh indicator, whose visibility is coupled to the drag
 * gesture's own settle animation and can occasionally stay on screen if [isRefreshing] flips back
 * to false before that animation finishes (most noticeable on very fast responses). This one is
 * driven directly by [isRefreshing], so it always hides the instant a refresh completes - while
 * still popping down from above and spinning like the platform's classic arrow indicator, just
 * cycling through solid rainbow colors (a flat color at any instant, not blended) rather than one
 * fixed tint.
 */
@Composable
fun RainbowRefreshIndicator(isRefreshing: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = isRefreshing,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
        ) {
            RainbowSpinner(
                strokeWidth = 3.dp,
                modifier = Modifier.padding(8.dp).size(24.dp),
            )
        }
    }
}

/**
 * The app's standard indeterminate spinner: a Material-style arc that spins while its length
 * breathes in and out, drawn as consecutive ROYGBIV bands (red at the tail, violet at the head)
 * using the same [RainbowStripeColors] as [RainbowRefreshIndicator].
 */
@Composable
fun RainbowSpinner(
    modifier: Modifier = Modifier,
    strokeWidth: androidx.compose.ui.unit.Dp = androidx.compose.material3.ProgressIndicatorDefaults.CircularStrokeWidth,
) {
    val transition = rememberInfiniteTransition(label = "rainbowSpinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing)),
        label = "rainbowSpinnerRotation",
    )
    val sweep by transition.animateFloat(
        initialValue = 90f,
        targetValue = 290f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "rainbowSpinnerSweep",
    )
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .defaultMinSize(40.dp, 40.dp)
            .progressSemantics(),
    ) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2
        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
        val n = RainbowStripeColors.size
        val seg = sweep / n
        RainbowStripeColors.forEachIndexed { i, color ->
            drawArc(
                color = color,
                // Each band overlaps the next by a hair so no seams show between them.
                startAngle = rotation - 90f + i * seg,
                sweepAngle = seg + if (i < n - 1) 0.8f else 0f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Butt),
            )
        }
    }
}


/**
 * The app's loading stripe. Indeterminate (no [progress]): ROYGBIV bands, each [bandWidth] wide,
 * repeating across the width and sliding steadily right. Determinate: the seven bands span the
 * full width as a fixed red-to-violet meter, revealed up to the current fraction over a faint track.
 */
@Composable
fun RainbowLinearIndicator(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    bandWidth: androidx.compose.ui.unit.Dp = 24.dp,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
) {
    val n = RainbowStripeColors.size
    if (progress != null) {
        androidx.compose.foundation.Canvas(modifier = modifier.progressSemantics(progress().coerceIn(0f, 1f))) {
            drawRect(trackColor)
            val filled = size.width * progress().coerceIn(0f, 1f)
            val band = size.width / n
            for (i in 0 until n) {
                val left = i * band
                if (left >= filled) break
                drawRect(
                    color = RainbowStripeColors[i],
                    topLeft = androidx.compose.ui.geometry.Offset(left, 0f),
                    size = androidx.compose.ui.geometry.Size(minOf(band + 0.5f, filled - left), size.height),
                )
            }
        }
        return
    }
    val transition = rememberInfiniteTransition(label = "rainbowLinear")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing)),
        label = "rainbowLinearPhase",
    )
    androidx.compose.foundation.Canvas(modifier = modifier.progressSemantics()) {
        val band = bandWidth.toPx()
        val period = band * n
        // Start one full period off-screen so the left edge is always covered while sliding.
        var x = -period + phase * period
        var i = 0
        while (x < size.width) {
            drawRect(
                color = RainbowStripeColors[i % n],
                topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                size = androidx.compose.ui.geometry.Size(band + 0.5f, size.height),
            )
            x += band
            i++
        }
    }
}
