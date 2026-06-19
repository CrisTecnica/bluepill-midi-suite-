package com.example.drumsamplebluepill

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbDevice
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

// ── Pad state ─────────────────────────────────────────────────────────────────
data class PadState(
    val index: Int,
    val midiNote: Int,
    val label: String,
    val sampleName: String? = null,
    val sampleUri: Uri? = null,
    val isHit: Boolean = false,
)

// ── Board config (mirrors firmware Config struct) ──────────────────────────────
data class BoardConfig(
    val th: List<Int>   = List(8) { 80 },
    val note: List<Int> = listOf(36, 38, 42, 46, 48, 45, 49, 51),
    val gammaX100: Int  = 60,
    val maskMs: Int     = 30,
    val scanUs: Int     = 2500,
    val noteLenMs: Int  = 40,
    val peakMax: Int    = 3500,
    val channel: Int    = 0,
)

// ── Combined UI state ─────────────────────────────────────────────────────────
data class DrumUiState(
    val pads: List<PadState>  = defaultPads(),
    val connected: Boolean    = false,
    val deviceName: String    = "",
    val serialReady: Boolean  = false,
    val boardConfig: BoardConfig = BoardConfig(),
)

private fun defaultPads() = listOf(
    PadState(0, 36, "KICK"),
    PadState(1, 38, "SNARE"),
    PadState(2, 42, "HH CLOSE"),
    PadState(3, 46, "HH OPEN"),
    PadState(4, 48, "MID TOM"),
    PadState(5, 45, "LOW TOM"),
    PadState(6, 49, "CRASH"),
    PadState(7, 51, "RIDE"),
)

// ── ViewModel ─────────────────────────────────────────────────────────────────
class DrumViewModel(app: Application) : AndroidViewModel(app) {

