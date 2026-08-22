package one.proci.e621.data.repository

import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import one.proci.e621.data.api.E621ApiService
import one.proci.e621.data.util.PERMANENTLY_HIDDEN_TAG

/** Sentinel stored in [AvatarRepository.cache] for "looked up, no avatar available" - ConcurrentHashMap can't hold null values. */
private const val NoAvatar = ""

/**
 * e621 has no direct "avatar image URL" API field - only `avatar_id`, which is a post id whose
 * preview thumbnail *is* the avatar (see e621ng's `user_avatar_url_cache.rb`: the uncropped-avatar
 * path, which is what's used here; cropped avatars are server-rendered and not reproducible from
 * public API data, so those users just fall back to no avatar). Resolving one therefore takes two
 * requests (user -> avatar post), so results are cached in memory for the process lifetime to
 * avoid repeating that for the same commenter/poster/sender across screens.
 */
class AvatarRepository(private val api: E621ApiService) {

    private val cache = ConcurrentHashMap<Long, String>()

    suspend fun fetchAvatarUrl(userId: Long): String? {
        cache[userId]?.let { return it.ifEmpty { null } }
        val url = runCatching {
            val avatarId = api.getUser(userId).avatarId ?: return@runCatching null
            val raw = api.getPostRawJson(avatarId).use { it.string() }
            val root = Json.parseToJsonElement(raw) as? JsonObject
            val postObj = (root?.get("post") as? JsonObject) ?: root
            // This fetches the avatar post directly by id, bypassing PostRepository.fetchPosts'
            // `-notkastar` search exclusion - checked again here so an avatar can't leak a
            // permanently hidden post's thumbnail. See PERMANENTLY_HIDDEN_TAG.
            val tagsObj = postObj?.get("tags") as? JsonObject
            val isHidden = tagsObj?.values.orEmpty().any { category ->
                (category as? JsonArray).orEmpty().any {
                    (it as? JsonPrimitive)?.contentOrNull?.equals(PERMANENTLY_HIDDEN_TAG, ignoreCase = true) == true
                }
            }
            if (isHidden) return@runCatching null
            ((postObj?.get("preview") as? JsonObject)?.get("url") as? JsonPrimitive)?.contentOrNull
        }.getOrNull()
        cache[userId] = url ?: NoAvatar
        return url
    }
}
