package com.example.drumsamplebluepill.ui

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drumsamplebluepill.BoardConfig
import com.example.drumsamplebluepill.DrumViewModel
import com.example.drumsamplebluepill.PadState

// ── Paleta ────────────────────────────────────────────────────────────────────
private val BgPage   = Color(0xFFF0F0F5)
private val BgPanel  = Color(0xFFFFFFFF)
private val BgPad    = Color(0xFF0F0F14)
private val Divider  = Color(0xFFDDDDE8)
private val TextPri  = Color(0xFF1A1A28)
private val TextSec  = Color(0xFF6A6A7C)
private val TextHint = Color(0xFFAAAAAC)

private val PadAccent = listOf(
    Color(0xFF4488FF), Color(0xFF44DDAA), Color(0xFFFFAA33), Color(0xFFFF5599),
    Color(0xFF9966FF), Color(0xFF33DDFF), Color(0xFFFFDD44), Color(0xFF66FF88),
)

// ── Screen ────────────────────────────────────────────────────────────────────
@Composable
fun DrumPadScreen(viewModel: DrumViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedPad by remember { mutableIntStateOf(-1) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val col = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            c.moveToFirst(); if (col >= 0) c.getString(col) else null
        } ?: uri.lastPathSegment ?: "Sample"
        if (selectedPad >= 0) viewModel.assignSample(selectedPad, uri, name)
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPage),
    ) {
        // ── LEFT: pads ────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusChip(
                connected = state.connected,
                serialReady = state.serialReady,
                deviceName = state.deviceName,
            )
            // 2 col × 4 row grid
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(4) { row ->
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        repeat(2) { col ->
                            val pad = state.pads[row * 2 + col]
                            RubberPad(
                                pad = pad,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onClick = { selectedPad = pad.index; filePicker.launch(arrayOf("audio/*")) },
                            )
                        }
                    }
                }
            }
        }

        VerticalDivider(color = Divider)

        // ── RIGHT: config ─────────────────────────────────────────────────────
        ConfigPanel(
            config = state.boardConfig,
            serialReady = state.serialReady,
            onRefresh = viewModel::refreshConfig,
            onSave = viewModel::saveConfig,
            onThreshold = viewModel::setThreshold,
            onNote = viewModel::setNote,
            onGamma = viewModel::setGamma,
            onMask = viewModel::setMask,
            onScan = viewModel::setScan,
            onLen = viewModel::setLen,
            onPeakMax = viewModel::setPeakMax,
            onChannel = viewModel::setChannel,
        )
    }
}

// ── Status chip ───────────────────────────────────────────────────────────────
@Composable
private fun StatusChip(connected: Boolean, serialReady: Boolean, deviceName: String) {
    val dotColor by animateColorAsState(
        if (connected) Color(0xFF22CC77) else Color(0xFFBBBBCC),
        tween(400), label = "dot",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BgPanel)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(dotColor, CircleShape))
        Text(
            text = if (connected) deviceName.ifBlank { "Blue Pill" } else "Sem placa",
            color = if (connected) TextPri else TextSec,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (serialReady) {
            Text("CFG", color = Color(0xFF22CC77), fontFamily = FontFamily.Monospace, fontSize = 8.sp)
        }
    }
}

// ── Rubber pad ────────────────────────────────────────────────────────────────
@Composable
private fun RubberPad(pad: PadState, modifier: Modifier, onClick: () -> Unit) {
    val accent = PadAccent[pad.index]
    val hasSample = pad.sampleName != null

    val glowAlpha by animateColorAsState(
        targetValue = if (pad.isHit) accent.copy(alpha = 0.55f) else Color.Transparent,
        animationSpec = tween(if (pad.isHit) 8 else 160),
        label = "glow",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            pad.isHit  -> accent
            hasSample  -> accent.copy(alpha = 0.35f)
            else       -> Color(0xFF2A2A35)
        },
        animationSpec = tween(if (pad.isHit) 8 else 160),
        label = "border",
    )

    // Pre-compute noise dots (stable per pad index)
    val dots = remember(pad.index) {
        val rng = java.util.Random(pad.index.toLong() * 31L + 7L)
        List(180) { Offset(rng.nextFloat(), rng.nextFloat()) }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BgPad)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Rubber texture dots
        Canvas(Modifier.fillMaxSize()) {
            dots.forEach { n ->
                drawCircle(Color.White.copy(alpha = 0.016f), radius = 0.8f, center = Offset(n.x * size.width, n.y * size.height))
            }
            // Glow overlay on hit
            if (glowAlpha != Color.Transparent) {
                drawRect(glowAlpha, size = size)
            }
        }

        // Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(6.dp),
        ) {
            Text(
                text = "CH ${pad.index + 1}",
                color = if (pad.isHit) accent else accent.copy(alpha = 0.85f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "N${pad.midiNote}",
                color = Color(0xFF44445A),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = pad.sampleName ?: "—",
                color = if (hasSample) Color(0xFFBBBBCC) else Color(0xFF303040),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 11.sp,
            )
        }
    }
}

