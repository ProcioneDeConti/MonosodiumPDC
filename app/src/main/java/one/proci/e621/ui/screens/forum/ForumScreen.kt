package one.proci.e621.ui.screens.forum

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
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
import one.proci.e621.data.model.ForumTopic
import one.proci.e621.data.util.formatRelativeTime
import one.proci.e621.ui.components.RainbowRefreshIndicator
import one.proci.e621.ui.components.EmptyState
import one.proci.e621.ui.components.ErrorState
import one.proci.e621.ui.components.ListCard
import one.proci.e621.ui.components.ListCardContentPadding
import one.proci.e621.ui.components.ListCardSpacing
import one.proci.e621.ui.components.LoadingMoreFooter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumScreen(
    state: ForumUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenTopic: (ForumTopic) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.forum_title)) },
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
                // Loading with nothing to show yet - leave this blank rather than a second,
                // competing spinner; the pop-down arrow indicator above already says "loading".
                state.isRefreshing && state.topics.isEmpty() -> Box(Modifier.fillMaxSize())
                state.error != null && state.topics.isEmpty() ->
                    ErrorState(message = state.error, onRetry = onRefresh)
                state.topics.isEmpty() -> EmptyState(
                    icon = Icons.Filled.Forum,
                    title = stringResource(R.string.forum_no_topics),
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = ListCardContentPadding,
                    verticalArrangement = ListCardSpacing,
                ) {
                    items(state.topics, key = { it.id }) { topic ->
                        TopicRow(topic, onClick = { onOpenTopic(topic) })
                    }
                    if (state.isLoadingMore) item { LoadingMoreFooter() }
                }
            }
        }
    }
}

@Composable
private fun TopicRow(topic: ForumTopic, onClick: () -> Unit) {
    ListCard(
        onClick = onClick,
        color = if (topic.isSticky) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (topic.isSticky) {
                Icon(
                    Icons.Filled.PushPin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (topic.isLocked) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Text(
                text = topic.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (topic.isSticky) FontWeight.Bold else FontWeight.Normal,
            )
        }
        Text(
            text = stringResource(
                R.string.forum_topic_meta,
                topic.responseCount,
                topic.creatorName ?: "?",
                formatRelativeTime(topic.updatedAt),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
