package com.example.drumsamplebluepill.ui.kit

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.drumsamplebluepill.ui.theme.RufoColor
import com.example.drumsamplebluepill.ui.theme.RufoFont
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ── Entry point ──────────────────────────────────────────────────────────────
fun DrawScope.drawKitCanvas(
    pieces: List<PieceUi>,
    flashAlpha: (String) -> Float,
    flashScale: (String) -> Float,
    textMeasurer: TextMeasurer,
) {
    val s = min(size.width / 960f, size.height / 540f)
    val ox = (size.width  - 960f * s) / 2f
    val oy = (size.height - 540f * s) / 2f

    withTransform({ translate(ox, oy); scale(s, s, Offset.Zero) }) {
        drawWood()
        drawStands()
        drawPedals()
        // pieces in draw order (back → front; last drawn = front priority for hit-test)
        pieces.forEach { p ->
            val fa = flashAlpha(p.id)
            val fs = flashScale(p.id)
            withTransform({ rotate(p.rotationDeg, Offset(p.cx, p.cy)) }) {
                if (p.type == PieceType.CYMBAL) drawCymbal(p, fa, fs)
                else                             drawDrum(p, fa, fs)
            }
        }
        // Labels drawn without rotation (readability)
        pieces.forEach { p -> drawPieceLabels(p, textMeasurer) }
    }
}

// ── Background ────────────────────────────────────────────────────────────────
private fun DrawScope.drawWood() {
    val w = 960f; val h = 540f
    // Base gradient
    drawRect(
        Brush.verticalGradient(
            listOf(RufoColor.wood0, RufoColor.wood1, RufoColor.wood2),
            startY = 0f, endY = h,
        ),
        size = Size(w, h),
    )
    // Plank joints
    val jointColor = Color(0xFF140D07).copy(alpha = 0.55f)
    drawLine(jointColor, Offset(0f, h * 0.34f), Offset(w, h * 0.34f), 1.5f)
    drawLine(jointColor, Offset(0f, h * 0.67f), Offset(w, h * 0.67f), 1.5f)
    // Subtle grain lines
    val rng = java.util.Random(17L)
    repeat(70) {
        val x = rng.nextFloat() * w
        val y = rng.nextFloat() * h
        val len = 20f + rng.nextFloat() * 90f
        drawLine(
            Color(0xFF5C3A1B).copy(alpha = 0.06f),
            Offset(x, y),
            Offset(x + len, y + (rng.nextFloat() - 0.5f) * 3f),
            0.8f,
        )
    }
    // Vignette
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color(0xFF0A0604).copy(alpha = 0.75f)),
            center = Offset(w / 2, h / 2),
            radius = w * 0.72f,
        ),
        size = Size(w, h),
    )
}

// ── Stands (chrome poles) ─────────────────────────────────────────────────────
private fun DrawScope.drawStands() {
    val standColor = Color(0xFFCFCFD6).copy(alpha = 0.55f)
    val sw = 2.2f
    // hi-hat: 150→(150,470)
    drawLine(standColor, Offset(152f, 155f), Offset(150f, 470f), sw)
    // crash1: 335→(360,470)
    drawLine(standColor, Offset(337f, 158f), Offset(360f, 470f), sw)
    // ride: 715→(690,430)
    drawLine(standColor, Offset(720f, 168f), Offset(690f, 430f), sw)
    // crash2: 775→(760,470)
    drawLine(standColor, Offset(779f, 338f), Offset(760f, 470f), sw)
}

