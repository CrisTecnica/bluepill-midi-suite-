package com.example.drumsamplebluepill.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// ── Palette ────────────────────────────────────────────────────────────────
object RufoColor {
    val gold        = Color(0xFFD8B25E)
    val brassBright = Color(0xFFF7E6AD)
    val goldDark    = Color(0xFF7A5A26)
    val ink         = Color(0xFF241A0A)

    val brass       = Color(0xFFCF9C45)
    val wood0       = Color(0xFF3C2A1B)
    val wood1       = Color(0xFF2B1D12)
    val wood2       = Color(0xFF1C120B)

    val headLight   = Color(0xFF54545A)
    val headMid     = Color(0xFF303034)
    val headDark    = Color(0xFF141417)

    val chrome0     = Color(0xFFE8E8EE)
    val chrome1     = Color(0xFF9A9AA2)
    val chrome2     = Color(0xFF6A6A72)

    val text        = Color(0xFFF0E7D4)
    val dim         = Color(0xFFA39A83)
    val sage        = Color(0xFF86B074)

    val labelOnBrass = Color(0xFF2A1E0C)
    val rimLabel    = Color(0xFFE0C79A)
    val rimOutline  = Color(0xFF0C0A07)

    // Accent dots per piece type
    val accentTeal   = Color(0xFF5BB6A8)
    val accentViolet = Color(0xFF9A7BD6)
    val accentAmber  = Color(0xFFE0A33D)
    val accentRose   = Color(0xFFD6708A)

    // Flash
    val flashAmber  = Color(0xFFFFE6A0)
}

// ── Typography ──────────────────────────────────────────────────────────────
// Using system fallbacks; replace Font() with Google Fonts when added:
//   implementation("androidx.compose.ui:ui-text-google-fonts")
object RufoFont {
    val Display  = FontFamily.SansSerif    // target: Oswald 600/700
    val Mono     = FontFamily.Monospace    // target: Space Mono
}
