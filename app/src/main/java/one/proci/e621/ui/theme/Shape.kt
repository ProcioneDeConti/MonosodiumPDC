package one.proci.e621.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

private val SharpCorner = RoundedCornerShape(7.dp)

/** Every Material3 default component corner (cards, dialogs, menus, sheets, ...) tightened to 7dp. */
val E621Shapes = Shapes(
    extraSmall = SharpCorner,
    small = SharpCorner,
    medium = SharpCorner,
    large = SharpCorner,
    extraLarge = SharpCorner,
)
