package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.data.model.SceneEntity
import com.example.data.model.SourceEntity
import com.example.data.model.SourceType
import com.example.ui.theme.*

@Composable
fun LeftPanel(
    scenes: List<SceneEntity>,
    selectedScene: SceneEntity?,
    sources: List<SourceEntity>,
    selectedSource: SourceEntity?,
    onSelectScene: (SceneEntity) -> Unit,
    onAddScene: () -> Unit,
    onDuplicateScene: (SceneEntity) -> Unit,
    onDeleteScene: (SceneEntity) -> Unit,
    onSelectSource: (String) -> Unit,
    onAddSource: () -> Unit,
    onToggleSourceVisibility: (SourceEntity) -> Unit,
    onToggleSourceLock: (SourceEntity) -> Unit,
    onReorderSource: (SourceEntity, Boolean) -> Unit,
    onDuplicateSource: (SourceEntity) -> Unit,
    onDeleteSource: (SourceEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CharcoalPanel,
        modifier = modifier
            .width(240.dp)
            .fillMaxHeight()
            .border(1.dp, CharcoalBorder)
            .testTag("studio_left_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // TOP SECTION: SCENE MANAGER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SCENES",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                IconButton(
                    onClick = onAddScene,
                    modifier = Modifier.size(24.dp).testTag("add_scene_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Scene",
                        tint = ChampagneGold,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Scenes List (fixed height / weight 0.4)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.42f)
            ) {
                items(scenes, key = { it.id }) { scene ->
                    val isSelected = scene.id == selectedScene?.id
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) CharcoalSurface else Color.Transparent)
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) ChampagneGold else Color.Transparent,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { onSelectScene(scene) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("scene_item_${scene.name}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = scene.name,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            Row {
                                IconButton(
                                    onClick = { onDuplicateScene(scene) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Duplicate Scene",
                                        tint = TextMuted,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                if (scenes.size > 1) {
                                    IconButton(
                                        onClick = { onDeleteScene(scene) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Scene",
                                            tint = TextMuted,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Divider(color = CharcoalBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

            // BOTTOM SECTION: SOURCE MANAGER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SOURCES",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                IconButton(
                    onClick = onAddSource,
                    modifier = Modifier.size(24.dp).testTag("add_source_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Source",
                        tint = ChampagneGold,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Sources List (remaining weight)
            val sortedSources = sources.sortedByDescending { it.zIndex }
            if (sortedSources.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.58f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No sources in scene\nTap + to add Screen or Camera",
                        color = TextMuted,
                        fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.58f)
                ) {
                    items(sortedSources, key = { it.id }) { source ->
                        val isSelected = source.id == selectedSource?.id
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) CharcoalSurface else Color.Transparent)
                                .border(
                                    width = if (isSelected) 1.dp else 0.dp,
                                    color = if (isSelected) ChampagneGold else Color.Transparent,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable { onSelectSource(source.id) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("source_item_${source.name}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Visibility toggle
                                IconButton(
                                    onClick = { onToggleSourceVisibility(source) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = if (source.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle Visibility",
                                        tint = if (source.isVisible) TextPrimary else TextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                // Lock toggle
                                IconButton(
                                    onClick = { onToggleSourceLock(source) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = if (source.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = "Toggle Lock",
                                        tint = if (source.isLocked) ChampagneGold else TextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                // Source icon & name
                                val typeIcon = when (source.type) {
                                    SourceType.SCREEN -> Icons.Default.ScreenShare
                                    SourceType.CAMERA -> Icons.Default.Videocam
                                    SourceType.TEXT -> Icons.Default.TextFields
                                    SourceType.IMAGE -> Icons.Default.Image
                                    SourceType.WEB -> Icons.Default.Language
                                }
                                Icon(
                                    imageVector = typeIcon,
                                    contentDescription = source.type.name,
                                    tint = ChampagneGold,
                                    modifier = Modifier.size(14.dp)
                                )

                                Text(
                                    text = source.name,
                                    color = if (isSelected) TextPrimary else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )

                                // Reorder actions
                                IconButton(
                                    onClick = { onReorderSource(source, true) },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Move Up",
                                        tint = TextMuted,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onReorderSource(source, false) },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = "Move Down",
                                        tint = TextMuted,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }

                                // Delete
                                IconButton(
                                    onClick = { onDeleteSource(source) },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete Source",
                                        tint = TextMuted,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
