package com.monai.optimizer.ui.theme

import androidx.compose.ui.graphics.Color

// ── Neutral scale (monochrome, Linear/Notion-style) ─────────────
// Background sedikit lebih terang dari pure-black agar tidak terkesan "cyber",
// setiap level surface naik sangat tipis (tonal elevation ala Material 3).
val Neutral0   = Color(0xFF0B0B0D)  // app background
val Neutral50  = Color(0xFF141416)  // surface (card level 1)
val Neutral100 = Color(0xFF1B1B1E)  // surface raised (card level 2 / nested)
val Neutral200 = Color(0xFF232327)  // hover / pressed state
val Neutral700 = Color(0xFF8A8A93)  // secondary text
val Neutral500 = Color(0xFF6C6C75)  // tertiary text
val Neutral300 = Color(0xFF3A3A40)  // disabled text
val Neutral900 = Color(0xFFF2F2F3)  // primary text (near-white, not pure white)

val HairlineBorder = Color(0x14FFFFFF)  // separator sangat tipis, tanpa warna

// ── Satu-satunya accent (dipakai SANGAT jarang: CTA utama & state aktif) ──
val Accent      = Color(0xFF5B7CFA)  // muted indigo-blue, desaturated
val AccentMuted = Color(0xFF262A3D)  // container accent (low-key bg)
val OnAccent    = Color(0xFFFFFFFF)

// ── Status semantik (bukan dekorasi — hanya dipakai utk arti error/warn/ok) ──
val StatusError = Color(0xFFC97A7D)  // desaturated red
val StatusWarn  = Color(0xFFC9A268)  // desaturated amber
val StatusOk    = Color(0xFF7FA98F)  // desaturated green

// ─────────────────────────────────────────────────────────────────
// Alias kompatibilitas — supaya screen lain (Home/Tools/Charging/Log)
// tidak perlu diedit satu-satu. Semua "glow" lama sekarang menunjuk ke
// palet monokrom di atas, jadi otomatis hilang efek neon-nya di semua
// tempat tanpa mengganti logic tiap screen.
// ─────────────────────────────────────────────────────────────────
val CyanGlow     = Accent
val EmeraldGlow  = Accent
val PurpleGlow   = Accent
val OrangeGlow   = StatusWarn
val OrangeAcc    = StatusWarn

val DarkBg         = Neutral0
val DarkSurface    = Neutral50
val DarkSurfaceVar = Neutral100
val Surface2       = Neutral50
val Surface3       = Neutral100
val GlassSurface   = Neutral50
val GlassCard      = Neutral50
val GlassBorder    = HairlineBorder
val GlassBorderStrong = Color(0x24FFFFFF)

val TextPrimary   = Neutral900
val TextSecondary = Neutral700
val TextTertiary  = Neutral500
val TextDisabled  = Neutral300

val GreenOk   = StatusOk
val RedErr    = StatusError
val AmberWarn = StatusWarn
