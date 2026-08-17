package one.proci.e621.ui.screens.detail

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import one.proci.e621.R
import one.proci.e621.data.download.MediaDownloader
import one.proci.e621.data.download.MediaSharer
import one.proci.e621.data.model.CategorizedTag
import one.proci.e621.data.model.Comment
import one.proci.e621.data.model.Post
import one.proci.e621.data.model.TagCategory
import one.proci.e621.data.repository.AvatarRepository
import one.proci.e621.data.repository.PostActionsRepository
import one.proci.e621.data.settings.Site
import one.proci.e621.data.util.formatCount
import one.proci.e621.ui.components.DTextView
import one.proci.e621.ui.components.MediaViewer
import one.proci.e621.ui.components.UserAvatar
import one.proci.e621.ui.theme.FavoriteGold
import one.proci.e621.ui.theme.PostStatusActive
import one.proci.e621.ui.theme.PostStatusDeleted
import one.proci.e621.ui.theme.PostStatusFlagged
import one.proci.e621.ui.theme.PostStatusPending
import one.proci.e621.ui.theme.RatingExplicit
import one.proci.e621.ui.theme.RatingQuestionable
import one.proci.e621.ui.theme.RatingSafe
import one.proci.e621.ui.theme.ScoreHigh
import one.proci.e621.ui.theme.ScoreLow
import one.proci.e621.ui.theme.ScoreMid
import one.proci.e621.ui.theme.TagArtist
import one.proci.e621.ui.theme.TagCharacter
import one.proci.e621.ui.theme.TagCopyright
import one.proci.e621.ui.theme.TagGeneral
import one.proci.e621.ui.theme.TagSpecies
import one.proci.e621.ui.theme.VoteDownActive
import one.proci.e621.ui.theme.VoteDownPale
import one.proci.e621.ui.theme.VoteUpActive
import one.proci.e621.ui.theme.VoteUpPale
import one.proci.e621.ui.theme.ViewerBackground

