package com.example.audio

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates clean, punchy synthetic drum one-shots as standard WAV files.
 * Useful for demo presets and quick testing.
 */
object ProceduralDrumGenerator {

    private const val SAMPLE_RATE = 44100

    enum class DrumType {
        KICK_808,
        KICK_PUNCH,
        SNARE_CRISP,
        SNARE_FAT,
        HIHAT_CLOSED,
        HIHAT_OPEN,
        CLAP,
        RIMSHOT,
        TOM_LOW,
        TOM_MID,
        PERC_WOOD,
        CYMBAL_CRASH
    }

    fun generateSample(outputFile: File, type: DrumType) {
        val samples = when (type) {
            DrumType.KICK_808 -> generateKick(decay = 0.5f, startFreq = 160f, endFreq = 42f)
            DrumType.KICK_PUNCH -> generateKick(decay = 0.25f, startFreq = 220f, endFreq = 55f)
            DrumType.SNARE_CRISP -> generateSnare(toneFreq = 185f, noiseDecay = 0.18f)
            DrumType.SNARE_FAT -> generateSnare(toneFreq = 150f, noiseDecay = 0.25f)
            DrumType.HIHAT_CLOSED -> generateHiHat(decay = 0.05f)
            DrumType.HIHAT_OPEN -> generateHiHat(decay = 0.35f)
            DrumType.CLAP -> generateClap()
            DrumType.RIMSHOT -> generateRimshot()
            DrumType.TOM_LOW -> generateTom(freq = 90f, decay = 0.35f)
            DrumType.TOM_MID -> generateTom(freq = 135f, decay = 0.3f)
            DrumType.PERC_WOOD -> generateWoodblock()
            DrumType.CYMBAL_CRASH -> generateCrash(decay = 0.7f)
        }

        writeWavFile(outputFile, samples, SAMPLE_RATE)
    }

