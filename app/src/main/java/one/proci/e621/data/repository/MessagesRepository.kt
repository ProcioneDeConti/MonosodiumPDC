package one.proci.e621.data.repository

import one.proci.e621.data.api.E621ApiService
import one.proci.e621.data.model.CreateDmailFields
import one.proci.e621.data.model.CreateDmailRequest
import one.proci.e621.data.model.Dmail

class MessagesRepository(private val api: E621ApiService) {

    /** @param beforeId when set, fetches dmails older than this id (keyset pagination), for infinite scroll. */
    suspend fun fetchInbox(beforeId: Long? = null, limit: Int = 50): List<Dmail> {
        val page = beforeId?.let { "b$it" }
        return api.getDmails(limit = limit, page = page)
    }

    /** Also marks the dmail as read server-side, if it wasn't already. */
    suspend fun fetchDmail(id: Long): Dmail = api.getDmail(id)

    suspend fun sendDmail(toName: String, title: String, body: String, respondToId: Long? = null): Dmail =
        api.createDmail(CreateDmailRequest(CreateDmailFields(title, body, toName, respondToId)))

    /**
     * Soft-deletes a dmail (`DELETE dmails/:id.json`). e621ng renders a template-less response
     * after the delete, so a 406/500 is still a success; only 401/403/404 mean it didn't happen.
     */
    suspend fun deleteDmail(id: Long) {
        val response = api.deleteDmail(id)
        if (response.code() in intArrayOf(401, 403, 404)) {
            throw java.io.IOException("e621 rejected the deletion (HTTP ${response.code()})")
        }
    }
}
