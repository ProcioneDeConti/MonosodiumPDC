package one.proci.e621.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.proci.e621.R
import one.proci.e621.data.model.Post
import one.proci.e621.data.util.formatCount
import one.proci.e621.ui.theme.FavoriteGold
import one.proci.e621.ui.theme.RatingExplicit
import one.proci.e621.ui.theme.RatingQuestionable
import one.proci.e621.ui.theme.RatingSafe

private val ThumbnailShape = RoundedCornerShape(7.dp)

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
    val hasQuickActions = onQuickFavorite != null || onQuickUpvote != null ||
        onQuickDownload != null || onEnterSelection != null

    Box(
        modifier = modifier
            .aspectRatio(aspect.coerceIn(0.5f, 2f))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (hasQuickActions && selected == null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true })
                } else {
                    Modifier.clickable(onClick = onClick)
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
                    .size(20.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.Black.copy(alpha = 0.55f)),
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

        if (selected == true) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(FavoriteGold),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
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
 * Replaces the old rating badge (top-left) and play/gif icon overlay (bottom-right, which read as
 * inconsistent and easy to miss) with a single bar. Rating anchors the left edge and filetype the
 * right (mirroring e621's own web grid, where the extension badge alone is enough to signal
 * "this is a video/gif" without a separate icon); score and the favorite star sit between them,
 * spaced evenly across the whole bar rather than clustered next to the rating.
 */
@Composable
private fun InfoDock(post: Post, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        RatingChip(rating = post.rating)
        Text(
            text = "Score: ${formatCount(post.score.total)}",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        if (post.isFavorited) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = FavoriteGold,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(
            text = post.extension.uppercase(),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
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
            .clip(RoundedCornerShape(2.dp))
            .background(color.copy(alpha = 0.85f))
            .padding(horizontal = 4.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
