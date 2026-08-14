package one.proci.e621.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import kotlinx.coroutines.launch
import one.proci.e621.BuildConfig
import one.proci.e621.R
import one.proci.e621.data.model.Rating
import one.proci.e621.data.settings.UserSettings
import one.proci.e621.data.util.ImageCacheLimits
import one.proci.e621.data.util.VideoPlaybackSpeeds
import one.proci.e621.ui.screens.eula.EulaReadOnlyDialog
import one.proci.e621.ui.theme.AccentPresets

/** Fixed regardless of the user's accent color - a beta badge should always read as gold, not blend in. */
private val BetaGold = Color(0xFFFFD700)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    isSyncing: Boolean,
    onBack: () -> Unit,
    onSaveAccount: suspend (username: String, apiKey: String) -> AccountSaveOutcome,
    onSetAdultModeEnabled: (Boolean) -> Unit,
    onSetUseE6Ai: (Boolean) -> Unit,
    onSetRatingEnabled: (Rating, Boolean) -> Unit,
    onSaveBlacklist: (String) -> Unit,
    onImportBlacklist: suspend () -> Result<String>,
    onPushBlacklist: suspend (String) -> Result<Unit>,
    onSetAccentColor: (Int?) -> Unit,
    onSetImageCacheLimitMb: (Int) -> Unit,
    onSetVideoLoopEnabled: (Boolean) -> Unit,
    onSetVideoPlaybackSpeed: (Float) -> Unit,
    onSetVideoAutoplayEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var username by remember(settings.username) { mutableStateOf(settings.username) }
    var apiKey by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }
    var blacklist by remember(settings.blacklist) { mutableStateOf(settings.blacklist) }
    var showEulaDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val savedMessage = stringResource(R.string.settings_saved)
    val authSuccessMessage = stringResource(R.string.settings_auth_success)
    val authFailedTemplate = stringResource(R.string.settings_auth_failed)
    val importedMessage = stringResource(R.string.settings_blacklist_imported, settings.site.displayName)
    val pushedMessage = stringResource(R.string.settings_blacklist_pushed, settings.site.displayName)
    val syncFailedTemplate = stringResource(R.string.settings_blacklist_sync_failed)
    val cacheClearedMessage = stringResource(R.string.settings_cache_cleared)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSection(stringResource(R.string.settings_adult_mode), titleTrailing = {
                Switch(checked = settings.adultModeEnabled, onCheckedChange = onSetAdultModeEnabled)
            }) {
                Text(
                    stringResource(R.string.settings_adult_mode_disclaimer),
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsSection(stringResource(R.string.settings_site), titleTrailing = {
                Switch(checked = settings.useE6Ai, onCheckedChange = onSetUseE6Ai)
            }) {
                Text(
                    stringResource(R.string.settings_site_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsSection(stringResource(R.string.settings_account)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.settings_username, settings.site.displayName)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.settings_api_key)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.settings_api_key_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.settings_api_key_get_link, settings.site.apiHost),
                    style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { uriHandler.openUri("${settings.site.webBaseUrl}/api_keys") },
                )
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        stringResource(R.string.settings_api_key_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(onClick = {
                    scope.launch {
                        val message = when (val outcome = onSaveAccount(username, apiKey)) {
                            AccountSaveOutcome.Saved -> savedMessage
                            AccountSaveOutcome.Authenticated -> authSuccessMessage
                            is AccountSaveOutcome.AuthFailed -> String.format(authFailedTemplate, outcome.message)
                        }
                        snackbarHostState.showSnackbar(message)
                    }
                }) {
                    Text(stringResource(R.string.settings_save))
                }
            }

            SettingsSection(stringResource(R.string.settings_appearance)) {
                Text(
                    stringResource(R.string.settings_accent_color),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AccentColorPicker(currentColor = settings.accentColor, onSelect = onSetAccentColor)
            }

            SettingsSection(stringResource(R.string.settings_cache)) {
                Text(
                    stringResource(R.string.settings_cache_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                var cacheUsageBytes by remember { mutableLongStateOf(0L) }
                @OptIn(DelicateCoilApi::class)
                LaunchedEffect(Unit) {
                    cacheUsageBytes = SingletonImageLoader.get(context).diskCache?.size ?: 0L
                }
                Text(
                    stringResource(R.string.settings_cache_usage, formatCacheSize((cacheUsageBytes / (1024 * 1024)).toInt())),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                CacheLimitSlider(
                    currentMb = settings.imageCacheLimitMb,
                    onLimitChanged = onSetImageCacheLimitMb,
                )
                OutlinedButton(onClick = {
                    @OptIn(DelicateCoilApi::class)
                    SingletonImageLoader.get(context).apply {
                        diskCache?.clear()
                        memoryCache?.clear()
                    }
                    cacheUsageBytes = 0L
                    scope.launch { snackbarHostState.showSnackbar(cacheClearedMessage) }
                }) {
                    Text(stringResource(R.string.settings_cache_clear))
                }
            }

            SettingsSection(stringResource(R.string.settings_video)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_video_autoplay), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = settings.videoAutoplayEnabled, onCheckedChange = onSetVideoAutoplayEnabled)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_video_loop), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = settings.videoLoopEnabled, onCheckedChange = onSetVideoLoopEnabled)
                }
                VideoSpeedSlider(
                    currentSpeed = settings.videoPlaybackSpeed,
                    onSpeedChanged = onSetVideoPlaybackSpeed,
                )
            }

            SettingsSection(stringResource(R.string.settings_content)) {
                // Without 18+ Mode, content is locked to safe regardless of what's stored here.
                RatingRow(
                    label = stringResource(R.string.settings_rating_safe),
                    checked = !settings.adultModeEnabled || Rating.SAFE in settings.enabledRatings,
                    enabled = settings.adultModeEnabled,
                    onCheckedChange = { onSetRatingEnabled(Rating.SAFE, it) },
                )
                RatingRow(
                    label = stringResource(R.string.settings_rating_questionable),
                    checked = settings.adultModeEnabled && Rating.QUESTIONABLE in settings.enabledRatings,
                    enabled = settings.adultModeEnabled,
                    onCheckedChange = { onSetRatingEnabled(Rating.QUESTIONABLE, it) },
                )
                RatingRow(
                    label = stringResource(R.string.settings_rating_explicit),
                    checked = settings.adultModeEnabled && Rating.EXPLICIT in settings.enabledRatings,
                    enabled = settings.adultModeEnabled,
                    onCheckedChange = { onSetRatingEnabled(Rating.EXPLICIT, it) },
                )
            }

            SettingsSection(stringResource(R.string.settings_blacklist)) {
                Text(
                    stringResource(R.string.settings_blacklist_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Capped height so a long blacklist can't push the rest of the settings screen
                // off-screen; the field scrolls internally once its content exceeds that height.
                OutlinedTextField(
                    value = blacklist,
                    onValueChange = { blacklist = it },
                    placeholder = { Text(stringResource(R.string.settings_blacklist_placeholder)) },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp),
                )
                Button(onClick = {
                    onSaveBlacklist(blacklist)
                    scope.launch { snackbarHostState.showSnackbar(savedMessage) }
                }) {
                    Text(stringResource(R.string.settings_save))
                }

                Text(
                    stringResource(R.string.settings_blacklist_sync_hint, settings.site.displayName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        enabled = settings.isAuthenticated && !isSyncing,
                        onClick = {
                            scope.launch {
                                onImportBlacklist().fold(
                                    onSuccess = { imported ->
                                        blacklist = imported
                                        snackbarHostState.showSnackbar(importedMessage)
                                    },
                                    onFailure = { e -> snackbarHostState.showSnackbar(formatSyncFailed(syncFailedTemplate, e)) },
                                )
                            }
                        },
                    ) {
                        Text(stringResource(R.string.settings_blacklist_import, settings.site.displayName))
                    }
                    OutlinedButton(
                        enabled = settings.isAuthenticated && !isSyncing,
                        onClick = {
                            scope.launch {
                                onPushBlacklist(blacklist).fold(
                                    onSuccess = { snackbarHostState.showSnackbar(pushedMessage) },
                                    onFailure = { e -> snackbarHostState.showSnackbar(formatSyncFailed(syncFailedTemplate, e)) },
                                )
                            }
                        },
                    ) {
                        Text(stringResource(R.string.settings_blacklist_push, settings.site.displayName))
                    }
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }

            SettingsSection(stringResource(R.string.settings_legal)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.eula_agreed_label), style = MaterialTheme.typography.bodyLarge)
                }
                OutlinedButton(onClick = { showEulaDialog = true }) {
                    Text(stringResource(R.string.eula_read_again))
                }
            }

            CreditsFooter()
        }
    }

    if (showEulaDialog) {
        EulaReadOnlyDialog(onDismiss = { showEulaDialog = false })
    }
}

