package com.example.fiddler.subapps.ntspd

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
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
import android.graphics.Typeface

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
    private var offsetDp = -25 // default center offset

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

        // Apply dynamic color based on theme
        val textColor = getThemeTextColor()
        txtDownload?.setTextColor(textColor)
        txtUpload?.setTextColor(textColor)

        // Align text to center
        txtDownload?.gravity = Gravity.CENTER
        txtUpload?.gravity = Gravity.CENTER

        val params = WindowManager.LayoutParams(
            dpToPx(30), dpToPx(25), // width 30dp, height 25dp
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = getGravity(placement)
        params.x = dpToPx(offsetDp)
        params.y = dpToPx(5) // 5dp top padding

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

                txtDownload?.text = formatSpeed(rxSpeed, "↓")
                txtUpload?.text = formatSpeed(txSpeed, "↑")

                // Calculate weights
                val downloadWeight: Float
                val uploadWeight: Float
                if (rxSpeed == txSpeed) {
                    downloadWeight = 0.5f
                    uploadWeight = 0.5f
                } else if (rxSpeed > txSpeed) {
                    downloadWeight = 0.6f
                    uploadWeight = 0.4f
                } else {
                    downloadWeight = 0.4f
                    uploadWeight = 0.6f
                }

                // Update layout params, boldness, and font size
                txtDownload?.let {
                    val lp = it.layoutParams as LinearLayout.LayoutParams
                    lp.weight = downloadWeight
                    it.layoutParams = lp
                    it.setTypeface(null, if (rxSpeed >= txSpeed) Typeface.BOLD else Typeface.NORMAL)
                    it.setTextSize(TypedValue.COMPLEX_UNIT_DIP, if (rxSpeed >= txSpeed) 11f else 9f)
                }

                txtUpload?.let {
                    val lp = it.layoutParams as LinearLayout.LayoutParams
                    lp.weight = uploadWeight
                    it.layoutParams = lp
                    it.setTypeface(null, if (txSpeed > rxSpeed) Typeface.BOLD else Typeface.NORMAL)
                    it.setTextSize(TypedValue.COMPLEX_UNIT_DIP, if (txSpeed > rxSpeed) 11f else 9f)
                }

                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun formatSpeed(speedBytes: Long, arrow: String): SpannableString {
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

        val displayValue = String.format("%.0f", value)
        val text = "$arrow$displayValue $unit"
        val spannable = SpannableString(text)
        val start = text.indexOf(unit)
        spannable.setSpan(RelativeSizeSpan(0.5f), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannable
    }

    private fun getThemeTextColor(): Int {
        return try {
            val typedValue = TypedValue()
            theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
            if (typedValue.resourceId != 0) {
                ContextCompat.getColor(this, typedValue.resourceId)
            } else typedValue.data
        } catch (e: Exception) {
            0xFFFFFFFF.toInt() // fallback to white
        }
    }

    private fun dpToPx(dp: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { wm.removeView(it) }
        overlayView = null
        handler.removeCallbacksAndMessages(null)
    }

    override fun onBind(intent: Intent?) = null
}
