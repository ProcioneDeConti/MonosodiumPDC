package one.proci.e621.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import one.proci.e621.R

private val ORDERS = listOf(
    "" to "Default", "score" to "Score", "favcount" to "Favorites", "comment_count" to "Comments",
    "id" to "Newest", "id_asc" to "Oldest", "mpixels" to "Resolution", "filesize" to "File size",
    "duration" to "Duration", "random" to "Random",
)
private val RATINGS = listOf("s" to "Safe", "q" to "Questionable", "e" to "Explicit")
private val TYPES = listOf("" to "Any", "jpg" to "JPG", "png" to "PNG", "gif" to "GIF", "webm" to "WEBM", "mp4" to "MP4")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdvancedSearchScreen(
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var includeTags by remember { mutableStateOf("") }
    var excludeTags by remember { mutableStateOf("") }
    var ratings by remember { mutableStateOf(setOf<String>()) }
    var order by remember { mutableStateOf("") }
    var minScore by remember { mutableStateOf("") }
    var minFavcount by remember { mutableStateOf("") }
    var dateAfter by remember { mutableStateOf("") }
    var fileType by remember { mutableStateOf("") }

    fun buildQuery(): String = buildList {
        includeTags.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { add(it) }
        excludeTags.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { add("-${it.removePrefix("-")}") }
        when (ratings.size) {
            1 -> add("rating:${ratings.first()}")
            2 -> ratings.forEach { add("~rating:$it") }
        }
        if (order.isNotBlank()) add("order:$order")
        minScore.trim().toIntOrNull()?.let { add("score:>=$it") }
        minFavcount.trim().toIntOrNull()?.let { add("favcount:>=$it") }
        dateAfter.trim().takeIf { it.isNotBlank() }?.let { add("date:>=$it") }
        if (fileType.isNotBlank()) add("type:$fileType")
    }.joinToString(" ")

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.advanced_search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = includeTags,
                onValueChange = { includeTags = it },
                label = { Text(stringResource(R.string.advanced_search_include)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = excludeTags,
                onValueChange = { excludeTags = it },
                label = { Text(stringResource(R.string.advanced_search_exclude)) },
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel(stringResource(R.string.advanced_search_rating))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RATINGS.forEach { (value, label) ->
                    FilterChip(
                        selected = value in ratings,
                        onClick = { ratings = if (value in ratings) ratings - value else ratings + value },
                        label = { Text(label) },
                    )
                }
            }

            SectionLabel(stringResource(R.string.advanced_search_order))
            ChipRow(ORDERS, order) { order = it }

            SectionLabel(stringResource(R.string.advanced_search_type))
            ChipRow(TYPES, fileType) { fileType = it }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = minScore,
                    onValueChange = { minScore = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.advanced_search_min_score)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = minFavcount,
                    onValueChange = { minFavcount = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.advanced_search_min_favcount)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            OutlinedTextField(
                value = dateAfter,
                onValueChange = { dateAfter = it },
                label = { Text(stringResource(R.string.advanced_search_date_after)) },
                placeholder = { Text("2024-01-01") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            val query = buildQuery()
            if (query.isNotBlank()) {
                Text(query, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = { onSearch(query) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.advanced_search_run))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
                shape = RoundedCornerShape(7.dp),
            )
        }
    }
}
