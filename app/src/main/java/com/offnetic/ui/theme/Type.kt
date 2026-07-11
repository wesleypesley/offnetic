package com.offnetic.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Defaults = Typography()

val OffneticTypography = Typography(
    displayLarge = Defaults.displayLarge.copy(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp
    ),
    titleLarge = Defaults.titleLarge.copy(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 22.sp
    ),
    titleMedium = Defaults.titleMedium.copy(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 18.sp
    ),
    bodyLarge = Defaults.bodyLarge.copy(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 15.sp
    ),
    bodyMedium = Defaults.bodyMedium.copy(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 15.sp
    ),
    bodySmall = Defaults.bodySmall.copy(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp
    ),
    labelLarge = Defaults.labelLarge.copy(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 15.sp
    ),
    labelMedium = Defaults.labelMedium.copy(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 13.sp
    ),
    // Previously undefined (O10) — screens referencing labelSmall silently fell back
    // to the Material default, which didn't match the app's type scale
    labelSmall = Defaults.labelSmall.copy(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 12.sp
    )
)
