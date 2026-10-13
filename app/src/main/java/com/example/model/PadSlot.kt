package com.example.model

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Represents one of the 12 pad slots in the 3x4 sampler.
 */
data class PadSlot(
    val id: Int, // 0 to 11
    val name: String = "Slot ${id + 1}",
    val samplePath: String? = null,
    val sampleFileName: String? = null,
    val midiNote: Int = 36 + id, // Standard General MIDI drum notes: 36 (C1) to 47 (B1)
    val midiChannel: Int = 0, // 0 = omni (any channel), 1-16 = specific channel
    val chokeGroup: Int = 0, // 0 = off, 1 = group 1, 2 = group 2
    val volume: Float = 1.0f, // 0.0 to 1.0
    val playbackRate: Float = 1.0f, // 0.5 to 2.0 (pitch factor)
    val varispeed: Boolean = true, // true = classic sampler keyboard varispeed (default), false = time-stretched pitch
    val colorHex: Long = defaultColorForPad(id)
) {
    val isLoaded: Boolean
        get() = !samplePath.isNullOrEmpty()

    val pitchSemitones: Int
        get() {
            if (playbackRate <= 0.05f) return 0
            return (12.0 * log2(playbackRate.toDouble())).roundToInt().coerceIn(-12, 12)
        }

    companion object {
        fun semitonesToRate(semitones: Int): Float {
            return 2.0.pow(semitones.toDouble() / 12.0).toFloat()
        }

        fun defaultColorForPad(id: Int): Long {
            // Palette of rich hardware sampler LED colors
            return when (id % 6) {
                0 -> 0xFFFF8A00 // MPC Amber
                1 -> 0xFF00E5FF // Cyan LED
                2 -> 0xFFFF2E93 // Neon Magenta
                3 -> 0xFF00E676 // Bright Mint
                4 -> 0xFFFFD600 // Electric Yellow
                else -> 0xFF7C4DFF // Deep Violet
            }
        }
    }
}
