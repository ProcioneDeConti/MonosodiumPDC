package one.proci.e621.ui.screens.recovered

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import one.proci.e621.R
import one.proci.e621.data.model.Post
import one.proci.e621.ui.components.EmptyState
import one.proci.e621.ui.components.PostGridBody

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveredScreen(
    state: RecoveredUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPostClick: (index: Int) -> Unit,
    onSetBlacklistDisabled: (Boolean) -> Unit,
    onThumbnailSizeChange: (Int) -> Unit,
    onQuickFavorite: (Post) -> Unit,
    onQuickUpvote: (Post) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recovered_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { onSetBlacklistDisabled(!state.blacklistDisabled) }) {
                        Icon(
                            if (state.blacklistDisabled) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = stringResource(if (state.blacklistDisabled) R.string.blacklist_enable else R.string.blacklist_disable),
                        )
                    }
                },
            )
        },
    ) { padding ->
        PostGridBody(
            posts = state.posts,
            isRefreshing = state.isLoading,
            isLoadingMore = false,
            error = state.error,
            onRefresh = onRefresh,
            onLoadMore = {},
            onDismissError = {},
            onPostClick = onPostClick,
            modifier = Modifier.padding(padding),
            blacklistDisabled = state.blacklistDisabled,
            blacklistedIds = state.blacklistedIds,
            onEnableBlacklist = { onSetBlacklistDisabled(false) },
            thumbnailSizeDp = state.gridThumbnailSizeDp,
            onThumbnailSizeChange = onThumbnailSizeChange,
            onQuickFavorite = onQuickFavorite,
            onQuickUpvote = onQuickUpvote,
            emptyContent = {
                EmptyState(
                    icon = Icons.Filled.Recycling,
                    title = stringResource(R.string.recovered_empty_title),
                    message = stringResource(R.string.recovered_empty_body),
                )
            },
        )
    }
}
