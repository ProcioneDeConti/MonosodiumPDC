package one.proci.e621.data.repository

import one.proci.e621.data.api.E621ApiService
import one.proci.e621.data.model.UpdateUserFields
import one.proci.e621.data.model.UpdateUserRequest
import one.proci.e621.data.model.UserProfile
import java.io.IOException

data class NotificationStatus(val unreadDmailCount: Int, val forumUnread: Boolean)

/** Reads/writes the user's own e621 account settings - currently just the blacklist. */
class UserRepository(private val api: E621ApiService) {

    /** Requires username + API key to be configured; throws on failure (e.g. bad credentials). */
    suspend fun fetchRemoteBlacklist(): String {
        return api.getCurrentUser().blacklistedTags.orEmpty()
    }

    /** Null [id] fetches the signed-in user's own profile (requires username + API key); otherwise public. */
    suspend fun fetchProfile(id: Long?): UserProfile =
        if (id == null) api.getCurrentUser() else api.getUser(id)

    /** Backs the top bar's Messages/Forum badges; requires username + API key to be configured. */
    suspend fun fetchNotificationStatus(): NotificationStatus {
        val me = api.getCurrentUser()
        return NotificationStatus(me.unreadDmailCount, me.forumNotificationDot)
    }

    /** Pushes the local blacklist to e621, overwriting whatever is currently saved there. */
    suspend fun pushBlacklist(blacklist: String) {
        val id = api.getCurrentUser().id
        val response = api.updateUser(id, UpdateUserRequest(UpdateUserFields(blacklist)))
        if (!response.isSuccessful) {
            throw IOException("e621 rejected the update (HTTP ${response.code()})")
        }
    }
}
