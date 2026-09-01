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
