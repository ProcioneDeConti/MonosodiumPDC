package one.proci.e621.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import one.proci.e621.R
import one.proci.e621.data.model.Post
import one.proci.e621.data.util.GridThumbnailSize
import one.proci.e621.ui.theme.RatingQuestionable
import kotlin.math.roundToInt

/**
 * The pull-to-refresh, infinite-scroll staggered grid shared by the search grid and the
 * favorites screen. Each caller supplies its own top bar/Scaffold around this.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostGridBody(
    posts: List<Post>,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onDismissError: () -> Unit,
    onPostClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    blacklistDisabled: Boolean = false,
    onEnableBlacklist: () -> Unit = {},
    thumbnailSizeDp: Int = GridThumbnailSize.DEFAULT_DP,
    onThumbnailSizeChange: (Int) -> Unit = {},
    emptyContent: @Composable () -> Unit = { DefaultEmptyState() },
) {
    val gridState = rememberLazyStaggeredGridState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Live-updated during a pinch/spread gesture (see the grid's pointerInput below); only
    // committed back via onThumbnailSizeChange once the gesture ends, so a fast pinch doesn't spam
    // DataStore writes. A single stable MutableState instance (not re-`remember`ed per value) so
    // the gesture handler - launched once via pointerInput(Unit) - keeps reading/writing the same
    // object across recompositions; it's instead re-synced from the persisted value via the
    // LaunchedEffect below (e.g. once the initial DataStore read completes).
    var liveSizeDp by remember { mutableFloatStateOf(thumbnailSizeDp.toFloat()) }
    LaunchedEffect(thumbnailSizeDp) { liveSizeDp = thumbnailSizeDp.toFloat() }
    val onThumbnailSizeChangeState = rememberUpdatedState(onThumbnailSizeChange)

    LaunchedEffect(error, posts.isEmpty()) {
        if (error != null && posts.isNotEmpty()) {
            snackbarHostState.showSnackbar(error)
            onDismissError()
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 9
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (blacklistDisabled) {
            BlacklistDisabledBanner(onEnableBlacklist)
        }
        Box(modifier = Modifier.weight(1f)) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    RainbowRefreshIndicator(
                        isRefreshing = isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                    )
                },
            ) {
                when {
                    error != null && posts.isEmpty() -> ErrorState(error, onRefresh)
                    // Loading with nothing to show yet - leave this blank rather than a second,
                    // competing spinner; the pop-down arrow indicator above already says "loading".
                    posts.isEmpty() && isRefreshing -> Box(Modifier.fillMaxSize())
                    posts.isEmpty() -> emptyContent()
                    else -> {
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Adaptive(minSize = liveSizeDp.dp),
                            state = gridState,
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalItemSpacing = 6.dp,
                            modifier = Modifier
                                .fillMaxSize()
                                // Only intercepts once a second pointer comes down, so ordinary
                                // one-finger scrolling is left entirely alone. Runs on the Initial
                                // (outside-in) pass and consumes from that point on, so the grid's
                                // own (Main-pass) scroll/drag handling sees the change already
                                // consumed and backs off for the rest of the gesture.
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        var zooming = false
                                        do {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            if (!zooming && event.changes.count { it.pressed } >= 2) {
                                                zooming = true
                                            }
                                            if (zooming) {
                                                val zoomChange = event.calculateZoom()
                                                if (zoomChange != 1f) {
                                                    liveSizeDp = (liveSizeDp * zoomChange).coerceIn(
                                                        GridThumbnailSize.MIN_DP.toFloat(),
                                                        GridThumbnailSize.MAX_DP.toFloat(),
                                                    )
                                                }
                                                event.changes.forEach { it.consume() }
                                            }
                                        } while (event.changes.any { it.pressed })
                                        if (zooming) onThumbnailSizeChangeState.value(liveSizeDp.roundToInt())
                                    }
                                },
                        ) {
                            itemsIndexed(posts, key = { _, post -> post.id }) { index, post ->
                                PostThumbnail(post = post, onClick = { onPostClick(index) })
                            }
                            if (isLoadingMore) {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun BlacklistDisabledBanner(onEnable: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RatingQuestionable.copy(alpha = 0.18f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.blacklist_disabled_banner),
            style = MaterialTheme.typography.bodyMedium,
            color = RatingQuestionable,
        )
        TextButton(onClick = onEnable) {
            Text(stringResource(R.string.blacklist_disabled_re_enable))
        }
    }
}

@Composable
fun DefaultEmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.empty_results),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.error_retry))
        }
    }
}