// ── Pedals ────────────────────────────────────────────────────────────────────
private fun DrawScope.drawPedals() {
    fun pedal(cx: Float, cy: Float) {
        val w = 28f; val h = 38f
        val grad = Brush.verticalGradient(
            listOf(Color(0xFFB0B0BC), Color(0xFF6A6A74), Color(0xFF3A3A42)),
            startY = cy - h / 2, endY = cy + h / 2,
        )
        drawRoundRect(
            grad,
            topLeft = Offset(cx - w / 2, cy - h / 2),
            size = Size(w, h),
            cornerRadius = CornerRadius(5f),
        )
        // Foot plate
        drawRect(
            Color(0xFF505058).copy(alpha = 0.8f),
            topLeft = Offset(cx - w / 2 - 4f, cy + h / 2 - 8f),
            size = Size(w + 8f, 10f),
        )
    }
    pedal(150f, 492f)  // hi-hat pedal
    pedal(360f, 500f)  // kick pedal
}

// ── Cymbal ────────────────────────────────────────────────────────────────────
private fun DrawScope.drawCymbal(piece: PieceUi, flashAlpha: Float, flashScale: Float) {
    val cx = piece.cx; val cy = piece.cy
    val rx = piece.r * flashScale
    val ry = piece.ry * flashScale

    // Shadow
    drawOvalShadow(cx, cy + 6f, rx, ry, blurR = 14f, alpha = 0.60f)

    // Base plate
    drawOval(
        Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFFFBEEC0),
                0.26f to Color(0xFFE6C478),
                0.60f to Color(0xFFBF9444),
                0.85f to Color(0xFF8C6A2E),
                1.00f to Color(0xFF5C441F),
            ),
            center = Offset(cx - rx * 0.08f, cy - ry * 0.2f),
            radius = rx,
        ),
        topLeft = Offset(cx - rx, cy - ry),
        size = Size(rx * 2f, ry * 2f),
    )

    // Lathe lines
    val ryRx = piece.ry / piece.r
    var lr = piece.r - 6f
    while (lr > 16f) {
        val lry = lr * ryRx * flashScale
        val lrx = lr * flashScale
        drawOval(
            color = Color(0xFF6E5224).copy(alpha = 0.26f),
            topLeft = Offset(cx - lrx, cy - lry),
            size = Size(lrx * 2f, lry * 2f),
            style = Stroke(1f),
        )
        lr -= 6f
    }

    // Bell (dome center)
    val bellRx = rx * 0.11f; val bellRy = ry * 0.50f
    drawOval(
        Brush.radialGradient(
            listOf(Color(0xFFFFF3CF), Color(0xFFD6A951), Color(0xFF7D5C28)),
            center = Offset(cx, cy),
            radius = bellRx * 1.5f,
        ),
        topLeft = Offset(cx - bellRx, cy - bellRy),
        size = Size(bellRx * 2f, bellRy * 2f),
    )

    // Specular highlight
    drawOval(
        color = Color.White.copy(alpha = 0.11f),
        topLeft = Offset(cx - rx * 0.72f, cy - ry * 0.88f),
        size = Size(rx * 0.9f, ry * 0.45f),
    )

    // Flash glow
    if (flashAlpha > 0f) {
        drawOval(
            color = RufoColor.flashAmber.copy(alpha = flashAlpha * 0.55f),
            topLeft = Offset(cx - rx, cy - ry),
            size = Size(rx * 2f, ry * 2f),
        )
    }
}

