package one.proci.e621.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import one.proci.e621.R
import one.proci.e621.data.recovery.SourceLogins
import one.proci.e621.data.recovery.SourceLogins.KeySite
import one.proci.e621.data.recovery.SourceLogins.LoginStatus
import one.proci.e621.data.recovery.SourceRecovery
import one.proci.e621.data.recovery.SourceRecovery.KeyState
import one.proci.e621.ui.theme.CardShape

/**
 * Settings > Account > Source logins (ported from the desktop's SourceLoginsSection): the optional
 * Fur Affinity sign-in, its live state asked of FA itself, and API keys for Gelbooru / Rule34.xxx
 * (md5 lookups) and Weasyl. Everything stays encrypted on this device ([SourceLogins]); the UI only
 * learns whether something is saved, and each key is checked against its site on save and on open.
 */
@Composable
fun SourceLoginsSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, CardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.source_logins_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.source_logins_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FurAffinityRow()
        HorizontalDivider()
        Text(stringResource(R.string.source_keys_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.source_keys_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        KeySite.entries.forEach { KeyRow(it) }
    }
}

@Composable
private fun FurAffinityRow() {
    val site = SourceLogins.Site.FUR_AFFINITY
    val signedIn by SourceLogins.signedIn.collectAsState()
    val isSaved = site in signedIn
    var status by remember { mutableStateOf<LoginStatus?>(null) }
    var showLogin by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Asks FA whether the saved session still works (it may have expired or been signed out elsewhere).
    LaunchedEffect(isSaved) {
        status = if (isSaved) {
            status = null
            SourceLogins.status(site)
        } else {
            LoginStatus.SIGNED_OUT
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Fur Affinity", style = MaterialTheme.typography.bodyLarge)
            val (text, color) = when (status) {
                null -> stringResource(R.string.source_logins_checking) to MaterialTheme.colorScheme.onSurfaceVariant
                LoginStatus.SIGNED_IN -> stringResource(R.string.source_logins_signed_in) to Color(0xFF22C55E)
                LoginStatus.EXPIRED -> stringResource(R.string.source_logins_expired) to Color(0xFFF59E0B)
                LoginStatus.UNVERIFIED -> stringResource(R.string.source_logins_unverified) to Color(0xFF22C55E).copy(alpha = 0.7f)
                LoginStatus.SIGNED_OUT -> stringResource(R.string.source_logins_signed_out) to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
        }
        if (isSaved) {
            OutlinedButton(onClick = { scope.launch { SourceLogins.signOut(site) } }) { Text(stringResource(R.string.source_logins_sign_out)) }
        } else {
            Button(onClick = { showLogin = true }) { Text(stringResource(R.string.source_logins_sign_in)) }
        }
    }
    if (status == LoginStatus.EXPIRED) {
        Text(
            stringResource(R.string.source_logins_expired_help),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFF59E0B),
        )
    }
    if (showLogin) {
        SourceLoginDialog(site = site, onDone = { showLogin = false })
    }
}

@Composable
private fun KeyRow(site: KeySite) {
    val saved by SourceLogins.keysSaved.collectAsState()
    val isSaved = site in saved
    var check by remember { mutableStateOf<SourceRecovery.KeyCheck?>(null) }
    var checking by remember { mutableStateOf(false) }
    var userId by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    suspend fun runCheck() {
        checking = true
        check = SourceRecovery.checkKey(site)
        checking = false
    }
    LaunchedEffect(Unit) { if (isSaved) runCheck() }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(site.displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            val (text, color) = when {
                !isSaved -> stringResource(R.string.source_key_none) to MaterialTheme.colorScheme.onSurfaceVariant
                checking || check == null -> stringResource(R.string.source_key_checking) to MaterialTheme.colorScheme.onSurfaceVariant
                check?.state == KeyState.VALID -> stringResource(R.string.source_key_valid) to Color(0xFF22C55E)
                check?.state == KeyState.REJECTED -> stringResource(R.string.source_key_rejected) to MaterialTheme.colorScheme.error
                else -> stringResource(R.string.source_key_unverified) to Color(0xFFF59E0B)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
        }
        check?.message?.takeIf { isSaved && check?.state != KeyState.VALID }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (site.needsUserId) {
            OutlinedTextField(
                value = userId,
                onValueChange = { userId = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.source_key_user_id)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it.trim() },
            label = { Text(stringResource(if (isSaved) R.string.source_key_replace else R.string.source_key_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = apiKey.isNotBlank() && (!site.needsUserId || userId.isNotBlank()),
                onClick = {
                    scope.launch {
                        SourceLogins.saveKey(site, userId, apiKey)
                        userId = ""
                        apiKey = ""
                        runCheck()
                    }
                },
            ) { Text(stringResource(R.string.source_key_save)) }
            if (isSaved) {
                OutlinedButton(onClick = {
                    scope.launch {
                        SourceLogins.removeKey(site)
                        check = null
                    }
                }) { Text(stringResource(R.string.source_key_remove)) }
            }
            TextButton(onClick = { runCatching { uriHandler.openUri(site.keyPageUrl) } }) {
                Text(stringResource(R.string.source_key_get))
            }
        }
    }
}
