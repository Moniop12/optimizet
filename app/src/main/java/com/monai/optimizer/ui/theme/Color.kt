package com.monai.optimizer.ui.theme

import androidx.compose.ui.graphics.Color

// ── Dasar kartu — abu muda hangat, tanpa efek neumorphic blur lagi ──
// (blur dual-shadow dari lib sebelumnya kelihatan butek/pecah di device
// asli, jadi kartu sekarang pakai shadow tunggal gelap yang bersih —
// lihat Components.kt: Modifier.shadow(...) pakai CardShadow di bawah).
val NeuBase   = Color(0xFFE6E7EE)
val CardShadow = Color(0x3A2E3141)  // shadow gelap tunggal, bukan blur ganda

// Teks & ikon di atas base terang
val InkPrimary   = Color(0xFF2E3141)
val InkSecondary = Color(0xFF6B7280)
val InkTertiary  = Color(0xFF9AA3B2)
val InkDisabled  = Color(0xFFC3C8D3)

val HairlineBorder = Color(0x14000000)

// Accent utama (brand/interaktif: nav aktif, link, teks angka penting)
val Accent      = Color(0xFF4C5FD5)  // indigo
val AccentMuted = Color(0xFFDDE0F5)
val OnAccent    = Color(0xFFFFFFFF)

val StatusError = Color(0xFFC1666B)
val StatusWarn  = Color(0xFFBF9B5B)
val StatusOk    = Color(0xFF3D9463)

// ── Warna kontekstual per fitur — dipakai HANYA di ikon (bukan seluruh
// kartu), jadi tetap kalem: satu warna = satu makna, konsisten. ──
val BatteryGreen  = Color(0xFF3D9463)  // baterai, root, status "aktif/aman"
val PerfOrange    = Color(0xFFD98A4C)  // performa/CPU, panas/thermal
val MemoryBlue    = Color(0xFF4C5FD5)  // RAM, notifikasi, AI/smart (= Accent)
val CleanPurple   = Color(0xFF8B6CC9)  // cleaning/cache/trim

// ─────────────────────────────────────────────────────────────────
// Alias yang dipakai Home/Tools/Log/Charging — sekarang tiap alias
// benar-benar beda warna sesuai makna aslinya (sebelumnya sempat
// semua ke-alias ke satu warna waktu fase hitam-putih, itu bug-nya).
// ─────────────────────────────────────────────────────────────────
val CyanGlow    = MemoryBlue
val EmeraldGlow = BatteryGreen
val PurpleGlow  = CleanPurple
val OrangeGlow  = PerfOrange
val OrangeAcc   = PerfOrange

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

// Sisa alias lama supaya file lama yang mungkin masih merujuknya tidak pecah.
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
// Alias lama dari fase neumorphic-blur (tidak dipakai lagi oleh Components.kt,
// dibiarkan ada supaya tidak pecah kalau masih direferensikan di tempat lain).
val NeuLightShadow = Color(0xFFFFFFFF)
val NeuDarkShadow  = Color(0xFFA3B1C6)
