package com.example.midi

import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.model.MidiDeviceItem
import com.example.model.MidiEventTelemetry
import com.example.model.MidiUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

/**
 * Service that interfaces with Android MidiManager to enumerate USB/BLE MIDI controllers,
 * opens all output ports on the device, decodes Note-On, Note-Off, Running Status,
 * and All-Notes-Off events with zero latency.
 */
class MidiService(
    private val context: Context,
    private val onDeviceConnected: (deviceName: String) -> Unit = {},
    private val onNoteOn: (channel: Int, note: Int, velocity: Int) -> Unit,
    private val onNoteOff: (channel: Int, note: Int, velocity: Int) -> Unit,
    private val onAllNotesOff: () -> Unit
) {
    private val tag = "MidiService"
    private val mainHandler = Handler(Looper.getMainLooper())

    private val midiManager: MidiManager? = if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
        context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    } else {
        null
    }

    private val _availableDevices = MutableStateFlow<List<MidiDeviceItem>>(emptyList())
    val availableDevices: StateFlow<List<MidiDeviceItem>> = _availableDevices.asStateFlow()

    private val _activeDevice = MutableStateFlow<MidiDeviceItem?>(null)
    val activeDevice: StateFlow<MidiDeviceItem?> = _activeDevice.asStateFlow()

    private val _lastTelemetry = MutableStateFlow<MidiEventTelemetry?>(null)
    val lastTelemetry: StateFlow<MidiEventTelemetry?> = _lastTelemetry.asStateFlow()

    private var openMidiDevice: MidiDevice? = null
    private val openOutputPorts = mutableListOf<MidiOutputPort>()
    private var currentDeviceInfo: MidiDeviceInfo? = null

    // MIDI Parser state machine for running status and message framing
    private var runningStatusByte: Int = 0
    private var pendingStatus: Int = 0
    private var pendingData1: Int = -1

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            val name = getDeviceName(device)
            Log.d(tag, "MIDI Device Added: $name")
            refreshDeviceList()
            // Auto connect if none connected yet
            if (openMidiDevice == null) {
                openDevice(device)
            }
        }

        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            val name = getDeviceName(device)
            Log.d(tag, "MIDI Device Removed: $name")
            if (currentDeviceInfo?.id == device.id) {
                closeCurrentDevice()
            }
            refreshDeviceList()
        }

        override fun onDeviceStatusChanged(status: android.media.midi.MidiDeviceStatus) {
            refreshDeviceList()
        }
    }

    init {
        registerCallbacks()
        refreshDeviceList()
        val initialDevices = midiManager?.devices ?: emptyArray()
        if (initialDevices.isNotEmpty()) {
            openDevice(initialDevices.first())
        }
    }

    private fun registerCallbacks() {
        try {
            midiManager?.registerDeviceCallback(deviceCallback, mainHandler)
        } catch (e: Exception) {
            Log.w(tag, "Could not register MIDI device callback", e)
        }
    }

    fun refreshDeviceList() {
        val manager = midiManager
        if (manager == null) {
            _availableDevices.value = emptyList()
            return
        }

        val devices = manager.devices ?: emptyArray()
        val items = devices.map { info ->
            val name = getDeviceName(info)
            val properties = info.properties
            val manufacturer = properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER) ?: "Generic"
            val isCurrent = currentDeviceInfo?.id == info.id
            MidiDeviceItem(
                id = info.id.toString(),
                name = name,
                manufacturer = manufacturer,
                isConnected = isCurrent,
                inputPortCount = info.inputPortCount,
                outputPortCount = info.outputPortCount
            )
        }
        _availableDevices.value = items
    }

    private fun getDeviceName(info: MidiDeviceInfo): String {
        val properties = info.properties
        return properties.getString(MidiDeviceInfo.PROPERTY_NAME)
            ?: properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
            ?: "MIDI Device #${info.id}"
    }

    fun openDeviceById(deviceIdStr: String) {
        val manager = midiManager ?: return
        val device = manager.devices.find { it.id.toString() == deviceIdStr }
        if (device != null) {
            openDevice(device)
        }
    }

    fun openDevice(deviceInfo: MidiDeviceInfo) {
        val manager = midiManager ?: return
        closeCurrentDevice()

        val devName = getDeviceName(deviceInfo)
        Log.d(tag, "Opening MIDI Device: $devName (output ports: ${deviceInfo.outputPortCount})")

        manager.openDevice(deviceInfo, { device ->
            if (device == null) {
                Log.e(tag, "Could not open MIDI device: ${deviceInfo.id}")
                return@openDevice
            }

            openMidiDevice = device
            currentDeviceInfo = deviceInfo
            openOutputPorts.clear()

            // Open ALL available output ports on this device to ensure we capture pad/key events
            val portCount = deviceInfo.outputPortCount
            var portsOpened = 0

            for (portIndex in 0 until portCount) {
                try {
                    val outputPort = device.openOutputPort(portIndex)
                    if (outputPort != null) {
                        openOutputPorts.add(outputPort)
                        outputPort.connect(object : MidiReceiver() {
                            override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                                parseMidiStream(msg, offset, count)
                            }
                        })
                        portsOpened++
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Could not open output port $portIndex", e)
                }
            }

            val manufacturer = deviceInfo.properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER) ?: "Generic"
            _activeDevice.value = MidiDeviceItem(
                id = deviceInfo.id.toString(),
                name = devName,
                manufacturer = manufacturer,
                isConnected = true,
                inputPortCount = deviceInfo.inputPortCount,
                outputPortCount = deviceInfo.outputPortCount
            )
            refreshDeviceList()
            onDeviceConnected(devName)
            Log.d(tag, "Successfully connected to MIDI Device: $devName ($portsOpened ports active)")
        }, mainHandler)
    }

    /**
     * Complete streaming MIDI parser supporting:
     * - Standard 3-byte Note-On and Note-Off
     * - MIDI Running Status (where successive data bytes omit the status byte)
     * - Control Change 120 and 123 (All Notes Off)
     * - Multi-packet byte streaming
     */
    @Synchronized
    private fun parseMidiStream(msg: ByteArray, offset: Int, count: Int) {
        var idx = offset
        val end = offset + count

        while (idx < end) {
            val byte = msg[idx].toInt() and 0xFF
            idx++

            // System real-time messages (0xF8 - 0xFF) can arrive at any point and do not reset running status
            if (byte >= 0xF8) {
                continue
            }

            // System Common / SysEx (0xF0 - 0xF7)
            if (byte in 0xF0..0xF7) {
                runningStatusByte = 0
                pendingStatus = 0
                pendingData1 = -1
                continue
            }

            // Status Byte (0x80 - 0xEF)
            if (byte >= 0x80) {
                runningStatusByte = byte
                pendingStatus = byte
                pendingData1 = -1
                continue
            }

            // Data Byte (< 0x80)
            val currentStatus = if (pendingStatus >= 0x80) pendingStatus else runningStatusByte
            if (currentStatus < 0x80) {
                // Orphaned data byte without status
                continue
            }

            val command = currentStatus and 0xF0
            val channel = (currentStatus and 0x0F) + 1

            if (pendingData1 == -1) {
                // First data byte
                when (command) {
                    0xC0, 0xD0 -> {
                        // 1-data-byte commands (Program Change, Channel Pressure)
                        pendingStatus = runningStatusByte // Ready for next running status
                        pendingData1 = -1
                    }
                    else -> {
                        // 2-data-byte commands (Note On, Note Off, Control Change, Pitch Bend)
                        pendingData1 = byte
                    }
                }
            } else {
                // Second data byte received! We have a complete 3-byte message
                val data1 = pendingData1
                val data2 = byte
                pendingData1 = -1
                pendingStatus = runningStatusByte // Prepare for potential next running status

                when (command) {
                    0x90 -> { // Note On
                        mainHandler.post {
                            if (data2 > 0) {
                                val telemetry = MidiEventTelemetry(
                                    noteNumber = data1,
                                    noteName = MidiUtils.noteNumberToName(data1),
                                    velocity = data2,
                                    channel = channel
                                )
                                _lastTelemetry.value = telemetry
                                onNoteOn(channel, data1, data2)
                            } else {
                                // Note On with velocity 0 is Note Off per standard MIDI spec
                                onNoteOff(channel, data1, 0)
                            }
                        }
                    }
                    0x80 -> { // Note Off
                        mainHandler.post {
                            onNoteOff(channel, data1, data2)
                        }
                    }
                    0xB0 -> { // Control Change
                        // CC 120 = All Sound Off, CC 123 = All Notes Off
                        if (data1 == 120 || data1 == 123) {
                            mainHandler.post {
                                onAllNotesOff()
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Virtual trigger helper: allows simulating an incoming Note-On event
     * for testing MIDI-Learn and pad triggering without a physical cable.
     */
    fun simulateMidiNote(note: Int, velocity: Int = 100, channel: Int = 1) {
        val telemetry = MidiEventTelemetry(
            noteNumber = note,
            noteName = MidiUtils.noteNumberToName(note),
            velocity = velocity,
            channel = channel
        )
        _lastTelemetry.value = telemetry
        onNoteOn(channel, note, velocity)
    }

    fun simulateAllNotesOff() {
        onAllNotesOff()
    }

    fun closeCurrentDevice() {
        for (port in openOutputPorts) {
            try {
                port.close()
            } catch (e: IOException) {
                // Ignore
            }
        }
        openOutputPorts.clear()

        try {
            openMidiDevice?.close()
        } catch (e: IOException) {
            // Ignore
        }
        openMidiDevice = null
        currentDeviceInfo = null
        _activeDevice.value = null
        refreshDeviceList()
    }

    fun release() {
        try {
            midiManager?.unregisterDeviceCallback(deviceCallback)
        } catch (e: Exception) {
            // Ignore
        }
        closeCurrentDevice()
    }
}
