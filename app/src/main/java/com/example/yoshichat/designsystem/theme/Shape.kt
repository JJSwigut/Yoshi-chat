package com.example.yoshichat.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Stable
object YoshiShapes {
    val ExtraSmall: Shape = RoundedCornerShape(8.dp)
    val Small: Shape = RoundedCornerShape(12.dp)
    val Medium: Shape = RoundedCornerShape(20.dp)
    val Large: Shape = RoundedCornerShape(28.dp)
    val ExtraLarge: Shape = RoundedCornerShape(36.dp)
    val SideSheet: Shape =
        RoundedCornerShape(
            topStart = 0.dp,
            bottomStart = 0.dp,
            topEnd = 36.dp,
            bottomEnd = 36.dp,
        )

    /** Fully-rounded "pill" — use for inputs, chat bubbles, and the composer. */
    val Pill: Shape = RoundedCornerShape(percent = 50)

    /** Material 3 [Shapes] mapping for the system shape roles. */
    val material: Shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(36.dp),
    )
}
