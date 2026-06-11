package com.offnetic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkColorScheme = androidx.compose.material3.darkColorScheme(
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

private val LightColorScheme = androidx.compose.material3.lightColorScheme(
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

val FontFamilySyne = FontFamily(
    Font(com.offnetic.R.font.syne_variable, FontWeight.Normal),
    Font(com.offnetic.R.font.syne_variable, FontWeight.Medium),
    Font(com.offnetic.R.font.syne_variable, FontWeight.SemiBold),
    Font(com.offnetic.R.font.syne_variable, FontWeight.Bold),
    Font(com.offnetic.R.font.syne_variable, FontWeight.ExtraBold),
)

val Typography = androidx.compose.material3.Typography(
    displayLarge = androidx.compose.material3.Typography().displayLarge.copy(
        fontFamily = FontFamilySyne, fontWeight = FontWeight.ExtraBold
    ),
    headlineLarge = androidx.compose.material3.Typography().headlineLarge.copy(
        fontFamily = FontFamilySyne, fontWeight = FontWeight.Bold
    ),
    headlineMedium = androidx.compose.material3.Typography().headlineMedium.copy(
        fontFamily = FontFamilySyne, fontWeight = FontWeight.Bold
    ),
    headlineSmall = androidx.compose.material3.Typography().headlineSmall.copy(
        fontFamily = FontFamilySyne, fontWeight = FontWeight.Bold
    ),
    titleLarge = androidx.compose.material3.Typography().titleLarge.copy(
        fontFamily = FontFamilySyne, fontWeight = FontWeight.Bold
    ),
    titleMedium = androidx.compose.material3.Typography().titleMedium.copy(
        fontFamily = FontFamilySyne, fontWeight = FontWeight.SemiBold
    ),
    titleSmall = androidx.compose.material3.Typography().titleSmall.copy(
        fontFamily = FontFamilySyne, fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = androidx.compose.material3.Typography().bodyLarge.copy(
        fontFamily = FontFamilySyne, fontWeight = FontWeight.Medium
    ),
    bodyMedium = androidx.compose.material3.Typography().bodyMedium.copy(
        fontFamily = FontFamilySyne, fontWeight = FontWeight.Medium
    ),
    bodySmall = androidx.compose.material3.Typography().bodySmall.copy(
        fontFamily = FontFamilySyne, fontWeight = FontWeight.Medium
    ),
    labelLarge = androidx.compose.material3.Typography().labelLarge.copy(
        fontFamily = FontFamilySyne, fontWeight = FontWeight.SemiBold
    ),
    labelMedium = androidx.compose.material3.Typography().labelMedium.copy(
        fontFamily = FontFamilySyne, fontWeight = FontWeight.SemiBold
    ),
    labelSmall = androidx.compose.material3.Typography().labelSmall.copy(
        fontFamily = FontFamilySyne, fontWeight = FontWeight.SemiBold
    )
)

val Shapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
)

@Composable
fun OffneticColors(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}