package one.proci.e621.data.backup

import kotlinx.serialization.Serializable
import one.proci.e621.data.model.Rating
import one.proci.e621.data.settings.UserSettings

/** Portable snapshot of everything in [UserSettings] worth restoring - i.e. everything except [UserSettings.isLoaded]. */
@Serializable
data class SettingsBackup(
    val useE6Ai: Boolean,
    val e621Username: String,
    val e621ApiKey: String,
    val e6aiUsername: String,
    val e6aiApiKey: String,
    /** [Rating.name] values, rather than the enum itself, so this format doesn't depend on how that model type serializes. */
    val enabledRatings: List<String>,
    val adultModeEnabled: Boolean,
    val blacklist: String,
    val accentColor: Int? = null,
    val eulaAcceptedHash: String? = null,
    val imageCacheLimitMb: Int,
    val gridThumbnailSizeDp: Int,
    val videoLoopEnabled: Boolean,
    val videoPlaybackSpeed: Float,
    val videoAutoplayEnabled: Boolean,
    val downloadLocationUri: String? = null,
)

fun UserSettings.toBackup() = SettingsBackup(
    useE6Ai = useE6Ai,
    e621Username = e621Username,
    e621ApiKey = e621ApiKey,
    e6aiUsername = e6aiUsername,
    e6aiApiKey = e6aiApiKey,
    enabledRatings = enabledRatings.map { it.name },
    adultModeEnabled = adultModeEnabled,
    blacklist = blacklist,
    accentColor = accentColor,
    eulaAcceptedHash = eulaAcceptedHash,
    imageCacheLimitMb = imageCacheLimitMb,
    gridThumbnailSizeDp = gridThumbnailSizeDp,
    videoLoopEnabled = videoLoopEnabled,
    videoPlaybackSpeed = videoPlaybackSpeed,
    videoAutoplayEnabled = videoAutoplayEnabled,
    downloadLocationUri = downloadLocationUri,
)
