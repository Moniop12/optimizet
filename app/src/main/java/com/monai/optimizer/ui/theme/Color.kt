package com.monai.optimizer.ui.theme

import androidx.compose.ui.graphics.Color

// ── Dasar terang. Kartu dibuat PUTIH PENUH, background sedikit abu-abu
// (bukan putih rata) — supaya kartu tetap "elevated"/beda dari
// background walau shadow-nya tipis. Kebalikan dari dark theme (di situ
// kartu lebih TERANG dari bg, di sini kartu lebih PUTIH/BERSIH dari bg). ──
val BgBase      = Color(0xFFE4E6EC)  // background — diturunin dari #F1F2F6, biar gak nyolok
val CardBase    = Color(0xFFF8F9FB)  // kartu level 1 — off-white, bukan putih 100%
val CardRaised  = Color(0xFFE9EAF0)  // elemen nested (chip terpilih, badge tidak aktif)
val CardShadow  = Color(0x262A2E3D)  // shadow lembut, dinaikin dikit biar tetap kerasa di atas bg yg lebih gelap

// Highlight gloss nggak begitu perlu di tema terang (kartu udah putih),
// jadi dibikin nyaris transparan — biar nggak nambah elemen yang gak perlu.
val GlossHighlight = Color(0x00FFFFFF)
val GlossFade      = Color(0x00FFFFFF)

// Teks & ikon di atas kartu putih
val InkPrimary   = Color(0xFF15161C)  // hampir hitam
val InkSecondary = Color(0xFF676D7A)
val InkTertiary  = Color(0xFF9AA1AE)
val InkDisabled  = Color(0xFFC8CBD4)

val HairlineBorder     = Color(0x14000000)
val GlassBorderStrong  = Color(0x28000000)

// Accent utama (brand/interaktif) — dipekatin dari versi dark biar tetap
// kontras di atas putih (warna pastel/cerah pudar kalau ditaruh gitu aja
// di background terang, itu masalah yg sama kayak neumorphic dulu).
val Accent      = Color(0xFF4C5FE0)
val AccentMuted = Color(0xFFE3E5FB)
val OnAccent    = Color(0xFFFFFFFF)

val StatusError = Color(0xFFE0313F)
val StatusWarn  = Color(0xFFC97A1E)
val StatusOk    = Color(0xFF12A15C)

// ── Warna kontekstual per fitur — dipakai HANYA di ikon (bukan seluruh
// kartu). Sama makna dgn versi dark, cuma nilainya dipekatin buat kontras
// di atas putih. ──
val BatteryGreen  = Color(0xFF12A15C)  // baterai, root, status "aktif/aman"
val PerfOrange    = Color(0xFFDB7A1E)  // performa/CPU, panas/thermal
val MemoryBlue    = Color(0xFF4C5FE0)  // RAM & notifikasi (= Accent)
val CleanPurple   = Color(0xFF8C4FE0)  // cache/cleaning
val UtilityTeal   = Color(0xFF0FA79E)  // tools sistem/ADB
val DangerRed     = Color(0xFFE0313F)  // reset/hapus permanen (= StatusError)

// ─────────────────────────────────────────────────────────────────
val CyanGlow    = MemoryBlue
val EmeraldGlow = BatteryGreen
val PurpleGlow  = CleanPurple
val OrangeGlow  = PerfOrange
val OrangeAcc   = PerfOrange

val DarkBg         = BgBase
val DarkSurface    = CardBase
val DarkSurfaceVar = CardRaised
val Surface2       = CardBase
val Surface3       = CardRaised
val GlassSurface   = CardBase
val GlassCard      = CardBase
val GlassBorder    = HairlineBorder

val TextPrimary   = InkPrimary
val TextSecondary = InkSecondary
val TextTertiary  = InkTertiary
val TextDisabled  = InkDisabled

val GreenOk   = StatusOk
val RedErr    = StatusError
val AmberWarn = StatusWarn

// Alias lama supaya file lain yang mungkin masih merujuknya tidak pecah.
val NeuBase     = CardBase
val White       = InkPrimary
val White70     = InkSecondary
val White45     = InkTertiary
val White20     = InkDisabled
val Ink1        = BgBase
val Ink2        = CardBase
val Ink3        = CardRaised
val Ink4        = Color(0xFFDCDEE6)
val PureBlack   = Color(0xFF000000)
val NeuLightShadow = Color(0xFFFFFFFF)
val NeuDarkShadow  = Color(0xFF000000)
