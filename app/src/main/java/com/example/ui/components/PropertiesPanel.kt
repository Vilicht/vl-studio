package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SourceEntity
import com.example.data.model.SourceType
import com.example.ui.theme.*

@Composable
fun PropertiesPanel(
    selectedSource: SourceEntity?,
    onUpdateSource: (SourceEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CharcoalPanel,
        modifier = modifier
            .width(260.dp)
            .fillMaxHeight()
            .border(1.dp, CharcoalBorder)
            .testTag("studio_properties_panel")
    ) {
        if (selectedSource == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select a source to edit\nposition, transform,\nand video filters",
                    color = TextMuted,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "TRANSFORM & FILTERS",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Badge(containerColor = CharcoalSurface) {
                        Text(
                            text = selectedSource.type.name,
                            color = ChampagneGold,
                            fontSize = 10.sp
                        )
                    }
                }

                Text(
                    text = selectedSource.name,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Divider(color = CharcoalBorder, thickness = 1.dp)

                // TEXT OVERLAY CONTENT EDITOR
                if (selectedSource.type == SourceType.TEXT) {
                    Text(
                        text = "Text Content",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = selectedSource.textContent ?: "",
                        onValueChange = { onUpdateSource(selectedSource.copy(textContent = it)) },
                        modifier = Modifier.fillMaxWidth().testTag("text_content_input"),
                        singleLine = false,
                        maxLines = 2,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = TextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ChampagneGold,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedContainerColor = CharcoalSurface,
                            unfocusedContainerColor = CharcoalSurface
                        )
                    )
                    Divider(color = CharcoalBorder, thickness = 1.dp)
                }

                // POSITION (X, Y)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Position", color = TextSecondary, fontSize = 11.sp)
                    Text(
                        text = "X: ${(selectedSource.x * 100).toInt()}%  Y: ${(selectedSource.y * 100).toInt()}%",
                        color = TextPrimary,
                        fontSize = 11.sp
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "X", color = TextMuted, fontSize = 10.sp)
                        Slider(
                            value = selectedSource.x,
                            onValueChange = { onUpdateSource(selectedSource.copy(x = it)) },
                            valueRange = 0f..0.9f,
                            colors = SliderDefaults.colors(
                                thumbColor = ChampagneGold,
                                activeTrackColor = ChampagneGold,
                                inactiveTrackColor = CharcoalBorder
                            )
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Y", color = TextMuted, fontSize = 10.sp)
                        Slider(
                            value = selectedSource.y,
                            onValueChange = { onUpdateSource(selectedSource.copy(y = it)) },
                            valueRange = 0f..0.9f,
                            colors = SliderDefaults.colors(
                                thumbColor = ChampagneGold,
                                activeTrackColor = ChampagneGold,
                                inactiveTrackColor = CharcoalBorder
                            )
                        )
                    }
                }

                // DIMENSIONS (WIDTH, HEIGHT)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Scale", color = TextSecondary, fontSize = 11.sp)
                    Text(
                        text = "W: ${(selectedSource.width * 100).toInt()}%  H: ${(selectedSource.height * 100).toInt()}%",
                        color = TextPrimary,
                        fontSize = 11.sp
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Width", color = TextMuted, fontSize = 10.sp)
                        Slider(
                            value = selectedSource.width,
                            onValueChange = { onUpdateSource(selectedSource.copy(width = it)) },
                            valueRange = 0.1f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = ChampagneGold,
                                activeTrackColor = ChampagneGold,
                                inactiveTrackColor = CharcoalBorder
                            )
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Height", color = TextMuted, fontSize = 10.sp)
                        Slider(
                            value = selectedSource.height,
                            onValueChange = { onUpdateSource(selectedSource.copy(height = it)) },
                            valueRange = 0.05f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = ChampagneGold,
                                activeTrackColor = ChampagneGold,
                                inactiveTrackColor = CharcoalBorder
                            )
                        )
                    }
                }

                // ROTATION & OPACITY
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Rotation", color = TextSecondary, fontSize = 11.sp)
                    Text(text = "${selectedSource.rotation.toInt()}°", color = TextPrimary, fontSize = 11.sp)
                }
                Slider(
                    value = selectedSource.rotation,
                    onValueChange = { onUpdateSource(selectedSource.copy(rotation = it)) },
                    valueRange = -180f..180f,
                    colors = SliderDefaults.colors(
                        thumbColor = ChampagneGold,
                        activeTrackColor = ChampagneGold,
                        inactiveTrackColor = CharcoalBorder
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Opacity", color = TextSecondary, fontSize = 11.sp)
                    Text(text = "${(selectedSource.opacity * 100).toInt()}%", color = TextPrimary, fontSize = 11.sp)
                }
                Slider(
                    value = selectedSource.opacity,
                    onValueChange = { onUpdateSource(selectedSource.copy(opacity = it)) },
                    valueRange = 0.1f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = ChampagneGold,
                        activeTrackColor = ChampagneGold,
                        inactiveTrackColor = CharcoalBorder
                    )
                )

                // FLIP BUTTONS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onUpdateSource(selectedSource.copy(flipH = !selectedSource.flipH)) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selectedSource.flipH) CharcoalSurface else Color.Transparent,
                            contentColor = if (selectedSource.flipH) ChampagneGold else TextSecondary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Flip H", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { onUpdateSource(selectedSource.copy(flipV = !selectedSource.flipV)) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selectedSource.flipV) CharcoalSurface else Color.Transparent,
                            contentColor = if (selectedSource.flipV) ChampagneGold else TextSecondary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Flip V", fontSize = 11.sp)
                    }
                }

                Divider(color = CharcoalBorder, thickness = 1.dp)

                // CHROMA KEY FILTER SECTION (§8)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Chroma Key",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = selectedSource.chromaKeyEnabled,
                        onCheckedChange = { onUpdateSource(selectedSource.copy(chromaKeyEnabled = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ChampagneGold,
                            checkedTrackColor = CharcoalSurface,
                            uncheckedTrackColor = CharcoalBorder
                        ),
                        modifier = Modifier.testTag("chroma_key_switch")
                    )
                }

                if (selectedSource.chromaKeyEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CharcoalSurface)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "Key Color", color = TextSecondary, fontSize = 11.sp)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Green Screen Preset
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00FF00))
                                    .border(
                                        2.dp,
                                        if (selectedSource.chromaKeyColor == 0xFF00FF00L) ChampagneGold else Color.Transparent,
                                        CircleShape
                                    )
                                    .clickable { onUpdateSource(selectedSource.copy(chromaKeyColor = 0xFF00FF00L)) }
                            )
                            // Blue Screen Preset
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0000FF))
                                    .border(
                                        2.dp,
                                        if (selectedSource.chromaKeyColor == 0xFF0000FFL) ChampagneGold else Color.Transparent,
                                        CircleShape
                                    )
                                    .clickable { onUpdateSource(selectedSource.copy(chromaKeyColor = 0xFF0000FFL)) }
                            )
                            // Custom Magenta/Pink Preset
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF00FF))
                                    .border(
                                        2.dp,
                                        if (selectedSource.chromaKeyColor == 0xFFFF00FFL) ChampagneGold else Color.Transparent,
                                        CircleShape
                                    )
                                    .clickable { onUpdateSource(selectedSource.copy(chromaKeyColor = 0xFFFF00FFL)) }
                            )
                        }

                        // Similarity Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Similarity", color = TextSecondary, fontSize = 10.sp)
                            Text(text = "${(selectedSource.chromaSimilarity * 100).toInt()}%", color = TextPrimary, fontSize = 10.sp)
                        }
                        Slider(
                            value = selectedSource.chromaSimilarity,
                            onValueChange = { onUpdateSource(selectedSource.copy(chromaSimilarity = it)) },
                            valueRange = 0.1f..0.8f,
                            colors = SliderDefaults.colors(
                                thumbColor = StatusGreen,
                                activeTrackColor = StatusGreen,
                                inactiveTrackColor = CharcoalBorder
                            )
                        )

                        // Smoothness Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Smoothness", color = TextSecondary, fontSize = 10.sp)
                            Text(text = "${(selectedSource.chromaSmoothness * 100).toInt()}%", color = TextPrimary, fontSize = 10.sp)
                        }
                        Slider(
                            value = selectedSource.chromaSmoothness,
                            onValueChange = { onUpdateSource(selectedSource.copy(chromaSmoothness = it)) },
                            valueRange = 0.01f..0.3f,
                            colors = SliderDefaults.colors(
                                thumbColor = StatusGreen,
                                activeTrackColor = StatusGreen,
                                inactiveTrackColor = CharcoalBorder
                            )
                        )
                    }
                }
            }
        }
    }
}
