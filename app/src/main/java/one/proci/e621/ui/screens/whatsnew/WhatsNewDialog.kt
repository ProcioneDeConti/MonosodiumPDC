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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import one.proci.e621.BuildConfig
import one.proci.e621.R

/**
 * Shown once automatically after an update (see [one.proci.e621.data.settings.UserSettings.lastSeenVersionCode])
 * and reachable anytime from Settings > About. Content is a single static string updated by hand
 * each release, describing only the current version's changes - not a full historical changelog.
 */
@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(7.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.heightIn(max = 480.dp).padding(20.dp)) {
                Text(
                    stringResource(R.string.whats_new_title, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.whats_new_body),
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
