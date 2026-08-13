package one.proci.e621.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import one.proci.e621.R
import one.proci.e621.data.model.Post
import one.proci.e621.data.repository.AvatarRepository
import one.proci.e621.data.repository.PostActionsRepository
import one.proci.e621.data.repository.PostRepository
import one.proci.e621.data.settings.Site
import one.proci.e621.ui.theme.ViewerBackground

/**
 * Wraps [PostDetailScreen] to view a single arbitrary post by id (rather than an index into an
 * already-loaded list), e.g. jumping to the post behind a user's avatar. `id:<n>` is e621's
 * exact-id search tag, so this reuses the normal typed [Post] fetch path rather than the raw-JSON
 * one - the same rating filter that applies to search results applies here too, so a post outside
 * the viewer's content settings surfaces as "not found" rather than bypassing them.
 */
@Composable
fun PostByIdScreen(
    postId: Long,
    postRepository: PostRepository,
    postActionsRepository: PostActionsRepository,
    avatarRepository: AvatarRepository,
    onBack: () -> Unit,
    onAddTagToBlacklist: (String) -> Unit,
    onSearchTag: (String) -> Unit,
    onAddTagToSearch: (String) -> Unit,
    onExcludeTagFromSearch: (String) -> Unit,
    onOpenProfile: (Long) -> Unit,
    site: Site,
    modifier: Modifier = Modifier,
) {
    var post by remember(postId) { mutableStateOf<Post?>(null) }
    var error by remember(postId) { mutableStateOf<String?>(null) }
    val loadFailedTemplate = stringResource(R.string.post_load_failed)
    val notFoundMessage = stringResource(R.string.post_not_found)

    LaunchedEffect(postId) {
        error = null
        runCatching { postRepository.fetchPosts("id:$postId", limit = 1).firstOrNull() }
            .onSuccess { found -> if (found != null) post = found else error = notFoundMessage }
            .onFailure { e -> error = String.format(loadFailedTemplate, e.message ?: e.toString()) }
    }

    val currentPost = post
    if (currentPost != null) {
        PostDetailScreen(
            posts = listOf(currentPost),
            initialIndex = 0,
            onBack = onBack,
            onLoadMore = {},
            onPostUpdated = { updated -> post = updated },
            postActionsRepository = postActionsRepository,
            avatarRepository = avatarRepository,
            onAddTagToBlacklist = onAddTagToBlacklist,
            onSearchTag = onSearchTag,
            onAddTagToSearch = onAddTagToSearch,
            onExcludeTagFromSearch = onExcludeTagFromSearch,
            onOpenProfile = onOpenProfile,
            site = site,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier.fillMaxSize().background(ViewerBackground)) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.statusBars).padding(4.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = Color.White)
            }
            when (val message = error) {
                null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
                else -> Column(modifier = Modifier.align(Alignment.Center).padding(24.dp)) {
                    Text(message, color = Color.White, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
