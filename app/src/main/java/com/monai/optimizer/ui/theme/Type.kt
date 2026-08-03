package com.monai.optimizer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.monai.optimizer.R

/**
 * Font Geist (Vercel). Unduh dari https://fonts.google.com/specimen/Geist
 * (gratis, lisensi OFL) lalu taruh file .ttf berikut di res/font/:
 *   geist_regular.ttf, geist_medium.ttf, geist_semibold.ttf, geist_bold.ttf
 *
 * Kalau file belum ada, HAPUS baris Font(...) di bawah dan biarkan
 * FontFamily.Default supaya project tetap kompil sebelum kamu tambahkan file-nya.
 */
val GeistFontFamily = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
)

val MonAiTypography = Typography(
    titleLarge = TextStyle(fontFamily = GeistFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontFamily = GeistFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = (-0.2).sp),
    titleSmall = TextStyle(fontFamily = GeistFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = GeistFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = GeistFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = GeistFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = GeistFontFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = GeistFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp),
    labelSmall = TextStyle(fontFamily = GeistFontFamily, fontWeight = FontWeight.Medium, fontSize = 10.sp),
)
