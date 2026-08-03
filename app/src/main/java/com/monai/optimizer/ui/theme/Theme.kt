package com.monai.optimizer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MonAiColorScheme = darkColorScheme(
    primary              = White,
    onPrimary            = PureBlack,
    primaryContainer     = AccentMuted,
    onPrimaryContainer   = White,
    secondary            = White70,
    onSecondary          = PureBlack,
    tertiary             = White70,
    background           = Ink1,
    surface              = Ink2,
    surfaceVariant       = Ink3,
    surfaceContainer     = Ink2,
    surfaceContainerHigh = Ink3,
    surfaceContainerLow  = Ink1,
    onBackground         = White,
    onSurface            = White,
    onSurfaceVariant     = White70,
    outline              = HairlineBorder,
    error                = StatusError,
)

// Shape M3 Expressive: radius lebih besar & sedikit variatif antar ukuran
// komponen (bukan satu radius rata untuk semua), memberi kesan lebih
// "hidup" tanpa menambah warna.
private val MonAiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
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
