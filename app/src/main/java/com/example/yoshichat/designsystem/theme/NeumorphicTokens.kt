package com.example.yoshichat.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class NeumorphicStyle(
    val shadowColor: Color,
    val highlightColor: Color,
    val blur: Dp,
    val distance: Dp,
)

object YoshiNeumorphism {
    val Recessed: NeumorphicStyle = NeumorphicStyle(
        shadowColor = Color.Black.copy(alpha = 0.70f),
        highlightColor = Color.White.copy(alpha = 0.04f),
        blur = 10.dp,
        distance = 3.dp,
    )

    val Raised: NeumorphicStyle = NeumorphicStyle(
        shadowColor = Color.Black.copy(alpha = 0.55f),
        highlightColor = Color.White.copy(alpha = 0.04f),
        blur = 20.dp,
        distance = 6.dp,
    )

    val RaisedPadding: Dp = 28.dp
}
