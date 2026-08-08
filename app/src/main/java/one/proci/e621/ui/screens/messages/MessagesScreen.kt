package one.proci.e621.ui.screens.messages

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import one.proci.e621.R
import one.proci.e621.data.model.Dmail
import one.proci.e621.data.repository.AvatarRepository
import one.proci.e621.data.util.formatRelativeTime
import one.proci.e621.ui.components.RainbowRefreshIndicator
import one.proci.e621.ui.components.UserAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    state: MessagesUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenDmail: (Dmail) -> Unit,
    onCompose: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: (Long) -> Unit,
    avatarRepository: AvatarRepository,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.messages_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.isAuthenticated) {
                        IconButton(onClick = onCompose) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.message_new),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!state.isAuthenticated) {
            NeedsAccountState(onOpenSettings = onOpenSettings, modifier = Modifier.padding(padding))
        } else {
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
                    state.isRefreshing && state.dmails.isEmpty() -> Box(Modifier.fillMaxSize())
                    state.error != null && state.dmails.isEmpty() ->
                        Column(
                            Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(state.error, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = onRefresh) { Text(stringResource(R.string.error_retry)) }
                        }
                    state.dmails.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.messages_none),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(state.dmails, key = { it.id }) { dmail ->
                            DmailRow(dmail, avatarRepository, onClick = { onOpenDmail(dmail) }, onOpenProfile = onOpenProfile)
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
    }
}

@Composable
private fun DmailRow(dmail: Dmail, avatarRepository: AvatarRepository, onClick: () -> Unit, onOpenProfile: (Long) -> Unit) {
    val fromId = dmail.fromId
    // A nested clickable (avatar/name) inside the row's own clickable (open dmail) - taps inside
    // the smaller area resolve to it and never fall through to the outer one.
    val profileModifier = if (fromId != null) Modifier.clickable { onOpenProfile(fromId) } else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UserAvatar(
            userId = dmail.fromId,
            name = dmail.fromName ?: dmail.toName,
            avatarRepository = avatarRepository,
            size = 40.dp,
            modifier = profileModifier,
        )
        Column(modifier = Modifier.weight(1f)) {
            // The sender is the most prominent line - bold, accent-colored - so it's immediately
            // clear who a message is from, with the subject as a secondary line underneath.
            Text(
                text = dmail.fromName ?: dmail.toName ?: "?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = profileModifier,
            )
            Text(
                text = dmail.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (!dmail.isRead) FontWeight.Bold else FontWeight.Normal,
            )
            dmail.createdAt?.let {
                Text(formatRelativeTime(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NeedsAccountState(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.messages_needs_account),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onOpenSettings) { Text(stringResource(R.string.settings)) }
    }
}
