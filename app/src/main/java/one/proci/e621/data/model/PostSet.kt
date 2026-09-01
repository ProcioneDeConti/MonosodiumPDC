package one.proci.e621.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** https://e621.net/post_sets.json - a user-curated ordered collection of posts. */
@Serializable
data class PostSet(
    val id: Long,
    val name: String = "",
    val shortname: String = "",
    val description: String = "",
    @SerialName("is_public") val isPublic: Boolean = false,
    @SerialName("post_count") val postCount: Int = 0,
    @SerialName("post_ids") val postIds: List<Long> = emptyList(),
    @SerialName("creator_id") val creatorId: Long? = null,
)

@Serializable
data class CreatePostSetRequest(@SerialName("post_set") val postSet: CreatePostSetFields)

@Serializable
data class CreatePostSetFields(
    val name: String,
    val shortname: String,
    val description: String = "",
    @SerialName("is_public") val isPublic: Boolean = false,
)

/**
 * Body for `POST post_sets/:id/add_posts.json` / `remove_posts.json` - a top-level `post_ids`
 * array (verified against e621ng's `PostSetsController#add_remove_posts_params`). An empty array
 * is rejected server-side.
 */
@Serializable
data class SetPostIdsRequest(@SerialName("post_ids") val postIds: List<Long>)

/** Suggests a valid shortname from a set name: lowercased, non-`[a-z0-9_]` collapsed to `_`. */
fun suggestShortname(name: String): String =
    name.trim().lowercase()
        .replace(Regex("[^a-z0-9_]+"), "_")
        .trim('_')
        .take(50)

/** e621ng rule: 3-50 chars, `[a-z0-9_]` only, at least one letter or underscore. */
fun isValidShortname(shortname: String): Boolean =
    shortname.length in 3..50 &&
        shortname.all { it.isDigit() || it in 'a'..'z' || it == '_' } &&
        shortname.any { it in 'a'..'z' || it == '_' }
