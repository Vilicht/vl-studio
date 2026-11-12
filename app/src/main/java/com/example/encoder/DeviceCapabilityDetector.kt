package com.example.encoder

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.os.PowerManager
import android.util.Log

data class StreamPreset(
    val label: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val defaultBitrateKbps: Int,
    val description: String
)

data class AutoDetectionResult(
    val selectedPreset: StreamPreset,
    val explanation: String,
    val socModel: String,
    val totalRamGb: Float,
    val thermalStatus: String,
    val hardwareEncoderName: String
)

object DeviceCapabilityDetector {
    private const val TAG = "VilichtCapability"

    val PRESET_1080P_60 = StreamPreset("1080p @ 60fps", 1920, 1080, 60, 6500, "Full HD High Frame Rate")
    val PRESET_1080P_30 = StreamPreset("1080p @ 30fps", 1920, 1080, 30, 4500, "Full HD Standard")
    val PRESET_720P_60  = StreamPreset("720p @ 60fps", 1280, 720, 60, 4500, "HD High Frame Rate")
    val PRESET_720P_30  = StreamPreset("720p @ 30fps", 1280, 720, 30, 3000, "HD Standard (Snapdragon 7s Gen 2 Sweet Spot)")
    val PRESET_540P_30  = StreamPreset("540p @ 30fps", 960, 540, 30, 2000, "qHD Efficient Stream")
    val PRESET_360P_30  = StreamPreset("360p @ 30fps", 640, 360, 30, 1000, "Low-bandwidth / Thermal Saver")

    val ALL_PRESETS = listOf(
        PRESET_1080P_60,
        PRESET_1080P_30,
        PRESET_720P_60,
        PRESET_720P_30,
        PRESET_540P_30,
        PRESET_360P_30
    )

    fun detectOptimalPreset(context: Context): AutoDetectionResult {
        // 1. RAM detection
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val ramGb = memInfo.totalMem.toFloat() / (1024f * 1024f * 1024f)

        // 2. Thermal Status
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val thermalStatusInt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
        } else {
            0
        }
        val thermalStr = when (thermalStatusInt) {
            PowerManager.THERMAL_STATUS_NONE -> "Cool (None)"
            PowerManager.THERMAL_STATUS_LIGHT -> "Light Throttle"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderate Throttle"
            PowerManager.THERMAL_STATUS_SEVERE -> "Severe Throttle"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Critical Heat"
            else -> "Normal"
        }

        // 3. SoC Identification
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.ifEmpty { Build.HARDWARE }
        } else {
            Build.HARDWARE
        }
        val hardwareLower = "${Build.HARDWARE} ${Build.BOARD} ${Build.MODEL} $socModel".lowercase()

        // 4. Hardware MediaCodec Check
        val (hwEncoder, maxW, maxH) = findH264HardwareEncoder()

        // Evaluation Logic
        val isSnapdragon7sGen2 = hardwareLower.contains("7s") || hardwareLower.contains("sm7435") ||
                hardwareLower.contains("adreno 710") || hardwareLower.contains("taro")
        val isFlagshipSoC = hardwareLower.contains("sm8") || hardwareLower.contains("snapdragon 8") ||
                hardwareLower.contains("dimensity 9") || hardwareLower.contains("tensor g")

        val (preset, reason) = when {
            thermalStatusInt >= PowerManager.THERMAL_STATUS_MODERATE -> {
                PRESET_540P_30 to "Thermal limit active ($thermalStr) — lowering resolution to preserve frame stability and battery"
            }
            ramGb < 3.8f -> {
                PRESET_540P_30 to "RAM constrained (${String.format("%.1f", ramGb)} GB) — 540p30 chosen for stable memory buffer"
            }
            isSnapdragon7sGen2 -> {
                PRESET_720P_30 to "Snapdragon 7s Gen 2 (Adreno 710) detected — 720p30 calibrated for rock-solid thermals & 60fps in-game performance"
            }
            isFlagshipSoC && ramGb >= 7.5f && thermalStatusInt == PowerManager.THERMAL_STATUS_NONE -> {
                PRESET_1080P_30 to "High-performance chipset detected ($socModel, ${String.format("%.1f", ramGb)} GB RAM) — 1080p30 enabled"
            }
            maxW >= 1280 && maxH >= 720 -> {
                PRESET_720P_30 to "Optimal universal profile for ${Build.MODEL} — 720p30 balancing encoder throughput & latency"
            }
            else -> {
                PRESET_540P_30 to "Default stable profile for hardware encoder ($hwEncoder)"
            }
        }

        Log.i(TAG, "Optimal preset calculated: ${preset.label} ($reason)")
        return AutoDetectionResult(
            selectedPreset = preset,
            explanation = reason,
            socModel = if (socModel.isNotBlank()) socModel else Build.MODEL,
            totalRamGb = ramGb,
            thermalStatus = thermalStr,
            hardwareEncoderName = hwEncoder
        )
    }

    private fun findH264HardwareEncoder(): Triple<String, Int, Int> {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            val types = info.supportedTypes
            for (type in types) {
                if (type.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)) {
                    val isSoftware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        info.isSoftwareOnly
                    } else {
                        info.name.startsWith("OMX.google.") || info.name.startsWith("c2.android.")
                    }
                    if (!isSoftware) {
                        val caps = info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities
                        val maxW = caps?.supportedWidths?.upper ?: 1920
                        val maxH = caps?.supportedHeights?.upper ?: 1080
                        return Triple(info.name, maxW, maxH)
                    }
                }
            }
        }
        return Triple("Default AVC Encoder", 1920, 1080)
    }
}
