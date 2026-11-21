package one.proci.e621.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * e621's Post API "v2" format (the default since 2026-11-13; legacy is removed ~May 2027 - see
 * E621_API_V2_MIGRATION.md) dropped the `{"posts": [...]}` / `{"post": {...}}` envelope and moved
 * most fields under `files` / `stats` / `has`. Rather than reshape [Post] and every call site that
 * reads `post.file.url` etc, v2 objects are rewritten into the legacy shape before decoding, so
 * [Post] keeps its legacy field layout and legacy responses (e.g. a site that hasn't switched yet)
 * still decode untouched.
 */
object PostJson {

    /** The `mode` the app requests - it needs tag categories (detail tag sections, grid chips). */
    const val MODE_EXTENDED = "extended"

    /** Unwraps a post list from either a v2 bare array or a legacy `{"posts": [...]}` envelope. */
    fun postArray(root: JsonElement): List<JsonElement> = when (root) {
        is JsonArray -> root
        is JsonObject -> (root["posts"] as? JsonArray).orEmpty()
        else -> emptyList()
    }

    /** Unwraps a single post from either a v2 bare object or a legacy `{"post": {...}}` envelope. */
    fun singlePost(root: JsonElement): JsonObject? {
        val obj = root as? JsonObject ?: return null
        return (obj["post"] as? JsonObject) ?: obj
    }

    /** Rewrites a v2 post object into the legacy layout [Post] decodes; legacy objects pass through. */
    fun normalize(element: JsonElement): JsonElement {
        val post = element as? JsonObject ?: return element
        val files = post["files"] as? JsonObject ?: return normalizeLegacy(post)

        val meta = files.obj("meta")
        val original = files.obj("original")
        val previewObj = files.obj("preview")
        val sampleObj = files.obj("sample")
        val stats = post.obj("stats")
        val has = post.obj("has")
        val relationships = post.obj("relationships")
        val hasSample = meta.bool("has_sample") ?: has.bool("sample") ?: false

        return buildJsonObject {
            // Unchanged top-level fields (id, rating, flags, sources, pools, description, ...).
            post.forEach { (key, value) ->
                if (key !in V2_ONLY_KEYS) put(key, value)
            }
            put("file", buildJsonObject {
                original["width"]?.let { put("width", it) }
                original["height"]?.let { put("height", it) }
                meta["ext"]?.let { put("ext", it) }
                meta["size"]?.let { put("size", it) }
                meta["md5"]?.let { put("md5", it) }
                put("url", original["url"] ?: JsonNull)
            })
            put("preview", buildJsonObject {
                previewObj["width"]?.let { put("width", it) }
                previewObj["height"]?.let { put("height", it) }
                put("url", previewObj.firstUrl("jpg", "webp"))
            })
            put("sample", buildJsonObject {
                put("has", hasSample)
                sampleObj["width"]?.let { put("width", it) }
                sampleObj["height"]?.let { put("height", it) }
                // v2 fills sample.jpg/webp with the ORIGINAL file when there is no sample (legacy
                // sent null) - keep legacy semantics so "sample else original" logic doesn't end
                // up loading a full-size original, or a .webm where an image is expected.
                put("url", if (hasSample) sampleObj.firstUrl("jpg", "webp") else JsonNull)
            })
            stats["score"]?.let { put("score", it) }
            stats["fav_count"]?.let { put("fav_count", it) }
            stats["comment_count"]?.let { put("comment_count", it) }
            stats["is_favorited"]?.let { put("is_favorited", it) }
            stats["vote"]?.let { put("vote", it) }
            has["notes"]?.let { put("has_notes", it) }
            meta["duration"]?.let { put("duration", it) }
            put("relationships", buildJsonObject {
                relationships.forEach { (key, value) -> put(key, value) }
                has["children"]?.let { put("has_children", it) }
                has["active_children"]?.let { put("has_active_children", it) }
            })
            put("tags", normalizeTags(post["tags"]))
        }
    }

    /** Legacy posts only need their tags tolerated in case a `mode` other than grouped came back. */
    private fun normalizeLegacy(post: JsonObject): JsonObject {
        val tags = post["tags"]
        if (tags == null || tags is JsonObject) return post
        return JsonObject(post + ("tags" to normalizeTags(tags)))
    }

    /**
     * `mode=extended` (and legacy) give `{category: [...]}`; `mode=basic` a flat list and
     * `mode=thumbnails` a space-separated string - both of those land in `general` since the
     * categories are unknown.
     */
    private fun normalizeTags(tags: JsonElement?): JsonElement = when (tags) {
        is JsonObject -> tags
        is JsonArray -> buildJsonObject { put("general", tags) }
        is JsonPrimitive -> buildJsonObject {
            val names = tags.contentOrNull.orEmpty().split(' ').filter { it.isNotBlank() }
            put("general", JsonArray(names.map(::JsonPrimitive)))
        }
        else -> JsonObject(emptyMap())
    }

    private val V2_ONLY_KEYS = setOf("files", "stats", "has", "relationships", "tags")

    private fun JsonObject.obj(key: String): JsonObject = this[key] as? JsonObject ?: EMPTY
    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.firstUrl(vararg keys: String): JsonElement =
        keys.firstNotNullOfOrNull { key -> (this[key] as? JsonPrimitive)?.takeIf { it.contentOrNull != null } }
            ?: JsonNull

    private val EMPTY = JsonObject(emptyMap())
}

/** Decodes `posts.json` / `popular.json` in either the v2 (bare array) or legacy (enveloped) format. */
object PostsResponseSerializer : KSerializer<PostsResponse> {
    private val listSerializer = ListSerializer(Post.serializer())

    override val descriptor: SerialDescriptor = listSerializer.descriptor

    override fun deserialize(decoder: Decoder): PostsResponse {
        val input = decoder as? JsonDecoder ?: throw SerializationException("PostsResponse is JSON-only")
        val posts = PostJson.postArray(input.decodeJsonElement()).map {
            input.json.decodeFromJsonElement(Post.serializer(), PostJson.normalize(it))
        }
        return PostsResponse(posts)
    }

    override fun serialize(encoder: Encoder, value: PostsResponse) {
        val output = encoder as? JsonEncoder ?: throw SerializationException("PostsResponse is JSON-only")
        output.encodeSerializableValue(listSerializer, value.posts)
    }
}
