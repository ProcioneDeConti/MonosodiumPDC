package one.proci.e621.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.proci.e621.data.model.Post
import one.proci.e621.data.util.formatCount
import one.proci.e621.ui.theme.FavoriteGold
import one.proci.e621.ui.theme.RatingExplicit
import one.proci.e621.ui.theme.RatingQuestionable
import one.proci.e621.ui.theme.RatingSafe

/**
 * Diagonal golden-yellow/dark-grey repeating stripe, like caution tape - painted only into a thin
 * border ring (see [PostThumbnail]'s `showCautionBorder`), not the whole thumbnail, so it reads as
 * "temporarily unhidden" rather than a loud warning. [TileMode.Repeated] tiles this short diagonal
 * segment across the whole border regardless of the thumbnail's actual size.
 */
private val CautionYellow = Color(0xFFFFC107)
private val CautionDarkGrey = Color(0xFF2B2B2B)
private val CautionStripeBrush = Brush.linearGradient(
    colorStops = arrayOf(0.0f to CautionYellow, 0.5f to CautionYellow, 0.5f to CautionDarkGrey, 1.0f to CautionDarkGrey),
    start = Offset.Zero,
    end = Offset(16f, 16f),
    tileMode = TileMode.Repeated,
)

private val ThumbnailShape = RoundedCornerShape(7.dp)

@Composable
fun PostThumbnail(post: Post, onClick: () -> Unit, showCautionBorder: Boolean = false, modifier: Modifier = Modifier) {
    val aspect = if (post.preview.width > 0 && post.preview.height > 0) {
        post.preview.width.toFloat() / post.preview.height.toFloat()
    } else {
        1f
    }
    val shape = ThumbnailShape

    Box(
        modifier = modifier
            .aspectRatio(aspect.coerceIn(0.5f, 2f))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .then(if (showCautionBorder) Modifier.border(2.dp, CautionStripeBrush, shape) else Modifier),
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
