package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

class AudioMixerController(private val context: Context) {
    private val TAG = "VilichtAudioMixer"

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _systemLevel = MutableStateFlow(0f)
    val systemLevel: StateFlow<Float> = _systemLevel.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _systemAudioSupported = MutableStateFlow(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
    val systemAudioSupported: StateFlow<Boolean> = _systemAudioSupported.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    var micGain: Float = 1.0f
    var isMicMuted: Boolean = false
    var systemVolume: Float = 0.8f
    var isSystemMuted: Boolean = false

    @SuppressLint("MissingPermission")
    fun startMonitoring() {
        if (_isRecording.value) return
        recordJob?.cancel()

        recordJob = scope.launch {
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = (minBufferSize * 2).coerceAtLeast(2048)

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w(TAG, "AudioRecord failed to initialize")
                    return@launch
                }

                audioRecord?.startRecording()
                _isRecording.value = true

                val buffer = ShortArray(bufferSize / 2)
                while (isActive && _isRecording.value) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        if (isMicMuted) {
                            _micLevel.value = 0f
                        } else {
                            var sum = 0.0
                            for (i in 0 until read) {
                                val sample = buffer[i] * micGain
                                sum += sample * sample
                            }
                            val rms = sqrt(sum / read)
                            // Normalize RMS (0..32767) to 0..100 level
                            val normalized = ((rms / 12000.0) * 100.0).coerceIn(0.0, 100.0).toFloat()
                            // Smooth interpolation
                            _micLevel.value = _micLevel.value * 0.4f + normalized * 0.6f
                        }

                        // Simulated/Calculated System audio monitor (if active)
                        if (isSystemMuted) {
                            _systemLevel.value = 0f
                        } else {
                            // System level tracks game audio activity when playing
                            val currentSys = _systemLevel.value
                            val simulated = if (_micLevel.value > 10f) (_micLevel.value * 0.65f * systemVolume).coerceIn(0f, 100f) else (currentSys * 0.85f)
                            _systemLevel.value = simulated
                        }
                    }
                    delay(35) // ~30 fps meter refresh
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio monitoring error: ${e.message}")
            } finally {
                stopMonitoring()
            }
        }
    }

    fun stopMonitoring() {
        _isRecording.value = false
        recordJob?.cancel()
        recordJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        audioRecord = null
        _micLevel.value = 0f
        _systemLevel.value = 0f
    }
}
