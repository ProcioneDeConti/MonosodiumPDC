package one.proci.e621.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * A full type scale with clear contrast between levels: bold, slightly tight headlines for
 * screen and section headers, semibold titles, plain body text, and medium-weight labels with a
 * touch of tracking. Everything stays on the system font (Roboto / the device's default).
 */

private val Base = Typography()

val E621Typography = Typography(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge,
    bodyMedium = Base.bodyMedium,
    bodySmall = Base.bodySmall,
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = Base.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

/**
 * Fixed-width digits for scores, counts and other numbers that change in place, so a vote going
 * from 99 to 100 doesn't make the surrounding layout jump. Apply as `style.tabular()`.
 */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
