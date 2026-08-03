package com.monai.optimizer.ui.theme

import androidx.compose.ui.graphics.Color

// ── Palet Neumorphism terang klasik ──────────────────────────────
// Basis WAJIB satu warna rata (base) — semua card/tombol pakai warna
// yang SAMA dengan background, bedanya cuma dari shadow terang/gelap
// di sekitarnya (bukan dari warna beda). Ini rumus neumorphism standar.
val NeuBase        = Color(0xFFE6E7EE)  // abu muda sedikit kebiruan (dasar neumorphism)
val NeuLightShadow = Color(0xFFFFFFFF)  // shadow terang (kiri-atas)
val NeuDarkShadow  = Color(0xFFA3B1C6)  // shadow gelap (kanan-bawah)

// Teks & ikon di atas base terang
val InkPrimary   = Color(0xFF2E3141)  // hampir hitam, sedikit biru gelap biar senada
val InkSecondary = Color(0xFF6B7280)
val InkTertiary  = Color(0xFF9AA3B2)
val InkDisabled  = Color(0xFFC3C8D3)

val HairlineBorder = Color(0x14000000)

// Satu accent dipakai sangat jarang (CTA utama, state aktif)
val Accent      = Color(0xFF4C5FD5)  // indigo redup, tetap netral-elegan
val AccentMuted = Color(0xFFDDE0F5)
val OnAccent    = Color(0xFFFFFFFF)

val StatusError = Color(0xFFC1666B)
val StatusWarn  = Color(0xFFBF9B5B)
val StatusOk    = Color(0xFF4C8567)

// ─────────────────────────────────────────────────────────────────
// Alias kompatibilitas ke nama lama yang dipakai Home/Tools/Log/
// Charging screen, supaya otomatis ikut pindah ke tema baru.
// ─────────────────────────────────────────────────────────────────
val CyanGlow    = Accent
val EmeraldGlow = Accent
val PurpleGlow  = Accent
val OrangeGlow  = StatusWarn
val OrangeAcc   = StatusWarn

val DarkBg         = NeuBase
val DarkSurface    = NeuBase
val DarkSurfaceVar = NeuBase
val Surface2       = NeuBase
val Surface3       = NeuBase
val GlassSurface   = NeuBase
val GlassCard      = NeuBase
val GlassBorder    = HairlineBorder
val GlassBorderStrong = Color(0x1F000000)

val TextPrimary   = InkPrimary
val TextSecondary = InkSecondary
val TextTertiary  = InkTertiary
val TextDisabled  = InkDisabled

val GreenOk   = StatusOk
val RedErr    = StatusError
val AmberWarn = StatusWarn

// Sisa alias lama (bekas tema hitam-putih sebelumnya) supaya file lama
// yang mungkin masih merujuknya tidak pecah — semua menunjuk netral.
val White       = InkPrimary
val White70     = InkSecondary
val White45     = InkTertiary
val White20     = InkDisabled
val Ink1        = NeuBase
val Ink2        = NeuBase
val Ink3        = NeuBase
val Ink4        = Color(0xFFD4D7E2)
val PureBlack   = InkPrimary
val GlossHighlight = Color(0x00000000)
val GlossFade      = Color(0x00000000)
