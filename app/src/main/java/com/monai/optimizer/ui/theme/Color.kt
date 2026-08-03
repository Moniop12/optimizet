package com.monai.optimizer.ui.theme

import androidx.compose.ui.graphics.Color

// ── Murni hitam & putih. Tidak ada tint biru/abu-hangat sama sekali. ──
val PureBlack   = Color(0xFF000000)
val Ink1        = Color(0xFF0A0A0A)  // background (nyaris hitam, bukan #000 rata biar gradient glossy kelihatan)
val Ink2        = Color(0xFF141414)  // surface card
val Ink3        = Color(0xFF1E1E1E)  // surface raised / nested
val Ink4        = Color(0xFF2A2A2A)  // hover/pressed, hairline kuat

val White       = Color(0xFFFFFFFF)
val White70     = Color(0xB3FFFFFF)  // teks sekunder
val White45     = Color(0x73FFFFFF)  // teks tersier
val White20     = Color(0x33FFFFFF)  // teks disabled / ikon pasif

val HairlineBorder     = Color(0x14FFFFFF)  // separator sangat tipis
val GlassBorderStrong  = Color(0x33FFFFFF)  // rim highlight kartu "emphasize"

// Gradient "glossy": highlight tipis di pojok kiri-atas kartu, memudar ke bawah.
val GlossHighlight = Color(0x14FFFFFF)
val GlossFade      = Color(0x00FFFFFF)

// Satu-satunya "accent" adalah PUTIH itu sendiri dipakai lebih terang/pekat
// untuk state aktif (bukan warna baru) — sesuai permintaan: cuma hitam & putih.
val Accent      = White
val AccentMuted = Ink3
val OnAccent    = PureBlack

// Status semantik tetap perlu ada (error/ok) tapi didesaturasi ekstrem
// supaya nyaris abu — dipakai HANYA di teks status charging, bukan dekorasi.
val StatusError = Color(0xFFBFBFBF)
val StatusWarn  = Color(0xFFBFBFBF)
val StatusOk    = Color(0xFFFFFFFF)

// ─────────────────────────────────────────────────────────────────
// Alias kompatibilitas ke nama lama supaya Home/Tools/Log/Charging
// screen otomatis ikut berubah tanpa diedit satu-satu.
// ─────────────────────────────────────────────────────────────────
val CyanGlow    = White
val EmeraldGlow = White
val PurpleGlow  = White
val OrangeGlow  = White70
val OrangeAcc   = White70

val DarkBg         = Ink1
val DarkSurface    = Ink2
val DarkSurfaceVar = Ink3
val Surface2       = Ink2
val Surface3       = Ink3
val GlassSurface   = Ink2
val GlassCard      = Ink2
val GlassBorder    = HairlineBorder

val TextPrimary   = White
val TextSecondary = White70
val TextTertiary  = White45
val TextDisabled  = White20

val GreenOk   = StatusOk
val RedErr    = StatusError
val AmberWarn = StatusWarn
