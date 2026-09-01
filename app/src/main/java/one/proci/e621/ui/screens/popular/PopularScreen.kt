package one.proci.e621.ui.screens.popular

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import one.proci.e621.R
import one.proci.e621.data.repository.PopularScale
import one.proci.e621.ui.components.PostGridBody

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PopularScreen(
    state: PopularUiState,
    onBack: () -> Unit,
    onSetScale: (PopularScale) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onNow: () -> Unit,
    onRefresh: () -> Unit,
    onPostClick: (index: Int) -> Unit,
    onSetBlacklistDisabled: (Boolean) -> Unit,
    onThumbnailSizeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.popular_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { onSetBlacklistDisabled(!state.blacklistDisabled) }) {
                        Icon(
                            if (state.blacklistDisabled) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = stringResource(
                                if (state.blacklistDisabled) R.string.blacklist_enable else R.string.blacklist_disable,
                            ),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Day / Week / Month
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val options = listOf(
                    PopularScale.DAY to stringResource(R.string.popular_scale_day),
                    PopularScale.WEEK to stringResource(R.string.popular_scale_week),
                    PopularScale.MONTH to stringResource(R.string.popular_scale_month),
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .fillMaxWidth(),
                ) {
                    options.forEach { (scale, label) ->
                        val selected = scale == state.scale
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSetScale(scale) }
                                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            // < date > + Now
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.popular_previous))
                }
                Text(
                    state.date.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = onNext, enabled = !state.atNow) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.popular_next))
                }
                TextButton(onClick = onNow, enabled = !state.atNow) { Text(stringResource(R.string.popular_now)) }
            }

            PostGridBody(
                posts = state.posts,
                isRefreshing = state.isLoading,
                isLoadingMore = false,
                error = state.error,
                onRefresh = onRefresh,
                onLoadMore = {},
                onDismissError = onRefresh,
                onPostClick = onPostClick,
                blacklistDisabled = state.blacklistDisabled,
                blacklistedIds = state.blacklistedIds,
                onEnableBlacklist = { onSetBlacklistDisabled(false) },
                thumbnailSizeDp = state.gridThumbnailSizeDp,
                onThumbnailSizeChange = onThumbnailSizeChange,
            )
        }
    }
}
