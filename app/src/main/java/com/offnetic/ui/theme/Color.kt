package com.offnetic.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val DarkColorScheme = darkColorScheme(
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF141414),
    surfaceVariant = Color(0xFF1E1E1E),
    outline = Color(0x14FFFFFF),
    outlineVariant = Color(0x24FFFFFF),
    primary = Color.White,
    onPrimary = Color(0xFF0A0A0A),
    secondary = Color(0xFF4ADE80),
    onSecondary = Color.Black,
    tertiary = Color(0xFFEF4444),
    onTertiary = Color.White,
    error = Color(0xFFEF4444),
    onError = Color.White,
    surfaceContainerHighest = Color(0xFF353535),
    onSurface = Color.White,
    onSurfaceVariant = Color(0x73FFFFFF),
    inverseSurface = Color(0xFFF5F5F5),
    inverseOnSurface = Color(0xFF0A0A0A),
    primaryContainer = Color(0xFFE2E2E2),
    onPrimaryContainer = Color(0xFF636363),
    secondaryContainer = Color(0xFF474746),
    onSecondaryContainer = Color(0xFFB6B5B4),
    tertiaryContainer = Color(0xFFE3E2E2),
    onTertiaryContainer = Color(0xFF646464),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

internal val LightColorScheme = lightColorScheme(
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    surfaceVariant = Color(0xFFEBEBEB),
    outline = Color(0x14000000),
    outlineVariant = Color(0x24000000),
    primary = Color(0xFF0A0A0A),
    onPrimary = Color.White,
    secondary = Color(0xFF16A34A),
    onSecondary = Color.White,
    tertiary = Color(0xFFDC2626),
    onTertiary = Color.White,
    error = Color(0xFFDC2626),
    onError = Color.White,
    onSurface = Color(0xFF0A0A0A),
    onSurfaceVariant = Color(0x73000000),
    inverseSurface = Color(0xFF0A0A0A),
    inverseOnSurface = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF690005)
)

/**
 * Semantic tokens the Material color roles don't cover — the faint white/black
 * alpha layers the design leans on. Use these instead of inline Color(0x??FFFFFF)
 * literals so screens stay consistent and re-skinnable (D19).
 */
object OffneticColors {
    val textPrimary = Color.White
    val textSecondary = Color(0x99FFFFFF)
    val textMuted = Color(0x73FFFFFF)
    val textFaint = Color(0x59FFFFFF)
    val textDisabled = Color(0x4DFFFFFF)
    val textHint = Color(0x40FFFFFF)
    val textGhost = Color(0x33FFFFFF)

    val surfaceCard = Color(0x0DFFFFFF)
    val surfaceChip = Color(0x12FFFFFF)
    val surfaceBubble = Color(0x14FFFFFF)
    val surfaceRaised = Color(0x1AFFFFFF)
    val borderSubtle = Color(0x0FFFFFFF)
    val borderFaint = Color(0x08FFFFFF)

    val accentGreen = Color(0xFF4ADE80)
    val accentBlue = Color(0xFF60A5FA)
    val accentLink = Color(0xFF3B82F6)
    val danger = Color(0xFFEF4444)
    val dangerSurface = Color(0x1AEF4444)
}
