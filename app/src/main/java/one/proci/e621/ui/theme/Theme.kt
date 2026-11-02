package one.proci.e621.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import one.proci.e621.data.settings.DarkSurfaceStyle

private val LightColors = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    error = md_theme_light_error,
)

private val DarkColors = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    error = md_theme_dark_error,
)

@Composable
fun E621Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    accentColor: Color? = null,
    surfaceStyle: DarkSurfaceStyle = DarkSurfaceStyle.CHARCOAL,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // A user-chosen accent takes priority over wallpaper-derived dynamic color; dynamic color is
    // only used as the default when the user hasn't picked one of their own.
    val baseColorScheme = when {
        accentColor == null && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    var colorScheme = baseColorScheme
    if (accentColor != null) {
        colorScheme = colorScheme.withAccent(accentColor, darkTheme)
    }
    // Applied last so the faint tint follows whichever primary ended up in use.
    colorScheme = colorScheme.withTonalSurfaces(darkTheme, surfaceStyle)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = E621Typography,
        shapes = E621Shapes,
        content = content,
    )
}

/**
 * One surface ladder: the base (background/surface) and each surfaceContainer step above it,
 * plus surfaceVariant (the fill for inputs, chips and info blocks).
 */
private class SurfaceLadder(
    val base: Color,
    val lowest: Color,
    val low: Color,
    val container: Color,
    val high: Color,
    val highest: Color,
    val variant: Color,
)

private fun darkLadder(style: DarkSurfaceStyle) = when (style) {
    // The long-standing charcoal, matching the media viewer.
    DarkSurfaceStyle.CHARCOAL -> SurfaceLadder(
        base = ViewerBackground, lowest = Color(0xFF0D0D0D), low = Color(0xFF1A1A1A),
        container = Color(0xFF1F1F1F), high = Color(0xFF292929), highest = Color(0xFF333333),
        variant = Color(0xFF2B2B2B),
    )
    // True black for OLED screens; containers stay just visible.
    DarkSurfaceStyle.OLED -> SurfaceLadder(
        base = Color.Black, lowest = Color.Black, low = Color(0xFF0A0A0A),
        container = Color(0xFF111111), high = Color(0xFF181818), highest = Color(0xFF222222),
        variant = Color(0xFF1C1C1C),
    )
    // Desktop's "navy" palette.
    DarkSurfaceStyle.NAVY -> SurfaceLadder(
        base = Color(0xFF0C1C36), lowest = Color(0xFF08152A), low = Color(0xFF112444),
        container = Color(0xFF152B4F), high = Color(0xFF1C365F), highest = Color(0xFF1F3C67),
        variant = Color(0xFF1B3459),
    )
    // Desktop's "warm" palette.
    DarkSurfaceStyle.WARM -> SurfaceLadder(
        base = Color(0xFF1C1916), lowest = Color(0xFF151310), low = Color(0xFF221E1B),
        container = Color(0xFF26221E), high = Color(0xFF2E2924), highest = Color(0xFF38322C),
        variant = Color(0xFF302A25),
    )
}

private fun lightLadder(style: DarkSurfaceStyle) = when (style) {
    DarkSurfaceStyle.WARM -> SurfaceLadder(
        base = Color(0xFFFAF7F2), lowest = Color(0xFFFFFDFA), low = Color(0xFFF4EFE7),
        container = Color(0xFFEFE9E0), high = Color(0xFFE9E2D8), highest = Color(0xFFE3DBD0),
        variant = Color(0xFFEAE3D9),
    )
    else -> SurfaceLadder(
        base = md_theme_light_background, lowest = Color.White, low = Color(0xFFF5F6F9),
        container = Color(0xFFEFF1F5), high = Color(0xFFE9EBF0), highest = Color(0xFFE3E5EB),
        variant = Color(0xFFE6E8EE),
    )
}

/**
 * Layered surfaces: a fixed neutral base (so the app doesn't pick up whatever near-black a
 * wallpaper palette produces) with each surfaceContainer level a step lighter (dark) or darker
 * (light), faintly tinted with primary. Material components already pick these roles (menus,
 * sheets, dialogs, nav bars), so they separate from the background by tone, not by shadow.
 */
private fun ColorScheme.withTonalSurfaces(isDark: Boolean, style: DarkSurfaceStyle): ColorScheme {
    val ladder = if (isDark) darkLadder(style) else lightLadder(style)
    val step = if (isDark) 0.01f else 0.008f
    fun tint(c: Color, level: Int) = primary.copy(alpha = step * (level + 2)).compositeOver(c)
    return copy(
        background = ladder.base,
        surface = ladder.base,
        surfaceDim = if (isDark) ladder.base else tint(ladder.highest, 3),
        surfaceBright = if (isDark) tint(ladder.highest, 4) else ladder.base,
        surfaceContainerLowest = ladder.lowest,
        surfaceContainerLow = tint(ladder.low, 1),
        surfaceContainer = tint(ladder.container, 2),
        surfaceContainerHigh = tint(ladder.high, 3),
        surfaceContainerHighest = tint(ladder.highest, 4),
        surfaceVariant = tint(ladder.variant, 4),
    )
}
