package one.proci.e621.ui.components

import one.proci.e621.ui.navigation.postSharedBounds
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import one.proci.e621.ui.theme.PillShape
import one.proci.e621.ui.theme.tabular
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.proci.e621.R
import one.proci.e621.data.model.Post
import one.proci.e621.data.util.formatCount
import one.proci.e621.ui.theme.FavoriteGold
import one.proci.e621.ui.theme.RatingExplicit
import one.proci.e621.ui.theme.RatingQuestionable
import one.proci.e621.ui.theme.RatingSafe
import one.proci.e621.ui.theme.CardShape
import one.proci.e621.ui.theme.ChipShape

private val ThumbnailShape = CardShape

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostThumbnail(
    post: Post,
    onClick: () -> Unit,
    showCautionBorder: Boolean = false,
    modifier: Modifier = Modifier,
    /** Long-press quick actions; all null (the default) means an ordinary click-only thumbnail. */
    onQuickFavorite: (() -> Unit)? = null,
    onQuickUpvote: (() -> Unit)? = null,
    onQuickDownload: (() -> Unit)? = null,
    onEnterSelection: (() -> Unit)? = null,
    /** When non-null the thumbnail is in multi-select mode; the value is whether this post is selected. */
    selected: Boolean? = null,
) {
    val aspect = if (post.preview.width > 0 && post.preview.height > 0) {
        post.preview.width.toFloat() / post.preview.height.toFloat()
    } else {
        1f
    }
    val shape = ThumbnailShape
    var menuExpanded by remember { mutableStateOf(false) }
    // A quick spring "press in" so a tap visibly lands before the viewer opens.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed || menuExpanded) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "thumbPress",
    )
    val hasQuickActions = onQuickFavorite != null || onQuickUpvote != null ||
        onQuickDownload != null || onEnterSelection != null

    Box(
        modifier = modifier
            .aspectRatio(aspect.coerceIn(0.5f, 2f))
            .postSharedBounds(post.id, enabled = !post.isVideo)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (hasQuickActions && selected == null) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = onClick,
                        onLongClick = { menuExpanded = true },
                    )
                } else {
                    Modifier.clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick)
                },
            )
            .then(
                when {
                    selected == true -> Modifier.border(3.dp, FavoriteGold, shape)
                    showCautionBorder -> Modifier.border(2.dp, CautionStripeBrush, shape)
                    else -> Modifier
                },
            ),
    ) {
        NetworkImage(
            model = post.preview.url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            shimmerWhileLoading = true,
            modifier = Modifier.fillMaxSize(),
        )

        InfoDock(post = post, modifier = Modifier.align(Alignment.BottomCenter))

        // Judged purely from the extension (see Post.isAnimated), not the file's actual content -
        // a static image saved with a .gif extension would be mislabeled, but that's rare enough
        // not to be worth actually inspecting frame data for.
        if (post.isAnimated) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(PillShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Movie,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
            }
        }

        // In multi-select mode every thumbnail shows a checkbox: blank when unselected, filled
        // (accent) when selected. A dark scrim keeps the blank box visible on light images.
        if (selected != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(ChipShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (selected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                    contentDescription = null,
                    tint = if (selected) FavoriteGold else Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            onQuickUpvote?.let { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.quick_action_upvote)) },
                    leadingIcon = { Icon(Icons.Filled.ArrowUpward, contentDescription = null) },
                    onClick = { menuExpanded = false; action() },
                )
            }
            onQuickFavorite?.let { action ->
                DropdownMenuItem(
                    text = {
                        Text(stringResource(if (post.isFavorited) R.string.quick_action_unfavorite else R.string.quick_action_favorite))
                    },
                    leadingIcon = {
                        Icon(if (post.isFavorited) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = null, tint = FavoriteGold)
                    },
                    onClick = { menuExpanded = false; action() },
                )
            }
            onQuickDownload?.let { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.quick_action_download)) },
                    leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                    onClick = { menuExpanded = false; action() },
                )
            }
            onEnterSelection?.let { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.quick_action_select)) },
                    leadingIcon = { Icon(Icons.Filled.Checklist, contentDescription = null) },
                    onClick = { menuExpanded = false; action() },
                )
            }
        }
    }
}

/**
 * Rating, score, favorite star and filetype along the bottom edge, over a soft gradient (rather
 * than a flat black band) so the image shows through. Rating anchors the left edge and filetype
 * the right, mirroring e621's own web grid, where the extension alone signals video/gif.
 */
@Composable
private fun InfoDock(post: Post, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
            .padding(start = 6.dp, end = 7.dp, top = 14.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RatingChip(rating = post.rating)
        Icon(
            imageVector = Icons.Filled.ArrowUpward,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = formatCount(post.score.total),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium.tabular(),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(x = (-4).dp),
        )
        FavoriteStar(favorited = post.isFavorited)
        Spacer(Modifier.weight(1f))
        Text(
            text = post.extension.uppercase(),
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** The favorite star pops in with a spring when a post becomes favorited (and shrinks away when it isn't). */
@Composable
private fun FavoriteStar(favorited: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (favorited) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow),
        label = "favStar",
    )
    if (scale > 0.01f) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = FavoriteGold,
            modifier = Modifier
                .size(13.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
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
            .size(18.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center,
    ) {
        // Trim the font's built-in ascent/descent padding so the letter centers optically in the badge.
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = TextStyle(
                fontSize = 10.sp,
                lineHeight = 10.sp,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
        )
    }
}
