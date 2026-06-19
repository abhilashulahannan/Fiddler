package com.example.fiddler.subapps.ntspd

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.fiddler.R

class NetSpeedService : Service() {

    private lateinit var wm: WindowManager
    private var overlayView: LinearLayout? = null
    private var txtUpload: TextView? = null
    private var txtDownload: TextView? = null
    private val handler = Handler(Looper.getMainLooper())

    private var lastRx = 0L
    private var lastTx = 0L
    private var lastTime = 0L

    private var placement = "center"
    private var offsetDp = -25

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
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.notification_netspeed, null) as LinearLayout
        txtDownload = overlayView?.findViewById(R.id.txt_download)
        txtUpload = overlayView?.findViewById(R.id.txt_upload)

        val textColor = getThemeTextColor()
        txtDownload?.setTextColor(textColor)
        txtUpload?.setTextColor(textColor)
        txtDownload?.gravity = Gravity.CENTER
        txtUpload?.gravity = Gravity.CENTER

        val params = WindowManager.LayoutParams(
            dpToPx(30), dpToPx(25),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = gravityFor(placement)
            x = dpToPx(offsetDp)
            y = dpToPx(5)
        }

        wm.addView(overlayView, params)
    }

    private fun updateOverlayPosition(newPlacement: String, newOffset: Int) {
        overlayView?.let {
            val params = it.layoutParams as WindowManager.LayoutParams
            params.gravity = gravityFor(newPlacement)
            params.x = dpToPx(newOffset)
            wm.updateViewLayout(it, params)
        }
        placement = newPlacement
        offsetDp = newOffset
    }

    private fun gravityFor(p: String) = when (p) {
        "left"  -> Gravity.TOP or Gravity.START
        "center" -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        else    -> Gravity.TOP or Gravity.END
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            val nowRx = TrafficStats.getTotalRxBytes()
            val nowTx = TrafficStats.getTotalTxBytes()
            val nowTime = System.currentTimeMillis()
            val elapsed = nowTime - lastTime

            if (elapsed > 0) {
                val rxSpeed = (nowRx - lastRx) * 1000 / elapsed
                val txSpeed = (nowTx - lastTx) * 1000 / elapsed

                txtDownload?.text = formatSpeed(rxSpeed, "↓")
                txtUpload?.text = formatSpeed(txSpeed, "↑")

                val rxDominant = rxSpeed >= txSpeed
                txtDownload?.apply {
                    (layoutParams as? LinearLayout.LayoutParams)?.let {
                        it.weight = if (rxDominant) 0.6f else 0.4f
                        layoutParams = it
                    }
                    setTypeface(null, if (rxDominant) Typeface.BOLD else Typeface.NORMAL)
                    setTextSize(TypedValue.COMPLEX_UNIT_DIP, if (rxDominant) 11f else 9f)
                }
                txtUpload?.apply {
                    (layoutParams as? LinearLayout.LayoutParams)?.let {
                        it.weight = if (!rxDominant) 0.6f else 0.4f
                        layoutParams = it
                    }
                    setTypeface(null, if (!rxDominant) Typeface.BOLD else Typeface.NORMAL)
                    setTextSize(TypedValue.COMPLEX_UNIT_DIP, if (!rxDominant) 11f else 9f)
                }
            }

            lastRx = nowRx
            lastTx = nowTx
            lastTime = nowTime

            handler.postDelayed(this, 1000)
        }
    }

    private fun startUpdating() {
        handler.post(updateRunnable)
    }

    private fun formatSpeed(speedBytes: Long, arrow: String): SpannableString {
        var value = speedBytes / 1024.0
        var unit = "KB/s"
        if (value >= 100) { value /= 1024.0; unit = "MB/s" }
        if (value >= 100) { value /= 1024.0; unit = "GB/s" }

        val text = "$arrow${String.format("%.0f", value)} $unit"
        val spannable = SpannableString(text)
        val start = text.indexOf(unit)
        spannable.setSpan(RelativeSizeSpan(0.5f), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannable
    }

    private fun getThemeTextColor(): Int {
        return try {
            val typedValue = TypedValue()
            theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
            if (typedValue.resourceId != 0)
                ContextCompat.getColor(this, typedValue.resourceId)
            else
                typedValue.data
        } catch (e: Exception) {
            0xFFFFFFFF.toInt()
        }
    }

    private fun dpToPx(dp: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        overlayView?.let { wm.removeView(it) }
        overlayView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}