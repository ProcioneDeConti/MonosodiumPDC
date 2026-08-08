package one.proci.e621.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VoteRequest(val score: Int)

@Serializable
data class VoteResponse(
    val score: Int = 0,
    val up: Int = 0,
    val down: Int = 0,
    @SerialName("our_score") val ourScore: Int = 0,
)

@Serializable
data class FavoriteRequest(@SerialName("post_id") val postId: Long)

@Serializable
data class FavoriteResponse(
    @SerialName("post_id") val postId: Long = 0,
    @SerialName("favorite_count") val favoriteCount: Int = 0,
)
