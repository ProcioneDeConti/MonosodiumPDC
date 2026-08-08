package one.proci.e621.ui.theme

import androidx.compose.ui.graphics.Color

// e621's brand blue, used as the seed for a fallback (non-dynamic-color) Material3 scheme.
val SeedBlue = Color(0xFF3F72AF)

val md_theme_light_primary = Color(0xFF2C5F8A)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFD3E4FF)
val md_theme_light_onPrimaryContainer = Color(0xFF001C38)
val md_theme_light_secondary = Color(0xFF54607B)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_background = Color(0xFFFBFCFF)
val md_theme_light_onBackground = Color(0xFF1A1C1E)
val md_theme_light_surface = Color(0xFFFBFCFF)
val md_theme_light_onSurface = Color(0xFF1A1C1E)
val md_theme_light_surfaceVariant = Color(0xFFDFE2EB)
val md_theme_light_onSurfaceVariant = Color(0xFF43474E)
val md_theme_light_error = Color(0xFFBA1A1A)

val md_theme_dark_primary = Color(0xFFA1C9FF)
val md_theme_dark_onPrimary = Color(0xFF00325A)
val md_theme_dark_primaryContainer = Color(0xFF12477E)
val md_theme_dark_onPrimaryContainer = Color(0xFFD3E4FF)
val md_theme_dark_secondary = Color(0xFFBCC7E5)
val md_theme_dark_onSecondary = Color(0xFF263248)
val md_theme_dark_background = Color(0xFF111318)
val md_theme_dark_onBackground = Color(0xFFE2E2E9)
val md_theme_dark_surface = Color(0xFF111318)
val md_theme_dark_onSurface = Color(0xFFE2E2E9)
val md_theme_dark_surfaceVariant = Color(0xFF43474E)
val md_theme_dark_onSurfaceVariant = Color(0xFFC3C7CF)
val md_theme_dark_error = Color(0xFFFFB4AB)

// Immersive media viewer background - dark charcoal rather than pure black, which reads as
// harsher/flatter on OLED screens and makes the scrim overlays look overly stark.
val ViewerBackground = Color(0xFF121212)

// Rating badge colors, deliberately distinct from the primary palette so they read at a glance.
val RatingSafe = Color(0xFF4CAF50)
val RatingQuestionable = Color(0xFFFFA726)
val RatingExplicit = Color(0xFFE53935)

// e621 tag category colors, used for tag chips in the detail view.
val TagArtist = Color(0xFFD4AF37) // gold
val TagCopyright = Color(0xFF8E44AD) // purple
val TagCharacter = Color(0xFFCDDC39) // lime
val TagSpecies = Color(0xFFE04B4B) // red
val TagGeneral = Color(0x26FFFFFF) // ~15% white, matches the previous neutral chip look

// Vote buttons: pale when the user hasn't voted that direction, deep/saturated once cast.
val VoteUpPale = Color(0xFFA5D6A7)
val VoteUpActive = Color(0xFF2E7D32)
val VoteDownPale = Color(0xFFEF9A9A)
val VoteDownActive = Color(0xFFC62828)

// Score gradient endpoints; red at very negative, yellow at 0, green at strongly positive.
val ScoreLow = Color(0xFFE53935)
val ScoreMid = Color(0xFFFDD835)
val ScoreHigh = Color(0xFF43A047)

// Favorite star.
val FavoriteGold = Color(0xFFD4AF37)

// Post status chip (post info sheet) - each state gets its own color so it reads at a glance
// rather than blending into the accent-colored value text every other info row uses.
val PostStatusPending = Color(0xFF2196F3) // blue
val PostStatusActive = Color(0xFF32CD32) // lime green
val PostStatusDeleted = Color(0xFFFF3B30) // apple red
val PostStatusFlagged = Color(0xFFFF4081) // pink
