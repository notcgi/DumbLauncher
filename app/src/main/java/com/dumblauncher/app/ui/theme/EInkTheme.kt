package com.dumblauncher.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val EInkWhite = Color(0xFFFFFFFF)
val EInkBlack = Color(0xFF000000)

private val EInkColorScheme = lightColorScheme(
    primary = EInkBlack,
    onPrimary = EInkWhite,
    secondary = EInkBlack,
    onSecondary = EInkWhite,
    background = EInkWhite,
    onBackground = EInkBlack,
    surface = EInkWhite,
    onSurface = EInkBlack,
    surfaceVariant = EInkWhite,
    onSurfaceVariant = EInkBlack,
    outline = EInkBlack,
)

private val EInkTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 56.sp,
        color = EInkBlack,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        color = EInkBlack,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        color = EInkBlack,
        lineHeight = 32.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        color = EInkBlack,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = EInkBlack,
    ),
)

@Composable
fun EInkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EInkColorScheme,
        typography = EInkTypography,
        content = content,
    )
}
