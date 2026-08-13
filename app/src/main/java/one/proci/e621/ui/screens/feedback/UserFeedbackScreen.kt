package one.proci.e621.ui.screens.feedback

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import one.proci.e621.R
import one.proci.e621.data.model.UserFeedback
import one.proci.e621.data.repository.AvatarRepository
import one.proci.e621.data.util.formatRelativeTime
import one.proci.e621.ui.components.DTextView
import one.proci.e621.ui.components.RainbowRefreshIndicator
import one.proci.e621.ui.components.UserAvatar
import one.proci.e621.ui.theme.VoteDownActive
import one.proci.e621.ui.theme.VoteDownPale
import one.proci.e621.ui.theme.VoteUpActive
import one.proci.e621.ui.theme.VoteUpPale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserFeedbackScreen(
    state: UserFeedbackUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenProfile: (Long) -> Unit,
    avatarRepository: AvatarRepository,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(String.format(stringResource(R.string.user_feedback_title), state.username)) },
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
                state.isRefreshing && state.feedbacks.isEmpty() -> Box(Modifier.fillMaxSize())
                state.error != null && state.feedbacks.isEmpty() ->
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(state.error, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = onRefresh) { Text(stringResource(R.string.error_retry)) }
                    }
                !state.isRefreshing && state.feedbacks.isEmpty() ->
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.user_feedback_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.feedbacks, key = { it.id }) { feedback ->
                        FeedbackRow(feedback, avatarRepository, onOpenProfile)
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

@Composable
private fun FeedbackRow(feedback: UserFeedback, avatarRepository: AvatarRepository, onOpenProfile: (Long) -> Unit) {
    val creatorId = feedback.creatorId
    val profileModifier = if (creatorId != null) Modifier.clickable { onOpenProfile(creatorId) } else Modifier
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        UserAvatar(
            userId = feedback.creatorId,
            name = feedback.creatorName,
            avatarRepository = avatarRepository,
            size = 32.dp,
            modifier = profileModifier,
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    feedback.creatorName ?: "?",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = profileModifier,
                )
                CategoryChip(feedback.category)
                feedback.createdAt?.let {
                    Text(formatRelativeTime(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(4.dp))
            DTextView(text = feedback.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CategoryChip(category: String) {
    val (bg, fg, label) = when (category) {
        "positive" -> Triple(VoteUpPale, VoteUpActive, stringResource(R.string.profile_feedback_positive))
        "negative" -> Triple(VoteDownPale, VoteDownActive, stringResource(R.string.profile_feedback_negative))
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(R.string.profile_feedback_neutral),
        )
    }
    Box(modifier = Modifier.background(bg, RoundedCornerShape(7.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = fg)
    }
}
