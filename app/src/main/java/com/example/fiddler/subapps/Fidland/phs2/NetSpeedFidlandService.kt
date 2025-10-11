package com.example.fiddler.subapps.Fidland.phs2

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.net.TrafficStats
import android.os.*
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import android.graphics.Typeface
import android.view.WindowManager
import android.view.WindowManager.LayoutParams

class NetSpeedFidlandService : Service() {

    private lateinit var wm: WindowManager
    private var overlayView: LinearLayout? = null
    private var txtUpload: TextView? = null
    private var txtDownload: TextView? = null
    private val handler = Handler(Looper.getMainLooper())

    private var lastRx = 0L
    private var lastTx = 0L
    private var lastTime = 0L

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlayView == null) {
            lastRx = TrafficStats.getTotalRxBytes()
            lastTx = TrafficStats.getTotalTxBytes()
            lastTime = System.currentTimeMillis()
            createOverlay()
            startUpdating()
        }
        return START_STICKY
    }

    private fun createOverlay() {
        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER_VERTICAL
        }

        txtUpload = createSpeedTextView()
        txtDownload = createSpeedTextView()
        overlayView?.addView(txtUpload)
        overlayView?.addView(txtDownload)

        val params = LayoutParams(
            dpToPx(40),
            dpToPx(25),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                LayoutParams.TYPE_PHONE,
            LayoutParams.FLAG_NOT_FOCUSABLE or
                    LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        params.x = dpToPx(2)
        params.y = dpToPx(0)

        wm.addView(overlayView, params)
    }

    private fun createSpeedTextView(): TextView {
        return TextView(this).apply {
            setTextColor(getThemeTextColor())
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.NORMAL)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 9f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                0.5f
            )
        }
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

                // Format the display strings
                txtDownload?.text = formatSpeed(rxSpeed, "↓")
                txtUpload?.text = formatSpeed(txSpeed, "↑")

                // Calculate vertical weights
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

                // Apply layout weights and font styles
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

        if (value >= 1000) {
            value /= 1024.0
            unit = "MB/s"
        }
        if (value >= 1000) {
            value /= 1024.0
            unit = "GB/s"
        }

        // Keep display value 3 characters
        val displayValue = if (value < 10) String.format("%.1f", value) else String.format("%.0f", value)
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
            0xFFFFFFFF.toInt()
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
