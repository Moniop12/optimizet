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
    tertiary           = PurpleGlow,
    background         = DarkBg,
    surface            = DarkSurface,
    surfaceVariant     = DarkSurfaceVar,
    surfaceContainer   = Surface2,
    surfaceContainerHigh = Surface3,
    onBackground       = TextPrimary,
    onSurface          = TextPrimary,
    onSurfaceVariant   = TextSecondary,
    outline            = GlassBorder,
    error              = RedErr,
)

@Composable
fun MonAiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MonAiColorScheme, content = content)
}
