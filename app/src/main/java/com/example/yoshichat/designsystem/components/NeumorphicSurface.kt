package com.example.yoshichat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.yoshichat.designsystem.modifier.neumorphicRaised
import com.example.yoshichat.designsystem.modifier.neumorphicRecessed
import com.example.yoshichat.designsystem.theme.NeumorphicStyle
import com.example.yoshichat.designsystem.theme.YoshiColors
import com.example.yoshichat.designsystem.theme.YoshiNeumorphism
import com.example.yoshichat.designsystem.theme.YoshiShapes

enum class NeumorphicElevation { Raised, Recessed }

@Composable
fun NeumorphicSurface(
    modifier: Modifier = Modifier,
    elevation: NeumorphicElevation = NeumorphicElevation.Raised,
    shape: Shape = YoshiShapes.Large,
    color: Color = when (elevation) {
        NeumorphicElevation.Raised -> MaterialTheme.colorScheme.surfaceContainerHigh
        NeumorphicElevation.Recessed -> YoshiColors.RecessedFill
    },
    style: NeumorphicStyle = when (elevation) {
        NeumorphicElevation.Raised -> YoshiNeumorphism.Raised
        NeumorphicElevation.Recessed -> YoshiNeumorphism.Recessed
    },
    shadowPadding: Dp = if (elevation == NeumorphicElevation.Raised) YoshiNeumorphism.RaisedPadding else 0.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    when (elevation) {
        NeumorphicElevation.Raised -> Box(
            modifier = modifier
                .padding(shadowPadding)
                .neumorphicRaised(shape, style)
                .clip(shape)
                .background(color)
                .padding(contentPadding),
        ) { content() }
        NeumorphicElevation.Recessed -> Box(
            modifier = modifier
                .clip(shape)
                .background(color)
                .neumorphicRecessed(shape, style)
                .padding(contentPadding),
        ) { content() }
    }
}
