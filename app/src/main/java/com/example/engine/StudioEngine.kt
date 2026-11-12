package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import com.example.audio.GameMicAudioSource
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.gl.render.filters.`object`.SurfaceFilterRender
import androidx.core.content.ContextCompat
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.sources.video.NoVideoSource
import com.pedro.library.rtmp.RtmpStream
import com.pedro.library.view.TakePhotoCallback

/** Posisi layer relatif terhadap canvas, 0..1 (x,y = pojok kiri atas). */
private const val WEBCAM_ROTATION_OFFSET = 0

data class LayerRect(val x: Float, val y: Float, val w: Float, val h: Float)

/**
 * Mesin siaran: satu MediaProjection -> satu VirtualDisplay -> layer "game" di compositor GL.
 * Game adalah layer yang bisa digeser/diubah ukurannya di atas canvas (landscape atau portrait),
 * dan snapshot-nya diambil dari GL yang sama (aman untuk Android 14+).
 *
 * Alur: startPreview() -> (atur layout, snapshot) -> goLive() -> stopLive() -> release().
 */
class StudioEngine(
    private val context: Context,
    private val connectChecker: ConnectChecker
) {
    private val tag = "VilichtEngine"
    private val main = Handler(Looper.getMainLooper())

    private var stream: RtmpStream? = null
    private var projection: MediaProjection? = null
    private var gameLayer: SurfaceFilterRender? = null
    private var gameSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var vdThread: HandlerThread? = null
    private var previewReader: ImageReader? = null
    private var webcamLayer: SurfaceFilterRender? = null
    private var webcamSource: Camera2Source? = null
    private var webcamRect: LayerRect? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.w(tag, "MediaProjection stopped")
            main.post { onProjectionStopped?.invoke() }
        }
    }

    var onProjectionStopped: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    @Volatile var currentFps: Int = 0
        private set

    private var canvasW = 1280
    private var canvasH = 720
    private var fps = 30
    private var capW = 0
    private var capH = 0
    private var rect = LayerRect(0f, 0f, 1f, 1f)
    private var userRectSet = false

    val isPreviewing: Boolean get() = stream?.isOnPreview == true
    val isLive: Boolean get() = stream?.isStreaming == true

    /** width/height = ukuran landscape (mis. 1280x720); portrait dihitung dari flag. */
    fun startPreview(
        mp: MediaProjection, portrait: Boolean, width: Int, height: Int, fps: Int, bitrateBps: Int
    ): Boolean {
        release()
        projection = mp
        this.fps = fps
        val long = maxOf(width, height)
        val short = minOf(width, height)
        canvasW = if (portrait) short else long
        canvasH = if (portrait) long else short

        // Android 14+: callback harus terdaftar sebelum createVirtualDisplay
        mp.registerCallback(projectionCallback, main)

        val audio = GameMicAudioSource(mp)
        val s = RtmpStream(context, connectChecker, NoVideoSource(), audio)
        stream = s
        s.setFpsListener { f -> currentFps = f }

        // Konvensi RootEncoder: portrait = ukuran landscape + rotation 90
        val rotation = if (portrait) 90 else 0
        if (!s.prepareVideo(long, short, bitrateBps, fps, 2, rotation)) {
            Log.e(tag, "prepareVideo gagal")
            return false
        }
        val gl = s.getGlInterface()
        // Tidak ada frame dari source utama, jadi render dipaksa berjalan sesuai fps
        gl.setForceRender(true, fps)

        val layer = SurfaceFilterRender(SurfaceFilterRender.SurfaceReadyCallback { st -> attachGame(st) })
        gameLayer = layer
        gl.addFilter(layer)

        // Preview "kosong" yang terus dikuras, supaya thread GL tidak macet menunggu consumer
        val pw = maxOf(canvasW / 8, 16)
        val ph = maxOf(canvasH / 8, 16)
        val reader = ImageReader.newInstance(pw, ph, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({ r ->
            try { r.acquireLatestImage()?.close() } catch (_: Exception) {}
        }, main)
        previewReader = reader
        s.startPreview(reader.surface, pw, ph)
        return true
    }

    private fun attachGame(st: SurfaceTexture) {
        val mp = projection ?: return
        val (w, h) = captureSize()
        capW = w
        capH = h
        st.setDefaultBufferSize(w, h)
        val thread = HandlerThread("VilichtVD").also { it.start() }
        vdThread = thread
        val surface = Surface(st)
        gameSurface = surface
        try {
            virtualDisplay = mp.createVirtualDisplay(
                "VilichtGame", w, h, context.resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, Handler(thread.looper)
            )
        } catch (e: Exception) {
            Log.e(tag, "createVirtualDisplay gagal: ${e.message}", e)
            onError?.invoke("Capture layar gagal: ${e.localizedMessage}")
            return
        }
        applyRect(if (userRectSet) rect else fitRect())
    }

    /** Dipanggil saat rotasi layar berubah: sesuaikan ukuran capture. */
    fun onDisplayChanged() {
        val vd = virtualDisplay ?: return
        val (w, h) = captureSize()
        if (w == capW && h == capH) return
        capW = w
        capH = h
        try {
            vd.resize(w, h, context.resources.displayMetrics.densityDpi)
            gameLayer?.surfaceTexture?.setDefaultBufferSize(w, h)
        } catch (e: Exception) {
            Log.w(tag, "resize gagal: ${e.message}")
        }
    }

    /** Terapkan posisi dari editor (nilai 0..1 relatif canvas). */
    fun applyGameRect(x: Float, y: Float, w: Float, h: Float) {
        rect = LayerRect(x, y, w, h)
        userRectSet = true
        applyRect(rect)
    }

    /**
     * Webcam = kamera depan sebagai layer di atas game. Gagal = layer dilewati, siaran tetap jalan.
     * rect null atau enabled=false -> webcam dimatikan.
     */
    fun applyWebcam(enabled: Boolean, x: Float, y: Float, w: Float, h: Float) {
        val s = stream ?: return
        if (!enabled) {
            removeWebcam()
            return
        }
        webcamRect = LayerRect(x, y, w, h)
        if (webcamLayer == null) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
                onError?.invoke("Izin kamera belum diberikan, webcam dilewati.")
                return
            }
            val layer = SurfaceFilterRender(SurfaceFilterRender.SurfaceReadyCallback { st -> attachWebcam(st) })
            webcamLayer = layer
            s.getGlInterface().addFilter(layer) // ditambah setelah game, jadi tampil di atasnya
        }
        webcamLayer?.let {
            it.setScale(webcamRect!!.w * 100f, webcamRect!!.h * 100f)
            it.setPosition(webcamRect!!.x * 100f, webcamRect!!.y * 100f)
        }
    }

    private fun attachWebcam(st: SurfaceTexture) {
        try {
            val cam = Camera2Source(context)
            cam.switchCamera() // default BACK -> FRONT (belum jalan, jadi tidak restart)
            if (!cam.init(640, 480, 30, 0)) throw IllegalStateException("kamera depan tidak mendukung 640x480")
            webcamLayer?.setRotation(webcamRotation())
            cam.start(st)
            webcamSource = cam
            webcamRect?.let { r ->
                webcamLayer?.setScale(r.w * 100f, r.h * 100f)
                webcamLayer?.setPosition(r.x * 100f, r.y * 100f)
            }
        } catch (e: Exception) {
            Log.e(tag, "Webcam gagal: ${e.message}", e)
            onError?.invoke("Webcam gagal: ${e.localizedMessage}")
            removeWebcam()
        }
    }

    /**
     * Sudut putar gambar kamera depan agar tegak. Rumus standar Android:
     * (sensorOrientation + rotasiLayar) % 360. Kalau di perangkatmu webcam terlihat miring
     * atau terbalik, ubah WEBCAM_ROTATION_OFFSET (kelipatan 90).
     */
    private fun webcamRotation(): Int {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val frontId = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT
            } ?: return 0
            val sensor = cm.getCameraCharacteristics(frontId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 270
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            val disp = wm.defaultDisplay.rotation * 90
            ((sensor + disp + WEBCAM_ROTATION_OFFSET) % 360 + 360) % 360
        } catch (e: Exception) { 0 }
    }

    private fun removeWebcam() {
        val layer = webcamLayer
        try { webcamSource?.stop() } catch (_: Exception) {}
        webcamSource = null
        webcamLayer = null
        webcamRect = null
        if (layer != null) {
            try { stream?.getGlInterface()?.removeFilter(layer) } catch (_: Exception) {}
        }
    }

    private fun applyRect(r: LayerRect) {
        val layer = gameLayer ?: return
        layer.setScale(r.w * 100f, r.h * 100f)
        layer.setPosition(r.x * 100f, r.y * 100f)
    }

    /** Game dengan rasio aslinya, muat penuh di canvas dan di tengah. */
    private fun fitRect(): LayerRect {
        if (capW <= 0 || capH <= 0) return LayerRect(0f, 0f, 1f, 1f)
        val canvasAspect = canvasW.toFloat() / canvasH
        val gameAspect = capW.toFloat() / capH
        val w: Float
        val h: Float
        if (gameAspect > canvasAspect) { w = 1f; h = canvasAspect / gameAspect }
        else { h = 1f; w = gameAspect / canvasAspect }
        return LayerRect((1f - w) / 2f, (1f - h) / 2f, w, h)
    }

    /**
     * Potret layar game saat ini (rasio asli) untuk editor layout. Game boleh sedang freeze:
     * yang diambil adalah frame terakhir yang sampai ke compositor.
     */
    fun captureGameSnapshot(onResult: (Bitmap?) -> Unit) {
        val s = stream
        if (s == null || gameLayer == null || capW <= 0) { onResult(null); return }
        onDisplayChanged()
        val fit = fitRect()
        applyRect(fit)
        webcamLayer?.setAlpha(0f)
        main.postDelayed({
            s.getGlInterface().takePhoto(TakePhotoCallback { bmp ->
                val out = try { cropTo(bmp, fit) } catch (e: Exception) {
                    Log.e(tag, "crop snapshot gagal: ${e.message}"); null
                }
                applyRect(if (userRectSet) rect else fitRect())
                webcamLayer?.setAlpha(1f)
                main.post { onResult(out) }
            })
        }, 250)
    }

    private fun cropTo(src: Bitmap, r: LayerRect): Bitmap {
        val x = (r.x * src.width).toInt().coerceIn(0, src.width - 1)
        val y = (r.y * src.height).toInt().coerceIn(0, src.height - 1)
        val w = (r.w * src.width).toInt().coerceIn(1, src.width - x)
        val h = (r.h * src.height).toInt().coerceIn(1, src.height - y)
        return Bitmap.createBitmap(src, x, y, w, h)
    }

    fun goLive(endpoint: String, audioBitrateBps: Int): Boolean {
        val s = stream ?: return false
        s.getStreamClient().setReTries(10)
        if (!s.prepareAudio(44100, true, audioBitrateBps)) return false
        s.startStream(endpoint)
        return true
    }

    fun retry(delayMs: Long, reason: String): Boolean =
        stream?.getStreamClient()?.reTry(delayMs, reason) == true

    fun stopLive() {
        val s = stream ?: return
        if (s.isStreaming) s.stopStream()
    }

    /** Jeda = layar hitam + audio senyap, siaran tetap tersambung. */
    fun setPaused(paused: Boolean) {
        com.example.audio.StreamAudioParams.paused = paused
        val gl = stream?.getGlInterface() ?: return
        if (paused) gl.muteVideo() else gl.unMuteVideo()
    }

    fun release() {
        removeWebcam()
        try { stream?.release() } catch (e: Exception) { Log.w(tag, "release stream: ${e.message}") }
        stream = null
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { gameSurface?.release() } catch (_: Exception) {}
        gameSurface = null
        vdThread?.quitSafely()
        vdThread = null
        try { previewReader?.close() } catch (_: Exception) {}
        previewReader = null
        gameLayer = null
        projection?.let {
            try { it.unregisterCallback(projectionCallback) } catch (_: Exception) {}
            try { it.stop() } catch (_: Exception) {}
        }
        projection = null
        capW = 0
        capH = 0
        userRectSet = false
        currentFps = 0
    }

    private fun captureSize(): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        var rw: Int
        var rh: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            rw = b.width(); rh = b.height()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
            rw = dm.widthPixels; rh = dm.heightPixels
        }
        // Batasi sisi terpanjang supaya ringan, jaga rasio, dan pastikan genap
        val maxSide = 1280f
        val scale = minOf(1f, maxSide / maxOf(rw, rh))
        rw = (rw * scale).toInt() and 1.inv()
        rh = (rh * scale).toInt() and 1.inv()
        return maxOf(rw, 2) to maxOf(rh, 2)
    }
}
