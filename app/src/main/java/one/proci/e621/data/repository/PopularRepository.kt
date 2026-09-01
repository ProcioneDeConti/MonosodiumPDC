package one.proci.e621.data.repository

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import one.proci.e621.data.api.E621ApiService
import one.proci.e621.data.model.Post

enum class PopularScale(val apiValue: String) {
    DAY("day"),
    WEEK("week"),
    MONTH("month");

    /** The anchor date one period earlier / later. */
    fun previous(date: LocalDate): LocalDate = when (this) {
        DAY -> date.minusDays(1)
        WEEK -> date.minusWeeks(1)
        MONTH -> date.minusMonths(1)
    }

    fun next(date: LocalDate): LocalDate = when (this) {
        DAY -> date.plusDays(1)
        WEEK -> date.plusWeeks(1)
        MONTH -> date.plusMonths(1)
    }
}

class PopularRepository(private val api: E621ApiService) {
    suspend fun fetchPopular(date: LocalDate, scale: PopularScale): List<Post> =
        api.getPopular(date.format(DateTimeFormatter.ISO_LOCAL_DATE), scale.apiValue).posts
}
