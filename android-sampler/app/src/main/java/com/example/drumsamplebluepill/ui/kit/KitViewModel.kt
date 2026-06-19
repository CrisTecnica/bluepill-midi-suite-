package com.example.drumsamplebluepill.ui.kit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FlashEvent(val pieceId: String, val velocity: Int)

class KitViewModel : ViewModel() {

    private val _state = MutableStateFlow(
        KitUiState(
            pieces    = defaultKitPieces(),
            styles    = listOf(
                StyleChip("rock", "Rock",  active = true),
                StyleChip("jazz", "Jazz",  active = false),
                StyleChip("trap", "Trap",  active = false),
            ),
            connected = false,
        )
    )
    val state: StateFlow<KitUiState> = _state.asStateFlow()

    private val _flashEvents = MutableSharedFlow<FlashEvent>(extraBufferCapacity = 16)
    val flashEvents: SharedFlow<FlashEvent> = _flashEvents.asSharedFlow()

    // Called by external MIDI source or by internal tap
    fun flash(pieceId: String, velocity: Int) {
        viewModelScope.launch {
            _flashEvents.emit(FlashEvent(pieceId, velocity.coerceIn(1, 127)))
        }
    }

    fun onHit(pieceId: String, zoneId: ZoneId? = null) {
        flash(pieceId, 100)
        // Audio / MIDI forwarding wired from Activity/Service
    }

    fun onSelectStyle(styleId: String) {
        _state.update { s ->
            s.copy(styles = s.styles.map { it.copy(active = it.id == styleId) })
        }
    }

    fun setConnected(connected: Boolean, name: String = "") {
        _state.update { it.copy(connected = connected, deviceName = name) }
    }
}
