package one.proci.e621.ui.screens.whatsnew

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import one.proci.e621.R

/**
 * A hand-curated release history, newest first, capped at 10 entries by convention (see
 * `whats_new_versions`/`whats_new_entries` in strings.xml) - NOT one entry per versionCode/every
 * bump. Shown once automatically after an update (see
 * [one.proci.e621.data.settings.UserSettings.lastSeenVersionCode]) and reachable anytime from
 * Settings > Updates.
 */
@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    val versions = stringArrayResource(R.array.whats_new_versions)
    val bodies = stringArrayResource(R.array.whats_new_entries)
    val entries = remember(versions, bodies) { versions.zip(bodies) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(7.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.heightIn(max = 520.dp).padding(20.dp)) {
                Text(
                    stringResource(R.string.whats_new_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    entries.forEachIndexed { index, (version, body) ->
                        Text(
                            "v$version",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(text = body, style = MaterialTheme.typography.bodyMedium)
                        if (index != entries.lastIndex) {
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.dialog_close))
                }
            }
        }
    }
}
