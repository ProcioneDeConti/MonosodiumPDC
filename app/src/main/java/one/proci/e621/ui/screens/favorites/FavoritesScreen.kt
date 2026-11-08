package one.proci.e621.ui.screens.favorites

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import one.proci.e621.R
import androidx.compose.material.icons.filled.AccountCircle
import one.proci.e621.ui.components.EmptyState
import one.proci.e621.ui.components.PostGridBody

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    state: FavoritesUiState,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onDismissError: () -> Unit,
    onPostClick: (index: Int) -> Unit,
    onOpenSettings: () -> Unit,
    onSetBlacklistDisabled: (Boolean) -> Unit,
    onThumbnailSizeChange: (Int) -> Unit,
    onOpenAnalysis: (username: String) -> Unit,
    onQuickFavorite: (one.proci.e621.data.model.Post) -> Unit,
    onQuickUpvote: (one.proci.e621.data.model.Post) -> Unit,
    onQuickDownload: (one.proci.e621.data.model.Post) -> Unit,
    onBulkFavorite: (Set<Long>, Boolean) -> Unit,
    bulkProgress: one.proci.e621.data.util.BulkProgress?,
    postSetRepository: one.proci.e621.data.repository.PostSetRepository?,
    modifier: Modifier = Modifier,
) {
    val selection = one.proci.e621.ui.components.rememberGridSelectionState()
    var bulkAddToSetIds by remember { mutableStateOf<List<Long>?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            androidx.compose.animation.AnimatedContent(
                targetState = selection.active,
                transitionSpec = { androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut() },
                label = "favoritesTopBar",
            ) { selecting ->
                if (selecting) {
                    one.proci.e621.ui.components.SelectionTopBar(
                        selection = selection,
                        allIds = remember(state.posts) { state.posts.map { it.id } },
                        bulkProgress = bulkProgress,
                    )
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                if (state.username.isBlank()) {
                                    stringResource(R.string.favorites)
                                } else {
                                    stringResource(R.string.favorites_title_owned, state.username)
                                },
                            )
                        },
                        navigationIcon = {
                            // Null when this screen is a bottom-bar tab rather than a pushed screen.
                            if (onBack != null) {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                                }
                            }
                        },
                        actions = {
                            if (state.username.isNotBlank()) {
                                IconButton(onClick = { onOpenAnalysis(state.username) }) {
                                    Icon(Icons.Filled.QueryStats, contentDescription = stringResource(R.string.fav_analysis_open))
                                }
                            }
                            IconButton(onClick = { onSetBlacklistDisabled(!state.blacklistDisabled) }) {
                                Icon(
                                    if (state.blacklistDisabled) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = stringResource(
                                        if (state.blacklistDisabled) R.string.blacklist_enable else R.string.blacklist_disable,
                                    ),
                                )
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        if (state.username.isBlank()) {
            NeedsUsernameState(onOpenSettings = onOpenSettings, modifier = Modifier.padding(padding))
        } else {
            PostGridBody(
                posts = state.posts,
                isRefreshing = state.isRefreshing,
                isLoadingMore = state.isLoadingMore,
                error = state.error,
                onRefresh = onRefresh,
                onLoadMore = onLoadMore,
                onDismissError = onDismissError,
                onPostClick = onPostClick,
                blacklistDisabled = state.blacklistDisabled,
                blacklistedIds = state.blacklistedIds,
                onEnableBlacklist = { onSetBlacklistDisabled(false) },
                thumbnailSizeDp = state.gridThumbnailSizeDp,
                onThumbnailSizeChange = onThumbnailSizeChange,
                onQuickFavorite = onQuickFavorite,
                onQuickUpvote = onQuickUpvote,
                onQuickDownload = onQuickDownload,
                selection = selection,
                onBulkFavorite = { list -> onBulkFavorite(list.map { it.id }.toSet(), true) },
                onBulkUnfavorite = { list -> onBulkFavorite(list.map { it.id }.toSet(), false) },
                onBulkUpvote = { list -> list.forEach(onQuickUpvote) },
                onBulkDownload = { list -> list.forEach(onQuickDownload) },
                onBulkAddToSet = postSetRepository?.let { { list -> bulkAddToSetIds = list.map { it.id } } },
                bulkProgress = bulkProgress,
                modifier = Modifier.padding(padding),
            )
        }
    }

    val addToSetIds = bulkAddToSetIds
    if (addToSetIds != null && postSetRepository != null) {
        one.proci.e621.ui.components.AddToSetDialog(
            postIds = addToSetIds,
            repository = postSetRepository,
            onDismiss = { bulkAddToSetIds = null },
        )
    }
}

@Composable
private fun NeedsUsernameState(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Filled.AccountCircle,
        title = stringResource(R.string.favorites_needs_username),
        actionLabel = stringResource(R.string.settings),
        onAction = onOpenSettings,
        modifier = modifier,
    )
}
