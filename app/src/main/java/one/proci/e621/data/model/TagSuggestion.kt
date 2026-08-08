package one.proci.e621.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single result from e621's live tag-autocomplete endpoint. `name` is always the canonical tag -
 * when the search prefix matched via an alias, e621 already resolves it and reports the alias that
 * matched in [antecedentName], so callers never need their own alias-resolution logic.
 */
@Serializable
data class TagSuggestion(
    val name: String,
    @SerialName("post_count") val postCount: Int = 0,
    val category: Int = 0,
    @SerialName("antecedent_name") val antecedentName: String? = null,
) {
    val tagCategory: TagCategory
        get() = when (category) {
            1 -> TagCategory.ARTIST
            3 -> TagCategory.COPYRIGHT
            4 -> TagCategory.CHARACTER
            5 -> TagCategory.SPECIES
            7 -> TagCategory.META
            8 -> TagCategory.LORE
            else -> TagCategory.GENERAL
        }
}
