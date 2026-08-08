package one.proci.e621.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/** Curated quick-pick swatches shown in Settings; users can also enter any hex color. */
val AccentPresets = listOf(
    Color(0xFF3F72AF), // blue (default)
    Color(0xFF5C6BC0), // indigo
    Color(0xFF9575CD), // purple
    Color(0xFFEC407A), // pink
    Color(0xFFEF5350), // red
    Color(0xFFFF7043), // orange
    Color(0xFFFFB300), // amber
    Color(0xFF66BB6A), // green
    Color(0xFF26A69A), // teal
    Color(0xFF29B6F6), // cyan
    Color(0xFFF7B700), // gold
    Color(0xFF00BFFF), // deep sky blue
)

private fun contrastingOn(color: Color): Color {
    val luminance = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
    return if (luminance > 0.5) Color.Black else Color.White
}

/**
 * Applies a user-chosen accent to the primary/secondary roles (which is what Material3 buttons,
 * checkboxes, progress indicators, selection states, etc. key off by default), keeping
 * structural surfaces (background, cards, app bars) neutral - the same restraint dynamic color
 * theming uses, just seeded from the user's pick instead of their wallpaper.
 */
fun ColorScheme.withAccent(accent: Color, isDark: Boolean): ColorScheme {
    val onAccent = contrastingOn(accent)
    val blendTarget = if (isDark) Color.Black else Color.White
    val container = Color(ColorUtils.blendARGB(accent.toArgb(), blendTarget.toArgb(), if (isDark) 0.6f else 0.75f))
    val onContainer = contrastingOn(container)
    return copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = container,
        onSecondaryContainer = onContainer,
    )
}
