package com.monai.optimizer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MonAiColorScheme = darkColorScheme(
    primary            = Cyan500,
    onPrimary          = DarkBg,
    primaryContainer   = Cyan900,
    onPrimaryContainer = CyanGlow,
    secondary          = CyanGlow,
    onSecondary        = DarkBg,
    tertiary           = CyanGlow,
    background         = DarkBg,
    surface            = DarkSurface,
    surfaceVariant     = DarkSurfaceVar,
    onBackground       = TextPrimary,
    onSurface          = TextPrimary,
    onSurfaceVariant   = TextSecondary,
    error              = RedErr,
)

@Composable
fun MonAiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MonAiColorScheme, content = content)
}