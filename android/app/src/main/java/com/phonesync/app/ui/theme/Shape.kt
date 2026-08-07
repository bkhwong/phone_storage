package com.phonesync.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** One UI-style generous, soft corner radii — flat tonal surfaces rely on shape, not shadow. */
val PhotoSyncShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Even softer radius used for hero cards / brand marks where 32dp still reads as "square-ish". */
val HeroCornerRadius = 36.dp
