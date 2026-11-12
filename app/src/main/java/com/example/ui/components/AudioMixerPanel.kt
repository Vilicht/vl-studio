package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.encoder.StreamPreset
import com.example.service.StreamingStatus
import com.example.ui.theme.*

@Composable
fun AudioMixerPanel(
    status: StreamingStatus,
    micLevel: Float,
    systemLevel: Float,
    micVolume: Float,
    isMicMuted: Boolean,
    systemVolume: Float,
    isSystemMuted: Boolean,
    currentPreset: StreamPreset,
    isAutoMode: Boolean,
    onToggleMicMute: () -> Unit,
    onUpdateMicVolume: (Float) -> Unit,
    onToggleSystemMute: () -> Unit,
    onUpdateSystemVolume: (Float) -> Unit,
    onOpenPresetSelector: () -> Unit,
    onGoLive: () -> Unit,
    onStopStream: () -> Unit,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CharcoalSecondary,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .border(1.dp, CharcoalBorder)
            .testTag("studio_bottom_panel")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // LEFT: AUDIO CHANNELS (MIC + GAME AUDIO)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.weight(1f)
            ) {
                // CHANNEL 1: MICROPHONE
                AudioChannelStrip(
                    title = "MIC",
                    icon = Icons.Default.Mic,
                    isMuted = isMicMuted,
                    level = micLevel,
                    volume = micVolume,
                    onToggleMute = onToggleMicMute,
                    onVolumeChange = onUpdateMicVolume,
                    modifier = Modifier.width(220.dp)
                )

                // CHANNEL 2: GAME / SYSTEM AUDIO
                AudioChannelStrip(
                    title = "GAME",
                    icon = Icons.Default.VolumeUp,
                    isMuted = isSystemMuted,
                    level = systemLevel,
                    volume = systemVolume,
                    onToggleMute = onToggleSystemMute,
                    onVolumeChange = onUpdateSystemVolume,
                    modifier = Modifier.width(220.dp)
                )
            }

            // RIGHT: PRESET CHIP & GO LIVE BUTTON
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Selected preset chip
                Surface(
                    color = CharcoalPanel,
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CharcoalBorder)),
                    modifier = Modifier
                        .clickable { onOpenPresetSelector() }
                        .testTag("preset_selector_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isAutoMode) ChampagneGold else TextSecondary)
                        )
                        Text(
                            text = if (isAutoMode) "AUTO: ${currentPreset.label}" else currentPreset.label,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Select Preset",
                            tint = ChampagneGold,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Pause button (only while streaming)
                if (status == StreamingStatus.LIVE || status == StreamingStatus.PAUSED) {
                    OutlinedButton(
                        onClick = onTogglePause,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CharcoalPanel,
                            contentColor = ChampagneGold
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(ChampagneGold)),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(40.dp).testTag("pause_stream_button")
                    ) {
                        Icon(
                            imageVector = if (status == StreamingStatus.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = "Pause",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (status == StreamingStatus.PAUSED) "Resume" else "Pause", fontSize = 12.sp)
                    }
                }

                // MAIN ACTION BUTTON: GO LIVE / END STREAM
                if (status == StreamingStatus.OFFLINE || status == StreamingStatus.ERROR || status == StreamingStatus.PREVIEW) {
                    Button(
                        onClick = onGoLive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ChampagneGold,
                            contentColor = CharcoalMain
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(42.dp)
                            .testTag("go_live_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "Go Live",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GO LIVE",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    Button(
                        onClick = onStopStream,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(42.dp)
                            .testTag("end_stream_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "End Stream",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "END STREAM",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioChannelStrip(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isMuted: Boolean,
    level: Float,
    volume: Float,
    onToggleMute: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedLevel by animateFloatAsState(targetValue = if (isMuted) 0f else level, label = "vu_meter")

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Mute toggle icon
        IconButton(
            onClick = onToggleMute,
            modifier = Modifier.size(28.dp).testTag("mute_${title.lowercase()}")
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Default.MicOff else icon,
                contentDescription = "$title Mute",
                tint = if (isMuted) StatusRed else ChampagneGold,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${(volume * 100).toInt()}%",
                    color = TextMuted,
                    fontSize = 9.sp
                )
            }

            // Real-time animated VU Meter bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(CharcoalPanel)
            ) {
                val meterFraction = (animatedLevel / 100f).coerceIn(0f, 1f)
                val meterColor = when {
                    animatedLevel > 80f -> StatusRed
                    animatedLevel > 50f -> StatusAmber
                    else -> StatusGreen
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(meterFraction)
                        .clip(RoundedCornerShape(3.dp))
                        .background(meterColor)
                )
            }

            // Mini volume slider
            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                valueRange = 0f..1.5f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                colors = SliderDefaults.colors(
                    thumbColor = ChampagneGold,
                    activeTrackColor = ChampagneGold,
                    inactiveTrackColor = CharcoalBorder
                )
            )
        }
    }
}
