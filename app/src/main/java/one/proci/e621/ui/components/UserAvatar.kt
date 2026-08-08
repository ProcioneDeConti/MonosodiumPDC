package one.proci.e621.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import one.proci.e621.data.repository.AvatarRepository

/**
 * A circular user avatar, lazily resolved (see [AvatarRepository]) and cached for the session.
 * Falls back to the user's initial on a tinted circle - which reflects the app's accent color via
 * `primaryContainer` - while loading, on failure, or for users with no avatar set.
 */
@Composable
fun UserAvatar(
    userId: Long?,
    name: String?,
    avatarRepository: AvatarRepository,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    var url by remember(userId) { mutableStateOf<String?>(null) }
    LaunchedEffect(userId) {
        url = userId?.let { avatarRepository.fetchAvatarUrl(it) }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            Text(
                text = (name?.trim()?.firstOrNull()?.uppercaseChar() ?: '?').toString(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp,
            )
        }
    }
}
