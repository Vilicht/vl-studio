package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MainActivity

class FloatingLiveOverlay(
    private val context: Context,
    private val onEndLive: () -> Unit,
    private val onTogglePause: () -> Unit,
    private val onToggleMic: () -> Unit,
    private val onEditLayout: () -> Unit = {}
) {
    private val TAG = "VilichtFloatingOverlay"
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isExpanded = false

    private var statusTextView: TextView? = null
    private var statsTextView: TextView? = null
    private var pauseButton: Button? = null
    private var micButton: Button? = null
    private var layoutButton: Button? = null
    private var expandedLayout: LinearLayout? = null

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW permission not granted. Cannot show floating control.")
            return
        }
        if (overlayView != null) return

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 120
        }

        // Build root container with charcoal & champagne gold styling
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(0xEE171715.toInt())
                cornerRadius = 36f
                setStroke(2, 0xFFC5A46D.toInt())
            }
            background = bg
            setPadding(24, 16, 24, 16)
            elevation = 16f
        }

        // Header pill (always visible, draggable)
        val pillHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Live dot + Status
        statusTextView = TextView(context).apply {
            text = "● LIVE 00:00"
            setTextColor(0xFF5B8A64.toInt()) // Muted green
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(8, 0, 12, 0)
        }

        // Stats (FPS + Bitrate)
        statsTextView = TextView(context).apply {
            text = "30 FPS | 3000 kbps"
            setTextColor(0xFFF2F0E9.toInt())
            textSize = 11f
            setPadding(0, 0, 16, 0)
        }

        // Open studio icon button
        val expandIcon = TextView(context).apply {
            text = "⤢"
            setTextColor(0xFFC5A46D.toInt())
            textSize = 16f
            setPadding(8, 0, 8, 0)
            setOnClickListener {
                bringStudioToFront()
            }
        }

        pillHeader.addView(statusTextView)
        pillHeader.addView(statsTextView)
        pillHeader.addView(expandIcon)

        // Expanded Action Drawer
        expandedLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            setPadding(0, 16, 0, 4)
            gravity = Gravity.CENTER
        }

        // Atur Layout: potret layar game lalu buka editor
        layoutButton = Button(context).apply {
            text = "Atur Layout"
            textSize = 11f
            setTextColor(0xFF1B1B19.toInt())
            background = GradientDrawable().apply {
                setColor(0xFFC5A46D.toInt())
                cornerRadius = 16f
            }
            setOnClickListener {
                onEditLayout()
            }
        }

        // Pause/Resume button
        pauseButton = Button(context).apply {
            text = "Pause"
            textSize = 11f
            setTextColor(0xFFF2F0E9.toInt())
            background = GradientDrawable().apply {
                setColor(0xFF282825.toInt())
                cornerRadius = 16f
                setStroke(1, 0xFF41413B.toInt())
            }
            setOnClickListener {
                onTogglePause()
            }
        }

        // Mic mute toggle button
        micButton = Button(context).apply {
            text = "Mic ON"
            textSize = 11f
            setTextColor(0xFFF2F0E9.toInt())
            background = GradientDrawable().apply {
                setColor(0xFF282825.toInt())
                cornerRadius = 16f
                setStroke(1, 0xFF41413B.toInt())
            }
            setOnClickListener {
                onToggleMic()
            }
        }

        // End Live button
        val endButton = Button(context).apply {
            text = "End Live"
            textSize = 11f
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply {
                setColor(0xFFB24A4A.toInt()) // Red
                cornerRadius = 16f
            }
            setOnClickListener {
                onEndLive()
            }
        }

        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(8, 0, 8, 0)
        }

        expandedLayout?.addView(layoutButton, btnParams)
        expandedLayout?.addView(pauseButton, btnParams)
        expandedLayout?.addView(micButton, btnParams)
        expandedLayout?.addView(endButton, btnParams)

        rootLayout.addView(pillHeader)
        rootLayout.addView(expandedLayout)

        // Dragging & Single Tap handling
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        pillHeader.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(rootLayout, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // Toggle expand
                        isExpanded = !isExpanded
                        expandedLayout?.visibility = if (isExpanded) View.VISIBLE else View.GONE
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(rootLayout, params)
            overlayView = rootLayout
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay view: ${e.message}")
        }
    }

    fun updateStatus(status: StreamingStatus, durationStr: String, fps: Int, bitrateKbps: Int, isPaused: Boolean, isMicMuted: Boolean) {
        statusTextView?.post {
            statusTextView?.text = when (status) {
                StreamingStatus.LIVE -> if (isPaused) "❚❚ PAUSED" else "● LIVE $durationStr"
                StreamingStatus.RECONNECTING -> "↻ RECONNECTING"
                StreamingStatus.CONNECTING -> "◌ CONNECTING"
                StreamingStatus.PREVIEW -> "◉ SIAP"
                else -> "OFFLINE"
            }
            statusTextView?.setTextColor(
                when {
                    isPaused -> 0xFFC49B4B.toInt() // Amber
                    status == StreamingStatus.LIVE -> 0xFF5B8A64.toInt() // Green
                    status == StreamingStatus.RECONNECTING -> 0xFFC49B4B.toInt()
                    status == StreamingStatus.PREVIEW -> 0xFFC5A46D.toInt()
                    else -> 0xFFB24A4A.toInt()
                }
            )
            statsTextView?.text = "$fps FPS | $bitrateKbps kbps"
            pauseButton?.text = if (isPaused) "Resume" else "Pause"
            micButton?.text = if (isMicMuted) "Mic OFF" else "Mic ON"
        }
    }

    fun hide() {
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay view: ${e.message}")
            }
            overlayView = null
        }
    }

    private fun bringStudioToFront() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(intent)
    }
}
