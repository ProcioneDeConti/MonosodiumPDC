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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.State
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    /** Ids in [posts] that would normally be hidden - bordered with a caution stripe while [blacklistDisabled] is showing them anyway. */
    blacklistedIds: Set<Long> = emptySet(),
    onEnableBlacklist: () -> Unit = {},
    /** Already-opened posts to draw dimmed (seen-post tracking). */
    seenIds: Set<Long> = emptySet(),
    thumbnailSizeDp: Int = GridThumbnailSize.DEFAULT_DP,
    onThumbnailSizeChange: (Int) -> Unit = {},
    /** Long-press quick actions from the grid; null means that action isn't offered. */
    onQuickFavorite: ((Post) -> Unit)? = null,
    onQuickUpvote: ((Post) -> Unit)? = null,
    onQuickDownload: ((Post) -> Unit)? = null,
    /**
     * Multi-select; null means the grid doesn't offer it. When set, long-pressing a tile selects it
     * (and dragging on sweeps a range) instead of opening the quick-action menu - the bulk actions
     * bar covers those actions instead.
     */
    selection: GridSelectionState? = null,
    onBulkFavorite: (List<Post>) -> Unit = {},
    onBulkUnfavorite: (List<Post>) -> Unit = {},
    onBulkUpvote: ((List<Post>) -> Unit)? = null,
    onBulkDownload: (List<Post>) -> Unit = {},
    onBulkAddToSet: ((List<Post>) -> Unit)? = null,
    bulkProgress: one.proci.e621.data.util.BulkProgress? = null,
    emptyContent: @Composable () -> Unit = { NoResultsState() },
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
    val onPostClickState = rememberUpdatedState(onPostClick)
    val postsState = rememberUpdatedState(posts)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val selecting = selection?.active == true
    androidx.activity.compose.BackHandler(enabled = selecting) { selection?.exit() }

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
                    // Loading with nothing to show yet: shimmering placeholder tiles shaped like the
                    // grid (no second spinner - the pull indicator above already says "loading").
                    posts.isEmpty() && isRefreshing -> SkeletonGrid(minCellSize = liveSizeDp.dp)
                    posts.isEmpty() -> emptyContent()
                    else -> {
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Adaptive(minSize = liveSizeDp.dp),
                            state = gridState,
                            // Room below the last row for the floating selection bar.
                            contentPadding = PaddingValues(
                                start = GridEdgePadding,
                                top = GridEdgePadding,
                                end = GridEdgePadding,
                                bottom = GridEdgePadding + if (selecting) SelectionBarClearance else 0.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(GridGap),
                            verticalItemSpacing = GridGap,
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
                                }
                                .then(
                                    if (selection != null) {
                                        Modifier.dragToSelect(gridState, selection, postsState, scope, haptics)
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            itemsIndexed(posts, key = { _, post -> post.id }) { index, post ->
                                // Hoisted via remember(index) rather than allocated inline: an
                                // inline `{ onPostClick(index) }` here is a fresh closure every
                                // recomposition, which alone would make this item non-skippable
                                // regardless of Post's own stability.
                                val onClick = remember(index) { { onPostClickState.value(index) } }
                                // With selection on offer, long-press belongs to dragToSelect, so
                                // the tile gets no quick-action menu of its own.
                                val menu = selection == null
                                PostThumbnail(
                                    post = post,
                                    onClick = if (selecting) ({
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selection!!.toggle(post.id)
                                    }) else onClick,
                                    showCautionBorder = blacklistDisabled && post.id in blacklistedIds,
                                    onQuickFavorite = if (menu) onQuickFavorite?.let { cb -> { cb(post) } } else null,
                                    onQuickUpvote = if (menu) onQuickUpvote?.let { cb -> { cb(post) } } else null,
                                    onQuickDownload = if (menu) onQuickDownload?.let { cb -> { cb(post) } } else null,
                                    selected = if (selecting) post.id in selection!!.ids else null,
                                    dimmed = post.id in seenIds,
                                )
                            }
                            if (isLoadingMore) {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        RainbowSpinner(modifier = Modifier.size(28.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (selecting) SelectionBarClearance else 0.dp),
            )
            if (selection != null) {
                val recoverScope = androidx.compose.runtime.rememberCoroutineScope()
                val context = androidx.compose.ui.platform.LocalContext.current
                val notifyPermission = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                ) { }
                androidx.compose.animation.AnimatedVisibility(
                    visible = selecting,
                    enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    SelectionActionBar(
                        selected = posts.filter { it.id in selection.ids },
                        bulkProgress = bulkProgress,
                        onFavorite = onBulkFavorite,
                        onUnfavorite = onBulkUnfavorite,
                        onUpvote = onBulkUpvote,
                        onAddToSet = onBulkAddToSet,
                        onDownload = onBulkDownload,
                        onRecover = { list ->
                            val site = one.proci.e621.data.recovery.RecoveredStore.currentSite.value
                            val result = one.proci.e621.data.recovery.RecoveryQueue.enqueue(site, list)
                            val message = when (result) {
                                is one.proci.e621.data.recovery.RecoveryQueue.EnqueueResult.Added -> {
                                    selection.exit()
                                    // The "batch finished" notification (only shown while the app is in the background).
                                    if (android.os.Build.VERSION.SDK_INT >= 33 &&
                                        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                                        android.content.pm.PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    null
                                }
                                one.proci.e621.data.recovery.RecoveryQueue.EnqueueResult.TooMany ->
                                    context.getString(R.string.recovery_queue_too_many, one.proci.e621.data.recovery.RecoveryQueue.MAX_BATCH)
                                one.proci.e621.data.recovery.RecoveryQueue.EnqueueResult.NothingToDo ->
                                    context.getString(R.string.recovery_queue_nothing)
                            }
                            if (message != null) recoverScope.launch { snackbarHostState.showSnackbar(message) }
                        },
                    )
                }
            }
        }
    }
}

/** Height kept clear under the grid / snackbar for the floating selection bar. */
private val SelectionBarClearance = 88.dp

/**
 * Long-press a tile to select it, then keep dragging to sweep everything between it and the finger
 * (Google Photos style); auto-scrolls when the finger nears the top or bottom edge. Long-pressing a
 * tile that's already selected sweeps posts *out* of the selection instead.
 *
 * Runs on the Initial pass so that, once the long press fires, it can consume the rest of the
 * gesture before the tile's own clickable or the grid's scrolling see it. Before the long press it
 * only watches - any movement past touch slop, a second finger (pinch-zoom) or lifting the finger
 * abandons it, leaving taps and scrolls untouched.
 */
private fun Modifier.dragToSelect(
    gridState: LazyStaggeredGridState,
    selection: GridSelectionState,
    postsState: State<List<Post>>,
    scope: CoroutineScope,
    haptics: HapticFeedback,
): Modifier = pointerInput(gridState, selection) {
    val edge = 72.dp.toPx()
    val maxSpeed = 24.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var abandoned = false
        val waited = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (!abandoned) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                abandoned = change == null || !change.pressed ||
                    event.changes.count { it.pressed } > 1 ||
                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop
            }
        }
        if (waited != null || abandoned) return@awaitEachGesture

        val anchor = gridState.postIndexAt(down.position, postsState.value.size) ?: return@awaitEachGesture
        val anchorId = postsState.value[anchor].id
        val baseline = if (selection.active) selection.ids else emptySet()
        val adding = anchorId !in baseline
        selection.start()
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)

        var current = anchor
        fun sweepTo(index: Int) {
            val list = postsState.value
            val range = (minOf(anchor, index)..maxOf(anchor, index)).mapNotNull { list.getOrNull(it)?.id }
            selection.ids = if (adding) baseline + range else baseline - range.toSet()
        }
        sweepTo(anchor)
        var pointer = down.position
        fun track() {
            val index = gridState.postIndexAt(pointer, postsState.value.size) ?: return
            if (index != current) {
                current = index
                sweepTo(index)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }

        val autoScroll = scope.launch {
            while (isActive) {
                val y = pointer.y
                val h = size.height
                val speed = when {
                    y < edge -> -maxSpeed * ((edge - y) / edge).coerceIn(0f, 1f)
                    y > h - edge -> maxSpeed * ((y - (h - edge)) / edge).coerceIn(0f, 1f)
                    else -> 0f
                }
                if (speed != 0f) {
                    gridState.scrollBy(speed)
                    track()
                }
                withFrameNanos { }
            }
        }
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                event.changes.forEach { it.consume() }
                if (change == null || !change.pressed) break
                pointer = change.position
                track()
            }
        } finally {
            autoScroll.cancel()
        }
    }
}

/**
 * Index of the post tile under [position] (grid-local px), or the nearest visible one when the
 * finger sits in a gap or past the edge. Only the first [postCount] items are posts - anything after
 * (the load-more spinner) is ignored.
 */
private fun LazyStaggeredGridState.postIndexAt(position: Offset, postCount: Int): Int? {
    val info = layoutInfo
    // Item offsets are measured from the viewport start, which sits before the top content padding.
    val x = position.x
    val y = position.y + info.viewportStartOffset
    return info.visibleItemsInfo
        .filter { it.index < postCount }
        .minByOrNull { item ->
            val dx = maxOf(item.offset.x - x, 0f, x - (item.offset.x + item.size.width))
            val dy = maxOf(item.offset.y - y, 0f, y - (item.offset.y + item.size.height))
            dx * dx + dy * dy
        }
        ?.index
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
