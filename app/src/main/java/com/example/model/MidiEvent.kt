package com.example.model

/**
 * Real-time MIDI event telemetry displayed on the performance status line.
 */
data class MidiEventTelemetry(
    val noteNumber: Int,
    val noteName: String,
    val velocity: Int,
    val channel: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Representation of a detected MIDI hardware or virtual device.
 */
data class MidiDeviceItem(
    val id: String,
    val name: String,
    val manufacturer: String = "Generic",
    val isConnected: Boolean = false,
    val inputPortCount: Int = 0,
    val outputPortCount: Int = 0
)

object MidiUtils {
    private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /**
     * Converts a MIDI note number (0..127) into standard musical pitch notation (e.g., 36 -> "C1", 60 -> "C3").
     */
    fun noteNumberToName(noteNumber: Int): String {
        if (noteNumber !in 0..127) return "Invalid ($noteNumber)"
        val octave = (noteNumber / 12) - 2 // Standard MIDI: 36 is C1, 60 is C3
        val noteName = NOTE_NAMES[noteNumber % 12]
        return "$noteName$octave"
    }
}
