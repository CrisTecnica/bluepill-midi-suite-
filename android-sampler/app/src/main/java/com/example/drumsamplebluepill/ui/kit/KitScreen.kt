package com.example.drumsamplebluepill.ui.kit

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drumsamplebluepill.ui.theme.RufoColor
import com.example.drumsamplebluepill.ui.theme.RufoFont
import kotlinx.coroutines.launch
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitScreen(
    viewModel: KitViewModel,
    onHit: (String, ZoneId?) -> Unit = { _, _ -> },
    onEditPiece: (String) -> Unit = {},
    onSelectStyle: (String) -> Unit = {},
    onCreateStyle: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val textMeasurer = rememberTextMeasurer()

    // Per-piece animation state
    val flashAlphaMap = remember { state.pieces.associate { it.id to Animatable(0f) } }
    val flashScaleMap = remember { state.pieces.associate { it.id to Animatable(1f) } }

    // Collect flash events and drive animations
    LaunchedEffect(viewModel) {
        viewModel.flashEvents.collect { event ->
            val alpha = flashAlphaMap[event.pieceId] ?: return@collect
            val scale = flashScaleMap[event.pieceId] ?: return@collect
            launch {
                alpha.snapTo(event.velocity / 127f)
                alpha.animateTo(0f, tween(180))
            }
            launch {
                scale.animateTo(1.04f, tween(60))
                scale.animateTo(1.00f, tween(120))
            }
        }
    }

    // Bottom sheet edit placeholder
    var editPieceId by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RufoColor.wood2),
    ) {
        val density = LocalDensity.current
        val canvasW = with(density) { maxWidth.toPx() }
        val canvasH = with(density) { maxHeight.toPx() }
        val scale  = min(canvasW / 960f, canvasH / 540f)
        val offsetX = (canvasW - 960f * scale) / 2f
        val offsetY = (canvasH - 540f * scale) / 2f

        // ── Canvas layer ──────────────────────────────────────────────────────
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawKitCanvas(
                pieces = state.pieces,
                flashAlpha = { id -> flashAlphaMap[id]?.value ?: 0f },
                flashScale = { id -> flashScaleMap[id]?.value ?: 1f },
                textMeasurer = textMeasurer,
            )
        }

        // ── Hit layer ─────────────────────────────────────────────────────────
        PieceHitLayer(
            pieces = state.pieces,
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
            onHit = { pieceId, zoneId ->
                viewModel.onHit(pieceId, zoneId)
                onHit(pieceId, zoneId)
            },
            onLongPress = { pieceId ->
                editPieceId = pieceId
                onEditPiece(pieceId)
            },
        )

        // ── Top overlay ───────────────────────────────────────────────────────
        TopOverlay(
            styles = state.styles,
            connected = state.connected,
            deviceName = state.deviceName,
            onSelectStyle = { id -> viewModel.onSelectStyle(id); onSelectStyle(id) },
            onCreateStyle = onCreateStyle,
        )

        // ── Hint (bottom center) ──────────────────────────────────────────────
        Text(
            text = "toque pra tocar  ·  segure pra trocar o pacote de samples",
            color = RufoColor.dim.copy(alpha = 0.45f),
            fontFamily = RufoFont.Mono,
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp),
        )
    }

    // Edit piece bottom sheet (placeholder)
    if (editPieceId != null) {
        ModalBottomSheet(
            onDismissRequest = { editPieceId = null },
            sheetState = sheetState,
            containerColor = Color(0xFF1A1208),
        ) {
            val piece = state.pieces.find { it.id == editPieceId }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                Text(
                    text = piece?.label ?: "",
                    color = RufoColor.brassBright,
                    fontFamily = RufoFont.Display,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Pacote: ${piece?.packName ?: "— nenhum —"}",
                    color = RufoColor.dim,
                    fontFamily = RufoFont.Mono,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Seleção de pacote de samples  ·  em breve",
                    color = RufoColor.dim.copy(alpha = 0.5f),
                    fontFamily = RufoFont.Mono,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ── Hit / gesture layer ───────────────────────────────────────────────────────
@Composable
private fun PieceHitLayer(
    pieces: List<PieceUi>,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onHit: (String, ZoneId?) -> Unit,
    onLongPress: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(pieces, scale, offsetX, offsetY) {
                detectTapGestures(
                    onTap = { screenOffset ->
                        val refX = (screenOffset.x - offsetX) / scale
                        val refY = (screenOffset.y - offsetY) / scale
                        hitTest(pieces, refX, refY)?.let { onHit(it.id, ZoneId.BODY) }
                    },
                    onLongPress = { screenOffset ->
                        val refX = (screenOffset.x - offsetX) / scale
                        val refY = (screenOffset.y - offsetY) / scale
                        hitTest(pieces, refX, refY)?.let { onLongPress(it.id) }
                    },
                )
            },
    )
}

// Check from front-to-back (reversed list = front piece wins)
private fun hitTest(pieces: List<PieceUi>, refX: Float, refY: Float): PieceUi? {
    return pieces.asReversed().firstOrNull { p ->
        val dx = (refX - p.cx) / p.r
        val dy = (refY - p.cy) / p.ry
        dx * dx + dy * dy <= 1f
    }
}

// ── Top overlay ───────────────────────────────────────────────────────────────
@Composable
private fun TopOverlay(
    styles: List<StyleChip>,
    connected: Boolean,
    deviceName: String,
    onSelectStyle: (String) -> Unit,
    onCreateStyle: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xB8080604), Color.Transparent),
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY,
                )
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Brand
            Column {
                Text(
                    text = "RUFO",
                    style = TextStyle(
                        brush = Brush.horizontalGradient(
                            listOf(RufoColor.brassBright, RufoColor.brass)
                        ),
                        fontFamily = RufoFont.Display,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    ),
                )
                Text(
                    text = "kit",
                    fontFamily = RufoFont.Mono,
                    fontSize = 9.sp,
                    color = RufoColor.gold,
                )
            }

            Spacer(Modifier.width(20.dp))

            // Style chips (scrollable)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                styles.forEach { chip ->
                    StyleChipItem(chip, onClick = { onSelectStyle(chip.id) })
                }
                // + chip
                Text(
                    text = "＋",
                    color = RufoColor.gold,
                    fontFamily = RufoFont.Mono,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, RufoColor.gold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .clickable(onClick = onCreateStyle)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }

            Spacer(Modifier.width(16.dp))

            // Connection chip
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x22FFFFFF))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            if (connected) RufoColor.sage else RufoColor.dim,
                            CircleShape,
                        )
                )
                Text(
                    text = if (connected) "PLACA · USB" else "SEM PLACA",
                    fontFamily = RufoFont.Mono,
                    fontSize = 9.sp,
                    color = if (connected) RufoColor.sage else RufoColor.dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(10.dp))

            // Menu button
            Text(
                text = "⋮",
                color = RufoColor.dim,
                fontSize = 18.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { }
                    .padding(6.dp),
            )
        }
    }
}

@Composable
private fun StyleChipItem(chip: StyleChip, onClick: () -> Unit) {
    val bg = if (chip.active)
        Brush.horizontalGradient(listOf(RufoColor.brass, RufoColor.goldDark))
    else
        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))

    Text(
        text = chip.name,
        color = if (chip.active) RufoColor.ink else RufoColor.dim,
        fontFamily = RufoFont.Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(
                1.dp,
                if (chip.active) Color.Transparent else RufoColor.brass.copy(alpha = 0.35f),
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
    )
}