// ── Drum (pele) ───────────────────────────────────────────────────────────────
private fun DrawScope.drawDrum(piece: PieceUi, flashAlpha: Float, flashScale: Float) {
    val cx = piece.cx; val cy = piece.cy
    val rx = piece.r * flashScale
    val ry = piece.ry * flashScale

    // Shadow
    drawOvalShadow(cx, cy + 7f, rx, ry, blurR = 16f, alpha = 0.65f)

    // Outer ring (dark border)
    drawOval(
        color = Color(0xFF0E0E10),
        topLeft = Offset(cx - rx, cy - ry),
        size = Size(rx * 2f, ry * 2f),
    )

    // Chrome rim
    val rimRx = rx - 4f * flashScale; val rimRy = ry - 2f * flashScale
    drawOval(
        Brush.verticalGradient(
            listOf(RufoColor.chrome0, RufoColor.chrome1, RufoColor.chrome2, Color(0xFFCFCFD6)),
            startY = cy - rimRy, endY = cy + rimRy,
        ),
        topLeft = Offset(cx - rimRx, cy - rimRy),
        size = Size(rimRx * 2f, rimRy * 2f),
    )

    // Head
    val headRx = rx * 0.78f; val headRy = ry * 0.70f
    drawOval(
        Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to RufoColor.headLight,
                0.55f to RufoColor.headMid,
                1.00f to RufoColor.headDark,
            ),
            center = Offset(cx - headRx * 0.08f, cy - headRy * 0.14f),
            radius = headRx,
        ),
        topLeft = Offset(cx - headRx, cy - headRy),
        size = Size(headRx * 2f, headRy * 2f),
    )

    // Head texture (coated noise, masked)
    val headPath = Path().apply {
        addOval(Rect(cx - headRx, cy - headRy, cx + headRx, cy + headRy))
    }
    clipPath(headPath) {
        val rng = java.util.Random((cx * 7 + cy * 13).toLong())
        repeat(50) {
            val nx = cx - headRx + rng.nextFloat() * headRx * 2f
            val ny = cy - headRy + rng.nextFloat() * headRy * 2f
            val len = 1.5f + rng.nextFloat() * 7f
            drawLine(
                Color.White.copy(alpha = 0.055f),
                Offset(nx, ny),
                Offset(nx + len, ny + (rng.nextFloat() - 0.5f) * 2f),
                0.6f,
            )
        }
    }

    // Lugs
    drawLugs(cx, cy, rimRx, rimRy)

    // Flash glow on head
    if (flashAlpha > 0f) {
        val flashPath = Path().apply {
            addOval(Rect(cx - headRx, cy - headRy, cx + headRx, cy + headRy))
        }
        clipPath(flashPath) {
            drawOval(
                color = RufoColor.flashAmber.copy(alpha = flashAlpha * 0.45f),
                topLeft = Offset(cx - headRx, cy - headRy),
                size = Size(headRx * 2f, headRy * 2f),
            )
        }
    }
}

private fun DrawScope.drawLugs(cx: Float, cy: Float, rimRx: Float, rimRy: Float) {
    val count = 12
    val lugBrush = Brush.verticalGradient(
        listOf(RufoColor.chrome0, Color(0xFF8A8A92)),
        startY = -8f, endY = 8f,
    )
    repeat(count) { i ->
        val angle = (i * 360f / count) * PI.toFloat() / 180f
        val lx = cx + cos(angle) * rimRx
        val ly = cy + sin(angle) * rimRy
        withTransform({ rotate(i * 360f / count, Offset(cx, cy)) }) {
            drawRoundRect(
                lugBrush,
                topLeft = Offset(lx - 3f, ly - 5f),
                size = Size(6f, 10f),
                cornerRadius = CornerRadius(2f),
            )
        }
    }
}

