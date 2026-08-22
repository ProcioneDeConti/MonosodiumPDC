package one.proci.e621.data.settings

import one.proci.e621.data.model.Post
import one.proci.e621.data.model.Rating
import one.proci.e621.data.util.GridThumbnailSize
import one.proci.e621.data.util.ImageCacheLimits
import one.proci.e621.data.util.VideoPlaybackSpeeds

data class UserSettings(
    /** Which site is currently active - e621 or e6AI (e6ai.net). Each keeps its own separate login, below. */
    val useE6Ai: Boolean = false,
    val e621Username: String = "",
    val e621ApiKey: String = "",
    val e6aiUsername: String = "",
    val e6aiApiKey: String = "",
    val enabledRatings: Set<Rating> = setOf(Rating.SAFE, Rating.QUESTIONABLE, Rating.EXPLICIT),
    /** While off, the app is locked to safe-rated content regardless of [enabledRatings]. */
    val adultModeEnabled: Boolean = false,
    val blacklist: String = "",
    /** ARGB color int; null means "use the device's dynamic/default color." */
    val accentColor: Int? = null,
    /** The hash (see [one.proci.e621.data.util.eulaHash]) of whichever EULA text the user last agreed to; null means never agreed. */
    val eulaAcceptedHash: String? = null,
    /** Max on-disk size, in MB, for cached post images/thumbnails. See [one.proci.e621.data.util.ImageCacheLimits]. */
    val imageCacheLimitMb: Int = ImageCacheLimits.DEFAULT_MB,
    /** Min adaptive cell width, in dp, for post grid thumbnails - user-adjustable via pinch/spread. See [GridThumbnailSize]. */
    val gridThumbnailSizeDp: Int = GridThumbnailSize.DEFAULT_DP,
    /** Default loop-on-end behavior for newly opened videos; a video's own in-player toggle only affects that viewing session. */
    val videoLoopEnabled: Boolean = true,
    /** Default playback speed for newly opened videos. See [VideoPlaybackSpeeds]. */
    val videoPlaybackSpeed: Float = VideoPlaybackSpeeds.DEFAULT_SPEED,
    /** Whether videos start playing automatically once scrolled into view, vs. requiring a tap. */
    val videoAutoplayEnabled: Boolean = true,
    /**
     * SAF tree URI (as a string, via [android.net.Uri.toString]) of the user-chosen folder for
     * downloaded post media; null means "use the default Pictures/e621 or Movies/e621 MediaStore
     * location" (see [one.proci.e621.data.download.MediaDownloader]).
     */
    val downloadLocationUri: String? = null,
    /**
     * The app's versionCode as of the last time the "What's New" dialog was shown or dismissed
     * (or auto-recorded on first install, so first-time users don't see it). Null means never
     * recorded yet - either a brand-new install or, like [eulaAcceptedHash], not loaded from
     * DataStore yet (guard with [isLoaded]).
     */
    val lastSeenVersionCode: Int? = null,
    /** Whether Android's Auto Backup may back this app's data up to the user's Google account. Defaults to true (the platform's own default) so existing users see no behavior change until they visit Settings. */
    val cloudBackupEnabled: Boolean = true,
    /**
     * False only for the placeholder instance DataStore hasn't finished its first real read yet -
     * every value that actually comes out of [UserPreferences.settingsFlow] sets this true. Exists
     * so callers (like the EULA gate) can tell "genuinely not agreed" apart from "don't know yet,"
     * which otherwise look identical (both have a null [eulaAcceptedHash]) and would flash the
     * EULA screen on every launch before the real value loads.
     */
    val isLoaded: Boolean = false,
) {
    val site: Site get() = if (useE6Ai) Site.E6AI else Site.E621

    /** The active site's credentials - e621 and e6AI are separate accounts, so switching sites swaps these. */
    val username: String get() = if (useE6Ai) e6aiUsername else e621Username
    val apiKey: String get() = if (useE6Ai) e6aiApiKey else e621ApiKey

    val isAuthenticated: Boolean get() = username.isNotBlank() && apiKey.isNotBlank()

    /**
     * e621/e6AI require a descriptive User-Agent identifying the app and a way to contact the
     * developer/user; generic client User-Agents get blocked with 403. See https://e621.net/help/api
     */
    val userAgent: String
        get() {
            val who = username.ifBlank { "anonymous" }
            return "e621ForAndroid/1.0 (by $who on ${site.apiHost})"
        }

    /**
     * e621 search syntax uses a leading `~` on each alternative to OR tags together
     * (e.g. "~rating:safe ~rating:questionable"). A single rating needs no `~`, and
     * having every rating enabled means no filter at all.
     */
    fun ratingTagFilter(): String? {
        val effective = if (adultModeEnabled) enabledRatings else setOf(Rating.SAFE)
        if (effective.isEmpty() || effective.size == Rating.entries.size) return null
        return if (effective.size == 1) {
            effective.first().tag
        } else {
            effective.joinToString(" ") { "~${it.tag}" }
        }
    }

    /**
     * One entry per line; a post is hidden if it carries every tag on a line (AND within a
     * line, OR across lines), mirroring e621's own client-side blacklist syntax.
     */
    private val blacklistEntries: List<List<String>> by lazy {
        blacklist.lineSequence()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .map { line -> line.split(Regex("\\s+")) }
            .toList()
    }

    private fun matchingBlacklistEntries(post: Post): List<List<String>> {
        if (blacklistEntries.isEmpty()) return emptyList()
        val postTags = HashSet<String>(post.allTags.size + 1)
        post.allTags.mapTo(postTags) { it.lowercase() }
        Rating.entries.firstOrNull { it.letter == post.rating }?.let { postTags += it.tag }
        return blacklistEntries.filter { entry -> entry.all { it in postTags } }
    }

    fun isBlacklisted(post: Post): Boolean = matchingBlacklistEntries(post).isNotEmpty()

    /**
     * Lowercased tags (the union of every matching blacklist line's terms) responsible for [post]
     * matching the blacklist - empty if it doesn't match. May include a rating pseudo-tag (e.g.
     * "rating:explicit") if that's what a line matched on. Used to highlight which of a post's tag
     * chips are the reason it was hidden, on a post shown anyway via [one.proci.e621.data.settings.UserPreferences.blacklistDisabled].
     */
    fun matchingBlacklistTags(post: Post): Set<String> = matchingBlacklistEntries(post).flatten().toSet()
}
