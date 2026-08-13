package one.proci.e621.data.util

/**
 * Bounds for the user-adjustable post grid thumbnail size (pinch/spread to resize - see
 * [one.proci.e621.ui.components.PostGridBody]). Drives [androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells.Adaptive]'s
 * minSize, so a larger value means fewer, bigger columns.
 */
object GridThumbnailSize {
    const val MIN_DP = 90
    const val MAX_DP = 320
    const val DEFAULT_DP = 120

    fun clamp(dp: Int): Int = dp.coerceIn(MIN_DP, MAX_DP)
}
