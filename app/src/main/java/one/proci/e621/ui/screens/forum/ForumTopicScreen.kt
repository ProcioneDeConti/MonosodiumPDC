package one.proci.e621.ui.screens.forum

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import one.proci.e621.R
import one.proci.e621.data.model.ForumPost
import one.proci.e621.data.repository.AvatarRepository
import one.proci.e621.data.util.formatRelativeTime
import one.proci.e621.ui.components.DTextView
import one.proci.e621.ui.components.RainbowRefreshIndicator
import one.proci.e621.ui.components.UserAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumTopicScreen(
    state: ForumTopicUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onReply: (String, (Boolean) -> Unit) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: (Long) -> Unit,
    avatarRepository: AvatarRepository,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
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

            Box(modifier = Modifier.weight(1f)) {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
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
                        state.isRefreshing && state.posts.isEmpty() -> Box(Modifier.fillMaxSize())
                        state.error != null && state.posts.isEmpty() ->
                            Column(
                                Modifier.fillMaxSize().padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(state.error, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(12.dp))
                                TextButton(onClick = onRefresh) { Text(stringResource(R.string.error_retry)) }
                            }
                        else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(state.posts, key = { it.id }) { post ->
                                ForumPostRow(post, avatarRepository, onOpenProfile)
                                HorizontalDivider()
                            }
                            if (state.isLoadingMore) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            when {
                state.isLocked -> Text(
                    stringResource(R.string.forum_topic_locked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
                !state.isAuthenticated -> Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.forum_reply_needs_account), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings)) }
                }
                else -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text(stringResource(R.string.forum_reply_hint)) },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                    )
                    if (state.isReplying) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        IconButton(
                            onClick = { onReply(draft) { success -> if (success) draft = "" } },
                            enabled = draft.isNotBlank(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.forum_reply_send))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForumPostRow(post: ForumPost, avatarRepository: AvatarRepository, onOpenProfile: (Long) -> Unit) {
    val creatorId = post.creatorId
    val profileModifier = if (creatorId != null) Modifier.clickable { onOpenProfile(creatorId) } else Modifier
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        UserAvatar(
            userId = post.creatorId,
            name = post.creatorName,
            avatarRepository = avatarRepository,
            size = 32.dp,
            modifier = profileModifier,
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    post.creatorName ?: "?",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = profileModifier,
                )
                post.createdAt?.let {
                    Text(formatRelativeTime(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(4.dp))
            DTextView(text = post.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
