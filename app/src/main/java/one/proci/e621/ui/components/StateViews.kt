package one.proci.e621.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import one.proci.e621.R
import one.proci.e621.ui.theme.CardShape

/**
 * A soft highlight sweeping across the element, for placeholders that are still loading. Draws
 * over whatever background the element already has, so pair it with a surfaceVariant fill.
 */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerProgress",
    )
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    drawWithContent {
        drawContent()
        val width = size.width
        val x = progress * width
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, highlight, Color.Transparent),
                start = Offset(x - width * 0.6f, 0f),
                end = Offset(x, size.height),
            ),
        )
    }
}

/** Full-area loading state: a centered spinner, optionally with a line of text under it. */
@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String? = null) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RainbowSpinner(strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
        if (message != null) {
            Spacer(Modifier.height(14.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Full-area "nothing here" / "something went wrong" state: an icon in a tinted circle, a title,
 * an optional explanation, and an optional action button.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CardShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        if (message != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** The standard "request failed" state, with Retry. */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Filled.CloudOff,
        title = stringResource(R.string.state_error_title),
        message = message,
        actionLabel = stringResource(R.string.error_retry),
        onAction = onRetry,
        iconTint = MaterialTheme.colorScheme.error,
        modifier = modifier,
    )
}

/** The standard "search returned nothing" state. */
@Composable
fun NoResultsState(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Filled.SearchOff,
        title = stringResource(R.string.empty_results),
        message = stringResource(R.string.empty_results_hint),
        modifier = modifier,
    )
}

/**
 * Placeholder grid shown while a search's first page loads: shimmering tiles in a few varied
 * aspect ratios, laid out like the real masonry grid so the switch to content doesn't jump.
 */
@Composable
fun SkeletonGrid(minCellSize: Dp, modifier: Modifier = Modifier) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = minCellSize),
        contentPadding = PaddingValues(GridEdgePadding),
        horizontalArrangement = Arrangement.spacedBy(GridGap),
        verticalItemSpacing = GridGap,
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize(),
    ) {
        items(SKELETON_ASPECTS.size) { i ->
            Box(
                Modifier
                    .aspectRatio(SKELETON_ASPECTS[i])
                    .clip(CardShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .shimmer(),
            )
        }
    }
}

private val SKELETON_ASPECTS = listOf(0.75f, 1f, 1.33f, 0.8f, 1.2f, 0.7f, 1f, 1.5f, 0.85f, 1.1f, 0.75f, 1.3f, 0.9f, 1f, 0.7f, 1.25f, 0.8f, 1f)

/** Gap between grid cells, and the padding around the grid's edge. */
val GridGap = 4.dp
val GridEdgePadding = 6.dp
