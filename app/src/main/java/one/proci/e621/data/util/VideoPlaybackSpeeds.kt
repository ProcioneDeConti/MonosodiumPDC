package one.proci.e621.data.util

import kotlin.math.roundToInt

/**
 * Bounds for the user-configurable default video playback speed (see Settings > Video). Like
 * [ImageCacheLimits], the slider works in discrete indices rather than raw speed values.
 */
object VideoPlaybackSpeeds {
    const val MIN_SPEED = 0.25f
    const val MAX_SPEED = 2.0f
    const val STEP_SPEED = 0.25f
    const val DEFAULT_SPEED = 1.0f

    /** Number of discrete speed positions from [MIN_SPEED] to [MAX_SPEED] inclusive. */
    private val numericPositions = ((MAX_SPEED - MIN_SPEED) / STEP_SPEED).roundToInt() + 1
    val lastIndex = numericPositions - 1

    fun indexForSpeed(speed: Float): Int = ((clamp(speed) - MIN_SPEED) / STEP_SPEED).roundToInt()

    fun speedForIndex(index: Int): Float = MIN_SPEED + index.coerceIn(0, lastIndex) * STEP_SPEED

    fun clamp(speed: Float): Float = speed.coerceIn(MIN_SPEED, MAX_SPEED)
}
