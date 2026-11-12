package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.DiagnosticStats
import com.example.service.StreamingStatus
import com.example.ui.theme.*

@Composable
fun StudioTopBar(
    status: StreamingStatus,
    stats: DiagnosticStats,
    isPortraitCanvas: Boolean,
    onToggleRatio: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CharcoalSecondary,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("studio_top_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Logo and Live Status Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(ChampagneGold),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(CharcoalMain)
                        )
                    }
                    Text(
                        text = "VILICHT",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "STUDIO",
                        color = ChampagneGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    )
                }

                // Live status badge
                val (badgeBg, badgeText, badgeColor) = when (status) {
                    StreamingStatus.LIVE -> Triple(StatusGreen.copy(alpha = 0.2f), "LIVE", StatusGreen)
                    StreamingStatus.CONNECTING -> Triple(StatusAmber.copy(alpha = 0.2f), "CONNECTING", StatusAmber)
                    StreamingStatus.PAUSED -> Triple(StatusAmber.copy(alpha = 0.2f), "PAUSED", StatusAmber)
                    StreamingStatus.RECONNECTING -> Triple(StatusAmber.copy(alpha = 0.2f), "RECONNECTING", StatusAmber)
                    StreamingStatus.ERROR -> Triple(StatusRed.copy(alpha = 0.2f), "ERROR", StatusRed)
                    StreamingStatus.PREVIEW -> Triple(StatusAmber.copy(alpha = 0.2f), "SIAP", StatusAmber)
                    StreamingStatus.OFFLINE -> Triple(CharcoalPanel, "OFFLINE", TextSecondary)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(badgeBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .testTag("status_badge"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(badgeColor)
                        )
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Center: Telemetry (FPS, Bitrate, Duration)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (status == StreamingStatus.LIVE || status == StreamingStatus.PAUSED) {
                    val minutes = stats.durationSeconds / 60
                    val seconds = stats.durationSeconds % 60
                    Text(
                        text = String.format("%02d:%02d", minutes, seconds),
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "${stats.captureFps} FPS",
                    color = if (stats.captureFps > 20) TextPrimary else TextSecondary,
                    fontSize = 11.sp
                )

                Text(
                    text = "${stats.bitrateKbps} kbps",
                    color = TextPrimary,
                    fontSize = 11.sp
                )

                Text(
                    text = stats.resolution,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            // Right: Canvas aspect toggle, Diagnostics, Settings
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Canvas ratio toggle
                AssistChip(
                    onClick = onToggleRatio,
                    label = {
                        Text(
                            text = if (isPortraitCanvas) "9:16 Portrait" else "16:9 Landscape",
                            fontSize = 11.sp,
                            color = TextPrimary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isPortraitCanvas) Icons.Default.StayCurrentPortrait else Icons.Default.StayCurrentLandscape,
                            contentDescription = "Canvas Ratio",
                            tint = ChampagneGold,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = CharcoalPanel
                    ),
                    border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = CharcoalBorder),
                    modifier = Modifier.testTag("ratio_toggle_chip")
                )

                // Diagnostics shortcut
                IconButton(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.size(36.dp).testTag("diagnostics_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Diagnostic Stats",
                        tint = ChampagneGold,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Settings shortcut
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(36.dp).testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Studio Settings",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