    private val sampleEngine = SampleEngine(app)
    private val prefs = app.getSharedPreferences("drum_kit", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(DrumUiState())
    val uiState: StateFlow<DrumUiState> = _uiState.asStateFlow()

    private val noteMap = HashMap<Int, Int>()

    private lateinit var serialEngine: SerialEngine

    private val midiEngine = MidiEngine(
        context = app,
        onNoteOn  = { note, velocity -> handleNoteOn(note, velocity) },
        onNoteOff = { },
        onConnectionChanged = { connected, name, usbDevice ->
            _uiState.update { it.copy(connected = connected, deviceName = name) }
            if (connected && usbDevice != null) serialEngine.connect(usbDevice)
            else if (!connected) serialEngine.disconnect()
        },
    )

    init {
        serialEngine = SerialEngine(
            context = app,
            onLine  = { line -> handleSerialLine(line) },
            onConnectionChanged = { ready ->
                _uiState.update { it.copy(serialReady = ready) }
                if (ready) serialEngine.sendLine("GET")
            },
        )
        _uiState.value.pads.forEach { noteMap[it.midiNote] = it.index }
        restoreSamples()
        midiEngine.start()
    }

    // ── Serial line handler ───────────────────────────────────────────────────
    private fun handleSerialLine(line: String) {
        if (!line.startsWith("{")) return
        try {
            val json = JSONObject(line)
            // Telemetry hit: {"e":"hit","p":0,"peak":1234,"vel":100}
            if (json.optString("e") == "hit") {
                val padIndex = json.optInt("p", -1)
                if (padIndex in 0..7) flashPad(padIndex)
                return
            }
            // Config response: {"th":[...],"note":[...],...}
            if (json.has("th")) {
                val thArr   = json.getJSONArray("th")
                val noteArr = json.getJSONArray("note")
                val cfg = BoardConfig(
                    th         = (0 until 8).map { thArr.getInt(it) },
                    note       = (0 until 8).map { noteArr.getInt(it) },
                    gammaX100  = json.getInt("gamma"),
                    maskMs     = json.getInt("mask"),
                    scanUs     = json.getInt("scan"),
                    noteLenMs  = json.getInt("len"),
                    peakMax    = json.getInt("pmax"),
                    channel    = json.getInt("ch"),
                )
                _uiState.update { s ->
                    // Sync MIDI note labels
                    val updatedPads = s.pads.map { p -> p.copy(midiNote = cfg.note[p.index]) }
                    s.copy(boardConfig = cfg, pads = updatedPads)
                }
                rebuildNoteMap()
            }
        } catch (_: Exception) {}
    }

    private fun rebuildNoteMap() {
        noteMap.clear()
        _uiState.value.pads.forEach { noteMap[it.midiNote] = it.index }
    }

    // ── MIDI hit ──────────────────────────────────────────────────────────────
    private fun handleNoteOn(note: Int, velocity: Int) {
        val padIndex = noteMap[note] ?: return
        sampleEngine.play(padIndex, velocity)
        flashPad(padIndex)
    }

    private fun flashPad(padIndex: Int) {
        viewModelScope.launch {
            _uiState.update { s -> s.copy(pads = s.pads.map { if (it.index == padIndex) it.copy(isHit = true) else it }) }
            delay(80)
            _uiState.update { s -> s.copy(pads = s.pads.map { if (it.index == padIndex) it.copy(isHit = false) else it }) }
        }
    }

    // ── Sample management ─────────────────────────────────────────────────────
    fun assignSample(padIndex: Int, uri: Uri, displayName: String) {
        if (!sampleEngine.loadSample(getApplication(), padIndex, uri)) return
        _uiState.update { s ->
            s.copy(pads = s.pads.map { if (it.index == padIndex) it.copy(sampleUri = uri, sampleName = displayName) else it })
        }
        prefs.edit()
            .putString("uri_$padIndex", uri.toString())
            .putString("name_$padIndex", displayName)
            .apply()
    }

    private fun restoreSamples() {
        val app = getApplication<Application>()
        _uiState.value.pads.forEach { pad ->
            val uriStr = prefs.getString("uri_${pad.index}", null) ?: return@forEach
            val name   = prefs.getString("name_${pad.index}", null) ?: return@forEach
            val uri    = Uri.parse(uriStr)
            if (sampleEngine.loadSample(app, pad.index, uri)) {
                _uiState.update { s ->
                    s.copy(pads = s.pads.map { if (it.index == pad.index) it.copy(sampleUri = uri, sampleName = name) else it })
                }
            }
        }
    }

    // ── Board config commands ─────────────────────────────────────────────────
    fun setThreshold(padIndex: Int, value: Int) {
        updateConfig { it.copy(th = it.th.toMutableList().also { l -> l[padIndex] = value }) }
        serialEngine.sendLine("SET TH $padIndex $value")
    }

    fun setNote(padIndex: Int, value: Int) {
        updateConfig { it.copy(note = it.note.toMutableList().also { l -> l[padIndex] = value }) }
        serialEngine.sendLine("SET NOTE $padIndex $value")
        rebuildNoteMap()
    }

    fun setGamma(value: Int) {
        updateConfig { it.copy(gammaX100 = value) }
        serialEngine.sendLine("SET GAMMA 0 $value")
    }

    fun setMask(value: Int) {
        updateConfig { it.copy(maskMs = value) }
        serialEngine.sendLine("SET MASK 0 $value")
    }

    fun setScan(value: Int) {
        updateConfig { it.copy(scanUs = value) }
        serialEngine.sendLine("SET SCAN 0 $value")
    }

    fun setLen(value: Int) {
        updateConfig { it.copy(noteLenMs = value) }
        serialEngine.sendLine("SET LEN 0 $value")
    }

    fun setPeakMax(value: Int) {
        updateConfig { it.copy(peakMax = value) }
        serialEngine.sendLine("SET PMAX 0 $value")
    }

    fun setChannel(value: Int) {
        updateConfig { it.copy(channel = value) }
        serialEngine.sendLine("SET CH 0 $value")
    }

    fun refreshConfig()  = serialEngine.sendLine("GET")
    fun saveConfig()     = serialEngine.sendLine("SAVE")

    private fun updateConfig(block: (BoardConfig) -> BoardConfig) {
        _uiState.update { it.copy(boardConfig = block(it.boardConfig)) }
    }

    override fun onCleared() {
        midiEngine.stop()
        serialEngine.release()
        sampleEngine.release()
        super.onCleared()
    }
}
