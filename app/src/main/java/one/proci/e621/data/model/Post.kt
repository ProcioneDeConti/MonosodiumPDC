package one.proci.e621.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PostsResponse(
    val posts: List<Post> = emptyList(),
)

@Serializable
data class Post(
    val id: Long,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val file: PostFile,
    val preview: PostPreview,
    val sample: PostSample,
    val score: PostScore = PostScore(),
    val tags: PostTags = PostTags(),
    @SerialName("locked_tags") val lockedTags: List<String> = emptyList(),
    val rating: String = "e",
    @SerialName("fav_count") val favCount: Int = 0,
    val sources: List<String> = emptyList(),
    val pools: List<Long> = emptyList(),
    val description: String = "",
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("is_favorited") val isFavorited: Boolean = false,
    @SerialName("has_notes") val hasNotes: Boolean = false,
    val duration: Double? = null,
    val flags: PostFlags = PostFlags(),
    /** The authenticated user's own vote: 1 up, -1 down, 0 none. Always 0 when not signed in. */
    @SerialName("vote_by") val voteBy: Int = 0,
    @SerialName("uploader_id") val uploaderId: Long? = null,
    @SerialName("approver_id") val approverId: Long? = null,
) {
    /** File extension used to decide how the media should be rendered. */
    val extension: String get() = file.ext.lowercase()

    val isVideo: Boolean get() = extension == "webm" || extension == "mp4"
    val isAnimated: Boolean get() = extension == "gif" || extension == "apng" || isVideo
    val isDeleted: Boolean get() = flags.deleted

    /** Best URL to use for full playback/viewing; falls back to sample when the original is null (deleted posts). */
    val playableUrl: String? get() = file.url ?: sample.url

    val allTags: List<String> get() = categorizedTags.map { it.name }

    /** Tags paired with their e621 category, for category-colored tag chips in the detail view. */
    val categorizedTags: List<CategorizedTag>
        get() = tags.artist.map { CategorizedTag(it, TagCategory.ARTIST) } +
            tags.copyright.map { CategorizedTag(it, TagCategory.COPYRIGHT) } +
            tags.character.map { CategorizedTag(it, TagCategory.CHARACTER) } +
            tags.species.map { CategorizedTag(it, TagCategory.SPECIES) } +
            tags.general.map { CategorizedTag(it, TagCategory.GENERAL) } +
            tags.lore.map { CategorizedTag(it, TagCategory.LORE) } +
            tags.meta.map { CategorizedTag(it, TagCategory.META) }

    /** Filename used when saving the original media to the device. */
    val downloadFileName: String get() = "e621_$id.$extension"

    val mimeType: String
        get() = when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png", "apng" -> "image/png"
            "gif" -> "image/gif"
            "webm" -> "video/webm"
            "mp4" -> "video/mp4"
            else -> if (isVideo) "video/*" else "image/*"
        }
}

@Serializable
data class PostFile(
    val width: Int = 0,
    val height: Int = 0,
    val ext: String = "jpg",
    val size: Long = 0,
    val md5: String? = null,
    val url: String? = null,
)

@Serializable
data class PostPreview(
    val width: Int = 0,
    val height: Int = 0,
    val url: String? = null,
)

@Serializable
data class PostSample(
    val has: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
    val url: String? = null,
)

@Serializable
data class PostScore(
    val up: Int = 0,
    val down: Int = 0,
    val total: Int = 0,
)

@Serializable
data class PostTags(
    val general: List<String> = emptyList(),
    val species: List<String> = emptyList(),
    val character: List<String> = emptyList(),
    val copyright: List<String> = emptyList(),
    val artist: List<String> = emptyList(),
    val invalid: List<String> = emptyList(),
    val lore: List<String> = emptyList(),
    val meta: List<String> = emptyList(),
)

@Serializable
data class PostFlags(
    val pending: Boolean = false,
    val flagged: Boolean = false,
    @SerialName("note_locked") val noteLocked: Boolean = false,
    @SerialName("status_locked") val statusLocked: Boolean = false,
    @SerialName("rating_locked") val ratingLocked: Boolean = false,
    val deleted: Boolean = false,
)

enum class Rating(val tag: String, val letter: String) {
    SAFE("rating:safe", "s"),
    QUESTIONABLE("rating:questionable", "q"),
    EXPLICIT("rating:explicit", "e"),
}

enum class TagCategory { ARTIST, COPYRIGHT, CHARACTER, SPECIES, GENERAL, LORE, META }

data class CategorizedTag(val name: String, val category: TagCategory)
