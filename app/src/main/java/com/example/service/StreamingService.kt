package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.StreamAudioParams
import com.example.engine.StudioEngine
import com.pedro.common.ConnectChecker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

class StreamingService : Service(), ConnectChecker {
    private val TAG = "VilichtStreamingService"

    companion object {
        /** Mulai capture + engine dalam mode PREVIEW (belum siaran). */
        const val ACTION_START_PREVIEW = "com.example.service.START_PREVIEW"
        /** Mulai siaran RTMP dari mode PREVIEW. */
        const val ACTION_GO_LIVE = "com.example.service.GO_LIVE"
        const val ACTION_SNAPSHOT = "com.example.service.SNAPSHOT"
        const val ACTION_STOP = "com.example.service.STOP_STREAM"
        const val ACTION_PAUSE = "com.example.service.PAUSE_STREAM"
        const val ACTION_RESUME = "com.example.service.RESUME_STREAM"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA = "extra_data"
        const val EXTRA_ENDPOINT = "extra_endpoint"
        const val EXTRA_WIDTH = "extra_width"
        const val EXTRA_HEIGHT = "extra_height"
        const val EXTRA_FPS = "extra_fps"
        const val EXTRA_BITRATE = "extra_bitrate"
        const val EXTRA_AUDIO_BITRATE = "extra_audio_bitrate"
        const val EXTRA_PORTRAIT = "extra_portrait"

        private const val NOTIFICATION_CHANNEL_ID = "vilicht_live_channel"
        private const val NOTIFICATION_ID = 1001

        // Singleton observable state for Compose UI
        private val _studioState = MutableStateFlow(LiveStudioState())
        val studioState: StateFlow<LiveStudioState> = _studioState.asStateFlow()

        var activeServiceInstance: StreamingService? = null
    }

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): StreamingService = this@StreamingService
    }

    private var engine: StudioEngine? = null

    private var floatingOverlay: FloatingLiveOverlay? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var tickerJob: Job? = null

    private var currentEndpoint: String = ""
    private var streamStartTime: Long = 0
    private var isStreamPaused = false
    private var stopping = false
    private var currentBitrate: Int = 3000
    private val totalFrames = AtomicLong(0)
    private val droppedFrames = AtomicLong(0)

    private var targetWidth = 1280
    private var targetHeight = 720
    private var targetFps = 30

    override fun onCreate() {
        super.onCreate()
        activeServiceInstance = this
        createNotificationChannel()
        floatingOverlay = FloatingLiveOverlay(
            context = this,
            onEndLive = { stopStreaming() },
            onTogglePause = { togglePause() },
            onToggleMic = { toggleMic() },
            onEditLayout = { captureLayoutSnapshot() }
        )
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Game diputar landscape/portrait: sesuaikan ukuran capture
        engine?.onDisplayChanged()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PREVIEW -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }
                targetWidth = intent.getIntExtra(EXTRA_WIDTH, 1280)
                targetHeight = intent.getIntExtra(EXTRA_HEIGHT, 720)
                targetFps = intent.getIntExtra(EXTRA_FPS, 30)
                currentBitrate = intent.getIntExtra(EXTRA_BITRATE, 3000)
                val portrait = intent.getBooleanExtra(EXTRA_PORTRAIT, false)

                if (resultCode == Activity.RESULT_OK && data != null) {
                    stopping = false
                    startForegroundServiceWithNotification()
                    startPreviewPipeline(resultCode, data, portrait)
                } else {
                    Log.e(TAG, "Invalid start parameters for StreamingService")
                    stopSelf()
                }
            }
            ACTION_GO_LIVE -> {
                val endpoint = intent.getStringExtra(EXTRA_ENDPOINT) ?: ""
                val audioBitrate = intent.getIntExtra(EXTRA_AUDIO_BITRATE, 128)
                goLive(endpoint, audioBitrate)
            }
            ACTION_SNAPSHOT -> captureLayoutSnapshot()
            ACTION_STOP -> stopStreaming()
            ACTION_PAUSE, ACTION_RESUME -> togglePause()
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "VILICHT Live Streaming",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live broadcast status and background control"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    @SuppressLint("ForegroundServiceType")
    private fun startForegroundServiceWithNotification() {
        val notification = buildNotification("Starting live broadcast...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            val micGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            // Android 14+: mic hanya boleh dipakai di background kalau service bertipe microphone
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && micGranted) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            val camGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            // Android 14+: kamera di background butuh tipe service camera
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && camGranted) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, StreamingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("VILICHT LIVE STUDIO")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(R.mipmap.ic_launcher, "Stop Stream", stopPendingIntent)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    /** Izin capture sudah diberikan: nyalakan engine (capture + compositor) tanpa siaran. */
    private fun startPreviewPipeline(resultCode: Int, data: Intent, portrait: Boolean) {
        try {
            // Token MediaProjection hanya boleh dipakai SEKALI; engine yang memegangnya.
            val mp = (getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                .getMediaProjection(resultCode, data)
                ?: throw IllegalStateException("MediaProjection token tidak valid atau null")
            val eng = StudioEngine(this, this)
            eng.onProjectionStopped = {
                if (!stopping) {
                    _studioState.value = _studioState.value.copy(
                        errorMessage = "Screen capture dihentikan oleh sistem atau pengguna."
                    )
                    stopStreaming()
                }
            }
            eng.onError = { msg ->
                _studioState.value = _studioState.value.copy(status = StreamingStatus.ERROR, errorMessage = msg)
            }
            engine = eng
            if (eng.startPreview(mp, portrait, targetWidth, targetHeight, targetFps, currentBitrate * 1000)) {
                _studioState.value = _studioState.value.copy(
                    status = StreamingStatus.PREVIEW,
                    errorMessage = null
                )
                updateNotification("Siap. Buka game lalu tekan Atur Layout.")
                floatingOverlay?.show()
                floatingOverlay?.updateStatus(StreamingStatus.PREVIEW, "00:00", 0, 0, false, false)
            } else {
                _studioState.value = _studioState.value.copy(
                    status = StreamingStatus.ERROR,
                    errorMessage = "Encoder video ${targetWidth}x${targetHeight} gagal disiapkan. Coba preset lebih rendah."
                )
                stopStreaming()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting preview pipeline: ${e.message}", e)
            _studioState.value = _studioState.value.copy(
                status = StreamingStatus.ERROR,
                errorMessage = "Gagal memulai capture: ${e.localizedMessage}"
            )
            stopStreaming()
        }
    }

    fun goLive(endpoint: String, audioBitrateKbps: Int) {
        val eng = engine
        if (eng == null || !eng.isPreviewing) {
            Log.e(TAG, "goLive dipanggil tanpa preview aktif")
            return
        }
        if (endpoint.isBlank()) {
            _studioState.value = _studioState.value.copy(errorMessage = "Server URL / Stream Key kosong.")
            return
        }
        currentEndpoint = endpoint
        _studioState.value = _studioState.value.copy(status = StreamingStatus.CONNECTING, errorMessage = null)
        try {
            if (eng.goLive(endpoint, audioBitrateKbps * 1000)) {
                streamStartTime = System.currentTimeMillis()
                startMetricsTicker()
            } else {
                _studioState.value = _studioState.value.copy(
                    status = StreamingStatus.PREVIEW,
                    errorMessage = "Encoder audio gagal disiapkan. Cek izin mikrofon."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "goLive failed: ${e.message}", e)
            _studioState.value = _studioState.value.copy(
                status = StreamingStatus.PREVIEW,
                errorMessage = "Gagal mulai siaran: ${e.localizedMessage}"
            )
        }
    }

    /** Ambil snapshot layar game (boleh freeze), lalu buka studio untuk atur posisi. */
    fun captureLayoutSnapshot() {
        val eng = engine ?: return
        eng.captureGameSnapshot { bmp ->
            if (bmp != null) {
                setFrozenSnapshot(bmp)
                bringStudioToFront()
            } else {
                _studioState.value = _studioState.value.copy(errorMessage = "Snapshot gagal. Coba lagi saat game tampil.")
            }
        }
    }

    /** Posisi layer game dari editor (0..1 relatif canvas). */
    fun applyGameRect(x: Float, y: Float, w: Float, h: Float) {
        engine?.applyGameRect(x, y, w, h)
    }

    /** Layer webcam dari editor (source bertipe CAMERA). */
    fun applyWebcam(enabled: Boolean, x: Float, y: Float, w: Float, h: Float) {
        engine?.applyWebcam(enabled, x, y, w, h)
    }

    private fun bringStudioToFront() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun startMetricsTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                if (streamStartTime > 0) {
                    val durationSec = (System.currentTimeMillis() - streamStartTime) / 1000
                    val minutes = durationSec / 60
                    val seconds = durationSec % 60
                    val durationStr = String.format("%02d:%02d", minutes, seconds)

                    val fps = if (engine?.isLive == true && !isStreamPaused) (engine?.currentFps ?: 0) else 0
                    val currentStats = DiagnosticStats(
                        status = _studioState.value.status,
                        virtualDisplayActive = engine?.isLive == true,
                        captureFps = fps,
                        compositorFps = fps,
                        encoderFps = fps,
                        bitrateKbps = currentBitrate,
                        droppedFrames = droppedFrames.get(),
                        totalFrames = totalFrames.addAndGet(fps.toLong()),
                        durationSeconds = durationSec,
                        thermalStatus = getThermalStatusString(),
                        destinationUrl = sanitizeUrl(currentEndpoint),
                        resolution = "${targetWidth}x${targetHeight} @ ${targetFps}fps"
                    )

                    _studioState.value = _studioState.value.copy(stats = currentStats)
                    updateNotification("LIVE ● $durationStr ($currentBitrate kbps)")
                    floatingOverlay?.updateStatus(
                        status = _studioState.value.status,
                        durationStr = durationStr,
                        fps = fps,
                        bitrateKbps = currentBitrate,
                        isPaused = isStreamPaused,
                        isMicMuted = _studioState.value.isMicMuted
                    )
                }
            }
        }
    }

    /** Pause = layar hitam + audio senyap (siaran tetap tersambung, seperti "BRB"). */
    fun togglePause() {
        val eng = engine ?: return
        if (!eng.isLive) return
        isStreamPaused = !isStreamPaused
        eng.setPaused(isStreamPaused)
        _studioState.value = _studioState.value.copy(
            status = if (isStreamPaused) StreamingStatus.PAUSED else StreamingStatus.LIVE
        )
    }

    fun toggleMic() = setMicMuted(!_studioState.value.isMicMuted)

    fun setMicMuted(muted: Boolean) {
        StreamAudioParams.micMuted = muted
        _studioState.value = _studioState.value.copy(isMicMuted = muted)
    }

    // Freeze-Frame Editing (§4.5.2)
    fun setPreviewFrozen(frozen: Boolean) {
        _studioState.value = _studioState.value.copy(isPreviewFrozen = frozen)
    }

    fun setFrozenSnapshot(bitmap: Bitmap?) {
        _studioState.value = _studioState.value.copy(
            isPreviewFrozen = true,
            frozenSnapshot = bitmap
        )
    }

    fun resumePreview() {
        _studioState.value = _studioState.value.copy(
            isPreviewFrozen = false,
            frozenSnapshot = null
        )
    }

    fun stopStreaming() {
        if (stopping) return
        stopping = true
        tickerJob?.cancel()
        tickerJob = null
        floatingOverlay?.hide()

        try {
            engine?.stopLive()
            engine?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping engine: ${e.message}")
        }
        engine = null

        StreamAudioParams.paused = false
        streamStartTime = 0
        isStreamPaused = false
        _studioState.value = _studioState.value.copy(
            status = StreamingStatus.OFFLINE,
            stats = DiagnosticStats(),
            isPreviewFrozen = false,
            frozenSnapshot = null
        )

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun getThermalStatusString(): String {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (pm?.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Cool (Optimal)"
                PowerManager.THERMAL_STATUS_LIGHT -> "Light Throttle"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderate Throttle"
                PowerManager.THERMAL_STATUS_SEVERE -> "Severe Throttle"
                else -> "Normal"
            }
        } else "Normal"
    }

    private fun sanitizeUrl(url: String): String {
        // Obfuscate stream key from URL
        val lastSlash = url.lastIndexOf('/')
        return if (lastSlash > 0 && lastSlash < url.length - 1) {
            url.substring(0, lastSlash + 1) + "********"
        } else url
    }

    // ConnectChecker Implementation
    override fun onConnectionStarted(url: String) {
        Log.i(TAG, "RTMP connection started to ${sanitizeUrl(url)}")
        _studioState.value = _studioState.value.copy(status = StreamingStatus.CONNECTING)
    }

    override fun onConnectionSuccess() {
        Log.i(TAG, "RTMP connection successfully established")
        _studioState.value = _studioState.value.copy(
            status = StreamingStatus.LIVE,
            errorMessage = null
        )
    }

    override fun onConnectionFailed(reason: String) {
        Log.e(TAG, "RTMP connection failed: $reason")
        droppedFrames.incrementAndGet()
        // reTry() menjadwalkan sambung ulang; false kalau jatah percobaan habis
        val willRetry = engine?.retry(3000, reason) == true
        if (willRetry) {
            _studioState.value = _studioState.value.copy(
                status = StreamingStatus.RECONNECTING,
                errorMessage = "Koneksi terputus: $reason. Mencoba menyambung ulang..."
            )
        } else {
            _studioState.value = _studioState.value.copy(
                status = StreamingStatus.ERROR,
                errorMessage = "Koneksi gagal: $reason"
            )
            serviceScope.launch { stopStreaming() }
        }
    }

    override fun onDisconnect() {
        Log.i(TAG, "RTMP disconnected")
        if (_studioState.value.status == StreamingStatus.LIVE) {
            _studioState.value = _studioState.value.copy(status = StreamingStatus.RECONNECTING)
        }
    }

    override fun onAuthError() {
        Log.e(TAG, "RTMP authentication failed - invalid stream key or credentials")
        _studioState.value = _studioState.value.copy(
            status = StreamingStatus.ERROR,
            errorMessage = "RTMP Authentication failed. Check your Server URL & Stream Key."
        )
        serviceScope.launch { stopStreaming() }
    }

    override fun onAuthSuccess() {
        Log.i(TAG, "RTMP auth successful")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
        activeServiceInstance = null
    }
}
