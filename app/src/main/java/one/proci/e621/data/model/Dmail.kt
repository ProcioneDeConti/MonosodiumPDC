package one.proci.e621.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Dmail(
    val id: Long,
    val title: String = "",
    val body: String = "",
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("to_id") val toId: Long? = null,
    @SerialName("to_name") val toName: String? = null,
    @SerialName("from_id") val fromId: Long? = null,
    @SerialName("from_name") val fromName: String? = null,
)

@Serializable
data class CreateDmailRequest(val dmail: CreateDmailFields)

@Serializable
data class CreateDmailFields(
    val title: String,
    val body: String,
    @SerialName("to_name") val toName: String,
    @SerialName("respond_to_id") val respondToId: Long? = null,
)
