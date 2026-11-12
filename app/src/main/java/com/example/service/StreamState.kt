package com.example.service

import android.graphics.Bitmap

enum class StreamingStatus {
    OFFLINE,
    CONNECTING,
    LIVE,
    PAUSED,
    RECONNECTING,
    ERROR,
    PREVIEW
}

data class DiagnosticStats(
    val status: StreamingStatus = StreamingStatus.OFFLINE,
    val virtualDisplayActive: Boolean = false,
    val captureFps: Int = 0,
    val compositorFps: Int = 0,
    val encoderFps: Int = 0,
    val bitrateKbps: Int = 0,
    val droppedFrames: Long = 0,
    val totalFrames: Long = 0,
    val durationSeconds: Long = 0,
    val thermalStatus: String = "Normal",
    val destinationUrl: String = "",
    val resolution: String = "1280x720 @ 30fps"
)

data class LiveStudioState(
    val status: StreamingStatus = StreamingStatus.OFFLINE,
    val isMicMuted: Boolean = false,
    val isPreviewFrozen: Boolean = false,
    val frozenSnapshot: Bitmap? = null,
    val errorMessage: String? = null,
    val stats: DiagnosticStats = DiagnosticStats()
)
