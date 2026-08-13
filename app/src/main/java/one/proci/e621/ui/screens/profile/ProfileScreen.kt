package one.proci.e621.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import one.proci.e621.R
import one.proci.e621.data.model.UserProfile
import one.proci.e621.data.model.levelLabel
import one.proci.e621.data.repository.AvatarRepository
import one.proci.e621.ui.components.DTextView
import one.proci.e621.ui.components.UserAvatar
import one.proci.e621.ui.theme.VoteDownActive
import one.proci.e621.ui.theme.VoteUpActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenPost: (Long) -> Unit,
    onOpenPosts: (String) -> Unit,
    onOpenFavorites: (String) -> Unit,
    onOpenComments: (Long, String) -> Unit,
    onOpenFeedback: (Long, String) -> Unit,
    avatarRepository: AvatarRepository,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.profile?.name ?: stringResource(R.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.profile == null && state.isLoading -> Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.profile == null && state.error != null -> Column(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    String.format(stringResource(R.string.profile_load_failed), state.error),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.error_retry)) }
            }

            state.profile != null -> ProfileContent(
                profile = state.profile,
                avatarRepository = avatarRepository,
                onOpenPost = onOpenPost,
                onOpenPosts = onOpenPosts,
                onOpenFavorites = onOpenFavorites,
                onOpenComments = onOpenComments,
                onOpenFeedback = onOpenFeedback,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ProfileContent(
    profile: UserProfile,
    avatarRepository: AvatarRepository,
    onOpenPost: (Long) -> Unit,
    onOpenPosts: (String) -> Unit,
    onOpenFavorites: (String) -> Unit,
    onOpenComments: (Long, String) -> Unit,
    onOpenFeedback: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProfileHeader(profile, avatarRepository, onOpenPost)

        ProfileSection(stringResource(R.string.profile_section_activity)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ActivityRow(
                    icon = Icons.Filled.PhotoLibrary,
                    label = stringResource(R.string.profile_action_posts),
                    onClick = { onOpenPosts(profile.name) },
                )
                ActivityRow(
                    icon = Icons.Filled.Favorite,
                    label = stringResource(R.string.profile_action_favorites),
                    onClick = { onOpenFavorites(profile.name) },
                )
                ActivityRow(
                    icon = Icons.AutoMirrored.Filled.Comment,
                    label = stringResource(R.string.profile_action_comments),
                    onClick = { onOpenComments(profile.id, profile.name) },
                )
                ActivityRow(
                    icon = Icons.Filled.Star,
                    label = stringResource(R.string.profile_action_records),
                    onClick = { onOpenFeedback(profile.id, profile.name) },
                )
            }
        }

        val stats = listOfNotNull(
            profile.favoriteCount?.let { stringResource(R.string.profile_stat_favorites) to it },
            profile.commentCount?.let { stringResource(R.string.profile_stat_comments) to it },
            profile.forumPostCount?.let { stringResource(R.string.profile_stat_forum_posts) to it },
            profile.wikiPageVersionCount?.let { stringResource(R.string.profile_stat_wiki_edits) to it },
            profile.artistVersionCount?.let { stringResource(R.string.profile_stat_artist_edits) to it },
            profile.poolVersionCount?.let { stringResource(R.string.profile_stat_pool_edits) to it },
            profile.uploadSlots?.let { stringResource(R.string.profile_stat_upload_slots) to it },
            profile.flagCount?.let { stringResource(R.string.profile_stat_flags) to it },
        )
        if (stats.isNotEmpty()) {
            ProfileSection(stringResource(R.string.profile_section_stats)) {
                StatsGrid(stats)
            }
        }

        if (profile.positiveFeedbackCount != null || profile.neutralFeedbackCount != null || profile.negativeFeedbackCount != null) {
            ProfileSection(stringResource(R.string.profile_section_feedback)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FeedbackStat(stringResource(R.string.profile_feedback_positive), profile.positiveFeedbackCount ?: 0, VoteUpActive)
                    FeedbackStat(
                        stringResource(R.string.profile_feedback_neutral),
                        profile.neutralFeedbackCount ?: 0,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FeedbackStat(stringResource(R.string.profile_feedback_negative), profile.negativeFeedbackCount ?: 0, VoteDownActive)
                }
            }
        }

        if (!profile.profileAbout.isNullOrBlank()) {
            ProfileSection(stringResource(R.string.profile_section_about)) {
                DTextView(text = profile.profileAbout, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (!profile.profileArtinfo.isNullOrBlank()) {
            ProfileSection(stringResource(R.string.profile_section_artist_info)) {
                DTextView(text = profile.profileArtinfo, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ProfileHeader(profile: UserProfile, avatarRepository: AvatarRepository, onOpenPost: (Long) -> Unit) {
    val gradient = Brush.verticalGradient(
        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceVariant),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradient, RoundedCornerShape(7.dp))
            .padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val avatarId = profile.avatarId
        Box(
            modifier = Modifier
                .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .padding(4.dp),
        ) {
            UserAvatar(
                userId = profile.id,
                name = profile.name,
                avatarRepository = avatarRepository,
                size = 112.dp,
                modifier = if (avatarId != null) Modifier.clickable { onOpenPost(avatarId) } else Modifier,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            profile.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        profile.levelLabel()?.let { label ->
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(7.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        profile.createdAt?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                String.format(stringResource(R.string.profile_joined), it.take(10)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActivityRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(7.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(7.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun StatsGrid(stats: List<Pair<String, Int>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        stats.chunked(2).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { (label, value) -> StatCard(label, value, modifier = Modifier.weight(1f)) }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(7.dp))
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "%,d".format(value),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FeedbackStat(label: String, value: Int, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
