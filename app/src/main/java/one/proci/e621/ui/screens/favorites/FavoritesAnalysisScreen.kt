package one.proci.e621.ui.screens.favorites

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import one.proci.e621.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesAnalysisScreen(
    state: FavoritesAnalysisUiState,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fav_analysis_title, state.username)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.running) {
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.fav_analysis_cancel)) }
                    } else if (state.analysis != null) {
                        TextButton(onClick = onRestart) { Text(stringResource(R.string.fav_analysis_rerun)) }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (state.running) {
                Text(stringResource(R.string.fav_analysis_progress, state.fetched))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            val a = state.analysis
            if (a != null) {
                Text(
                    stringResource(R.string.fav_analysis_total, a.total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                BarSection(stringResource(R.string.fav_analysis_ratings), a.ratings.entries.map { it.key to it.value })
                BarSection(stringResource(R.string.fav_analysis_filetypes), a.filetypes.entries.map { it.key to it.value })
                BarSection(stringResource(R.string.fav_analysis_scores), a.scoreBuckets.entries.map { it.key to it.value })
                BarSection(stringResource(R.string.fav_analysis_years), a.years.entries.map { it.key to it.value })
                BarSection(stringResource(R.string.fav_analysis_artists), a.topArtists.map { it.first.replace('_', ' ') to it.second })
                BarSection(stringResource(R.string.fav_analysis_characters), a.topCharacters.map { it.first.replace('_', ' ') to it.second })
            } else if (!state.running && state.error == null) {
                Text(stringResource(R.string.fav_analysis_start_hint))
                TextButton(onClick = onRestart) { Text(stringResource(R.string.fav_analysis_start)) }
            }
        }
    }
}

@Composable
private fun BarSection(title: String, rows: List<Pair<String, Int>>) {
    if (rows.isEmpty()) return
    val max = rows.maxOf { it.second }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        rows.forEach { (label, count) ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.4f), maxLines = 1)
                Box(modifier = Modifier.weight(0.5f).padding(end = 8.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(count.toFloat() / max)
                            .height(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Text(count.toString(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.1f))
            }
        }
    }
}
