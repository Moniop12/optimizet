package com.monai.optimizer.ui.theme

import androidx.compose.ui.graphics.Color

// ── Tema Putih Clean, Ergonomis, Non-Silau & High Contrast ─────────
val AppBg             = Color(0xFFF3F4F7)  // Soft Off-White (Porcelain) - Lembut di mata
val AppSurface        = Color(0xFFFFFFFF)  // Putih Murni untuk Kartu Utama
val AppSurfaceVariant = Color(0xFFE9ECF2)  // Chips & Sub-surfaces
val CardShadow        = Color(0x140F172A)  // Subtle Slate Shadow (Elevation lembut)

val GlossHighlight    = Color(0x00FFFFFF)
val GlossFade         = Color(0x00FFFFFF)

// Teks Kontras Tinggi & Nyaman Dibaca
val InkPrimary        = Color(0xFF14171F)  // Dark Charcoal (Sangat Kontras)
val InkSecondary      = Color(0xFF5A6072)  // Slate Grey (Subtitle)
val InkTertiary       = Color(0xFF8A92A6)  // Muted Label
val InkDisabled       = Color(0xFFC2C7D6)  // Disabled State

val HairlineBorder    = Color(0xFFE1E4EA)  // Border Kartu Tegas & Halus
val GlassBorderStrong = Color(0xFFCDD2DE)

// Brand Accents
val Accent            = Color(0xFF3B4CCA)  // Indigo Deep
val AccentMuted       = Color(0xFFEEF1FD)  // Background Button Aksen
val OnAccent          = Color(0xFFFFFFFF)

val StatusError       = Color(0xFFDC2626)  // Crimson Red
val StatusWarn        = Color(0xFFD97706)  // Amber Warm
val StatusOk          = Color(0xFF0F9F59)  // Emerald Green

// Contextual Colors
val BatteryGreen      = Color(0xFF0F9F59)
val PerfOrange        = Color(0xFFD97706)
val MemoryBlue        = Color(0xFF2563EB)
val CleanPurple       = Color(0xFF7C3AED)
val UtilityTeal       = Color(0xFF0D9488)
val DangerRed         = Color(0xFFDC2626)

// Aliases untuk Kompatibilitas Seluruh UI Code
val CyanGlow          = MemoryBlue
val EmeraldGlow       = BatteryGreen
val PurpleGlow        = CleanPurple
val OrangeGlow        = PerfOrange
val OrangeAcc         = PerfOrange

val DarkBg            = AppBg
val DarkSurface       = AppSurface
val DarkSurfaceVar    = AppSurfaceVariant
val Surface2          = AppSurface
val Surface3          = AppSurfaceVariant
val GlassSurface      = AppSurface
val GlassCard         = AppSurface
val GlassBorder       = HairlineBorder

val TextPrimary       = InkPrimary
val TextSecondary     = InkSecondary
val TextTertiary      = InkTertiary
val TextDisabled      = InkDisabled

val GreenOk           = StatusOk
val RedErr            = StatusError
val AmberWarn         = StatusWarn