// ── Labels ────────────────────────────────────────────────────────────────────
private fun DrawScope.drawPieceLabels(piece: PieceUi, tm: TextMeasurer) {
    val cx = piece.cx; val cy = piece.cy

    when (piece.type) {
        PieceType.CYMBAL -> {
            // Piece name on the cymbal (top third)
            val nameResult = tm.measure(
                piece.label,
                TextStyle(
                    fontFamily = RufoFont.Display,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                    color = RufoColor.labelOnBrass,
                ),
            )
            drawText(nameResult, topLeft = Offset(
                cx - nameResult.size.width / 2f,
                cy - piece.ry * 0.55f - nameResult.size.height / 2f,
            ))

            // Zone labels inside the cymbal
            piece.zones.forEach { zone ->
                when (zone.id) {
                    ZoneId.CLOSE -> drawZoneLabel(tm, zone.label ?: return@forEach, cx, cy - piece.ry * 0.20f)
                    ZoneId.OPEN  -> drawZoneLabel(tm, zone.label ?: return@forEach, cx, cy + piece.ry * 0.20f)
                    ZoneId.CHOKE -> drawZoneLabel(tm, zone.label ?: return@forEach, cx - piece.r * 0.58f, cy - piece.ry * 0.40f)
                    ZoneId.BELL  -> drawZoneLabel(tm, zone.label ?: return@forEach, cx + piece.r * 0.62f, cy)
                    else -> {}
                }
            }
        }

        PieceType.DRUM -> {
            // Brand label (RUFO) in center of snare
            if (piece.brandLabel != null) {
                val br = tm.measure(
                    piece.brandLabel,
                    TextStyle(
                        fontFamily = RufoFont.Display,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color(0xFFCBB380),
                        letterSpacing = 0.22f.sp,
                    ),
                )
                drawText(br, topLeft = Offset(cx - br.size.width / 2f, cy - br.size.height / 2f))
            }

            // Sublabel (DELUXE) below center
            if (piece.sublabel != null) {
                val sl = tm.measure(
                    piece.sublabel,
                    TextStyle(
                        fontFamily = RufoFont.Display,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 7.sp,
                        color = Color(0xFF8A8170),
                    ),
                )
                drawText(sl, topLeft = Offset(cx - sl.size.width / 2f, cy + piece.ry * 0.30f))
            }

            // Piece name above head
            val nr = tm.measure(
                piece.label,
                TextStyle(
                    fontFamily = RufoFont.Display,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 8.sp,
                    color = RufoColor.dim,
                ),
            )
            drawText(nr, topLeft = Offset(cx - nr.size.width / 2f, cy - piece.ry - nr.size.height - 2f))

            // RIM SHOT labels (outside aro, left and right)
            piece.zones.forEach { zone ->
                if (zone.id == ZoneId.RIMSHOT_LEFT || zone.id == ZoneId.RIMSHOT_RIGHT) {
                    val label = zone.label ?: return@forEach
                    val xPos = if (zone.id == ZoneId.RIMSHOT_LEFT) cx - piece.r - 18f else cx + piece.r - 10f
                    drawRimLabel(tm, label, xPos, cy - 5f)
                }
            }
        }
    }

    // Accent dot
    drawCircle(
        piece.accent,
        radius = 4f,
        center = Offset(cx + piece.r * 0.68f, cy - piece.ry * 1.20f),
    )
}

private fun DrawScope.drawZoneLabel(tm: TextMeasurer, text: String, cx: Float, cy: Float) {
    val result = tm.measure(
        text,
        TextStyle(fontFamily = RufoFont.Mono, fontSize = 7.sp, color = RufoColor.labelOnBrass),
    )
    drawText(result, topLeft = Offset(cx - result.size.width / 2f, cy - result.size.height / 2f))
}

private fun DrawScope.drawRimLabel(tm: TextMeasurer, text: String, x: Float, y: Float) {
    val result = tm.measure(
        text,
        TextStyle(fontFamily = RufoFont.Mono, fontSize = 7.sp, color = RufoColor.rimLabel),
    )
    // Stroke (outline)
    drawText(
        result,
        topLeft = Offset(x, y),
        blendMode = BlendMode.SrcOver,
    )
}

// ── Shadow helper (layered ovals, hardware-safe) ──────────────────────────────
private fun DrawScope.drawOvalShadow(cx: Float, cy: Float, rx: Float, ry: Float, blurR: Float, alpha: Float) {
    val layers = 7
    repeat(layers) { i ->
        val t = i.toFloat() / layers
        val spread = t * blurR * 0.6f
        val a = alpha * (1f - t) * 0.18f
        drawOval(
            color = Color.Black.copy(alpha = a),
            topLeft = Offset(cx - rx - spread, cy - ry - spread),
            size = Size((rx + spread) * 2f, (ry + spread) * 2f),
        )
    }
}
