package com.example.yoshichat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.example.yoshichat.designsystem.theme.YoshiColors

/**
 * Deep teal background. Keep this cheap: it sits behind the whole app, so
 * expensive full-screen radial effects make text input and streaming redraws
 * feel laggy on the emulator.
 */
@Composable
fun AmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors =
                        listOf(
                            YoshiColors.BackgroundDeep,
                            YoshiColors.BackgroundDeep.copy(alpha = 0.96f),
                        ),
                ),
            ),
        content = content,
    )
}
