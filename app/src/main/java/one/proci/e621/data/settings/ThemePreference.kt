package one.proci.e621.data.settings

/**
 * The user's light/dark preference. [SYSTEM] follows the device setting (the app's long-standing
 * behaviour and the default); [LIGHT]/[DARK] force it regardless. Persisted and included in the
 * settings backup snapshot.
 */
enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromName(name: String?): ThemePreference =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/**
 * Which base the theme's surfaces are built on (see the theme's tonal surface layering).
 * [CHARCOAL] is the long-standing look and the default; the others match the desktop app's
 * palettes. [WARM] also warms up light mode slightly.
 */
enum class DarkSurfaceStyle {
    CHARCOAL,
    OLED,
    NAVY,
    WARM;

    companion object {
        fun fromName(name: String?): DarkSurfaceStyle =
            entries.firstOrNull { it.name == name } ?: CHARCOAL
    }
}