@Composable
fun PostDetailScreen(
    posts: List<Post>,
    initialIndex: Int,
    onBack: () -> Unit,
    onLoadMore: () -> Unit,
    onPostUpdated: (Post) -> Unit,
    postActionsRepository: PostActionsRepository,
    avatarRepository: AvatarRepository,
    onAddTagToBlacklist: (String) -> Unit,
    onSearchTag: (String) -> Unit,
    onAddTagToSearch: (String) -> Unit,
    onExcludeTagFromSearch: (String) -> Unit,
    onOpenProfile: (Long) -> Unit,
    site: Site,
    videoLoopEnabled: Boolean,
    videoPlaybackSpeed: Float,
    videoAutoplayEnabled: Boolean,
    downloadLocationUri: String?,
    modifier: Modifier = Modifier,
) {
    if (posts.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, posts.lastIndex),
        pageCount = { posts.size },
    )
    var infoVisible by remember { mutableStateOf(true) }
    var infoSheetVisible by remember { mutableStateOf(false) }
    var commentsSheetVisible by remember { mutableStateOf(false) }
    // Flipped the instant back is triggered (tap or system gesture), rather than waiting for this
    // screen to actually leave composition - the exit transition keeps it composed and visible for
    // a moment, and a playing video would otherwise keep running throughout that transition.
    var screenActive by remember { mutableStateOf(true) }

    fun handleBack() {
        screenActive = false
        onBack()
    }

    BackHandler(onBack = ::handleBack)

    val shouldLoadMore by remember {
        derivedStateOf { pagerState.currentPage >= posts.size - 3 }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    val currentPost = posts.getOrNull(pagerState.currentPage)

    // A weighted Column (rather than layering everything in one full-bleed Box) so the info
    // panel occupies its own space below the pager instead of covering the bottom of the
    // image; hiding it on tap lets the pager expand to fill the screen for a true fullscreen view.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ViewerBackground),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val post = posts.getOrNull(page) ?: return@HorizontalPager
                MediaViewer(
                    post = post,
                    isActive = screenActive && pagerState.currentPage == page,
                    videoLoopEnabled = videoLoopEnabled,
                    videoPlaybackSpeed = videoPlaybackSpeed,
                    videoAutoplayEnabled = videoAutoplayEnabled,
                    onTap = { infoVisible = !infoVisible },
                    onDismiss = ::handleBack,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Fully qualified: inside Column/Box scopes, Kotlin prefers the ColumnScope-member
            // overload of AnimatedVisibility over this top-level one, which fails to resolve.
            androidx.compose.animation.AnimatedVisibility(
                visible = infoVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                TopBar(
                    onBack = ::handleBack,
                    index = pagerState.currentPage,
                    total = posts.size,
                    rating = currentPost?.rating,
                    onShowInfo = { infoSheetVisible = true },
                )
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = infoVisible && currentPost != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            if (currentPost != null) {
                InfoPanel(
                    post = currentPost,
                    postActionsRepository = postActionsRepository,
                    onPostUpdated = onPostUpdated,
                    onAddTagToBlacklist = onAddTagToBlacklist,
                    onSearchTag = onSearchTag,
                    onAddTagToSearch = onAddTagToSearch,
                    onExcludeTagFromSearch = onExcludeTagFromSearch,
                    onOpenComments = { commentsSheetVisible = true },
                    site = site,
                    downloadLocationUri = downloadLocationUri,
                )
            }
        }
    }

    if (infoSheetVisible && currentPost != null) {
        PostInfoSheet(
            post = currentPost,
            postActionsRepository = postActionsRepository,
            onDismiss = { infoSheetVisible = false },
            onOpenComments = {
                infoSheetVisible = false
                commentsSheetVisible = true
            },
            site = site,
        )
    }
    if (commentsSheetVisible && currentPost != null) {
        CommentsSheet(
            post = currentPost,
            postActionsRepository = postActionsRepository,
            avatarRepository = avatarRepository,
            onDismiss = { commentsSheetVisible = false },
            onOpenProfile = onOpenProfile,
        )
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, index: Int, total: Int, rating: String?, onShowInfo: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = Color.White)
        }
        Text(
            text = "${index + 1} / $total",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (rating != null) {
            RatingChip(rating)
            Spacer(modifier = Modifier.width(8.dp))
        }
        IconButton(onClick = onShowInfo) {
            Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.action_info), tint = Color.White)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoPanel(
    post: Post,
    postActionsRepository: PostActionsRepository,
    onPostUpdated: (Post) -> Unit,
    onAddTagToBlacklist: (String) -> Unit,
    onSearchTag: (String) -> Unit,
    onAddTagToSearch: (String) -> Unit,
    onExcludeTagFromSearch: (String) -> Unit,
    onOpenComments: () -> Unit,
    site: Site,
    downloadLocationUri: String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isDownloading by remember(post.id) { mutableStateOf(false) }
    var shareMenuExpanded by remember { mutableStateOf(false) }
    val downloadStartedMessage = stringResource(R.string.download_started)
    val downloadSavedMessage = stringResource(R.string.download_saved)
    val downloadFailedTemplate = stringResource(R.string.download_failed)
    val shareFailedTemplate = stringResource(R.string.share_failed)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .background(ViewerBackground)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        VoteFavoriteRow(
            post = post,
            postActionsRepository = postActionsRepository,
            onPostUpdated = onPostUpdated,
            onOpenComments = onOpenComments,
        )

        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
        ) {
            if (isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
            } else {
                TextAction(
                    icon = Icons.Filled.Download,
                    label = stringResource(R.string.download),
                    onClick = {
                        val url = post.playableUrl ?: return@TextAction
                        isDownloading = true
                        Toast.makeText(context, downloadStartedMessage, Toast.LENGTH_SHORT).show()
                        scope.launch {
                            val result = MediaDownloader(context).download(url, post.downloadFileName, post.mimeType, downloadLocationUri)
                            isDownloading = false
                            val message = result.fold(
                                onSuccess = { downloadSavedMessage },
                                onFailure = { e -> String.format(downloadFailedTemplate, e.message ?: e.toString()) },
                            )
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
            Box {
                TextAction(
                    icon = Icons.Filled.Share,
                    label = stringResource(R.string.action_share),
                    onClick = { shareMenuExpanded = true },
                )
                DropdownMenu(expanded = shareMenuExpanded, onDismissRequest = { shareMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_image)) },
                        onClick = {
                            shareMenuExpanded = false
                            val url = post.playableUrl
                            if (url != null) {
                                scope.launch {
                                    MediaSharer(context).shareFile(url, post.downloadFileName, post.mimeType)
                                        .onFailure { e ->
                                            Toast.makeText(
                                                context,
                                                String.format(shareFailedTemplate, e.message ?: e.toString()),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                }
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_image_link)) },
                        onClick = {
                            shareMenuExpanded = false
                            post.playableUrl?.let { MediaSharer(context).shareText(it) }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_post_link)) },
                        onClick = {
                            shareMenuExpanded = false
                            MediaSharer(context).shareText("${site.webBaseUrl}/posts/${post.id}")
                        },
                    )
                }
            }
        }

        if (post.categorizedTags.isNotEmpty()) {
            TagSections(
                tags = post.categorizedTags,
                onAddToBlacklist = onAddTagToBlacklist,
                onSearch = onSearchTag,
                onAddToSearch = onAddTagToSearch,
                onExcludeFromSearch = onExcludeTagFromSearch,
            )
        }
    }
}

