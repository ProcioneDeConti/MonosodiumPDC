package one.proci.e621.ui.screens.wiki

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import one.proci.e621.R
import one.proci.e621.ui.components.DTextView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiScreen(
    state: WikiUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onOpen: (one.proci.e621.data.model.WikiPage) -> Unit,
    onCloseSelected: () -> Unit,
    onSearchTag: (String) -> Unit,
    wikiPreview: suspend (String) -> String?,
    modifier: Modifier = Modifier,
) {
    val selected = state.selected
    BackHandler(enabled = selected != null, onBack = onCloseSelected)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(selected?.displayTitle ?: stringResource(R.string.wiki_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (selected != null) onCloseSelected() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (selected != null) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = { onSearchTag(selected.title) }) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text(stringResource(R.string.wiki_view_posts))
                }
                DTextView(
                    text = selected.body.ifBlank { stringResource(R.string.wiki_empty_body) },
                    style = MaterialTheme.typography.bodyMedium,
                    wikiPreview = wikiPreview,
                )
            }
        } else {
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                TextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(R.string.wiki_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.error != null -> Text(
                        state.error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                    state.query.isBlank() -> Text(
                        stringResource(R.string.wiki_search_prompt),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                    state.results.isEmpty() -> Text(
                        stringResource(R.string.wiki_no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                    else -> LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(state.results, key = { it.id }) { page ->
                            Text(
                                page.displayTitle,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpen(page) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
