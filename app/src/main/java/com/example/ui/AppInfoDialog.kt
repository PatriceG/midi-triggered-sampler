package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.ui.theme.ChassisBorder
import com.example.ui.theme.ChassisBorderBright
import com.example.ui.theme.ChassisSurface
import com.example.ui.theme.ChassisSurfaceHigh
import com.example.ui.theme.CyanLed
import com.example.ui.theme.MpcAmber

@Composable
fun AppInfoDialog(
    appName: String = "Midi-Triggered Sampler",
    versionName: String = "1.0",
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ChassisSurface,
            border = androidx.compose.foundation.BorderStroke(1.2.dp, ChassisBorderBright),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("app_info_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with MTS badge & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MpcAmber)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "MTS",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Black
                            )
                        }

                        Text(
                            text = "APP INFO",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                // Application Full Title & Version
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ChassisSurfaceHigh)
                        .border(1.dp, ChassisBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = appName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Version: $versionName",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CyanLed,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "•",
                                color = Color.Gray
                            )
                            Text(
                                text = "Build 1.0",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Features Overview
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "System Overview",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFBAC0CC),
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "• 3×4 (12 pads) ultra-low-latency performance sampler matrix\n" +
                               "• Real-time MIDI Learn & MIDI controller input\n" +
                               "• Automatic speaker routing protection for USB MIDI controllers\n" +
                               "• 1-semitone notched pitch tuning (-12 to +12 st) with Classic Varispeed\n" +
                               "• Polyphonic choke groups & All-Notes-Off Panic stop\n" +
                               "• Full Kit Export & Import (.zip archive format)",
                        fontSize = 11.5.sp,
                        color = Color(0xFF9EA3B0),
                        lineHeight = 16.sp
                    )
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MpcAmber, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("OK", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