@Composable
private fun VoteFavoriteRow(
    post: Post,
    postActionsRepository: PostActionsRepository,
    onPostUpdated: (Post) -> Unit,
    onOpenComments: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isVoting by remember(post.id) { mutableStateOf(false) }
    var isFavoriting by remember(post.id) { mutableStateOf(false) }
    val voteFailedTemplate = stringResource(R.string.vote_failed)
    val favoriteFailedTemplate = stringResource(R.string.favorite_failed)

    fun castVote(direction: Int) {
        if (isVoting) return
        isVoting = true
        scope.launch {
            runCatching { postActionsRepository.vote(post, direction) }
                .onSuccess(onPostUpdated)
                .onFailure { e ->
                    Toast.makeText(context, String.format(voteFailedTemplate, e.message ?: e.toString()), Toast.LENGTH_SHORT).show()
                }
            isVoting = false
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Two grouped pill containers (Material "assist chip" styling already used for tag
        // chips elsewhere) so voting and favoriting read as distinct clusters rather than one
        // undifferentiated row of icons.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(PillHeight)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(7.dp)),
        ) {
            VoteTooltipIconButton(
                tooltipText = "${post.score.up} upvotes",
                onClick = { castVote(1) },
                enabled = !isVoting,
            ) {
                Icon(
                    Icons.Filled.ArrowDropUp,
                    contentDescription = "Upvote",
                    tint = if (post.voteBy == 1) VoteUpActive else VoteUpPale,
                    modifier = Modifier.size(40.dp),
                )
            }
            Text(
                text = formatCount(post.score.total),
                color = scoreColor(post.score.total),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                softWrap = false,
            )
            VoteTooltipIconButton(
                tooltipText = "${post.score.down} downvotes",
                onClick = { castVote(-1) },
                enabled = !isVoting,
            ) {
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = "Downvote",
                    tint = if (post.voteBy == -1) VoteDownActive else VoteDownPale,
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        CommentCountPill(post.commentCount, onClick = onOpenComments)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(PillHeight)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(7.dp))
                .padding(end = 8.dp),
        ) {
            IconButton(
                enabled = !isFavoriting,
                modifier = Modifier.size(48.dp),
                onClick = {
                    if (isFavoriting) return@IconButton
                    isFavoriting = true
                    scope.launch {
                        runCatching {
                            if (post.isFavorited) {
                                postActionsRepository.unfavorite(post)
                            } else {
                                val favorited = postActionsRepository.favorite(post)
                                // Favoriting also upvotes, unless the post is already upvoted (voting
                                // the same direction again would just toggle the upvote back off).
                                if (favorited.voteBy != 1) postActionsRepository.vote(favorited, 1) else favorited
                            }
                        }.onSuccess(onPostUpdated)
                            .onFailure { e ->
                                Toast.makeText(
                                    context,
                                    String.format(favoriteFailedTemplate, e.message ?: e.toString()),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        isFavoriting = false
                    }
                },
            ) {
                Icon(
                    if (post.isFavorited) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Favorite",
                    tint = FavoriteGold,
                    modifier = Modifier.size(30.dp),
                )
            }
            Text(
                formatCount(post.favCount),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                softWrap = false,
            )
        }
    }
}

/** Shared height so the rating, vote, comment-count, and favorite pills all line up. */
private val PillHeight = 52.dp

@Composable
private fun CommentCountPill(count: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(PillHeight)
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatCount(count),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            softWrap = false,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoteTooltipIconButton(
    tooltipText: String,
    onClick: () -> Unit,
    enabled: Boolean,
    icon: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltipText) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
            icon()
        }
    }
}

/**
 * [netScore] is up+down (e621 reports down as a non-positive number, so this is already the net
 * vote differential). Within +/-5 votes counts as "roughly even" and stays yellow; beyond that it
 * grades toward green (net upvoted) or red (net downvoted), fully saturating at +/-25.
 */
private fun scoreColor(netScore: Int): Color {
    val flex = 5f
    val cap = 25f
    val n = netScore.toFloat()
    return when {
        n >= cap -> ScoreHigh
        n <= -cap -> ScoreLow
        n > flex -> lerp(ScoreMid, ScoreHigh, (n - flex) / (cap - flex))
        n < -flex -> lerp(ScoreMid, ScoreLow, (-n - flex) / (cap - flex))
        else -> ScoreMid
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PostInfoSheet(
    post: Post,
    postActionsRepository: PostActionsRepository,
    onDismiss: () -> Unit,
    onOpenComments: () -> Unit,
    site: Site,
) {
    val sheetState = rememberModalBottomSheetState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showJsonDialog by remember { mutableStateOf(false) }
    var jsonText by remember(post.id) { mutableStateOf<String?>(null) }
    var jsonLoading by remember { mutableStateOf(false) }
    var jsonError by remember { mutableStateOf<String?>(null) }
    val jsonFailedTemplate = stringResource(R.string.raw_json_failed)

    // The sheet's own Surface is painted in the accent color and shaped with the standard
    // rounded-top corners; the actual content sits in an inner surface inset by a few dp, which
    // leaves a ring of accent color tracing that curve rather than a flat bar sitting inside it.
    val sheetShape = RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = sheetShape,
        containerColor = MaterialTheme.colorScheme.primary,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                .background(ViewerBackground),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 32.dp, height = 4.dp)
                    .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(7.dp)),
            )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 32.dp),
        ) {
            Text(
                stringResource(R.string.info_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillAction(
                    icon = Icons.Filled.ContentCopy,
                    label = stringResource(R.string.copy_tags),
                    onClick = { clipboard.setText(AnnotatedString(post.allTags.joinToString(" "))) },
                )
                PillAction(
                    icon = Icons.Filled.Public,
                    label = stringResource(R.string.view_on_e621, site.displayName),
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${site.webBaseUrl}/posts/${post.id}")))
                        }
                    },
                )
                PillAction(
                    icon = Icons.Filled.DataObject,
                    label = stringResource(R.string.view_raw_json),
                    onClick = {
                        showJsonDialog = true
                        if (jsonText == null && !jsonLoading) {
                            jsonLoading = true
                            jsonError = null
                            scope.launch {
                                runCatching { postActionsRepository.fetchRawJson(post.id) }
                                    .onSuccess { jsonText = it }
                                    .onFailure { e -> jsonError = String.format(jsonFailedTemplate, e.message ?: e.toString()) }
                                jsonLoading = false
                            }
                        }
                    },
                )
                PillAction(
                    icon = Icons.AutoMirrored.Filled.Comment,
                    label = stringResource(R.string.action_comments),
                    onClick = onOpenComments,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            InfoRow(stringResource(R.string.info_post_id), "#${post.id}")
            InfoRow(stringResource(R.string.info_dimensions), "${post.file.width} × ${post.file.height}")
            InfoRow(stringResource(R.string.info_file_size), formatFileSize(post.file.size))
            InfoRow(stringResource(R.string.info_file_type), post.extension.uppercase())
            post.file.md5?.let { InfoRow(stringResource(R.string.info_md5), it) }
            post.uploaderId?.let { InfoRow(stringResource(R.string.info_uploader), "#$it") }
            post.approverId?.let { InfoRow(stringResource(R.string.info_approver), "#$it") }
            post.createdAt?.let { InfoRow(stringResource(R.string.info_created), it.take(10)) }
            post.updatedAt?.let { InfoRow(stringResource(R.string.info_updated), it.take(10)) }
            InfoRow(stringResource(R.string.info_comments), "${post.commentCount}")
            InfoRow(stringResource(R.string.info_favorites), "${post.favCount}")
            InfoRow(stringResource(R.string.score), "+${post.score.up} / ${post.score.down} / ${post.score.total}")
            StatusInfoRow(post)
            if (post.flags.flagged) {
                FlagReasonBox(postId = post.id, postActionsRepository = postActionsRepository)
            }
            if (post.pools.isNotEmpty()) {
                InfoRow(stringResource(R.string.info_pools), post.pools.joinToString(", ") { "#$it" })
            }

            if (post.sources.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.info_sources), style = MaterialTheme.typography.titleSmall, color = Color.White)
                post.sources.forEach { src ->
                    Text(
                        src,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clickable {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(src))) }
                            },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.info_description), style = MaterialTheme.typography.titleSmall, color = Color.White)
            Text(
                post.description.ifBlank { stringResource(R.string.info_no_description) },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        }
    }

    if (showJsonDialog) {
        AlertDialog(
            onDismissRequest = { showJsonDialog = false },
            confirmButton = {
                TextButton(onClick = { showJsonDialog = false }) {
                    Text(stringResource(R.string.dialog_close))
                }
            },
            title = { Text(stringResource(R.string.raw_json_title)) },
            text = {
                Box(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    when {
                        jsonLoading -> CircularProgressIndicator()
                        jsonError != null -> Text(jsonError.orEmpty())
                        jsonText != null -> Text(
                            text = jsonText.orEmpty(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        )
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(
    post: Post,
    postActionsRepository: PostActionsRepository,
    avatarRepository: AvatarRepository,
    onDismiss: () -> Unit,
    onOpenProfile: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var comments by remember(post.id) { mutableStateOf<List<Comment>?>(null) }
    var loading by remember(post.id) { mutableStateOf(true) }
    var loadError by remember(post.id) { mutableStateOf<String?>(null) }
    var draft by remember(post.id) { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }

    val loadFailedTemplate = stringResource(R.string.comments_load_failed)
    val postFailedTemplate = stringResource(R.string.comments_post_failed)
    val anonymousLabel = stringResource(R.string.comments_anonymous)

    LaunchedEffect(post.id) {
        loading = true
        loadError = null
        runCatching { postActionsRepository.fetchComments(post.id) }
            .onSuccess { comments = it }
            .onFailure { e -> loadError = String.format(loadFailedTemplate, e.message ?: e.toString()) }
        loading = false
    }

    fun submitComment() {
        val body = draft.trim()
        if (body.isEmpty() || posting) return
        posting = true
        scope.launch {
            runCatching { postActionsRepository.postComment(post.id, body) }
                .onSuccess { created ->
                    comments = comments.orEmpty() + created
                    draft = ""
                }
                .onFailure { e ->
                    Toast.makeText(
                        context,
                        String.format(postFailedTemplate, e.message ?: e.toString()),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            posting = false
        }
    }

    val sheetShape = RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = sheetShape,
        containerColor = MaterialTheme.colorScheme.primary,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                .background(ViewerBackground),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 32.dp, height = 4.dp)
                    .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(7.dp)),
            )
            Text(
                text = "${stringResource(R.string.comments_title)} (${comments?.size ?: post.commentCount})",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
            ) {
                when {
                    loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    loadError != null -> Text(
                        loadError.orEmpty(),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                    comments.isNullOrEmpty() -> Text(
                        stringResource(R.string.comments_none),
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(comments.orEmpty(), key = { it.id }) { comment ->
                            CommentRow(comment, anonymousLabel, avatarRepository, onOpenProfile)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text(stringResource(R.string.comments_add_hint)) },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                )
                if (posting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    IconButton(onClick = ::submitComment, enabled = draft.isNotBlank()) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.comments_post),
                            tint = if (draft.isNotBlank()) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: Comment, anonymousLabel: String, avatarRepository: AvatarRepository, onOpenProfile: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val creatorId = comment.creatorId
        val profileModifier = if (creatorId != null) Modifier.clickable { onOpenProfile(creatorId) } else Modifier
        UserAvatar(
            userId = comment.creatorId,
            name = comment.creatorName ?: anonymousLabel,
            avatarRepository = avatarRepository,
            size = 32.dp,
            modifier = profileModifier,
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    comment.creatorName ?: anonymousLabel,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = profileModifier,
                )
                comment.createdAt?.let {
                    Text(it.take(10), color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                }
            }
            DTextView(
                text = comment.body,
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Compact, individually-backgrounded action pill - sized so 3 fit on one row without scrolling. */
@Composable
private fun PillAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.info_copied)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                clipboard.setText(AnnotatedString(value))
                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
            }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Unlike other info rows, status is colored by what it actually says rather than a copy-affordance
 * hint - deleted/pending/flagged are worth catching at a glance, so each gets its own color instead
 * of blending into the accent-colored value text every other row uses.
 */
@Composable
private fun StatusInfoRow(post: Post) {
    val (color, label) = when {
        post.flags.deleted -> PostStatusDeleted to stringResource(R.string.status_deleted)
        post.flags.pending -> PostStatusPending to stringResource(R.string.status_pending)
        post.flags.flagged -> PostStatusFlagged to stringResource(R.string.status_flagged)
        else -> PostStatusActive to stringResource(R.string.status_active)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.info_status), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(color)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        ) {
            Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * e621's flag reason (post_flags.json's `reason` field) is always public, unlike the separate
 * `note` field it also has (visibility of that one depends on server config) - so this is safe to
 * always attempt, but silently shows nothing if e621 has no visible flag record for the post
 * (e.g. the flag was since resolved) rather than an awkward "unavailable" placeholder.
 */
@Composable
private fun FlagReasonBox(postId: Long, postActionsRepository: PostActionsRepository) {
    var reason by remember(postId) { mutableStateOf<String?>(null) }

    LaunchedEffect(postId) {
        reason = runCatching { postActionsRepository.fetchFlagReason(postId) }.getOrNull()
    }

    val text = reason
    if (!text.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(RatingExplicit.copy(alpha = 0.2f))
                .border(1.dp, RatingExplicit, RoundedCornerShape(7.dp))
                .padding(10.dp),
        ) {
            Text(
                text = String.format(stringResource(R.string.flag_reason_prefix), text),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    return "%.2f MB".format(kb / 1024.0)
}

@Composable
private fun TextAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.padding(2.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RatingChip(rating: String) {
    val (color, label) = when (rating) {
        "s" -> RatingSafe to "S"
        "q" -> RatingQuestionable to "Q"
        else -> RatingExplicit to "E"
    }
    Box(
        modifier = Modifier
            .size(26.dp)
            .background(color, RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private val TAG_SECTION_ORDER = listOf(
    TagCategory.ARTIST,
    TagCategory.COPYRIGHT,
    TagCategory.CHARACTER,
    TagCategory.SPECIES,
    TagCategory.GENERAL,
    TagCategory.LORE,
    TagCategory.META,
)

@Composable
private fun TagSections(
    tags: List<CategorizedTag>,
    onAddToBlacklist: (String) -> Unit,
    onSearch: (String) -> Unit,
    onAddToSearch: (String) -> Unit,
    onExcludeFromSearch: (String) -> Unit,
) {
    val grouped = tags.groupBy { it.category }
    Column {
        TAG_SECTION_ORDER.forEach { category ->
            val items = grouped[category]
            if (!items.isNullOrEmpty()) {
                TagSection(
                    category = category,
                    tags = items,
                    onAddToBlacklist = onAddToBlacklist,
                    onSearch = onSearch,
                    onAddToSearch = onAddToSearch,
                    onExcludeFromSearch = onExcludeFromSearch,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagSection(
    category: TagCategory,
    tags: List<CategorizedTag>,
    onAddToBlacklist: (String) -> Unit,
    onSearch: (String) -> Unit,
    onAddToSearch: (String) -> Unit,
    onExcludeFromSearch: (String) -> Unit,
) {
    val headerColor = when (category) {
        TagCategory.ARTIST -> TagArtist
        TagCategory.COPYRIGHT -> TagCopyright
        TagCategory.CHARACTER -> TagCharacter
        TagCategory.SPECIES -> TagSpecies
        TagCategory.GENERAL, TagCategory.LORE, TagCategory.META -> Color.White
    }
    val label = when (category) {
        TagCategory.ARTIST -> "Artists"
        TagCategory.COPYRIGHT -> "Copyright"
        TagCategory.CHARACTER -> "Characters"
        TagCategory.SPECIES -> "Species"
        TagCategory.GENERAL -> "General"
        TagCategory.LORE -> "Lore"
        TagCategory.META -> "Meta"
    }
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(
            text = "$label · ${tags.size}",
            color = headerColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
        FlowRow(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tags.forEach { tag ->
                TagChip(
                    tag = tag,
                    onAddToBlacklist = onAddToBlacklist,
                    onSearch = onSearch,
                    onAddToSearch = onAddToSearch,
                    onExcludeFromSearch = onExcludeFromSearch,
                )
            }
        }
    }
}

@Composable
private fun TagChip(
    tag: CategorizedTag,
    onAddToBlacklist: (String) -> Unit,
    onSearch: (String) -> Unit,
    onAddToSearch: (String) -> Unit,
    onExcludeFromSearch: (String) -> Unit,
) {
    val (background, content) = when (tag.category) {
        TagCategory.ARTIST -> TagArtist to Color.Black
        TagCategory.COPYRIGHT -> TagCopyright to Color.White
        TagCategory.CHARACTER -> TagCharacter to Color.Black
        TagCategory.SPECIES -> TagSpecies to Color.White
        TagCategory.GENERAL, TagCategory.LORE, TagCategory.META -> TagGeneral to Color.White
    }
    val shape = RoundedCornerShape(7.dp)
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val addedToBlacklistTemplate = stringResource(R.string.tag_added_to_blacklist)

    Box {
        Box(
            modifier = Modifier
                .background(background, shape)
                .then(
                    // sound_warning is easy to miss among a wall of tags, so it also gets a border
                    // on top of its normal category color.
                    if (tag.name == "sound_warning") {
                        Modifier.border(1.5.dp, Color.White, shape)
                    } else {
                        Modifier
                    },
                )
                .clickable { menuExpanded = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(tag.name.replace('_', ' '), color = content, fontSize = 12.sp)
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            Text(
                text = tag.name.replace('_', ' '),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tag_menu_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onSearch(tag.name)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tag_menu_add_to_search)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onAddToSearch(tag.name)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tag_menu_exclude_from_search)) },
                leadingIcon = { Icon(Icons.Filled.Remove, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onExcludeFromSearch(tag.name)
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.tag_menu_add_blacklist),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    menuExpanded = false
                    onAddToBlacklist(tag.name)
                    Toast.makeText(context, String.format(addedToBlacklistTemplate, tag.name), Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}
