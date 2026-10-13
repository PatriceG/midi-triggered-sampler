package com.example.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.MidiDeviceItem
import com.example.ui.theme.ChassisBorder
import com.example.ui.theme.ChassisBorderBright
import com.example.ui.theme.ChassisSurface
import com.example.ui.theme.ChassisSurfaceHigh
import com.example.ui.theme.CyanLed
import com.example.ui.theme.MpcAmber
import com.example.ui.theme.PanicRed

@Composable
fun MidiDeviceSelectionDialog(
    devices: List<MidiDeviceItem>,
    connectedDevice: MidiDeviceItem?,
    onSelectDevice: (String) -> Unit,
    onRefresh: () -> Unit,
    onSimulateNote: (note: Int) -> Unit,
    onSimulateAllNotesOff: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ChassisSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, ChassisBorderBright),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("midi_device_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = null,
                            tint = CyanLed,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "MIDI DEVICES",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Text(
                    text = "Select a connected USB or Bluetooth MIDI controller to receive Note-On events.",
                    fontSize = 12.sp,
                    color = Color(0xFFBAC0CC)
                )

                // Device List
                if (devices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ChassisSurfaceHigh)
                            .border(1.dp, ChassisBorder, RoundedCornerShape(10.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "No MIDI Devices Detected",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MpcAmber
                            )
                            Text(
                                text = "Connect a USB MIDI keyboard/pad controller via USB-OTG or pair a Bluetooth MIDI controller. Tap Refresh once connected.",
                                fontSize = 11.5.sp,
                                color = Color.Gray,
                                lineHeight = 15.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(devices) { device ->
                            val isSelected = connectedDevice?.id == device.id || device.isConnected
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) CyanLed.copy(alpha = 0.15f) else ChassisSurfaceHigh)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) CyanLed else ChassisBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelectDevice(device.id) }
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = device.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "${device.manufacturer} • Ports: Out ${device.outputPortCount}, In ${device.inputPortCount}",
                                            fontSize = 10.5.sp,
                                            color = Color.Gray,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    if (isSelected) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(CyanLed)
                                            )
                                            Text(
                                                text = "ACTIVE",
                                                color = CyanLed,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Virtual MIDI Test Bench
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ChassisSurfaceHigh)
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(imageVector = Icons.Default.Piano, contentDescription = null, tint = MpcAmber, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Test Bench (Simulate MIDI)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onSimulateNote(36) }, // C1
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("C1 (36)", fontSize = 10.sp, color = MpcAmber)
                            }
                            OutlinedButton(
                                onClick = { onSimulateNote(38) }, // D1
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("D1 (38)", fontSize = 10.sp, color = MpcAmber)
                            }
                            OutlinedButton(
                                onClick = { onSimulateNote(42) }, // F#1
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("F#1 (42)", fontSize = 10.sp, color = MpcAmber)
                            }
                        }
                        OutlinedButton(
                            onClick = onSimulateAllNotesOff,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PanicRed)
                        ) {
                            Text("Simulate CC123 (All Notes Off)", fontSize = 11.sp)
                        }
                    }
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_refresh_midi"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Refresh", fontSize = 12.sp)
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanLed, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
