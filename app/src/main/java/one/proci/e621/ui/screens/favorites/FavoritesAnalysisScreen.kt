package one.proci.e621.ui.screens.favorites

import one.proci.e621.ui.components.RainbowLinearIndicator
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
import androidx.compose.material.icons.filled.Insights
import one.proci.e621.ui.components.EmptyState
import one.proci.e621.ui.components.ErrorState
import one.proci.e621.ui.components.ListCard
import one.proci.e621.ui.components.BarChartCard

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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.running) {
                ListCard {
                    Text(stringResource(R.string.fav_analysis_progress, state.fetched))
                    RainbowLinearIndicator(modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(4.dp))
                }
            }
            state.error?.let { ErrorState(message = it, onRetry = onRestart) }

            val a = state.analysis
            if (a != null) {
                Text(
                    stringResource(R.string.fav_analysis_total, a.total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                BarChartCard(stringResource(R.string.fav_analysis_ratings), a.ratings.entries.map { it.key to it.value })
                BarChartCard(stringResource(R.string.fav_analysis_filetypes), a.filetypes.entries.map { it.key to it.value })
                BarChartCard(stringResource(R.string.fav_analysis_scores), a.scoreBuckets.entries.map { it.key to it.value })
                BarChartCard(stringResource(R.string.fav_analysis_years), a.years.entries.map { it.key to it.value })
                BarChartCard(stringResource(R.string.fav_analysis_artists), a.topArtists.map { it.first.replace('_', ' ') to it.second })
                BarChartCard(stringResource(R.string.fav_analysis_characters), a.topCharacters.map { it.first.replace('_', ' ') to it.second })
            } else if (!state.running && state.error == null) {
                EmptyState(
                    icon = Icons.Filled.Insights,
                    title = stringResource(R.string.fav_analysis_start_hint),
                    actionLabel = stringResource(R.string.fav_analysis_start),
                    onAction = onRestart,
                )
            }
        }
    }
}