// ── Config panel ──────────────────────────────────────────────────────────────
@Composable
private fun ConfigPanel(
    config: BoardConfig,
    serialReady: Boolean,
    onRefresh: () -> Unit,
    onSave: () -> Unit,
    onThreshold: (Int, Int) -> Unit,
    onNote: (Int, Int) -> Unit,
    onGamma: (Int) -> Unit,
    onMask: (Int) -> Unit,
    onScan: (Int) -> Unit,
    onLen: (Int) -> Unit,
    onPeakMax: (Int) -> Unit,
    onChannel: (Int) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(BgPanel),
    ) {
        // Tab bar + action buttons
        Row(verticalAlignment = Alignment.CenterVertically) {
            TabRow(
                selectedTabIndex = tab,
                modifier = Modifier.weight(1f),
                containerColor = BgPanel,
                contentColor = TextPri,
            ) {
                Tab(selected = tab == 0, onClick = { tab = 0 }) {
                    Text("Canais", fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp))
                }
                Tab(selected = tab == 1, onClick = { tab = 1 }) {
                    Text("Global", fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp))
                }
            }
            TextButton(onClick = onRefresh, enabled = serialReady) {
                Text("GET", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
            TextButton(onClick = onSave, enabled = serialReady) {
                Text("SALVAR", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
        HorizontalDivider(color = Divider)

        if (!serialReady) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Conecte o Blue Pill via USB OTG\npara configurar a placa",
                    color = TextHint,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                )
            }
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (tab) {
                0 -> PadsTab(config, onThreshold, onNote)
                1 -> GlobalTab(config, onGamma, onMask, onScan, onLen, onPeakMax, onChannel)
            }
        }
    }
}

// ── Pads tab ──────────────────────────────────────────────────────────────────
@Composable
private fun PadsTab(
    config: BoardConfig,
    onThreshold: (Int, Int) -> Unit,
    onNote: (Int, Int) -> Unit,
) {
    repeat(8) { i ->
        Column {
            Text(
                text = "CH ${i + 1}  ·  PA$i",
                color = PadAccent[i],
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
            ConfigSlider(
                label = "Threshold",
                value = config.th[i],
                min = 0, max = 4095,
                onValueCommit = { onThreshold(i, it) },
            )
            ConfigSlider(
                label = "Nota MIDI",
                value = config.note[i],
                min = 0, max = 127,
                displayValue = "${config.note[i]}  ${midiNoteName(config.note[i])}",
                onValueCommit = { onNote(i, it) },
            )
            if (i < 7) HorizontalDivider(color = Divider, modifier = Modifier.padding(top = 8.dp))
        }
    }
    Spacer(Modifier.height(12.dp))
}

// ── Global tab ────────────────────────────────────────────────────────────────
@Composable
private fun GlobalTab(
    config: BoardConfig,
    onGamma: (Int) -> Unit,
    onMask: (Int) -> Unit,
    onScan: (Int) -> Unit,
    onLen: (Int) -> Unit,
    onPeakMax: (Int) -> Unit,
    onChannel: (Int) -> Unit,
) {
    Spacer(Modifier.height(6.dp))
    ConfigSlider("Canal MIDI",          config.channel,   0,  15,   displayValue = "CH ${config.channel + 1}", onValueCommit = onChannel)
    ConfigSlider("Curva velocity ×100", config.gammaX100, 10, 300,  onValueCommit = onGamma)
    ConfigSlider("Anti-retrigger (ms)", config.maskMs,    1,  1000, onValueCommit = onMask)
    ConfigSlider("Janela pico (µs)",    config.scanUs,    200,20000,onValueCommit = onScan)
    ConfigSlider("Note-Off (ms)",       config.noteLenMs, 5,  2000, onValueCommit = onLen)
    ConfigSlider("Pico máx. ADC",       config.peakMax,   200,4095, onValueCommit = onPeakMax)
    Spacer(Modifier.height(12.dp))
}

// ── Slider row ────────────────────────────────────────────────────────────────
@Composable
private fun ConfigSlider(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    displayValue: String = value.toString(),
    onValueCommit: (Int) -> Unit,
) {
    var sliderPos by remember(value) { mutableFloatStateOf(value.toFloat()) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = TextSec,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.width(130.dp),
        )
        Slider(
            value = sliderPos,
            onValueChange = { sliderPos = it },
            onValueChangeFinished = { onValueCommit(sliderPos.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = TextPri,
                activeTrackColor = TextPri,
                inactiveTrackColor = Divider,
            ),
        )
        Text(
            text = displayValue,
            color = TextPri,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(70.dp),
        )
    }
}

// ── MIDI note name ────────────────────────────────────────────────────────────
private fun midiNoteName(note: Int): String {
    val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    return "${names[note % 12]}${note / 12 - 1}"
}
