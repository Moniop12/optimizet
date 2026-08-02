package com.monai.optimizer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MonAiColorScheme = darkColorScheme(
    primary = BrandPrimary,
    onPrimary = DarkBg,
    primaryContainer = BrandPrimaryDim,
    onPrimaryContainer = TextPrimary,
    secondary = BrandCyan,
    onSecondary = DarkBg,
    tertiary = BrandPrimary2,
    background = DarkBg,
    surface = DarkSurface,
    surfaceVariant = CardSurface,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = RedErr,
)

@Composable
fun MonAiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MonAiColorScheme,
        content = content
    )
}
