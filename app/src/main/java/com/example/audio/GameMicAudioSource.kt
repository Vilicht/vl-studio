package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.pedro.encoder.Frame
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.sources.audio.AudioSource
import kotlin.math.sqrt

/** Parameter audio live. Diubah dari UI (mixer) dan floating overlay, dibaca thread audio. */
object StreamAudioParams {
    @Volatile var micGain: Float = 1.0f
    @Volatile var micMuted: Boolean = false
    @Volatile var systemVolume: Float = 0.8f
    @Volatile var paused: Boolean = false
    /** Level 0..100 untuk meter UI saat live. */
    @Volatile var micLevel: Float = 0f
    @Volatile var systemLevel: Float = 0f
}

/**
 * Sumber audio buatan sendiri untuk encoder: audio game (AudioPlaybackCapture, Android 10+)
 * dicampur dengan mic, dengan volume dan mute mic terpisah. Output: PCM 16-bit stereo.
 */
class GameMicAudioSource(private val projection: MediaProjection?) : AudioSource() {
    private val tag = "VilichtAudioSource"

    @Volatile private var running = false
    private var worker: Thread? = null
    private var micRecord: AudioRecord? = null
    private var gameRecord: AudioRecord? = null

    override fun create(
        sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean, noiseSuppressor: Boolean
    ): Boolean {
        return AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ) > 0
    }

    override fun start(getMicrophoneData: GetMicrophoneData) {
        this.getMicrophoneData = getMicrophoneData
        if (running) return
        val rate = sampleRate
        micRecord = buildMic(rate)
        gameRecord = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) buildGame(rate) else null
        if (micRecord == null && gameRecord == null) {
            throw IllegalStateException("Tidak ada sumber audio: mic ditolak dan audio game tidak tersedia")
        }
        micRecord?.startRecording()
        gameRecord?.startRecording()
        running = true
        worker = Thread({ mixLoop() }, "VilichtAudioMix").also { it.start() }
    }

    override fun stop() {
        running = false
        try { worker?.join(500) } catch (_: InterruptedException) {}
        worker = null
        listOf(micRecord, gameRecord).forEach {
            try { it?.stop() } catch (_: Exception) {}
            try { it?.release() } catch (_: Exception) {}
        }
        micRecord = null
        gameRecord = null
        getMicrophoneData = null
        StreamAudioParams.micLevel = 0f
        StreamAudioParams.systemLevel = 0f
    }

    override fun isRunning(): Boolean = running

    override fun release() {}

    @SuppressLint("MissingPermission")
    private fun buildMic(rate: Int): AudioRecord? = try {
        val min = AudioRecord.getMinBufferSize(
            rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 4, 8192)
        )
        if (rec.state == AudioRecord.STATE_INITIALIZED) rec else { rec.release(); null }
    } catch (e: Exception) {
        Log.e(tag, "Mic init failed: ${e.message}")
        null
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    private fun buildGame(rate: Int): AudioRecord? {
        val mp = projection ?: return null
        return try {
            val cfg = AudioPlaybackCaptureConfiguration.Builder(mp)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val fmt = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()
            val min = AudioRecord.getMinBufferSize(
                rate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT
            )
            val rec = AudioRecord.Builder()
                .setAudioFormat(fmt)
                .setBufferSizeInBytes(maxOf(min * 4, 16384))
                .setAudioPlaybackCaptureConfig(cfg)
                .build()
            if (rec.state == AudioRecord.STATE_INITIALIZED) rec else { rec.release(); null }
        } catch (e: Exception) {
            Log.e(tag, "Game audio capture init failed: ${e.message}")
            null
        }
    }

    private fun readFully(r: AudioRecord, b: ByteArray, size: Int): Int {
        var off = 0
        while (running && off < size) {
            val n = r.read(b, off, size - off)
            if (n < 0) return if (off > 0) off else n
            if (n == 0) { try { Thread.sleep(2) } catch (_: InterruptedException) { return off }; continue }
            off += n
        }
        return off
    }

    private fun mixLoop() {
        val frames = 1024
        val gameBuf = ByteArray(frames * 4) // stereo 16-bit
        val micBuf = ByteArray(frames * 2)  // mono 16-bit
        val game = gameRecord
        val mic = micRecord

        while (running) {
            var gameRead = 0
            var micRead = 0
            if (game != null) gameRead = readFully(game, gameBuf, gameBuf.size)
            if (mic != null) micRead = readFully(mic, micBuf, micBuf.size)
            if (!running) break
            if (game != null && gameRead < gameBuf.size) java.util.Arrays.fill(gameBuf, maxOf(gameRead, 0), gameBuf.size, 0)
            if (game == null) java.util.Arrays.fill(gameBuf, 0)
            if (mic != null && micRead < micBuf.size) java.util.Arrays.fill(micBuf, maxOf(micRead, 0), micBuf.size, 0)
            if (game == null && micRead <= 0) { try { Thread.sleep(5) } catch (_: InterruptedException) {}; continue }

            val paused = StreamAudioParams.paused
            val sysVol = StreamAudioParams.systemVolume.coerceIn(0f, 1.5f)
            val micGain = if (StreamAudioParams.micMuted) 0f else StreamAudioParams.micGain.coerceIn(0f, 2f)

            val out = ByteArray(frames * 4)
            var sumMic = 0.0
            var sumGame = 0.0
            for (i in 0 until frames) {
                val gl = readShort(gameBuf, i * 4).toFloat()
                val gr = readShort(gameBuf, i * 4 + 2).toFloat()
                val m = if (mic != null) readShort(micBuf, i * 2).toFloat() else 0f
                sumMic += (m * m).toDouble()
                sumGame += ((gl * gl + gr * gr) / 2f).toDouble()
                if (!paused) {
                    writeShort(out, i * 4, clamp(gl * sysVol + m * micGain))
                    writeShort(out, i * 4 + 2, clamp(gr * sysVol + m * micGain))
                }
            }
            StreamAudioParams.micLevel = level(sumMic, frames)
            StreamAudioParams.systemLevel = level(sumGame, frames)

            getMicrophoneData?.inputPCMData(Frame(out, 0, out.size, System.nanoTime() / 1000))
        }
    }

    private fun level(sumSq: Double, n: Int): Float =
        ((sqrt(sumSq / n) / 12000.0) * 100.0).coerceIn(0.0, 100.0).toFloat()

    private fun readShort(b: ByteArray, i: Int): Int =
        ((b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xFF)).toShort().toInt()

    private fun writeShort(b: ByteArray, i: Int, v: Int) {
        b[i] = (v and 0xFF).toByte()
        b[i + 1] = ((v shr 8) and 0xFF).toByte()
    }

    private fun clamp(v: Float): Int = v.toInt().coerceIn(-32768, 32767)
}
