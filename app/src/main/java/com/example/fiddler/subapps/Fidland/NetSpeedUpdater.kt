package com.example.fiddler.subapps.Fidland

import android.content.Context
import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan

class NetSpeedUpdater(
    private val context: Context,
    private val txtUpload: TextView,
    private val txtDownload: TextView,
    private val leftSegment: LinearLayout,
    private val overlayView: View
) {

    private val handler = Handler(Looper.getMainLooper())
    private var lastRx = 0L
    private var lastTx = 0L
    private var lastTime = 0L
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true

        // Ensure TextViews are visible
        txtUpload.visibility = View.VISIBLE
        txtDownload.visibility = View.VISIBLE
        txtUpload.setTextColor(context.getColor(android.R.color.white))
        txtDownload.setTextColor(context.getColor(android.R.color.white))

        lastRx = TrafficStats.getTotalRxBytes()
        lastTx = TrafficStats.getTotalTxBytes()
        lastTime = System.currentTimeMillis()

        // Ensure layout is ready
        overlayView.post { updateLoop() }
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        txtUpload.visibility = View.GONE
        txtDownload.visibility = View.GONE
    }

    private fun updateLoop() {
        handler.post(object : Runnable {
            override fun run() {
                if (!isRunning) return

                val nowRx = TrafficStats.getTotalRxBytes()
                val nowTx = TrafficStats.getTotalTxBytes()
                val nowTime = System.currentTimeMillis()

                val rxSpeed = ((nowRx - lastRx) * 1000 / (nowTime - lastTime)).coerceAtLeast(0L)
                val txSpeed = ((nowTx - lastTx) * 1000 / (nowTime - lastTime)).coerceAtLeast(0L)

                lastRx = nowRx
                lastTx = nowTx
                lastTime = nowTime

                txtDownload.text = formatSpeed(rxSpeed, "↓")
                txtUpload.text = formatSpeed(txSpeed, "↑")

                adjustLayoutWeights(rxSpeed, txSpeed)

                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun adjustLayoutWeights(rxSpeed: Long, txSpeed: Long) {
        // Decide stronger speed for bold + weight
        val rxStronger = rxSpeed >= txSpeed
        val dlWeight = if (rxStronger) 0.6f else 0.4f
        val ulWeight = 1f - dlWeight

        // Apply weights safely
        (txtDownload.layoutParams as? LinearLayout.LayoutParams)?.let {
            it.weight = dlWeight
            txtDownload.layoutParams = it
        }
        (txtUpload.layoutParams as? LinearLayout.LayoutParams)?.let {
            it.weight = ulWeight
            txtUpload.layoutParams = it
        }

        // Bold the stronger
        txtDownload.setTypeface(null, if (rxStronger) Typeface.BOLD else Typeface.NORMAL)
        txtUpload.setTypeface(null, if (!rxStronger) Typeface.BOLD else Typeface.NORMAL)

        // Adjust font size dynamically
        txtDownload.setTextSize(TypedValue.COMPLEX_UNIT_DIP, if (rxStronger) 10f else 8f)
        txtUpload.setTextSize(TypedValue.COMPLEX_UNIT_DIP, if (!rxStronger) 10f else 8f)
    }

    private fun formatSpeed(speedBytes: Long, arrow: String): SpannableString {
        var value = speedBytes / 1024.0
        var unit = "KB/s"

        if (value >= 1024) {
            value /= 1024.0
            unit = "MB/s"
        }
        if (value >= 1024) {
            value /= 1024.0
            unit = "GB/s"
        }

        val displayValue = if (value < 10) String.format("%.1f", value) else String.format("%.0f", value)
        val text = "$arrow$displayValue $unit"

        val spannable = SpannableString(text)
        val start = text.indexOf(unit)
        spannable.setSpan(RelativeSizeSpan(0.5f), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannable
    }
}
