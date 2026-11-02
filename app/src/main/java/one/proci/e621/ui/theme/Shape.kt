package one.proci.e621.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * Shape scale. Sizes differ on purpose so surfaces read at a glance: small things (chips,
 * badges) are tight, content (thumbnails, cards, popups) is softer, and big containers (sheets,
 * dialogs) are clearly rounded. Use these tokens instead of a hardcoded RoundedCornerShape.
 */

/** Tag chips, badges, small inline blocks. */
val ChipShape = RoundedCornerShape(8.dp)

/** Thumbnails, tiles, cards, dropdown popups. */
val CardShape = RoundedCornerShape(12.dp)

/** Fully rounded ends: the search bar, segmented toggles, drag handles. */
val PillShape = RoundedCornerShape(percent = 50)

/** Bottom sheets: only the top corners are rounded. */
val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** Dialogs and other large floating containers. */
val DialogShape = RoundedCornerShape(28.dp)

val E621Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = ChipShape,
    medium = CardShape,
    large = RoundedCornerShape(16.dp),
    extraLarge = DialogShape,
)
