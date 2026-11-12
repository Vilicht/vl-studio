package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class SourceType {
    SCREEN,
    CAMERA,
    IMAGE,
    TEXT,
    WEB
}

@Entity(tableName = "scenes")
data class SceneEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isSelected: Boolean = false,
    val orderIndex: Int = 0,
    val canvasWidth: Int = 1280,
    val canvasHeight: Int = 720,
    val isPortrait: Boolean = false
)

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sceneId: String,
    val name: String,
    val type: SourceType,
    val isVisible: Boolean = true,
    val isLocked: Boolean = false,
    val zIndex: Int = 0,
    // Normalized 0f..1f relative to canvas
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 1f,
    val height: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val contentUri: String? = null,
    val textContent: String? = null,
    val textColor: Long = 0xFFF2F0E9L,
    val textBgColor: Long = 0x88000000L,
    val chromaKeyEnabled: Boolean = false,
    val chromaKeyColor: Long = 0xFF00FF00L, // Green default
    val chromaSimilarity: Float = 0.4f,
    val chromaSmoothness: Float = 0.1f
)

@Entity(tableName = "stream_profiles")
data class StreamProfileEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val serverUrl: String,
    val encryptedStreamKey: String = "",
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 30,
    val bitrateKbps: Int = 3000,
    val audioBitrateKbps: Int = 128,
    val isAutoMode: Boolean = true,
    val isSelected: Boolean = false
)

@Entity(tableName = "audio_settings")
data class AudioSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val micVolume: Float = 1.0f,
    val isMicMuted: Boolean = false,
    val systemVolume: Float = 0.8f,
    val isSystemMuted: Boolean = false
)
