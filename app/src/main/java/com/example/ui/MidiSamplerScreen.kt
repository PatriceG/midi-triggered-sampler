package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ChassisBackground
import com.example.ui.theme.ChassisBorder
import com.example.ui.theme.ChassisSurface
import com.example.ui.theme.ChassisSurfaceHigh
import com.example.ui.theme.CyanLed
import com.example.ui.theme.LcdBackground
import com.example.ui.theme.LcdBorder
import com.example.ui.theme.LcdTextAmber
import com.example.ui.theme.LcdTextCyan
import com.example.ui.theme.MpcAmber
import com.example.ui.theme.PanicRed
import com.example.ui.theme.PanicRedDark
import com.example.viewmodel.MidiSamplerViewModel

@Composable
fun MidiSamplerScreen(
    viewModel: MidiSamplerViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showKitMenu by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    // Launcher for importing kit zip archive
    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importKitFromUri(uri)
        }
    }

    // Launch share sheet when kit export completes
    LaunchedEffect(uiState.exportShareUri) {
        val shareUri = uiState.exportShareUri
        if (shareUri != null) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(Intent.EXTRA_SUBJECT, "MTS Sample Kit")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Export MTS Kit"))
            viewModel.clearExportUri()
        }
    }

    // Show temporary status messages
    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearStatusMessage()
        }
    }

    // Pulsing animation for MIDI Learn Top Bar button
    val infiniteTransition = rememberInfiniteTransition(label = "learn_bar_pulse")
    val learnAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "learn_btn_alpha"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ChassisBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ==========================================
            // TOP CONSOLE BAR: MTS BADGE, KIT, OCTAVE, LEARN, PANIC
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ChassisSurface)
                    .border(1.dp, ChassisBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "MTS" Hardware Badge (replaced "MPC 12")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MpcAmber)
                            .clickable { showInfoDialog = true }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                            .testTag("badge_mts")
                    ) {
                        Text(
                            text = "MTS",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = Color.Black
                        )
                    }
                }

                // Middle Controls: Octave Transpose (- / +)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ChassisSurfaceHigh)
                            .border(0.5.dp, ChassisBorder, RoundedCornerShape(6.dp))
                            .clickable { viewModel.shiftOctave(-1) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text("OCT -", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC0C4D0), fontFamily = FontFamily.Monospace)
                    }

                    val shiftText = when {
                        uiState.currentOctaveShift > 0 -> "+${uiState.currentOctaveShift}"
                        else -> "${uiState.currentOctaveShift}"
                    }
                    Text(
                        text = shiftText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanLed,
                        fontFamily = FontFamily.Monospace
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ChassisSurfaceHigh)
                            .border(0.5.dp, ChassisBorder, RoundedCornerShape(6.dp))
                            .clickable { viewModel.shiftOctave(1) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text("OCT +", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC0C4D0), fontFamily = FontFamily.Monospace)
                    }
                }

                // Right Controls: Kit Menu, MIDI Learn, Panic
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // KIT DROPDOWN BUTTON
                    Box {
                        OutlinedButton(
                            onClick = { showKitMenu = true },
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("btn_kit_menu"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD4D8E2))
                        ) {
                            Icon(imageVector = Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Kit", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }

                        DropdownMenu(
                            expanded = showKitMenu,
                            onDismissRequest = { showKitMenu = false },
                            modifier = Modifier.background(ChassisSurfaceHigh)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export Kit (.zip)", color = Color.White, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null, tint = CyanLed) },
                                onClick = {
                                    showKitMenu = false
                                    viewModel.exportCurrentKit()
                                },
                                modifier = Modifier.testTag("menu_export_kit")
                            )
                            DropdownMenuItem(
                                text = { Text("Import Kit (.zip)", color = Color.White, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = CyanLed) },
                                onClick = {
                                    showKitMenu = false
                                    zipPickerLauncher.launch("*/*")
                                },
                                modifier = Modifier.testTag("menu_import_kit")
                            )
                            DropdownMenuItem(
                                text = { Text("Load Demo Preset", color = Color.White, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MpcAmber) },
                                onClick = {
                                    showKitMenu = false
                                    viewModel.loadDemoPreset()
                                },
                                modifier = Modifier.testTag("menu_demo_kit")
                            )
                            DropdownMenuItem(
                                text = { Text("Force Speaker Audio", color = CyanLed, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Speaker, contentDescription = null, tint = CyanLed) },
                                onClick = {
                                    showKitMenu = false
                                    viewModel.forceSpeakerRouting()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear All Pads", color = PanicRed, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = PanicRed) },
                                onClick = {
                                    showKitMenu = false
                                    viewModel.clearAllPads()
                                },
                                modifier = Modifier.testTag("menu_clear_all")
                            )
                        }
                    }

                    // MIDI LEARN TOGGLE BUTTON
                    val learnBg = if (uiState.isLearnModeActive) MpcAmber.copy(alpha = learnAlpha) else ChassisSurfaceHigh
                    val learnTextColor = if (uiState.isLearnModeActive) Color.Black else MpcAmber
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(learnBg)
                            .border(
                                width = 1.dp,
                                color = if (uiState.isLearnModeActive) Color.White else MpcAmber.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.toggleLearnMode() }
                            .padding(horizontal = 8.dp)
                            .testTag("btn_midi_learn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (uiState.isLearnModeActive) Color.Black else MpcAmber)
                            )
                            Text(
                                text = if (uiState.isLearnModeActive) "LEARN ON" else "LEARN",
                                color = learnTextColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // PANIC (ALL NOTES OFF) BUTTON
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PanicRedDark)
                            .border(1.dp, PanicRed, RoundedCornerShape(8.dp))
                            .clickable { viewModel.panic() }
                            .padding(horizontal = 8.dp)
                            .testTag("btn_panic"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(
                                imageVector = Icons.Default.VolumeOff,
                                contentDescription = "Panic Stop",
                                tint = PanicRed,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "PANIC",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ==========================================
            // 3 x 4 PAD MATRIX (12 PADS IN 3 ROWS)
            // TOUCH-DOWN ZERO LATENCY TRIGGERING
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("pad_matrix_container"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val pads = uiState.pads.ifEmpty { List(12) { com.example.model.PadSlot(id = it) } }

                for (row in 0..2) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (col in 0..3) {
                            val padIndex = row * 4 + col
                            val pad = pads.getOrNull(padIndex) ?: com.example.model.PadSlot(id = padIndex)
                            val isHit = uiState.activePadHits.containsKey(pad.id)
                            val isLearningThis = uiState.learningPadId == pad.id

                            PadItemView(
                                pad = pad,
                                isHit = isHit,
                                isLearnModeActive = uiState.isLearnModeActive,
                                isLearningThisPad = isLearningThis,
                                onTap = {
                                    if (uiState.isLearnModeActive) {
                                        viewModel.setLearningPad(pad.id)
                                    } else {
                                        viewModel.triggerPad(pad.id)
                                    }
                                },
                                onEditClick = { viewModel.openSlotConfig(pad.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ==========================================
            // PERFORMANCE STATUS LINE (LCD DISPLAY)
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(LcdBackground)
                    .border(1.2.dp, LcdBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("performance_status_bar")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Line 1: Connected Device Selector & Signal Indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val devName = uiState.connectedDevice?.name ?: "No MIDI Device (Tap to Select)"
                        val hasDevice = uiState.connectedDevice != null
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (hasDevice) CyanLed.copy(alpha = 0.15f) else Color(0xFF131F28))
                                .border(
                                    width = 0.5.dp,
                                    color = if (hasDevice) CyanLed.copy(alpha = 0.7f) else LcdBorder,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { viewModel.openDevicePicker(true) }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Usb,
                                contentDescription = "MIDI Connection",
                                tint = if (hasDevice) CyanLed else Color.Gray,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "DEV: $devName",
                                color = if (hasDevice) LcdTextCyan else Color(0xFF8899A6),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // MIDI Activity Blinker & Speaker Safeguard Icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.forceSpeakerRouting() },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speaker,
                                    contentDescription = "Ensure Speaker Audio",
                                    tint = CyanLed,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            val isReceiving = uiState.lastNoteEvent != null &&
                                (System.currentTimeMillis() - (uiState.lastNoteEvent?.timestamp ?: 0)) < 350
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isReceiving) CyanLed else Color(0xFF243B48))
                            )
                            Text(
                                text = "MIDI IN",
                                color = if (isReceiving) CyanLed else Color(0xFF557082),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Line 2: Last Received Note-On Event (Note name, velocity, channel)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val lastNote = uiState.lastNoteEvent
                        val telemetryText = if (lastNote != null) {
                            "LAST: ${lastNote.noteName} (#${lastNote.noteNumber})  VEL:${lastNote.velocity}  CH:${lastNote.channel}"
                        } else {
                            "LAST: WAITING FOR NOTE-ON…"
                        }

                        Text(
                            text = telemetryText,
                            color = LcdTextAmber,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "CHOKE: OK",
                            color = Color(0xFF6B8A9E),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }

    // Modal Sheet for Slot Configuration
    uiState.editingPad?.let { pad ->
        PadConfigBottomSheet(
            pad = pad,
            onDismiss = { viewModel.closeSlotConfig() },
            onSave = { updated -> viewModel.saveEditingPad(updated) },
            onAudioFilePicked = { uri -> viewModel.assignAudioFileToPad(pad.id, uri) },
            onClearSlot = { viewModel.clearPadSlot(pad.id) },
            onAudition = { auditionPad -> viewModel.audioEngine.playPad(auditionPad, 1.0f, uiState.pads) }
        )
    }

    // Modal Dialog for MIDI Device Selection
    if (uiState.isDevicePickerOpen) {
        MidiDeviceSelectionDialog(
            devices = uiState.availableDevices,
            connectedDevice = uiState.connectedDevice,
            onSelectDevice = { id -> viewModel.selectMidiDevice(id) },
            onRefresh = { viewModel.refreshMidiDevices() },
            onSimulateNote = { note -> viewModel.simulateMidiNote(note) },
            onSimulateAllNotesOff = { viewModel.panic() },
            onDismiss = { viewModel.openDevicePicker(false) }
        )
    }

    // Modal Dialog for App Info
    if (showInfoDialog) {
        AppInfoDialog(
            appName = "Midi-Triggered Sampler",
            versionName = "1.0",
            onDismiss = { showInfoDialog = false }
        )
    }
}
