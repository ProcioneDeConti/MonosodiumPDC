package one.proci.e621.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Subset of https://e621.net/users/<id>.json - blacklisted_tags, has_mail, unread_dmail_count,
 * and forum_notification_dot are only present when the authenticated requester is viewing their
 * own profile (e.g. via users/me.json). The stat/count fields and profile text below (see
 * e621ng's `User#full_attributes`) are public on any profile.
 */
@Serializable
data class UserProfile(
    val id: Long,
    val name: String = "",
    val level: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("blacklisted_tags") val blacklistedTags: String? = null,
    @SerialName("has_mail") val hasMail: Boolean = false,
    @SerialName("unread_dmail_count") val unreadDmailCount: Int = 0,
    @SerialName("forum_notification_dot") val forumNotificationDot: Boolean = false,
    /** A post id; the actual avatar image is that post's preview thumbnail (see [one.proci.e621.data.repository.AvatarRepository]). */
    @SerialName("avatar_id") val avatarId: Long? = null,
    @SerialName("wiki_page_version_count") val wikiPageVersionCount: Int? = null,
    @SerialName("artist_version_count") val artistVersionCount: Int? = null,
    @SerialName("pool_version_count") val poolVersionCount: Int? = null,
    @SerialName("forum_post_count") val forumPostCount: Int? = null,
    @SerialName("comment_count") val commentCount: Int? = null,
    @SerialName("flag_count") val flagCount: Int? = null,
    @SerialName("favorite_count") val favoriteCount: Int? = null,
    @SerialName("positive_feedback_count") val positiveFeedbackCount: Int? = null,
    @SerialName("neutral_feedback_count") val neutralFeedbackCount: Int? = null,
    @SerialName("negative_feedback_count") val negativeFeedbackCount: Int? = null,
    @SerialName("upload_slots") val uploadSlots: Int? = null,
    // e621ng "method_attributes" - present on newer e621ng, may be absent, so all optional.
    @SerialName("level_string") val levelString: String? = null,
    @SerialName("upload_karma") val uploadKarma: Int? = null,
    @SerialName("base_upload_limit") val baseUploadLimit: Int? = null,
    @SerialName("post_upload_count") val postUploadCount: Int? = null,
    @SerialName("post_update_count") val postUpdateCount: Int? = null,
    @SerialName("note_update_count") val noteUpdateCount: Int? = null,
    @SerialName("can_approve_posts") val canApprovePosts: Boolean = false,
    @SerialName("can_upload_free") val canUploadFree: Boolean = false,
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("profile_about") val profileAbout: String? = null,
    @SerialName("profile_artinfo") val profileArtinfo: String? = null,
)

/**
 * e621ng derives a 0-10 "upload level" from `upload_karma` on a log scale (`User.level_from_karma`);
 * it isn't serialized, so this recomputes it from e621ng's *default* thresholds (L1 = 100 karma,
 * L10 = 10 000, scale 4.5). e621.net could override those in config, but they check out against
 * real top uploaders (~80-110k karma all land at level 10). Mirrors the desktop app's `uploadKarmaLevel`.
 */
private const val KARMA_L1 = 100.0
private const val KARMA_SCALE = 4.5
private const val MAX_UPLOAD_LEVEL = 10

fun uploadKarmaLevel(karma: Int): Int {
    if (karma < KARMA_L1) return 0
    return (Math.floor(Math.log10(karma / KARMA_L1) * KARMA_SCALE).toInt() + 1).coerceAtMost(MAX_UPLOAD_LEVEL)
}

private fun karmaForLevel(level: Int): Int {
    if (level <= 0) return 0
    return Math.ceil(KARMA_L1 * Math.pow(10.0, (level - 1) / KARMA_SCALE)).toInt()
}

data class UploadKarmaProgress(val level: Int, val isMax: Boolean, val percent: Float, val toNext: Int)

fun uploadKarmaProgress(karma: Int): UploadKarmaProgress {
    val level = uploadKarmaLevel(karma)
    if (level >= MAX_UPLOAD_LEVEL) return UploadKarmaProgress(level, isMax = true, percent = 1f, toNext = 0)
    val cur = karmaForLevel(level)
    val next = karmaForLevel(level + 1)
    val span = (next - cur).coerceAtLeast(1)
    return UploadKarmaProgress(
        level = level,
        isMax = false,
        percent = ((karma - cur).toFloat() / span).coerceIn(0f, 1f),
        toNext = (next - karma).coerceAtLeast(0),
    )
}

/** e621's `UserLevel::MAPPING` (app/logical/user_level.rb) - unrecognized values fall back to the raw number. */
private val UserLevelLabels = mapOf(
    0 to "Anonymous",
    10 to "Blocked",
    20 to "Member",
    30 to "Privileged",
    40 to "Former Staff",
    50 to "Staff",
    60 to "Janitor",
    70 to "Moderator",
    80 to "Admin",
)

fun UserProfile.levelLabel(): String? = level?.let { UserLevelLabels[it] ?: "Level $it" }

@Serializable
data class UpdateUserRequest(val user: UpdateUserFields)

@Serializable
data class UpdateUserFields(@SerialName("blacklisted_tags") val blacklistedTags: String)
