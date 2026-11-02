package one.proci.e621.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import one.proci.e621.R
import one.proci.e621.ui.components.BarChartCard
import androidx.compose.ui.text.style.TextAlign
import one.proci.e621.ui.theme.PillShape
import one.proci.e621.ui.theme.tabular
import one.proci.e621.ui.components.ListCard
import one.proci.e621.data.settings.UsageStats
import one.proci.e621.ui.theme.CardShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val s = state.stats
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ListCard(onClick = { onSetEnabled(!state.enabled) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.dashboard_enabled),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(checked = state.enabled, onCheckedChange = onSetEnabled)
                }
                Text(
                    stringResource(R.string.dashboard_local_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val tiles = listOf(
                stringResource(R.string.dashboard_posts_viewed) to s.postsViewed,
                stringResource(R.string.dashboard_searches) to s.searches,
                stringResource(R.string.dashboard_favorites_added) to s.favoritesAdded,
                stringResource(R.string.dashboard_favorites_removed) to s.favoritesRemoved,
                stringResource(R.string.dashboard_upvotes) to s.upvotes,
                stringResource(R.string.dashboard_downvotes) to s.downvotes,
                stringResource(R.string.dashboard_downloads) to s.downloads,
            )
            tiles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f)) }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }

            BarChartCard(stringResource(R.string.dashboard_per_site), s.perSitePostsViewed.entries.map { it.key to it.value })
            BarChartCard(stringResource(R.string.dashboard_daily), s.dailyPostsViewed.entries.sortedBy { it.key }.takeLast(14).map { it.key.takeLast(5) to it.value })
            BarChartCard(stringResource(R.string.dashboard_top_artists), topN(s.topArtists))
            BarChartCard(stringResource(R.string.dashboard_top_characters), topN(s.topCharacters))

            TextButton(onClick = { confirmClear = true }) {
                Text(stringResource(R.string.dashboard_clear), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.dashboard_clear)) },
            text = { Text(stringResource(R.string.dashboard_clear_body)) },
            confirmButton = {
                TextButton(onClick = { onClear(); confirmClear = false }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.comment_action_cancel)) } },
        )
    }
}

private fun topN(map: Map<String, Int>): List<Pair<String, Int>> =
    map.entries.sortedByDescending { it.value }.take(12).map { it.key.replace('_', ' ') to it.value }

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(14.dp),
    ) {
        Text("%,d".format(value), style = MaterialTheme.typography.titleLarge.tabular(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
