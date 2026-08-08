package one.proci.e621.ui.screens.eula

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import one.proci.e621.R
import one.proci.e621.data.util.loadEulaText

@Composable
private fun rememberEulaText(): String {
    val context = LocalContext.current
    return remember { loadEulaText(context) }
}

/**
 * Full-screen first-launch gate - shown instead of the rest of the app until [onAgree] fires.
 * Disagreeing shows an error and closes the app after a few seconds rather than letting the user
 * back out into the app some other way; the same screen reappears on every subsequent launch
 * until the user actually agrees.
 */
@Composable
fun EulaScreen(onAgree: () -> Unit, onDisagree: () -> Unit, modifier: Modifier = Modifier) {
    var disagreed by remember { mutableStateOf(false) }

    if (disagreed) {
        LaunchedEffect(Unit) {
            delay(3000)
            onDisagree()
        }
        Box(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(
                    Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.eula_disagree_error),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(20.dp),
        ) {
            val scrollState = rememberScrollState()
            // maxValue is 0 (so this is trivially true) until the content's been laid out, and
            // also whenever the whole EULA already fits on screen without scrolling - both cases
            // correctly mean "there's nothing left to scroll past."
            val hasReachedEnd by remember { derivedStateOf { scrollState.value >= scrollState.maxValue } }

            Text(stringResource(R.string.eula_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text(
                text = rememberEulaText(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).verticalScroll(scrollState),
            )
            if (!hasReachedEnd) {
                Text(
                    stringResource(R.string.eula_scroll_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { disagreed = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.eula_disagree))
                }
                Button(onClick = onAgree, enabled = hasReachedEnd, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.eula_agree))
                }
            }
        }
    }
}

/** Read-only re-display from Settings - no agree/disagree, just a close button. */
@Composable
fun EulaReadOnlyDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.heightIn(max = 480.dp).padding(20.dp)) {
                Text(stringResource(R.string.eula_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = rememberEulaText(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.dialog_close))
                }
            }
        }
    }
}
