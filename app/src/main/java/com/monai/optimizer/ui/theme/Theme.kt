package com.monai.optimizer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MonAiColorScheme = lightColorScheme(
    primary              = Accent,
    onPrimary            = OnAccent,
    primaryContainer     = AccentMuted,
    onPrimaryContainer   = Accent,
    secondary            = InkSecondary,
    onSecondary          = InkPrimary,
    tertiary             = InkSecondary,
    background           = NeuBase,
    surface              = NeuBase,
    surfaceVariant       = NeuBase,
    surfaceContainer     = NeuBase,
    surfaceContainerHigh = NeuBase,
    surfaceContainerLow  = NeuBase,
    onBackground         = InkPrimary,
    onSurface            = InkPrimary,
    onSurfaceVariant     = InkSecondary,
    outline              = HairlineBorder,
    error                = StatusError,
)

// Radius M3 Expressive tetap dipertahankan — cocok juga untuk neumorphism
// (sudut lebih besar & lembut memperkuat kesan "empuk").
private val MonAiShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small      = RoundedCornerShape(14.dp),
    medium     = RoundedCornerShape(20.dp),
    large      = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun MonAiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MonAiColorScheme,
        typography = MonAiTypography,
        shapes = MonAiShapes,
        content = content
    )
}
