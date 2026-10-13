package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.model.PadSlot
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance audio engine with:
 * 1. UNLIMITED sample duration (no SoundPool 1MB/5-second cap).
 * 2. Guaranteed speaker audio routing using setPreferredDevice to prevent USB MIDI audio hijacking.
 * 3. Low-latency instant retriggering and choke group management.
 */
class LowLatencyAudioEngine(private val context: Context) {

    private val tag = "LowLatencyAudioEngine"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
        .build()

    // Each pad maintains prepared MediaPlayer instances for instant touch-down playback
    private val padPlayers = ConcurrentHashMap<Int, MutableList<MediaPlayer>>()

    // Active currently-playing players per pad
    private val activePlayers = ConcurrentHashMap<Int, MutableList<MediaPlayer>>()

    // Cache of file paths per pad
    private val padFilePaths = ConcurrentHashMap<Int, String>()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            Log.d(tag, "Audio devices added - reapplying speaker routing")
            ensureSpeakerRouting()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            Log.d(tag, "Audio devices removed - reapplying speaker routing")
            ensureSpeakerRouting()
        }
    }

    init {
        try {
            audioManager?.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
        } catch (e: Exception) {
            Log.w(tag, "Could not register audio device callback", e)
        }
        ensureSpeakerRouting()
    }

    /**
     * Determines the best non-USB output device (built-in speaker or headphones)
     * to prevent USB MIDI controllers from silencing the device.
     */
    private fun getPreferredOutputDevice(): AudioDeviceInfo? {
        val manager = audioManager ?: return null
        val devices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        // 1. If wired or bluetooth headphones are plugged in, prefer them
        val headphones = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }
        if (headphones != null) return headphones

        // 2. Otherwise explicitly route to built-in speaker, ignoring USB devices
        return devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        }
    }

    /**
     * Applies preferred audio device routing to all active and prepared players.
     */
    fun ensureSpeakerRouting() {
        val preferred = getPreferredOutputDevice()
        Log.d(tag, "Applying preferred audio device: ${preferred?.productName ?: "Default"} (type: ${preferred?.type})")

        // Apply to all players in pool
        for ((_, players) in padPlayers) {
            for (player in players) {
                try {
                    player.setPreferredDevice(preferred)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

        // Apply to all currently playing
        for ((_, players) in activePlayers) {
            for (player in players) {
                try {
                    player.setPreferredDevice(preferred)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Loads a sample of ANY length into the engine.
     */
    fun loadSample(padId: Int, filePath: String, onLoaded: ((Boolean) -> Unit)? = null) {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            onLoaded?.invoke(false)
            return
        }

        // Unload old players for this pad
        unloadSample(padId)
        padFilePaths[padId] = filePath

        try {
            val list = mutableListOf<MediaPlayer>()
            // Pre-create 2 prepared player instances for polyphonic retriggering
            for (i in 0..1) {
                val player = createPreparedPlayer(filePath)
                if (player != null) {
                    list.add(player)
                }
            }
            padPlayers[padId] = list
            val success = list.isNotEmpty()
            mainHandler.post { onLoaded?.invoke(success) }
        } catch (e: Exception) {
            Log.e(tag, "Failed to load sample for pad $padId", e)
            mainHandler.post { onLoaded?.invoke(false) }
        }
    }

    private fun createPreparedPlayer(filePath: String): MediaPlayer? {
        return try {
            val player = MediaPlayer()
            player.setAudioAttributes(audioAttributes)
            player.setDataSource(filePath)

            // Force routing to speaker/headphones (prevents USB audio hijack)
            val preferred = getPreferredOutputDevice()
            if (preferred != null) {
                player.setPreferredDevice(preferred)
            }

            player.prepare()
            player
        } catch (e: Exception) {
            Log.e(tag, "Error creating prepared MediaPlayer for $filePath", e)
            null
        }
    }

    /**
     * Plays a pad slot with instantaneous touch-down response, choke groups,
     * volume, pitch, and NO duration limitation.
     */
    fun playPad(pad: PadSlot, velocity: Float = 1.0f, allPads: List<PadSlot>) {
        val players = padPlayers[pad.id] ?: return
        if (players.isEmpty()) return

        // 1. Choke Group: Stop all other pads in the same choke group
        if (pad.chokeGroup > 0) {
            for (other in allPads) {
                if (other.id != pad.id && other.chokeGroup == pad.chokeGroup) {
                    stopPad(other.id)
                }
            }
        }

        // 2. Retrigger: Stop previous instance of this pad
        stopPad(pad.id)

        // 3. Find an idle player or cycle
        var player = players.firstOrNull { !it.isPlaying }
        if (player == null) {
            // Take the oldest player, stop and seek to 0
            player = players.first()
            try {
                if (player.isPlaying) player.stop()
                player.prepare()
            } catch (e: Exception) {
                // Recreate if needed
                val path = padFilePaths[pad.id]
                if (path != null) {
                    player = createPreparedPlayer(path)
                    if (player != null) {
                        players[0] = player
                    }
                }
            }
        }

        if (player == null) return

        try {
            // Ensure routing to speaker/headphones
            val preferred = getPreferredOutputDevice()
            if (preferred != null) {
                player.setPreferredDevice(preferred)
            }

            // Set volume
            val effectiveVol = (pad.volume * velocity.coerceIn(0.1f, 1.0f)).coerceIn(0.0f, 1.0f)
            player.setVolume(effectiveVol, effectiveVol)

            // Apply pitch shift (modifies sample pitch, with optional varispeed)
            val pitchFactor = pad.playbackRate.coerceIn(0.5f, 2.0f)
            try {
                if (pad.playbackRate != 1.0f) {
                    player.playbackParams = PlaybackParams().apply {
                        pitch = pitchFactor
                        speed = if (pad.varispeed) pitchFactor else 1.0f
                    }
                } else {
                    player.playbackParams = PlaybackParams().apply {
                        pitch = 1.0f
                        speed = 1.0f
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Error setting PlaybackParams pitch", e)
            }

            // Seek to beginning and start!
            player.seekTo(0)
            player.start()

            // Record as active
            val activeList = activePlayers.getOrPut(pad.id) { mutableListOf() }
            activeList.add(player)

            player.setOnCompletionListener {
                activeList.remove(player)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error playing pad ${pad.id}", e)
        }
    }

    /**
     * Stops any currently playing audio on this pad.
     */
    fun stopPad(padId: Int) {
        val list = activePlayers.remove(padId) ?: return
        for (player in list) {
            try {
                if (player.isPlaying) {
                    player.pause()
                    player.seekTo(0)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    /**
     * Panic / All-Notes-Off: stops all playing audio immediately.
     */
    fun stopAll() {
        for ((_, list) in activePlayers) {
            for (player in list) {
                try {
                    if (player.isPlaying) {
                        player.pause()
                        player.seekTo(0)
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        activePlayers.clear()
    }

    /**
     * Unloads audio for a given pad.
     */
    fun unloadSample(padId: Int) {
        stopPad(padId)
        padFilePaths.remove(padId)
        val players = padPlayers.remove(padId) ?: return
        for (player in players) {
            try {
                player.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    /**
     * Clean release of all players and listeners.
     */
    fun release() {
        try {
            audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (e: Exception) {
            // Ignore
        }
        stopAll()
        for ((_, players) in padPlayers) {
            for (player in players) {
                try {
                    player.release()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        padPlayers.clear()
        activePlayers.clear()
        padFilePaths.clear()
    }
}
