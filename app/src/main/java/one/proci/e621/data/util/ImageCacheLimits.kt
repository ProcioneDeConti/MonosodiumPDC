package one.proci.e621.data.util

/**
 * Bounds for the user-configurable on-disk image cache size (see Settings > Cache). The slider
 * works in discrete positions rather than raw MB so its rightmost position can mean "Unlimited"
 * (sentinel [UNLIMITED]) rather than just another numeric step.
 */
object ImageCacheLimits {
    const val MIN_MB = 100
    const val MAX_MB = 4000
    const val STEP_MB = 50
    const val DEFAULT_MB = 250

    /** Persisted/returned to mean "no size cap" - see [one.proci.e621.E621Application.newImageLoader]. */
    const val UNLIMITED = -1

    /** Number of discrete numeric positions from [MIN_MB] to [MAX_MB] inclusive. */
    private val numericPositions = (MAX_MB - MIN_MB) / STEP_MB + 1

    /** Slider index of the trailing "Unlimited" position, one past the last numeric position. */
    val unlimitedIndex = numericPositions
    val lastIndex = unlimitedIndex

    fun indexForMb(mb: Int): Int =
        if (mb == UNLIMITED) unlimitedIndex else ((clamp(mb) - MIN_MB) / STEP_MB)

    fun mbForIndex(index: Int): Int =
        if (index >= unlimitedIndex) UNLIMITED else MIN_MB + index * STEP_MB

    fun clamp(mb: Int): Int = if (mb == UNLIMITED) UNLIMITED else mb.coerceIn(MIN_MB, MAX_MB)
}
