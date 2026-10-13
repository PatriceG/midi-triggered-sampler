package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MidiUtils
import com.example.model.PadSlot
import com.example.ui.theme.ChassisSurface
import com.example.ui.theme.ChassisSurfaceHigh
import com.example.ui.theme.CyanLed
import com.example.ui.theme.MpcAmber
import com.example.ui.theme.PadInactiveBg
import com.example.ui.theme.PadInactiveBorder

@Composable
fun PadItemView(
    pad: PadSlot,
    isHit: Boolean,
    isLearnModeActive: Boolean,
    isLearningThisPad: Boolean,
    onTap: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val padAccentColor = Color(pad.colorHex)

    // Hit animation scale: reduced shrink by 50% (0.97f instead of 0.94f) for subtle, high-precision tactile feedback
    val scale by animateFloatAsState(
        targetValue = if (isHit) 0.97f else 1.0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 900f),
        label = "pad_scale"
    )

    // Pulsing animation for MIDI Learn
    val infiniteTransition = rememberInfiniteTransition(label = "learn_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // Dynamic background color: bright flash on hit, subtle accent tint when loaded, dark when empty
    val animatedBgColor by animateColorAsState(
        targetValue = when {
            isHit -> padAccentColor.copy(alpha = 0.88f)
            isLearningThisPad -> MpcAmber.copy(alpha = 0.35f)
            pad.isLoaded -> ChassisSurfaceHigh
            else -> PadInactiveBg
        },
        animationSpec = tween(durationMillis = if (isHit) 30 else 180),
        label = "pad_bg_color"
    )

    val borderColor = when {
        isLearningThisPad -> MpcAmber.copy(alpha = pulseAlpha)
        isLearnModeActive -> MpcAmber.copy(alpha = 0.5f)
        isHit -> Color.White
        pad.isLoaded -> padAccentColor.copy(alpha = 0.7f)
        else -> PadInactiveBorder
    }

    val borderWidth = if (isHit || isLearningThisPad) 2.5.dp else 1.5.dp

    Box(
        modifier = modifier
            .testTag("pad_${pad.id}")
            .scale(scale)
            .shadow(
                elevation = if (isHit) 6.dp else 3.dp,
                shape = RoundedCornerShape(14.dp),
                spotColor = if (isHit) padAccentColor else Color.Black
            )
            .clip(RoundedCornerShape(14.dp))
            .background(ChassisSurface)
            .border(borderWidth, borderColor, RoundedCornerShape(14.dp))
            // ZERO-LATENCY TOUCH DOWN: Immediately triggers on pointer down, NOT on touch-up!
            .pointerInput(pad.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onTap() // Trigger sound immediately on finger down
                    waitForUpOrCancellation()
                }
            }
            .padding(1.5.dp)
    ) {
        // Inner pad surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            animatedBgColor,
                            animatedBgColor.copy(alpha = 0.75f)
                        )
                    )
                )
                .padding(horizontal = 7.dp, vertical = 6.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // --- TOP ROW: Pad Index & Badges ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pad Index Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (pad.isLoaded) padAccentColor else Color.Gray.copy(alpha = 0.4f))
                        )
                        Text(
                            text = "${pad.id + 1}",
                            color = if (isHit) Color.Black else Color(0xFFD0D4DC),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Choke Badge or Edit Action
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (pad.chokeGroup > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CyanLed.copy(alpha = 0.25f))
                                    .border(0.5.dp, CyanLed.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "CH${pad.chokeGroup}",
                                    color = CyanLed,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Dedicated Edit Button
                        IconButton(
                            onClick = onEditClick,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Edit Pad ${pad.id + 1}",
                                tint = if (isHit) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // --- CENTER: Pad Label or Empty Prompt ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLearningThisPad -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "LEARN…",
                                    color = MpcAmber,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Press Key",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        pad.isLoaded -> {
                            Text(
                                text = pad.name,
                                color = if (isHit) Color.Black else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                        else -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Empty Slot",
                                    tint = Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Empty",
                                    color = Color.White.copy(alpha = 0.35f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // --- BOTTOM ROW: MIDI Note & Status ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // MIDI Note display
                    val noteName = MidiUtils.noteNumberToName(pad.midiNote)
                    Text(
                        text = "$noteName (#${pad.midiNote})",
                        color = if (isHit) Color.Black.copy(alpha = 0.8f) else Color(0xFF9AA0AC),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )

                    // Wave indicator if sound loaded
                    if (pad.isLoaded) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Sample Loaded",
                            tint = if (isHit) Color.Black else padAccentColor.copy(alpha = 0.85f),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
        }
    }
}
