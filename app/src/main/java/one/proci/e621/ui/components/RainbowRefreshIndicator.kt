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
import androidx.compose.foundation.layout.padding
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
        val transition = rememberInfiniteTransition(label = "rainbowRefresh")
        val rotation by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing)),
            label = "rainbowRefreshRotation",
        )
        val n = RainbowStripeColors.size
        val cycle by transition.animateFloat(
            initialValue = 0f,
            targetValue = n.toFloat(),
            animationSpec = infiniteRepeatable(animation = tween(n * 350, easing = LinearEasing)),
            label = "rainbowRefreshCycle",
        )
        val color = RainbowStripeColors[cycle.toInt().coerceIn(0, n - 1)]

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .padding(8.dp)
                    .size(24.dp)
                    .graphicsLayer { rotationZ = rotation },
            )
        }
    }
}
