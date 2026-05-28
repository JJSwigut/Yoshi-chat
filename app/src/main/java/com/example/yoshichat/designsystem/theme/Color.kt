package com.example.yoshichat.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object YoshiColors {
    val BackgroundDeep = Color(0xFF06100D)
    val BackgroundBase = Color(0xFF0B1713)
    val BackgroundTealGlow = Color(0xFF1A3B2D)
    val BackgroundBlueGlow = Color(0xFF162A35)
    val BackgroundPurpleGlow = Color(0xFF211F38)

    val SurfaceDim = Color(0xFF0C1612)
    val Surface = Color(0xFF101C18)
    val SurfaceContainerLowest = Color(0xFF0A1410)
    val SurfaceContainerLow = Color(0xFF131F1B)
    val SurfaceContainer = Color(0xFF1A2722)
    val SurfaceContainerHigh = Color(0xFF1F2C26)
    val SurfaceContainerHighest = Color(0xFF253229)

    val RecessedFill = Color(0xFF0F1F1A)

    val ChatBubbleAi = Color(0xFF5B6D55)
    val OnChatBubbleAi = Color(0xFFF8FBF6)
    val ChatBubbleUser = Color(0xFF789782)
    val OnChatBubbleUser = Color(0xFF07120C)

    val OnSurface = Color(0xFFF1F6F1)
    val OnSurfaceVariant = Color(0xFFC6D0C8)
    val OnSurfaceMuted = Color(0xFF9CA8A0)

    val Primary = Color(0xFF8FCC9F)
    val OnPrimary = Color(0xFF0D2017)
    val PrimaryContainer = Color(0xFF2A4A38)
    val OnPrimaryContainer = Color(0xFFC9E7CF)

    val Secondary = Color(0xFFB4CDB9)
    val OnSecondary = Color(0xFF1F3327)
    val SecondaryContainer = Color(0xFF2E4636)
    val OnSecondaryContainer = Color(0xFFD0E5D4)

    val Tertiary = Color(0xFF8BB6C9)
    val OnTertiary = Color(0xFF0F2330)
    val TertiaryContainer = Color(0xFF2A4452)
    val OnTertiaryContainer = Color(0xFFCFE5F0)

    val Error = Color(0xFFE57373)
    val OnError = Color(0xFF300000)
    val ErrorContainer = Color(0xFF5B1A1A)
    val OnErrorContainer = Color(0xFFFAD7D7)

    val Outline = Color(0xFF3A4540)
    val OutlineVariant = Color(0xFF222D27)

    val NeumorphicShadow = Color(0xFF000000)
    val NeumorphicHighlight = Color(0xFFFFFFFF)
}

internal val YoshiDarkColorScheme: ColorScheme = darkColorScheme(
    primary = YoshiColors.Primary,
    onPrimary = YoshiColors.OnPrimary,
    primaryContainer = YoshiColors.PrimaryContainer,
    onPrimaryContainer = YoshiColors.OnPrimaryContainer,
    secondary = YoshiColors.Secondary,
    onSecondary = YoshiColors.OnSecondary,
    secondaryContainer = YoshiColors.SecondaryContainer,
    onSecondaryContainer = YoshiColors.OnSecondaryContainer,
    tertiary = YoshiColors.Tertiary,
    onTertiary = YoshiColors.OnTertiary,
    tertiaryContainer = YoshiColors.TertiaryContainer,
    onTertiaryContainer = YoshiColors.OnTertiaryContainer,
    error = YoshiColors.Error,
    onError = YoshiColors.OnError,
    errorContainer = YoshiColors.ErrorContainer,
    onErrorContainer = YoshiColors.OnErrorContainer,
    background = YoshiColors.BackgroundBase,
    onBackground = YoshiColors.OnSurface,
    surface = YoshiColors.Surface,
    onSurface = YoshiColors.OnSurface,
    surfaceDim = YoshiColors.SurfaceDim,
    surfaceBright = YoshiColors.SurfaceContainerHighest,
    surfaceContainerLowest = YoshiColors.SurfaceContainerLowest,
    surfaceContainerLow = YoshiColors.SurfaceContainerLow,
    surfaceContainer = YoshiColors.SurfaceContainer,
    surfaceContainerHigh = YoshiColors.SurfaceContainerHigh,
    surfaceContainerHighest = YoshiColors.SurfaceContainerHighest,
    onSurfaceVariant = YoshiColors.OnSurfaceVariant,
    outline = YoshiColors.Outline,
    outlineVariant = YoshiColors.OutlineVariant,
)

internal val YoshiLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF1F6B3B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFAAEAB1),
    onPrimaryContainer = Color(0xFF002111),
    secondary = Color(0xFF4F6353),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E8D4),
    onSecondaryContainer = Color(0xFF0D1F14),
    background = Color(0xFFF6FAF6),
    onBackground = Color(0xFF181C19),
    surface = Color(0xFFF4F8F4),
    onSurface = Color(0xFF181C19),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF3EE),
    surfaceContainer = Color(0xFFE8EDE8),
    surfaceContainerHigh = Color(0xFFE2E8E2),
    surfaceContainerHighest = Color(0xFFDDE3DC),
    onSurfaceVariant = Color(0xFF424842),
    outline = Color(0xFF727872),
    outlineVariant = Color(0xFFC1C7C0),
)
