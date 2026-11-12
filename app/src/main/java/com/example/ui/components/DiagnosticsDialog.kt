package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
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
import com.example.service.DiagnosticStats
import com.example.service.StreamingStatus
import com.example.ui.theme.*

@Composable
fun DiagnosticsDialog(
    isOpen: Boolean,
    stats: DiagnosticStats,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
                .testTag("diagnostics_dialog"),
            color = CharcoalPanel
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Diagnostics",
                            tint = ChampagneGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "ENGINE & PIPELINE DIAGNOSTICS",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Divider(color = CharcoalBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Telemetry Grid
                    DiagnosticRow("Capture Session Status", stats.status.name, if (stats.status == StreamingStatus.LIVE) StatusGreen else StatusAmber)
                    DiagnosticRow("VirtualDisplay Active", if (stats.virtualDisplayActive) "TRUE (MediaProjection Bound)" else "INACTIVE", if (stats.virtualDisplayActive) StatusGreen else TextMuted)
                    DiagnosticRow("Active Resolution", stats.resolution, TextPrimary)
                    DiagnosticRow("Capture Pipeline FPS", "${stats.captureFps} fps", if (stats.captureFps >= 25) StatusGreen else StatusAmber)
                    DiagnosticRow("Compositor OpenGL FPS", "${stats.compositorFps} fps", StatusGreen)
                    DiagnosticRow("MediaCodec Hardware Encoder", "${stats.encoderFps} fps", StatusGreen)
                    DiagnosticRow("Live Bitrate Output", "${stats.bitrateKbps} kbps", TextPrimary)
                    DiagnosticRow("Total Frames Rendered", "${stats.totalFrames} frames", TextSecondary)
                    DiagnosticRow("Dropped Frames", "${stats.droppedFrames}", if (stats.droppedFrames > 0) StatusAmber else StatusGreen)
                    DiagnosticRow("Device Thermal State", stats.thermalStatus, if (stats.thermalStatus.contains("Cool") || stats.thermalStatus.contains("Normal")) StatusGreen else StatusAmber)
                    DiagnosticRow("RTMP Target Sinks", stats.destinationUrl.ifEmpty { "None (Configured in Settings)" }, TextSecondary)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Pipeline Architecture flow
                    Surface(
                        color = CharcoalSurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "PIPELINE ARCHITECTURE (§4)",
                                color = ChampagneGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "MediaProjection → VirtualDisplay → SurfaceTexture → OpenGL ES Compositor (Layers + ChromaKey) → Encoder Surface → MediaCodec (H.264 Hardware) → RTMP",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CharcoalSurface,
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                ) {
                    Text("CLOSE", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(CharcoalSurface.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextSecondary, fontSize = 11.sp)
        Text(text = value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
