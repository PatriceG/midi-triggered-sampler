# 3x4 MIDI Sample Player ("MPC-12")

A high-performance, low-latency 12-pad (3 rows × 4 columns) audio sample player designed for live finger drumming and external MIDI hardware controllers. Built with a dark MPC hardware aesthetic, per-pad sample configuration, instant MIDI-Learn, choke groups, real-time MIDI status monitoring, and full Kit Export/Import (zip bundle containing samples, pad settings, and MIDI mappings).

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following product decisions have been established and updated based on your feedback:

- **Initial Kit State**: Empty slots on first launch. Users upload their own audio files (`.wav`, `.mp3`, `.ogg`, `.flac`, `.m4a`) into slots, with custom pad titles, colors, choke groups, volume, and pitch controls.
- **Trigger Mode & Choking**: One-shot retrigger behavior with configurable choke groups (Group 1, Group 2, or Off) so overlapping sounds (such as open vs. closed hi-hats) choke each other cleanly.
- **Kit Export & Import**: Complete kit backup and sharing capability. Exports all 12 pad configs, MIDI note mappings, and sample audio files into a portable `.zip` / `.mpckit` archive via Android Share Sheet/File Save. Imports any kit archive, unpacks samples to app storage, and restores all mappings.
- **Aesthetic Direction**: Dark MPC hardware sampler theme with matte dark chassis, brushed aluminum accents, tactile backlit rubber pads with hit animations, and a retro amber/cyan LED performance status bar.

---

## 1. Overview & Core Concept

- **What It Does**: Provides a responsive 3x4 pad matrix on mobile/tablet screens that can be tapped directly or triggered via any USB or Bluetooth MIDI controller. Users assign custom audio samples to any of the 12 pad slots, configure MIDI notes using tap-and-learn, set choke groups, adjust volume/pitch, monitor real-time MIDI telemetry, and export/import complete kits.
- **Target Audience**: Music producers, live beatmakers, finger drummers, electronic musicians, and live performers needing an ultra-responsive, zero-latency sample trigger station.
- **Key Value**: Low-latency execution via optimized Android `SoundPool`, hardware-grade MIDI connectivity (`android.media.midi`), intuitive MIDI-Learn with live status feedback, full kit portability, and zero dead-ends.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Performance Flow (Play Mode)**:
   - User opens the app into a sleek dark sampler console with 12 pads arranged in a 3×4 grid.
   - Tapping an empty pad prompts to load an audio file or configure the slot.
   - Tapping a loaded pad triggers instant audio playback with bright pad glow, haptic feedback, and retriggering.
   - Bottom status line indicates active MIDI device name, connection status, and last received note name with velocity.
2. **Sample Customization Flow**:
   - Long-pressing a pad (or tapping the slot edit button) opens the **Slot Config Sheet**.
   - User picks an audio file via the Android file picker, gives it a custom title, sets pad accent color, assigns choke group (Off, 1, or 2), and tunes volume and playback rate.
   - Selected audio is copied to internal app storage so samples load instantly across app sessions.
3. **MIDI-Learn Flow**:
   - Tapping the "MIDI Learn" toggle in the top bar highlights the matrix in amber "LEARN" mode.
   - Tapping any pad activates "Listening for MIDI..." on that pad.
   - Pressing any key, drum pad, or controller button on the connected MIDI device immediately maps that Note-On number to the pad, flashes green, and exits learn mode for that slot.
4. **Kit Export / Import Flow**:
   - Tapping the Kit menu provides "Export Current Kit" and "Import Kit".
   - **Export**: Compresses all 12 pad audio samples and a `kit_config.json` file into a zip package, sharing it via system Share sheet or saving to device storage.
   - **Import**: Extracts the zip bundle, registers audio files in the low-latency engine, and re-maps all pads, colors, choke groups, and MIDI assignments.
5. **Device Selection & Panic Flow**:
   - Tapping the device selector in the status bar opens a dialog listing all connected MIDI devices (USB, Bluetooth, virtual).
   - Tapping "Panic / All Notes Off" immediately stops all active sample streams and resets choke groups.

### Visual Identity & Theme
- **Chassis**: Deep gunmetal background (`#121316`) with subtle metallic border bevels and recessed pad bays.
- **Pads**: Thick rounded rectangular pads (`#1E2024` inactive, glowing backlit color when loaded, bright neon pulse on strike).
- **Status Screen**: Retro industrial LCD/OLED panel with high-contrast monospace text (cyan/amber `#38BDF8` & `#F59E0B`) showing:
  `[DEVICE: MPK mini III] • [LAST NOTE: C1 (36) | VEL: 118] • [CHOKE: OK]`
