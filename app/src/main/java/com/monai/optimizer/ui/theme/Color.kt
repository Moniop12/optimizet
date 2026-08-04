package com.monai.optimizer.ui.theme

import androidx.compose.ui.graphics.Color

// ── Dasar gelap — bukan hitam pekat rata (biar nggak "mati"/cyber),
// kartu dibuat SATU TINGKAT lebih terang dari background supaya beda
// jelas cuma dari tone-nya (bukan cuma shadow tipis yang gampang
// hilang di atas gelap — shadow emang kurang kelihatan di dark mode).
val BgBase      = Color(0xFF121317)  // background
val CardBase    = Color(0xFF1C1E24)  // kartu level 1
val CardRaised  = Color(0xFF262932)  // elemen nested (chip terpilih, badge tidak aktif)
val CardShadow  = Color(0x66000000)  // shadow tipis, cuma buat nambah kedalaman

// Highlight tipis di tepi atas kartu → kesan "mengkilap" ala M3 Expressive,
// tanpa border tebal atau glow warna.
val GlossHighlight = Color(0x14FFFFFF)
val GlossFade      = Color(0x00000000)

// Teks & ikon di atas kartu gelap
val InkPrimary   = Color(0xFFF3F4F6)  // hampir putih
val InkSecondary = Color(0xFFA1A6B3)
val InkTertiary  = Color(0xFF767C8A)
val InkDisabled  = Color(0xFF454956)

val HairlineBorder     = Color(0x14FFFFFF)
val GlassBorderStrong  = Color(0x26FFFFFF)

// Accent utama (brand/interaktif) — dinaikkan sedikit terangnya dari versi
// terang biar tetap nyala jelas di atas dasar gelap, tapi masih desaturated
// (bukan indigo neon).
val Accent      = Color(0xFF7B8CFF)
val AccentMuted = Color(0xFF2B3050)
val OnAccent    = Color(0xFF11121A)

val StatusError = Color(0xFFE0808A)
val StatusWarn  = Color(0xFFE0AC70)
val StatusOk    = Color(0xFF6FCB94)

// ── Warna kontekstual per fitur — dipakai HANYA di ikon (bukan seluruh
// kartu), disesuaikan biar tetap kebaca jelas di atas kartu gelap. ──
val BatteryGreen  = Color(0xFF6FCB94)  // baterai, root, status "aktif/aman"
val PerfOrange    = Color(0xFFE8A365)  // performa/CPU, panas/thermal
val MemoryBlue    = Color(0xFF7B8CFF)  // RAM, notifikasi, AI/smart (= Accent)
val CleanPurple   = Color(0xFFB79AEE)  // cleaning/cache/trim

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
val Ink4        = Color(0xFF31353F)
val PureBlack   = Color(0xFF000000)
val NeuLightShadow = Color(0xFFFFFFFF)
val NeuDarkShadow  = Color(0xFF000000)
