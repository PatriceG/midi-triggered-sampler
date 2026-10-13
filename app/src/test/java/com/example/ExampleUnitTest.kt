package com.example

import com.example.model.MidiUtils
import com.example.model.PadSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testMidiNoteToNameConversion() {
        // Standard General MIDI drum notes
        assertEquals("C1", MidiUtils.noteNumberToName(36))
        assertEquals("C#1", MidiUtils.noteNumberToName(37))
        assertEquals("D1", MidiUtils.noteNumberToName(38))
        assertEquals("F#1", MidiUtils.noteNumberToName(42))
        assertEquals("A#1", MidiUtils.noteNumberToName(46))

        // Middle C
        assertEquals("C3", MidiUtils.noteNumberToName(60))
        assertEquals("A3", MidiUtils.noteNumberToName(69))
    }

    @Test
    fun testPadSlotInitialization() {
        val pads = List(12) { id ->
            PadSlot(
                id = id,
                name = "Slot ${id + 1}",
                midiNote = 36 + id
            )
        }

        assertEquals(12, pads.size)
        assertEquals(36, pads[0].midiNote)
        assertEquals(47, pads[11].midiNote)
        assertFalse(pads[0].isLoaded)
        assertTrue(pads[0].varispeed)
    }

    @Test
    fun testChokeGroupConfiguration() {
        val openHat = PadSlot(id = 2, name = "Open Hat", chokeGroup = 1)
        val closedHat = PadSlot(id = 6, name = "Closed Hat", chokeGroup = 1)
        val kick = PadSlot(id = 0, name = "Kick", chokeGroup = 0)

        assertEquals(1, openHat.chokeGroup)
        assertEquals(1, closedHat.chokeGroup)
        assertEquals(0, kick.chokeGroup)
        assertEquals(openHat.chokeGroup, closedHat.chokeGroup)
    }

    @Test
    fun testSemitonePitchConversion() {
        // Center: 0 semitones -> 1.0x rate
        assertEquals(1.0f, PadSlot.semitonesToRate(0), 0.001f)
        assertEquals(0, PadSlot(id = 0, playbackRate = 1.0f).pitchSemitones)

        // -12 semitones -> 0.5x rate (one octave down)
        assertEquals(0.5f, PadSlot.semitonesToRate(-12), 0.001f)
        assertEquals(-12, PadSlot(id = 0, playbackRate = 0.5f).pitchSemitones)

        // +12 semitones -> 2.0x rate (one octave up)
        assertEquals(2.0f, PadSlot.semitonesToRate(12), 0.001f)
        assertEquals(12, PadSlot(id = 0, playbackRate = 2.0f).pitchSemitones)
    }
}