- **Responsive Layout**: Fluidly scales pad heights and spacing to maintain a perfect 3x4 layout on both phone portrait and tablet landscape orientations.

---

## 3. Key Product Decisions & Trade-Offs

- **Audio Engine**: Android `SoundPool` configured with `AudioAttributes.USAGE_MEDIA` / `CONTENT_TYPE_SONIFICATION` and low-latency stream limits.
  - *Why*: `SoundPool` decodes samples directly into raw PCM memory, delivering sub-15ms trigger latency with polyphonic retriggering and immediate stream stopping for choke groups and All-Notes-Off.
- **Storage Strategy**: Local file copying to app's `filesDir/samples/` + JSON metadata persistence.
  - *Why*: Direct URI access from external storage can be revoked or delayed across reboots; copying into internal app storage ensures immediate zero-permission startup loading and reliability.
- **Kit Portability**: Zip-based archive containing `kit_config.json` + audio files.
  - *Why*: Self-contained, standardized, cross-device portable format without reliance on external cloud services or account logins.
- **MIDI Integration**: Direct Android `MidiManager` API (`android.media.midi.MidiManager`).
  - *Why*: Native USB and BLE MIDI driver support without external libraries, lowest event dispatch latency, and standard event decoding (Note On, Note Off, CC 120 / CC 123 All Notes Off).

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                               UI LAYER                                 │
│  ┌───────────────────────┐  ┌──────────────────┐  ┌──────────────────┐ │
│  │ Top Bar (Kit, Learn)  │  │ 3x4 Pad Matrix   │  │ Slot Config      │ │
│  │ Panic Button          │  │ (12 M3 Pads)     │  │ BottomSheet      │ │
│  └───────────┬───────────┘  └────────┬─────────┘  └────────┬─────────┘ │
│              │                       │                     │           │
│              └───────────────────────┼─────────────────────┘           │
│                                      ▼                                 │
│                           MidiSamplerViewModel                         │
│                     - StateFlow<SamplerUiState>                        │
│                     - Trigger / Choke logic                            │
│                     - MIDI-Learn state machine                         │
│                     - Kit Export / Import Coordinator                  │
└──────────────────────────────────────┬─────────────────────────────────┘
                                       │
        ┌──────────────────────────────┼──────────────────────────────┐
        ▼                              ▼                              ▼
┌──────────────────┐       ┌──────────────────────┐       ┌──────────────────────┐
│   AudioEngine    │       │     MidiService      │       │      KitManager      │
│ - SoundPool      │       │ - MidiManager Detect │       │ - Zip Export / Share │
│ - Low Latency    │       │ - Note-On / Note-Off │       │ - Zip Unpack / Load  │
│ - Choke Groups   │       │ - CC120/123 Panic    │       │ - JSON serialization │
│ - stopAll()      │       │ - Real-time Status   │       │ - Local File Cache   │
└──────────────────┘       └──────────────────────┘       └──────────────────────┘
```

### Data Model & State
- **PadSlot**:
  - `id: Int` (0..11)
  - `name: String` (e.g. "Kick", "Snare", "Empty")
  - `samplePath: String?` (internal storage file path)
  - `sampleFileName: String?` (original file name)
  - `soundPoolId: Int?` (cached sound ID in memory)
  - `midiNote: Int` (default 36 + id, e.g. GM drum standard C1..B1)
  - `chokeGroup: Int` (0 = None, 1 = Choke 1, 2 = Choke 2)
  - `volume: Float` (0.0f..1.0f)
  - `playbackRate: Float` (0.5f..2.0f, pitch adjustment)
  - `colorHex: Long` (accent color for pad illumination)
- **MidiDeviceItem**:
  - `id: String`, `name: String`, `manufacturer: String`, `isOpened: Boolean`
- **MidiEventTelemetry**:
  - `noteNumber: Int`, `noteName: String` (e.g., "C1", "F#2"), `velocity: Int`, `channel: Int`, `timestamp: Long`
- **SamplerUiState**:
  - `pads: List<PadSlot>`
  - `activePadHits: Map<Int, Long>` (triggers Compose pulse animation)
  - `isLearnModeActive: Boolean`
  - `learningPadId: Int?`
  - `connectedDeviceName: String`
  - `availableDevices: List<MidiDeviceItem>`
  - `selectedDevice: MidiDeviceItem?`
  - `lastNoteEvent: MidiEventTelemetry?`
  - `isExportingOrImporting: Boolean`
  - `statusMessage: String?`
