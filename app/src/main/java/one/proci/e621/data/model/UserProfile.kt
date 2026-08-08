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
    @SerialName("profile_about") val profileAbout: String? = null,
    @SerialName("profile_artinfo") val profileArtinfo: String? = null,
)

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
