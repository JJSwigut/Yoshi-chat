package com.example.yoshichat.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Root MaterialTheme for the Yoshi neumorphic design system. Wrap your
 * activity content (or any preview) in this and the colors, type scale, and
 * shape tokens become available via [MaterialTheme].
 */
@Composable
fun YoshiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) YoshiDarkColorScheme else YoshiLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = YoshiTypography,
        shapes = YoshiShapes.material,
        content = content,
    )
}
