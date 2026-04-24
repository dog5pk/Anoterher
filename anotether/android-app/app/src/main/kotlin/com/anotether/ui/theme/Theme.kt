package com.anotether.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AnotetherColorScheme = darkColorScheme(
    primary = BurntOrange,
    onPrimary = White,
    primaryContainer = BurntOrangeDim,
    onPrimaryContainer = OffWhite,

    secondary = Navy500,
    onSecondary = OffWhite,
    secondaryContainer = Navy700,
    onSecondaryContainer = OffWhite,

    background = Navy900,
    onBackground = OffWhite,

    surface = Navy800,
    onSurface = OffWhite,
    surfaceVariant = Navy700,
    onSurfaceVariant = SubtleGray,

    outline = Navy600,
    outlineVariant = Navy700,

    error = ErrorRed,
    onError = White,

    inverseSurface = OffWhite,
    inverseOnSurface = Navy900,
    inversePrimary = BurntOrangeDim,

    scrim = Color(0x99000000),
)

@Composable
fun AnotetherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AnotetherColorScheme,
        typography = AnotetherTypography,
        content = content,
    )
}
