package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.StreamProfileEntity
import com.example.encoder.AutoDetectionResult
import com.example.encoder.DeviceCapabilityDetector
import com.example.encoder.StreamPreset
import com.example.ui.theme.*

@Composable
fun SettingsDialog(
    isOpen: Boolean,
    profiles: List<StreamProfileEntity>,
    selectedProfile: StreamProfileEntity?,
    autoDetection: AutoDetectionResult,
    currentPreset: StreamPreset,
    getDecryptedKey: (String) -> String,
    onSaveProfile: (StreamProfileEntity, String) -> Unit,
    onSelectProfile: (String) -> Unit,
    onSelectPreset: (StreamPreset) -> Unit,
    onSetAutoMode: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    var currentEditingProfile by remember(selectedProfile) {
        mutableStateOf(selectedProfile ?: StreamProfileEntity(name = "Custom RTMP", serverUrl = "rtmp://"))
    }
    var streamKeyText by remember(selectedProfile) {
        mutableStateOf(if (selectedProfile != null) getDecryptedKey(selectedProfile.encryptedStreamKey) else "")
    }
    var showStreamKey by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
                .testTag("settings_dialog"),
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
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = ChampagneGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "BROADCAST SETTINGS",
                            color = TextPrimary,
                            fontSize = 15.sp,
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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // AUTO MODE HARDWARE DETECTION BANNER (§11)
                    Surface(
                        color = CharcoalSurface,
                        shape = RoundedCornerShape(8.dp),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                if (currentEditingProfile.isAutoMode) ChampagneGold else CharcoalBorder
                            )
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Memory,
                                        contentDescription = "SoC",
                                        tint = ChampagneGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Hardware Recommendation (${autoDetection.socModel})",
                                        color = ChampagneGold,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(
                                    onClick = onSetAutoMode,
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        if (currentEditingProfile.isAutoMode) "Active" else "Apply Auto",
                                        color = if (currentEditingProfile.isAutoMode) StatusGreen else ChampagneGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = autoDetection.explanation,
                                color = TextPrimary,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "RAM: ${String.format("%.1f", autoDetection.totalRamGb)} GB | Thermals: ${autoDetection.thermalStatus} | Encoder: ${autoDetection.hardwareEncoderName}",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // PROFILE PICKER
                    Text(text = "DESTINATION PLATFORM", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        profiles.forEach { prof ->
                            val isSelected = prof.id == currentEditingProfile.id
                            Surface(
                                color = if (isSelected) CharcoalSurface else CharcoalMain,
                                shape = RoundedCornerShape(6.dp),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(
                                        if (isSelected) ChampagneGold else CharcoalBorder
                                    )
                                ),
                                modifier = Modifier
                                    .clickable {
                                        onSelectProfile(prof.id)
                                        currentEditingProfile = prof
                                        streamKeyText = getDecryptedKey(prof.encryptedStreamKey)
                                    }
                            ) {
                                Text(
                                    text = prof.name,
                                    color = if (isSelected) ChampagneGold else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    // SERVER URL & STREAM KEY
                    Text(text = "RTMP CONFIGURATION", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = currentEditingProfile.serverUrl,
                        onValueChange = { currentEditingProfile = currentEditingProfile.copy(serverUrl = it) },
                        label = { Text("RTMP Server URL", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("server_url_input"),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = TextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ChampagneGold,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedContainerColor = CharcoalSurface,
                            unfocusedContainerColor = CharcoalSurface
                        )
                    )

                    OutlinedTextField(
                        value = streamKeyText,
                        onValueChange = { streamKeyText = it },
                        label = { Text("Stream Key (Encrypted in KeyStore)", fontSize = 11.sp) },
                        visualTransformation = if (showStreamKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showStreamKey = !showStreamKey }) {
                                Icon(
                                    imageVector = if (showStreamKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Key Visibility",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("stream_key_input"),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = TextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ChampagneGold,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedContainerColor = CharcoalSurface,
                            unfocusedContainerColor = CharcoalSurface
                        )
                    )

                    // RESOLUTION & FPS PRESETS
                    Text(text = "RESOLUTION & FPS PRESETS", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DeviceCapabilityDetector.ALL_PRESETS.chunked(2).forEach { rowPresets ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowPresets.forEach { preset ->
                                    val isCurrent = currentPreset.width == preset.width &&
                                            currentPreset.height == preset.height &&
                                            currentPreset.fps == preset.fps
                                    Surface(
                                        color = if (isCurrent) CharcoalSurface else CharcoalMain,
                                        shape = RoundedCornerShape(6.dp),
                                        border = CardDefaults.outlinedCardBorder().copy(
                                            brush = androidx.compose.ui.graphics.SolidColor(
                                                if (isCurrent) ChampagneGold else CharcoalBorder
                                            )
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                onSelectPreset(preset)
                                                currentEditingProfile = currentEditingProfile.copy(
                                                    width = preset.width,
                                                    height = preset.height,
                                                    fps = preset.fps,
                                                    bitrateKbps = preset.defaultBitrateKbps,
                                                    isAutoMode = false
                                                )
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = preset.label,
                                                color = if (isCurrent) ChampagneGold else TextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${preset.defaultBitrateKbps} kbps",
                                                color = TextSecondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // VIDEO BITRATE SLIDER
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Target Video Bitrate", color = TextSecondary, fontSize = 11.sp)
                        Text(text = "${currentEditingProfile.bitrateKbps} kbps", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = currentEditingProfile.bitrateKbps.toFloat(),
                        onValueChange = { newVal -> currentEditingProfile = currentEditingProfile.copy(bitrateKbps = newVal.toInt()) },
                        valueRange = 800f..9000f,
                        colors = SliderDefaults.colors(
                            thumbColor = ChampagneGold,
                            activeTrackColor = ChampagneGold,
                            inactiveTrackColor = CharcoalBorder
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // SAVE BUTTON
                Button(
                    onClick = {
                        onSaveProfile(currentEditingProfile, streamKeyText)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChampagneGold,
                        contentColor = CharcoalMain
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(42.dp).testTag("save_settings_button")
                ) {
                    Text("SAVE PROFILE & APPLY", fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.5.sp)
                }
            }
        }
    }
}
