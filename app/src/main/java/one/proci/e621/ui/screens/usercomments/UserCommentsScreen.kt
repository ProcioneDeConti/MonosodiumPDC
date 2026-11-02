package one.proci.e621.ui.screens.usercomments

import one.proci.e621.ui.components.RainbowSpinner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import one.proci.e621.R
import one.proci.e621.ui.components.LoadingMoreFooter
import one.proci.e621.ui.components.ListCardSpacing
import one.proci.e621.ui.components.ListCardContentPadding
import one.proci.e621.ui.components.ListCard
import one.proci.e621.ui.components.ErrorState
import one.proci.e621.ui.components.EmptyState
import one.proci.e621.data.model.Comment
import one.proci.e621.data.util.formatRelativeTime
import one.proci.e621.ui.components.DTextView
import one.proci.e621.ui.components.RainbowRefreshIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserCommentsScreen(
    state: UserCommentsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenPost: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(String.format(stringResource(R.string.user_comments_title), state.username)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val listState = rememberLazyListState()
        val shouldLoadMore by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                val total = layoutInfo.totalItemsCount
                total > 0 && lastVisible >= total - 5
            }
        }
        LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) onLoadMore() }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
            indicator = {
                RainbowRefreshIndicator(
                    isRefreshing = state.isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                )
            },
        ) {
            when {
                state.isRefreshing && state.comments.isEmpty() -> Box(Modifier.fillMaxSize())
                state.error != null && state.comments.isEmpty() -> ErrorState(message = state.error, onRetry = onRefresh)
                !state.isRefreshing && state.comments.isEmpty() -> EmptyState(icon = Icons.Filled.ChatBubbleOutline, title = stringResource(R.string.user_comments_empty))
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = ListCardContentPadding,
                    verticalArrangement = ListCardSpacing,
                ) {
                    items(state.comments, key = { it.id }) { comment ->
                        CommentRow(comment, onOpenPost)
                    }
                    if (state.isLoadingMore) item { LoadingMoreFooter() }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: Comment, onOpenPost: (Long) -> Unit) {
    ListCard(onClick = { onOpenPost(comment.postId) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                String.format(stringResource(R.string.user_comments_post_label), comment.postId),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            comment.createdAt?.let {
                Text(formatRelativeTime(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        DTextView(text = comment.body, style = MaterialTheme.typography.bodyMedium)
    }
}
