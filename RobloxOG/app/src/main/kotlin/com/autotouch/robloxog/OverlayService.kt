package com.autotouch.robloxog

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Roblox OG UI")
            .setContentText("Floating menu is running")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun params(width: Int, height: Int) =
        WindowManager.LayoutParams(
            width,
            height,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(180)
        }

    private fun showBubble() {
        if (bubble != null) return

        val view = TextView(this).apply {
            text = "D"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(30, 30, 30))
            elevation = dp(8).toFloat()
        }

        val p = params(dp(54), dp(54))
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = p.x
                    startY = p.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > dp(6) || kotlin.math.abs(dy) > dp(6)) moved = true
                    p.x = startX + dx
                    p.y = startY + dy
                    windowManager.updateViewLayout(v, p)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) togglePanel()
                    true
                }
                else -> false
            }
        }

        bubble = view
        windowManager.addView(view, p)
    }

    private fun togglePanel() {
        if (panel == null) showPanel() else hidePanel()
    }

    private fun showPanel() {
        if (panel != null) return

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(8))
            setBackgroundColor(Color.rgb(17, 17, 17))
            elevation = dp(10).toFloat()
        }

        val title = TextView(this).apply {
            text = "DELTA • OG"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(dp(6), dp(3), dp(6), dp(5))
        }
        root.addView(title)

        val status = TextView(this).apply {
            text = "READY"
            textSize = 11f
            setTextColor(Color.LTGRAY)
            setPadding(dp(6), 0, dp(6), dp(7))
        }
        root.addView(status)

        listOf("FLY", "SPIN", "TELEPORT", "ESP", "REJOIN", "CONSOLE").forEach { label ->
            val button = TextView(this).apply {
                text = label
                textSize = 13f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(12), 0)
                setBackgroundColor(Color.rgb(38, 38, 38))
                setOnClickListener {
                    status.text = "$label • UI ONLY"
                }
            }

            root.addView(
                button,
                LinearLayout.LayoutParams(dp(190), dp(42)).apply {
                    bottomMargin = dp(5)
                }
            )
        }

        val close = TextView(this).apply {
            text = "CLOSE"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setOnClickListener { hidePanel() }
        }
        root.addView(close, LinearLayout.LayoutParams(dp(190), dp(34)))

        val p = params(dp(210), dp(360)).apply {
            x = dp(18)
            y = dp(120)
        }

        panel = root
        windowManager.addView(root, p)
    }

    private fun hidePanel() {
        panel?.let { runCatching { windowManager.removeView(it) } }
        panel = null
    }

    override fun onDestroy() {
        hidePanel()
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Roblox OG UI",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "roblox_og_overlay"
        private const val NOTIFICATION_ID = 1001
    }
}
