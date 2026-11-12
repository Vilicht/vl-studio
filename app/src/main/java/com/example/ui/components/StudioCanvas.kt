package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.SourceEntity
import com.example.data.model.SourceType
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun StudioCanvas(
    sources: List<SourceEntity>,
    selectedSource: SourceEntity?,
    isPortrait: Boolean,
    isPreviewFrozen: Boolean,
    frozenBitmap: Bitmap?,
    onResumePreview: () -> Unit,
    onSelectSource: (String?) -> Unit,
    onUpdateSourceTransform: (SourceEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val canvasAspectRatio = if (isPortrait) 9f / 16f else 16f / 9f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CharcoalMain)
            .padding(12.dp)
            .testTag("studio_canvas_container"),
        contentAlignment = Alignment.Center
    ) {
        // Aspect ratio container (preserves 16:9 or 9:16 independent of phone/tablet display)
        BoxWithConstraints(
            modifier = Modifier
                .aspectRatio(canvasAspectRatio, matchHeightConstraintsFirst = !isPortrait)
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black)
                .border(1.dp, CharcoalBorder, RoundedCornerShape(6.dp))
                .pointerInput(Unit) {
                    // Tap on background deselects
                    detectDragGestures(
                        onDragStart = { onSelectSource(null) },
                        onDrag = { _, _ -> },
                        onDragEnd = { },
                        onDragCancel = { }
                    )
                }
                .testTag("preview_canvas")
        ) {
            val canvasWidthPx = constraints.maxWidth.toFloat()
            val canvasHeightPx = constraints.maxHeight.toFloat()

            // Render semua layer sesuai urutan z. Layer game memakai snapshot layar (kalau ada),
            // jadi posisi game dan webcam bisa diatur di atas tampilan game yang asli.
            val sortedSources = sources.filter { it.isVisible }.sortedBy { it.zIndex }
            for (source in sortedSources) {
                SourceRenderItem(
                    source = source,
                    isSelected = source.id == selectedSource?.id,
                    canvasWidthPx = canvasWidthPx,
                    canvasHeightPx = canvasHeightPx,
                    gameSnapshot = frozenBitmap,
                    onSelect = { onSelectSource(source.id) },
                    onTransform = { updated -> onUpdateSourceTransform(updated) }
                )
            }

            // 3. Selection outline and manipulation handles (rendered only for selected source)
            selectedSource?.let { sel ->
                if (sel.isVisible && !sel.isLocked) {
                    SelectionHandles(
                        source = sel,
                        canvasWidthPx = canvasWidthPx,
                        canvasHeightPx = canvasHeightPx,
                        onTransform = onUpdateSourceTransform
                    )
                }
            }

            // 4. Freeze-Frame Editing banner (§4.5.2)
            AnimatedVisibility(
                visible = isPreviewFrozen,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Surface(
                    color = CharcoalPanel.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(20.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ChampagneGold)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ChampagneGold)
                        )
                        Text(
                            text = "Editing layout — preview paused (Stream active)",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Button(
                            onClick = onResumePreview,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ChampagneGold,
                                contentColor = CharcoalMain
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp).testTag("resume_preview_button")
                        ) {
                            Text("Resume Preview", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRenderItem(
    source: SourceEntity,
    isSelected: Boolean,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    gameSnapshot: Bitmap?,
    onSelect: () -> Unit,
    onTransform: (SourceEntity) -> Unit
) {
    val itemWidth = (source.width * canvasWidthPx).dp
    val itemHeight = (source.height * canvasHeightPx).dp
    val offsetX = (source.x * canvasWidthPx).roundToInt()
    val offsetY = (source.y * canvasHeightPx).roundToInt()

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX, offsetY) }
            .size(
                width = (source.width * canvasWidthPx / 2.75f).coerceAtLeast(30f).dp,
                height = (source.height * canvasHeightPx / 2.75f).coerceAtLeast(20f).dp
            )
            .fillMaxWidth(source.width)
            .fillMaxHeight(source.height)
            .rotate(source.rotation)
            .scale(
                scaleX = if (source.flipH) -1f else 1f,
                scaleY = if (source.flipV) -1f else 1f
            )
            .pointerInput(source.id, source.isLocked) {
                if (!source.isLocked) {
                    detectDragGestures(
                        onDragStart = { onSelect() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newX = (source.x + dragAmount.x / canvasWidthPx).coerceIn(0f, 0.95f)
                            val newY = (source.y + dragAmount.y / canvasHeightPx).coerceIn(0f, 0.95f)
                            onTransform(source.copy(x = newX, y = newY))
                        }
                    )
                }
            }
    ) {
        when (source.type) {
            SourceType.SCREEN -> if (gameSnapshot != null) {
                // Snapshot layar game asli (diambil dari mesin siaran)
                androidx.compose.foundation.Image(
                    bitmap = gameSnapshot.asImageBitmap(),
                    contentDescription = "Game snapshot",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            } else {
                // Primary screen capture surface preview
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F141C)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenShare,
                            contentDescription = "Screen Capture",
                            tint = ChampagneGold.copy(alpha = 0.85f),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "PRIMARY DISPLAY CAPTURE",
                            color = TextPrimary.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Active feed rendered to MediaCodec encoder",
                            color = TextSecondary,
                            fontSize = 9.sp
                        )
                    }
                }
            }
            SourceType.CAMERA -> {
                // Facecam / Webcam overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (source.chromaKeyEnabled) Color.Transparent
                            else Color(0xFF1E2833)
                        )
                        .border(
                            1.dp,
                            if (source.chromaKeyEnabled) StatusGreen else CharcoalBorder,
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Camera",
                            tint = if (source.chromaKeyEnabled) StatusGreen else ChampagneGold,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = if (source.chromaKeyEnabled) "CHROMA KEY ON" else "FACECAM",
                            color = TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            SourceType.TEXT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(source.textBgColor))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = source.textContent ?: "TEXT OVERLAY",
                        color = Color(source.textColor),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
            SourceType.IMAGE -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(CharcoalSurface),
                    contentAlignment = Alignment.Center
                ) {
                    if (!source.contentUri.isNullOrEmpty()) {
                        AsyncImage(
                            model = source.contentUri,
                            contentDescription = source.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Image Overlay",
                            tint = ChampagneGold,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            SourceType.WEB -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1A212B))
                        .border(1.dp, CharcoalBorder, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Web Overlay",
                            tint = StatusBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "WEB WIDGET",
                            color = TextPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionHandles(
    source: SourceEntity,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    onTransform: (SourceEntity) -> Unit
) {
    val offsetX = (source.x * canvasWidthPx).roundToInt()
    val offsetY = (source.y * canvasHeightPx).roundToInt()

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX, offsetY) }
            .fillMaxWidth(source.width)
            .fillMaxHeight(source.height)
            .border(1.5.dp, ChampagneGold)
    ) {
        // Corner resize handle (Bottom-Right)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(22.dp)
                .offset(4.dp, 4.dp)
                .clip(CircleShape)
                .background(ChampagneGold)
                .pointerInput(source.id) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val newW = (source.width + dragAmount.x / canvasWidthPx).coerceIn(0.1f, 1f)
                        val newH = (source.height + dragAmount.y / canvasHeightPx).coerceIn(0.05f, 1f)
                        onTransform(source.copy(width = newW, height = newH))
                    }
                }
        )
    }
}