@Composable
private fun CreditsFooter() {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            buildAnnotatedString {
                append(stringResource(R.string.settings_credits_version, BuildConfig.VERSION_NAME))
                append(" ")
                withStyle(SpanStyle(color = BetaGold, fontWeight = FontWeight.Bold)) {
                    append(stringResource(R.string.settings_credits_beta))
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.settings_credits_copyright, BuildConfig.BUILD_TIME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.settings_credits_handle),
            style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable { uriHandler.openUri("https://t.me/ProcioneDeConti") },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    titleTrailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(7.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (titleTrailing != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                titleTrailing()
            }
        } else {
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        content()
    }
}

private fun formatSyncFailed(template: String, e: Throwable): String =
    String.format(template, e.message ?: e.toString())

private fun formatCacheSize(mb: Int, unlimitedLabel: String = "Unlimited"): String = when {
    mb == ImageCacheLimits.UNLIMITED -> unlimitedLabel
    mb >= 1000 -> "%.1f GB".format(mb / 1000.0)
    else -> "$mb MB"
}

@Composable
private fun CacheLimitSlider(currentMb: Int, onLimitChanged: (Int) -> Unit) {
    // The slider works in discrete indices rather than raw MB, since its rightmost position is
    // the "Unlimited" sentinel rather than another numeric step. Local state mirrors the drag
    // position live; the setting itself is only committed (and the image cache resized) once the
    // user releases the thumb, not on every intermediate value.
    var sliderIndex by remember(currentMb) { mutableFloatStateOf(ImageCacheLimits.indexForMb(currentMb).toFloat()) }
    val steps = ImageCacheLimits.lastIndex - 1
    val unlimitedLabel = stringResource(R.string.settings_cache_unlimited)

    Column {
        Text(
            stringResource(
                R.string.settings_cache_limit_value,
                formatCacheSize(ImageCacheLimits.mbForIndex(sliderIndex.toInt()), unlimitedLabel),
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = sliderIndex,
            onValueChange = { sliderIndex = it },
            onValueChangeFinished = {
                onLimitChanged(ImageCacheLimits.mbForIndex(Math.round(sliderIndex)))
            },
            valueRange = 0f..ImageCacheLimits.lastIndex.toFloat(),
            steps = steps,
        )
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toLong().toFloat()) "${speed.toLong()}x" else "${speed}x"

@Composable
private fun VideoSpeedSlider(currentSpeed: Float, onSpeedChanged: (Float) -> Unit) {
    // Same discrete-index-with-live-local-state approach as CacheLimitSlider: the setting itself
    // only commits once the user releases the thumb, not on every intermediate drag value.
    var sliderIndex by remember(currentSpeed) {
        mutableFloatStateOf(VideoPlaybackSpeeds.indexForSpeed(currentSpeed).toFloat())
    }

    Column {
        Text(
            stringResource(R.string.settings_video_speed_value, formatSpeed(VideoPlaybackSpeeds.speedForIndex(sliderIndex.toInt()))),
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = sliderIndex,
            onValueChange = { sliderIndex = it },
            onValueChangeFinished = {
                onSpeedChanged(VideoPlaybackSpeeds.speedForIndex(Math.round(sliderIndex)))
            },
            valueRange = 0f..VideoPlaybackSpeeds.lastIndex.toFloat(),
            steps = VideoPlaybackSpeeds.lastIndex - 1,
        )
    }
}

@Composable
private fun RatingRow(label: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentColorPicker(currentColor: Int?, onSelect: (Int?) -> Unit) {
    var customHex by remember(currentColor) {
        mutableStateOf(currentColor?.let { "#%06X".format(0xFFFFFF and it) } ?: "")
    }
    var customError by remember { mutableStateOf(false) }

    Column {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ColorSwatch(
                background = MaterialTheme.colorScheme.surfaceVariant,
                selected = currentColor == null,
                onClick = { onSelect(null) },
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = stringResource(R.string.settings_accent_default),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            AccentPresets.forEach { preset ->
                ColorSwatch(
                    background = preset,
                    selected = currentColor == preset.toArgb(),
                    onClick = { onSelect(preset.toArgb()) },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = customHex,
                onValueChange = { customHex = it; customError = false },
                label = { Text(stringResource(R.string.settings_accent_custom_hex)) },
                singleLine = true,
                isError = customError,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = {
                val parsed = parseHexColor(customHex)
                if (parsed != null) {
                    customError = false
                    onSelect(parsed)
                } else {
                    customError = true
                }
            }) {
                Text(stringResource(R.string.settings_accent_custom_apply))
            }
        }
        if (customError) {
            Text(
                stringResource(R.string.settings_accent_custom_invalid),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    background: Color,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(background)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

private fun parseHexColor(input: String): Int? {
    val hex = input.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 8) return null
    return try {
        android.graphics.Color.parseColor("#${if (hex.length == 6) "FF$hex" else hex}")
    } catch (e: IllegalArgumentException) {
        null
    }
}
