package com.example.drumsamplebluepill.ui.kit

import androidx.compose.ui.graphics.Color
import com.example.drumsamplebluepill.ui.theme.RufoColor

enum class PieceType { CYMBAL, DRUM }

enum class ZoneId { BODY, CLOSE, OPEN, CHOKE, BELL, RIMSHOT_LEFT, RIMSHOT_RIGHT }

data class ZoneUi(
    val id: ZoneId,
    val midiNote: Int,
    val label: String? = null,
)

data class PieceUi(
    val id: String,
    val type: PieceType,
    val cx: Float,
    val cy: Float,
    val r: Float,
    val label: String,
    val brandLabel: String? = null,   // RUFO stamp on snare
    val sublabel: String? = null,     // DELUXE on toms
    val rotationDeg: Float = 0f,
    val zones: List<ZoneUi>,
    val packName: String? = null,
    val accent: Color,
) {
    val ryFactor get() = if (type == PieceType.CYMBAL) 0.33f else 0.40f
    val ry get() = r * ryFactor
}

data class StyleChip(
    val id: String,
    val name: String,
    val active: Boolean,
)

data class KitUiState(
    val pieces: List<PieceUi>,
    val styles: List<StyleChip>,
    val connected: Boolean,
    val deviceName: String = "",
)

// ── Default layout (reference space 960×540) ──────────────────────────────
fun defaultKitPieces(): List<PieceUi> = listOf(
    PieceUi(
        id = "hihat", type = PieceType.CYMBAL,
        cx = 150f, cy = 150f, r = 92f,
        label = "HI-HAT", rotationDeg = 0f,
        zones = listOf(
            ZoneUi(ZoneId.CLOSE, 42, "CLOSE"),
            ZoneUi(ZoneId.OPEN,  46, "OPEN"),
            ZoneUi(ZoneId.BODY,  42),
        ),
        accent = RufoColor.accentTeal,
    ),
    PieceUi(
        id = "crash1", type = PieceType.CYMBAL,
        cx = 335f, cy = 150f, r = 96f,
        label = "CRASH", rotationDeg = -6f,
        zones = listOf(
            ZoneUi(ZoneId.BODY,  49),
            ZoneUi(ZoneId.CHOKE, 49, "CHOKE"),
        ),
        accent = RufoColor.accentViolet,
    ),
    PieceUi(
        id = "tom1", type = PieceType.DRUM,
        cx = 500f, cy = 108f, r = 86f,
        label = "TOM 1", sublabel = "DELUXE",
        zones = listOf(ZoneUi(ZoneId.BODY, 48)),
        accent = RufoColor.accentAmber,
    ),
    PieceUi(
        id = "ride", type = PieceType.CYMBAL,
        cx = 715f, cy = 155f, r = 112f,
        label = "RIDE", rotationDeg = 4f,
        zones = listOf(
            ZoneUi(ZoneId.BODY, 51),
            ZoneUi(ZoneId.BELL, 53, "BELL"),
        ),
        accent = RufoColor.accentTeal,
    ),
    PieceUi(
        id = "crash2", type = PieceType.CYMBAL,
        cx = 775f, cy = 330f, r = 92f,
        label = "CRASH", rotationDeg = 7f,
        zones = listOf(ZoneUi(ZoneId.BODY, 57)),
        accent = RufoColor.accentViolet,
    ),
    PieceUi(
        id = "snare", type = PieceType.DRUM,
        cx = 415f, cy = 318f, r = 84f,
        label = "CAIXA", brandLabel = "RUFO",
        zones = listOf(
            ZoneUi(ZoneId.BODY,          38),
            ZoneUi(ZoneId.RIMSHOT_LEFT,  37, "RIM"),
            ZoneUi(ZoneId.RIMSHOT_RIGHT, 37, "RIM"),
        ),
        accent = RufoColor.accentRose,
    ),
    PieceUi(
        id = "bass", type = PieceType.DRUM,
        cx = 690f, cy = 428f, r = 108f,
        label = "SURDO", sublabel = "DELUXE",
        zones = listOf(ZoneUi(ZoneId.BODY, 45)),
        accent = RufoColor.accentAmber,
    ),
)
