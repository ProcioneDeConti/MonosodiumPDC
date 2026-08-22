package one.proci.e621.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode

/**
 * Diagonal golden-yellow/dark-grey repeating stripe, like caution tape - meant to be painted as a
 * thin border ring (not a full fill) around anything that's only visible because a blacklist is
 * being bypassed, so it reads as "temporarily unhidden" rather than a loud warning. Shared between
 * [PostThumbnail]'s grid border and the matching-tag chip highlight in
 * [one.proci.e621.ui.screens.detail.PostDetailScreen]. [TileMode.Repeated] tiles this short
 * diagonal segment across the whole border regardless of the border's actual size.
 */
private val CautionYellow = Color(0xFFFFC107)
private val CautionDarkGrey = Color(0xFF2B2B2B)
val CautionStripeBrush = Brush.linearGradient(
    colorStops = arrayOf(0.0f to CautionYellow, 0.5f to CautionYellow, 0.5f to CautionDarkGrey, 1.0f to CautionDarkGrey),
    start = Offset.Zero,
    end = Offset(16f, 16f),
    tileMode = TileMode.Repeated,
)
