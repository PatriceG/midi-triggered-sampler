package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MidiUtils
import com.example.model.PadSlot
import com.example.ui.theme.ChassisBorder
import com.example.ui.theme.ChassisBorderBright
import com.example.ui.theme.ChassisSurface
import com.example.ui.theme.ChassisSurfaceHigh
import com.example.ui.theme.CyanLed
import com.example.ui.theme.MpcAmber
import com.example.ui.theme.PanicRed
import kotlin.math.roundToInt

private val COLOR_PALETTE = listOf(
    0xFFFF8A00, // Amber
    0xFF00E5FF, // Cyan
    0xFFFF2E93, // Magenta
    0xFF00E676, // Mint
    0xFFFFD600, // Yellow
    0xFF7C4DFF, // Purple
    0xFF2979FF, // Blue
    0xFFFF5252  // Coral
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PadConfigBottomSheet(
    pad: PadSlot,
    onDismiss: () -> Unit,
    onSave: (PadSlot) -> Unit,
    onAudioFilePicked: (Uri) -> Unit,
    onClearSlot: () -> Unit,
    onAudition: (PadSlot) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember(pad.id) { mutableStateOf(pad.name) }
    var selectedColor by remember(pad.id) { mutableLongStateOf(pad.colorHex) }
    var chokeGroup by remember(pad.id) { mutableIntStateOf(pad.chokeGroup) }
    var midiNote by remember(pad.id) { mutableIntStateOf(pad.midiNote) }
    var volume by remember(pad.id) { mutableFloatStateOf(pad.volume) }
    // Pitch semitones from -12 to +12, centered at 0
    var pitchSemitones by remember(pad.id) { mutableIntStateOf(pad.pitchSemitones) }
    // Varispeed vs Time-stretching
    var varispeed by remember(pad.id) { mutableStateOf(pad.varispeed) }

    // Launcher for picking an audio file
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onAudioFilePicked(uri)
        }
    }

    val currentConfiguredPad = pad.copy(
        name = name.ifBlank { "Pad ${pad.id + 1}" },
        colorHex = selectedColor,
        chokeGroup = chokeGroup,
        midiNote = midiNote,
        volume = volume,
        playbackRate = PadSlot.semitonesToRate(pitchSemitones),
        varispeed = varispeed
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ChassisSurface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(selectedColor))
                    )
                    Text(
                        text = "CONFIGURE PAD ${pad.id + 1}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                }
            }

            // --- AUDIO FILE SECTION ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ChassisSurfaceHigh)
                    .border(1.dp, ChassisBorderBright, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = CyanLed,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Sample Audio",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        if (pad.isLoaded) {
                            OutlinedButton(
                                onClick = { onAudition(currentConfiguredPad) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanLed),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Audition", fontSize = 12.sp)
                            }
                        }
                    }

                    Text(
                        text = pad.sampleFileName ?: if (pad.isLoaded) "Sample loaded" else "No audio file loaded (Tap below to upload)",
                        fontSize = 12.5.sp,
                        color = if (pad.isLoaded) Color(0xFFE0E4EC) else Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )

                    Button(
                        onClick = { audioPickerLauncher.launch("audio/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_choose_audio"),
                        colors = ButtonDefaults.buttonColors(containerColor = MpcAmber, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (pad.isLoaded) "Replace Audio Sample" else "Choose Audio File (WAV, MP3, OGG)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // --- PAD LABEL ---
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Pad Label") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_pad_label"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MpcAmber,
                    unfocusedBorderColor = ChassisBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = MpcAmber,
                    unfocusedLabelColor = Color.Gray
                ),
                shape = RoundedCornerShape(8.dp)
            )

            // --- PAD COLOR PICKER ---
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Pad LED Color",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (colorHex in COLOR_PALETTE) {
                        val isSelected = selectedColor == colorHex
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(colorHex))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorHex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // --- CHOKE GROUP (HI-HAT CHOKE) ---
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Choke Group",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "Pads in the same choke group cut each other off (e.g. Open & Closed Hats)",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(0 to "Off", 1 to "Group 1", 2 to "Group 2").forEach { (group, label) ->
                        val isSelected = chokeGroup == group
                        FilterChip(
                            selected = isSelected,
                            onClick = { chokeGroup = group },
                            label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanLed.copy(alpha = 0.2f),
                                selectedLabelColor = CyanLed,
                                containerColor = ChassisSurfaceHigh,
                                labelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = ChassisBorder,
                                selectedBorderColor = CyanLed
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // --- ASSIGNED MIDI NOTE ---
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Assigned MIDI Note",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "${MidiUtils.noteNumberToName(midiNote)} (#$midiNote)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MpcAmber,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Slider(
                    value = midiNote.toFloat(),
                    onValueChange = { midiNote = it.toInt() },
                    valueRange = 0f..127f,
                    steps = 127,
                    colors = SliderDefaults.colors(
                        thumbColor = MpcAmber,
                        activeTrackColor = MpcAmber,
                        inactiveTrackColor = ChassisBorder
                    )
                )
            }

            // --- VOLUME SLIDER ---
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Volume", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("${(volume * 100).toInt()}%", fontSize = 12.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = CyanLed,
                        activeTrackColor = CyanLed,
                        inactiveTrackColor = ChassisBorder
                    )
                )
            }

            // --- PITCH / SEMITONE NOTCHES: -12 TO +12, CENTER 0 ---
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pitch Tuning", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val sign = when {
                            pitchSemitones > 0 -> "+$pitchSemitones"
                            else -> "$pitchSemitones"
                        }
                        val rate = PadSlot.semitonesToRate(pitchSemitones)
                        Text(
                            text = "$sign semitones (${String.format("%.2fx", rate)})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (pitchSemitones == 0) CyanLed else MpcAmber,
                            fontFamily = FontFamily.Monospace
                        )
                        if (pitchSemitones != 0) {
                            Text(
                                text = "Reset (0)",
                                fontSize = 11.sp,
                                color = CyanLed,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { pitchSemitones = 0 }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Notched slider: 23 steps between -12 and +12 creates 24 notches, 1 per semitone!
                Slider(
                    value = pitchSemitones.toFloat(),
                    onValueChange = { pitchSemitones = it.roundToInt() },
                    valueRange = -12f..12f,
                    steps = 23,
                    colors = SliderDefaults.colors(
                        thumbColor = if (pitchSemitones == 0) CyanLed else MpcAmber,
                        activeTrackColor = if (pitchSemitones == 0) CyanLed else MpcAmber,
                        inactiveTrackColor = ChassisBorder
                    )
                )

                // Scale notches indicator: -12 st, 0 st, +12 st
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("-12 st (-1 Oct)", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Text(
                        text = "0 st (Center)",
                        fontSize = 10.sp,
                        color = if (pitchSemitones == 0) CyanLed else Color.Gray,
                        fontWeight = if (pitchSemitones == 0) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                    Text("+12 st (+1 Oct)", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Pitch Mode: Keep Speed vs Classic Varispeed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = !varispeed,
                        onClick = { varispeed = false },
                        label = { Text("Keep Speed", fontSize = 11.5.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanLed.copy(alpha = 0.2f),
                            selectedLabelColor = CyanLed,
                            containerColor = ChassisSurfaceHigh,
                            labelColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = varispeed,
                        onClick = { varispeed = true },
                        label = { Text("Classic Varispeed", fontSize = 11.5.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MpcAmber.copy(alpha = 0.2f),
                            selectedLabelColor = MpcAmber,
                            containerColor = ChassisSurfaceHigh,
                            labelColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // --- ACTION BUTTONS: SAVE & CLEAR ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (pad.isLoaded) {
                    OutlinedButton(
                        onClick = onClearSlot,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_clear_slot"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PanicRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Slot", fontSize = 12.5.sp)
                    }
                }

                Button(
                    onClick = {
                        onSave(currentConfiguredPad)
                    },
                    modifier = Modifier
                        .weight(if (pad.isLoaded) 1.2f else 1f)
                        .testTag("btn_save_pad"),
                    colors = ButtonDefaults.buttonColors(containerColor = MpcAmber, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Pad", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