    private fun generateKick(decay: Float, startFreq: Float, endFreq: Float): ShortArray {
        val numSamples = (SAMPLE_RATE * decay).toInt()
        val buffer = ShortArray(numSamples)
        var phase = 0.0
        val random = Random(42)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = (t / decay).coerceIn(0f, 1f)
            // Exponential pitch drop
            val freq = endFreq + (startFreq - endFreq) * exp(-18f * progress)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val amp = exp(-6f * progress)
            // Add slight click at start
            val click = if (i < 80) (random.nextFloat() * 2f - 1f) * (1f - i / 80f) * 0.4f else 0f
            val sampleVal = (sin(phase).toFloat() + click) * amp
            buffer[i] = (sampleVal.coerceIn(-1f, 1f) * 32000).toInt().toShort()
        }
        return buffer
    }

    private fun generateSnare(toneFreq: Float, noiseDecay: Float): ShortArray {
        val numSamples = (SAMPLE_RATE * noiseDecay).toInt()
        val buffer = ShortArray(numSamples)
        var phase = 0.0
        val random = Random(123)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = t / noiseDecay
            val toneAmp = exp(-22f * progress) * 0.6f
            phase += 2.0 * PI * toneFreq / SAMPLE_RATE
            val tone = sin(phase).toFloat() * toneAmp

            val noiseAmp = exp(-8f * progress) * 0.8f
            val noise = (random.nextFloat() * 2f - 1f) * noiseAmp

            val sampleVal = tone + noise
            buffer[i] = (sampleVal.coerceIn(-1f, 1f) * 30000).toInt().toShort()
        }
        return buffer
    }

    private fun generateHiHat(decay: Float): ShortArray {
        val numSamples = (SAMPLE_RATE * decay).toInt()
        val buffer = ShortArray(numSamples)
        val random = Random(999)
        var lastNoise = 0f

        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = t / decay
            val amp = exp(-14f * progress)
            // High-pass filter noise
            val raw = random.nextFloat() * 2f - 1f
            val hp = raw - 0.85f * lastNoise
            lastNoise = raw
            val sampleVal = hp * amp * 0.7f
            buffer[i] = (sampleVal.coerceIn(-1f, 1f) * 28000).toInt().toShort()
        }
        return buffer
    }

    private fun generateClap(): ShortArray {
        val duration = 0.28f
        val numSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(numSamples)
        val random = Random(555)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            // 3 micro-bursts followed by sustained tail
            val burst = when {
                t < 0.012f -> exp(-200f * t)
                t < 0.024f -> exp(-200f * (t - 0.012f))
                t < 0.038f -> exp(-200f * (t - 0.024f))
                else -> exp(-14f * (t - 0.038f))
            }
            val noise = random.nextFloat() * 2f - 1f
            val sampleVal = noise * burst * 0.8f
            buffer[i] = (sampleVal.coerceIn(-1f, 1f) * 31000).toInt().toShort()
        }
        return buffer
    }

    private fun generateRimshot(): ShortArray {
        val duration = 0.08f
        val numSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(numSamples)
        var phase = 0.0
        val random = Random(777)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val amp = exp(-45f * t)
            phase += 2.0 * PI * 850.0 / SAMPLE_RATE
            val tone = sin(phase).toFloat() * 0.7f
            val noise = (random.nextFloat() * 2f - 1f) * 0.4f
            val sampleVal = (tone + noise) * amp
            buffer[i] = (sampleVal.coerceIn(-1f, 1f) * 32000).toInt().toShort()
        }
        return buffer
    }

    private fun generateTom(freq: Float, decay: Float): ShortArray {
        val numSamples = (SAMPLE_RATE * decay).toInt()
        val buffer = ShortArray(numSamples)
        var phase = 0.0

        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = t / decay
            val pitch = freq * (1f + 0.5f * exp(-20f * progress))
            phase += 2.0 * PI * pitch / SAMPLE_RATE
            val amp = exp(-6f * progress)
            val sampleVal = sin(phase).toFloat() * amp * 0.9f
            buffer[i] = (sampleVal.coerceIn(-1f, 1f) * 31000).toInt().toShort()
        }
        return buffer
    }

    private fun generateWoodblock(): ShortArray {
        val duration = 0.09f
        val numSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(numSamples)
        var phase = 0.0

        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val amp = exp(-35f * t)
            phase += 2.0 * PI * 1150.0 / SAMPLE_RATE
            val sampleVal = sin(phase).toFloat() * amp * 0.85f
            buffer[i] = (sampleVal.coerceIn(-1f, 1f) * 30000).toInt().toShort()
        }
        return buffer
    }

    private fun generateCrash(decay: Float): ShortArray {
        val numSamples = (SAMPLE_RATE * decay).toInt()
        val buffer = ShortArray(numSamples)
        val random = Random(888)
        var lp = 0f

        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = t / decay
            val amp = exp(-4f * progress)
            val raw = random.nextFloat() * 2f - 1f
            lp += 0.4f * (raw - lp) // Mild filtering
            val sampleVal = (raw - lp * 0.6f) * amp * 0.65f
            buffer[i] = (sampleVal.coerceIn(-1f, 1f) * 29000).toInt().toShort()
        }
        return buffer
    }

    private fun writeWavFile(file: File, samples: ShortArray, sampleRate: Int) {
        val totalAudioLen = (samples.size * 2).toLong()
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = (sampleRate * channels * 2).toLong()

        val header = ByteArray(44)
        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        bb.put("RIFF".toByteArray())
        bb.putInt(totalDataLen.toInt())
        bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray())
        bb.putInt(16) // Subchunk1Size for PCM
        bb.putShort(1.toShort()) // AudioFormat 1 = PCM
        bb.putShort(channels.toShort())
        bb.putInt(sampleRate)
        bb.putInt(byteRate.toInt())
        bb.putShort((channels * 2).toShort()) // BlockAlign
        bb.putShort(16.toShort()) // BitsPerSample
        bb.put("data".toByteArray())
        bb.putInt(totalAudioLen.toInt())

        FileOutputStream(file).use { fos ->
            fos.write(header)
            val audioBuffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in samples) {
                audioBuffer.putShort(sample)
            }
            fos.write(audioBuffer.array())
        }
    }
}
