package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.LowLatencyAudioEngine
import com.example.kit.KitManager
import com.example.midi.MidiService
import com.example.model.MidiDeviceItem
import com.example.model.MidiEventTelemetry
import com.example.model.MidiUtils
import com.example.model.PadSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SamplerUiState(
    val pads: List<PadSlot> = emptyList(),
    val activePadHits: Map<Int, Long> = emptyMap(),
    val isLearnModeActive: Boolean = false,
    val learningPadId: Int? = null,
    val lastNoteEvent: MidiEventTelemetry? = null,
    val availableDevices: List<MidiDeviceItem> = emptyList(),
    val connectedDevice: MidiDeviceItem? = null,
    val isDevicePickerOpen: Boolean = false,
    val editingPad: PadSlot? = null,
    val statusMessage: String? = null,
    val exportShareUri: Uri? = null,
    val isLoading: Boolean = false,
    val currentOctaveShift: Int = 0 // -3 to +3 octaves
)

class MidiSamplerViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val kitManager = KitManager(context)
    val audioEngine = LowLatencyAudioEngine(context)

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val midiService = MidiService(
        context = context,
        onDeviceConnected = { devName ->
            // Prevent USB audio hijack when MIDI controller is plugged in!
            audioEngine.ensureSpeakerRouting()
            _uiState.update { it.copy(statusMessage = "Connected: $devName (Speaker Protected)") }
        },
        onNoteOn = { channel, note, velocity -> handleMidiNoteOn(channel, note, velocity) },
        onNoteOff = { channel, note, velocity -> handleMidiNoteOff(channel, note, velocity) },
        onAllNotesOff = { handleAllNotesOff() }
    )

    private val _uiState = MutableStateFlow(
        SamplerUiState(
            pads = kitManager.createEmptyPads()
        )
    )
    val uiState: StateFlow<SamplerUiState> = _uiState.asStateFlow()

    init {
        // Collect MIDI device updates
        viewModelScope.launch {
            midiService.availableDevices.collect { devices ->
                _uiState.update { it.copy(availableDevices = devices) }
            }
        }
        viewModelScope.launch {
            midiService.activeDevice.collect { active ->
                _uiState.update { it.copy(connectedDevice = active) }
            }
        }
        viewModelScope.launch {
            midiService.lastTelemetry.collect { telemetry ->
                _uiState.update { it.copy(lastNoteEvent = telemetry) }
            }
        }

        // Load saved kit from storage
        loadInitialKit()
    }

    private fun loadInitialKit() {
        viewModelScope.launch(Dispatchers.IO) {
            val savedPads = kitManager.loadSavedKit()
            for (pad in savedPads) {
                pad.samplePath?.let { path ->
                    audioEngine.loadSample(pad.id, path)
                }
            }
            _uiState.update { it.copy(pads = savedPads) }
        }
    }

    /**
     * Trigger a pad instantly on touch-down.
     */
    fun triggerPad(padId: Int, velocity: Float = 1.0f) {
        val state = _uiState.value

        // If in MIDI-Learn mode, select this pad for incoming MIDI mapping!
        if (state.isLearnModeActive) {
            _uiState.update {
                it.copy(
                    learningPadId = padId,
                    statusMessage = "Pad ${padId + 1} selected. Press any key or pad on your MIDI controller…"
                )
            }
            return
        }

        val pad = state.pads.find { it.id == padId } ?: return

        // If pad is empty, open config sheet directly so user can load sound
        if (!pad.isLoaded) {
            openSlotConfig(padId)
            return
        }

        // Trigger audio playback with choke group and retrigger
        audioEngine.playPad(pad, velocity, state.pads)

        // Haptic feedback
        performHaptic()

        // Flash pad hit animation
        flashPadHit(padId)
    }

    private fun handleMidiNoteOn(channel: Int, note: Int, velocity: Int) {
        val state = _uiState.value

        // 1. MIDI Learn check
        if (state.isLearnModeActive && state.learningPadId != null) {
            val targetPadId = state.learningPadId
            val updatedPads = state.pads.map { pad ->
                if (pad.id == targetPadId) {
                    pad.copy(midiNote = note, midiChannel = channel)
                } else {
                    pad
                }
            }
            kitManager.saveKitConfig(updatedPads)

            flashPadHit(targetPadId)
            performHaptic()

            val noteName = MidiUtils.noteNumberToName(note)
            _uiState.update {
                it.copy(
                    pads = updatedPads,
                    learningPadId = null,
                    isLearnModeActive = false,
                    statusMessage = "Mapped Pad ${targetPadId + 1} to $noteName (#$note)"
                )
            }
            return
        }

        // 2. Normal playback triggering by matching MIDI note (taking active octave shift into account)
        val matchingPads = state.pads.filter { pad ->
            val effectiveNote = pad.midiNote
            effectiveNote == note && (pad.midiChannel == 0 || pad.midiChannel == channel)
        }

        if (matchingPads.isNotEmpty()) {
            val normVelocity = (velocity / 127f).coerceIn(0.1f, 1.0f)
            for (pad in matchingPads) {
                if (pad.isLoaded) {
                    audioEngine.playPad(pad, normVelocity, state.pads)
                    flashPadHit(pad.id)
                }
            }
        }
    }

    private fun handleMidiNoteOff(channel: Int, note: Int, velocity: Int) {
        // One-shot mode handles sample duration and choke groups
    }

    private fun handleAllNotesOff() {
        audioEngine.stopAll()
        _uiState.update {
            it.copy(
                activePadHits = emptyMap(),
                statusMessage = "All Notes Off received — Stopped all audio."
            )
        }
    }

    fun panic() {
        handleAllNotesOff()
    }

    fun toggleLearnMode() {
        _uiState.update { current ->
            val nextState = !current.isLearnModeActive
            current.copy(
                isLearnModeActive = nextState,
                learningPadId = if (nextState) current.learningPadId ?: 0 else null,
                statusMessage = if (nextState) "MIDI LEARN ACTIVE: Tap any pad, then press a key on your MIDI controller" else null
            )
        }
    }

    fun setLearningPad(padId: Int) {
        if (_uiState.value.isLearnModeActive) {
            _uiState.update {
                it.copy(
                    learningPadId = padId,
                    statusMessage = "Listening for MIDI on Pad ${padId + 1}…"
                )
            }
        }
    }

    /**
     * Shifts all pad mappings up or down by an octave (±12 semitones)
     * so players can quickly align their physical keyboard's current octave with the 12 pads.
     */
    fun shiftOctave(delta: Int) {
        val currentPads = _uiState.value.pads
        val newShift = (_uiState.value.currentOctaveShift + delta).coerceIn(-3, 4)
        val semitones = delta * 12

        val updatedPads = currentPads.map { pad ->
            val newNote = (pad.midiNote + semitones).coerceIn(0, 127)
            pad.copy(midiNote = newNote)
        }
        kitManager.saveKitConfig(updatedPads)
        _uiState.update {
            it.copy(
                pads = updatedPads,
                currentOctaveShift = newShift,
                statusMessage = "Transposed pads by ${if (delta > 0) "+1" else "-1"} Octave (Pads 1-12: ${MidiUtils.noteNumberToName(updatedPads.first().midiNote)}..${MidiUtils.noteNumberToName(updatedPads.last().midiNote)})"
            )
        }
    }

    fun forceSpeakerRouting() {
        audioEngine.ensureSpeakerRouting()
        _uiState.update { it.copy(statusMessage = "Audio forced to Device Speaker") }
    }

    fun openSlotConfig(padId: Int) {
        val pad = _uiState.value.pads.find { it.id == padId } ?: return
        _uiState.update { it.copy(editingPad = pad) }
    }

    fun closeSlotConfig() {
        _uiState.update { it.copy(editingPad = null) }
    }

    fun saveEditingPad(updatedPad: PadSlot) {
        val updatedList = _uiState.value.pads.map {
            if (it.id == updatedPad.id) updatedPad else it
        }
        kitManager.saveKitConfig(updatedList)
        _uiState.update {
            it.copy(
                pads = updatedList,
                editingPad = null,
                statusMessage = "Updated Pad ${updatedPad.id + 1}: ${updatedPad.name}"
            )
        }
    }

    fun assignAudioFileToPad(padId: Int, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = kitManager.importAudioFromUri(uri, padId)
            if (result != null) {
                val (filePath, fileName) = result
                audioEngine.loadSample(padId, filePath) { success ->
                    if (success) {
                        val currentPads = _uiState.value.pads
                        val updatedPads = currentPads.map { pad ->
                            if (pad.id == padId) {
                                val cleanName = fileName.substringBeforeLast(".")
                                    .take(16)
                                pad.copy(
                                    samplePath = filePath,
                                    sampleFileName = fileName,
                                    name = if (pad.name.startsWith("Slot ")) cleanName else pad.name
                                )
                            } else pad
                        }
                        kitManager.saveKitConfig(updatedPads)
                        _uiState.update {
                            it.copy(
                                pads = updatedPads,
                                editingPad = updatedPads.find { p -> p.id == padId },
                                statusMessage = "Loaded sample for Pad ${padId + 1}"
                            )
                        }
                    }
                }
            }
        }
    }

    fun clearPadSlot(padId: Int) {
        audioEngine.unloadSample(padId)
        val updatedList = _uiState.value.pads.map { pad ->
            if (pad.id == padId) {
                pad.copy(
                    samplePath = null,
                    sampleFileName = null,
                    name = "Slot ${padId + 1}"
                )
            } else pad
        }
        kitManager.saveKitConfig(updatedList)
        _uiState.update {
            it.copy(
                pads = updatedList,
                editingPad = null,
                statusMessage = "Cleared Pad ${padId + 1}"
            )
        }
    }

    fun clearAllPads() {
        audioEngine.stopAll()
        for (pad in _uiState.value.pads) {
            audioEngine.unloadSample(pad.id)
        }
        val emptyPads = kitManager.createEmptyPads()
        kitManager.saveKitConfig(emptyPads)
        _uiState.update {
            it.copy(
                pads = emptyPads,
                statusMessage = "All pads cleared."
            )
        }
    }

    fun loadDemoPreset() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Generating demo kit…") }
            val demoPads = kitManager.createDemoPreset()
            for (pad in demoPads) {
                pad.samplePath?.let { path ->
                    audioEngine.loadSample(pad.id, path)
                }
            }
            _uiState.update {
                it.copy(
                    pads = demoPads,
                    isLoading = false,
                    statusMessage = "Loaded demo kit with choke groups!"
                )
            }
        }
    }

    fun exportCurrentKit() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            val uri = kitManager.exportKitToZip(_uiState.value.pads, "MTS_Kit")
            _uiState.update {
                it.copy(
                    isLoading = false,
                    exportShareUri = uri,
                    statusMessage = if (uri != null) "Kit exported successfully!" else "Failed to export kit."
                )
            }
        }
    }

    fun clearExportUri() {
        _uiState.update { it.copy(exportShareUri = null) }
    }

    fun importKitFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Importing kit…") }
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val restored = kitManager.importKitFromZip(stream)
                    if (restored != null) {
                        audioEngine.stopAll()
                        for (pad in _uiState.value.pads) {
                            audioEngine.unloadSample(pad.id)
                        }
                        for (pad in restored) {
                            pad.samplePath?.let { path ->
                                audioEngine.loadSample(pad.id, path)
                            }
                        }
                        _uiState.update {
                            it.copy(
                                pads = restored,
                                isLoading = false,
                                statusMessage = "Kit imported successfully!"
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(isLoading = false, statusMessage = "Could not import kit.")
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, statusMessage = "Error importing kit: ${e.message}")
                }
            }
        }
    }

    fun openDevicePicker(open: Boolean) {
        if (open) midiService.refreshDeviceList()
        _uiState.update { it.copy(isDevicePickerOpen = open) }
    }

    fun selectMidiDevice(deviceId: String) {
        midiService.openDeviceById(deviceId)
        _uiState.update { it.copy(isDevicePickerOpen = false) }
    }

    fun refreshMidiDevices() {
        midiService.refreshDeviceList()
    }

    fun simulateMidiNote(note: Int, velocity: Int = 110) {
        midiService.simulateMidiNote(note, velocity)
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun flashPadHit(padId: Int) {
        val now = System.currentTimeMillis()
        _uiState.update { state ->
            val updated = state.activePadHits.toMutableMap()
            updated[padId] = now
            state.copy(activePadHits = updated)
        }
        viewModelScope.launch {
            delay(150)
            _uiState.update { state ->
                val updated = state.activePadHits.toMutableMap()
                if (updated[padId] == now) {
                    updated.remove(padId)
                }
                state.copy(activePadHits = updated)
            }
        }
    }

    private fun performHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(20)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onCleared() {
        super.onCleared()
        midiService.release()
        audioEngine.release()
    }
}
