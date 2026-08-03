package com.monai.optimizer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MonAiColorScheme = darkColorScheme(
    primary              = Accent,
    onPrimary            = OnAccent,
    primaryContainer     = AccentMuted,
    onPrimaryContainer   = Neutral900,
    secondary            = Neutral700,
    onSecondary          = Neutral900,
    tertiary             = Neutral700,
    background           = Neutral0,
    surface              = Neutral50,
    surfaceVariant       = Neutral100,
    surfaceContainer     = Neutral50,
    surfaceContainerHigh = Neutral100,
    surfaceContainerLow  = Neutral0,
    onBackground         = Neutral900,
    onSurface            = Neutral900,
    onSurfaceVariant     = Neutral700,
    outline              = HairlineBorder,
    error                = StatusError,
)

@Composable
fun MonAiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MonAiColorScheme, content = content)
}
