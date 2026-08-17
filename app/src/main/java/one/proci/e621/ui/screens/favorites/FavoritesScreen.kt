package one.proci.e621.ui.screens.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import one.proci.e621.R
import one.proci.e621.ui.components.PostGridBody

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    state: FavoritesUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onDismissError: () -> Unit,
    onPostClick: (index: Int) -> Unit,
    onOpenSettings: () -> Unit,
    onSetBlacklistDisabled: (Boolean) -> Unit,
    onThumbnailSizeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
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
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun NeedsUsernameState(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.favorites_needs_username),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onOpenSettings) {
            Text(stringResource(R.string.settings))
        }
    }
}
