package com.example.fiddler.subapps.ntspd

import NetSpeedOverlay
import android.app.Service
import android.content.Intent
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class NetSpeedService : Service() {

    private lateinit var wm: WindowManager
    private var overlayView: ComposeView? = null
    private val handler = Handler(Looper.getMainLooper())

    private var lastRx = 0L
    private var lastTx = 0L
    private var lastTime = 0L

    private var placement = "center"
    private var offsetDp = -25 // default center offset

    private val uploadState = mutableStateOf("↑0 KB/s")
    private val downloadState = mutableStateOf("↓0 KB/s")

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val newPlacement = intent?.getStringExtra("placement") ?: "center"
        val newOffset = intent?.getIntExtra("offset", -25) ?: -25

        if (overlayView != null) {
            updateOverlayPosition(newPlacement, newOffset)
        } else {
            placement = newPlacement
            offsetDp = newOffset
            lastRx = TrafficStats.getTotalRxBytes()
            lastTx = TrafficStats.getTotalTxBytes()
            lastTime = System.currentTimeMillis()
            createOverlay()
            startUpdating()
        }

        return START_STICKY
    }

    private fun createOverlay() {
        overlayView = ComposeView(this).apply {
            setContent {
                NetSpeedOverlay(
                    uploadText = uploadState.value,
                    downloadText = downloadState.value
                )
            }
        }

        val params = WindowManager.LayoutParams(
            dpToPx(30), dpToPx(25),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )

        params.gravity = getGravity(placement)
        params.x = dpToPx(offsetDp)
        params.y = dpToPx(5)

        wm.addView(overlayView, params)
    }

    private fun updateOverlayPosition(newPlacement: String, newOffset: Int) {
        overlayView?.let {
            val params = it.layoutParams as WindowManager.LayoutParams
            params.gravity = getGravity(newPlacement)
            params.x = dpToPx(newOffset)
            wm.updateViewLayout(it, params)
        }
        placement = newPlacement
        offsetDp = newOffset
    }

    private fun getGravity(placement: String) = when (placement) {
        "left" -> Gravity.TOP or Gravity.START
        "center" -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        else -> Gravity.TOP or Gravity.END
    }

    private fun startUpdating() {
        handler.post(object : Runnable {
            override fun run() {
                val nowRx = TrafficStats.getTotalRxBytes()
                val nowTx = TrafficStats.getTotalTxBytes()
                val nowTime = System.currentTimeMillis()

                val rxSpeed = ((nowRx - lastRx) * 1000 / (nowTime - lastTime)).toLong()
                val txSpeed = ((nowTx - lastTx) * 1000 / (nowTime - lastTime)).toLong()

                lastRx = nowRx
                lastTx = nowTx
                lastTime = nowTime

                uploadState.value = formatSpeed(txSpeed, "↑")
                downloadState.value = formatSpeed(rxSpeed, "↓")

                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun formatSpeed(speedBytes: Long, arrow: String): String {
        var value = speedBytes / 1024.0
        var unit = "KB/s"

        if (value >= 100) {
            value /= 1024.0
            unit = "MB/s"
        }
        if (value >= 100) {
            value /= 1024.0
            unit = "GB/s"
        }

        return "$arrow${String.format("%.0f", value)} $unit"
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { wm.removeView(it) }
        overlayView = null
        handler.removeCallbacksAndMessages(null)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
