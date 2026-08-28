package com.whiplash.music.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Consistent corner radii (section 43: "centralize radius").
 */
object WhiplashRadius {
    val small = 10.dp
    val medium = 16.dp
    val large = 22.dp
    val extraLarge = 28.dp
    val pill = 999.dp
}

val WhiplashShapes = Shapes(
    extraSmall = RoundedCornerShape(WhiplashRadius.small),
    small = RoundedCornerShape(WhiplashRadius.small),
    medium = RoundedCornerShape(WhiplashRadius.medium),
    large = RoundedCornerShape(WhiplashRadius.large),
    extraLarge = RoundedCornerShape(WhiplashRadius.extraLarge),
)
