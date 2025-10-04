package com.example.fiddler.subapps.ntspd

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.net.TrafficStats
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.fiddler.R

class NetSpeedService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var overlayView: LinearLayout
    private lateinit var txtUpload: TextView
    private lateinit var txtDownload: TextView
    private val handler = Handler(Looper.getMainLooper())

    private var lastRx = 0L
    private var lastTx = 0L
    private var lastTime = 0L

    private var placement = "right"
    private var offsetDp = 0

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        placement = intent?.getStringExtra("placement") ?: "right"
        offsetDp = intent?.getIntExtra("offset", 0) ?: 0

        lastRx = TrafficStats.getTotalRxBytes()
        lastTx = TrafficStats.getTotalTxBytes()
        lastTime = System.currentTimeMillis()

        showOverlay()
        startUpdating()

        return START_STICKY
    }

    private fun showOverlay() {
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.notification_netspeed, null) as LinearLayout
        txtDownload = overlayView.findViewById(R.id.txt_download)
        txtUpload = overlayView.findViewById(R.id.txt_upload)

        val params = WindowManager.LayoutParams(
            dpToPx(20), dpToPx(10),
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = when (placement) {
            "left" -> Gravity.TOP or Gravity.START
            "center" -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            else -> Gravity.TOP or Gravity.END
        }
        params.x = dpToPx(offsetDp)

        wm.addView(overlayView, params)
    }

    private fun startUpdating() {
        handler.post(object : Runnable {
            override fun run() {
                val nowRx = TrafficStats.getTotalRxBytes()
                val nowTx = TrafficStats.getTotalTxBytes()
                val nowTime = System.currentTimeMillis()

                val rxSpeed = ((nowRx - lastRx) * 1000 / (nowTime - lastTime)).toInt()
                val txSpeed = ((nowTx - lastTx) * 1000 / (nowTime - lastTime)).toInt()

                lastRx = nowRx
                lastTx = nowTx
                lastTime = nowTime

                txtDownload.text = "↓ ${rxSpeed / 1024} KB/s"
                txtUpload.text = "↑ ${txSpeed / 1024} KB/s"

                // adjust vertical weights
                val totalWeight = 1f
                val downloadWeight = if (rxSpeed >= txSpeed) 0.6f else 0.4f
                val uploadWeight = totalWeight - downloadWeight

                val layoutParamsDownload = txtDownload.layoutParams as LinearLayout.LayoutParams
                layoutParamsDownload.weight = downloadWeight
                txtDownload.layoutParams = layoutParamsDownload

                val layoutParamsUpload = txtUpload.layoutParams as LinearLayout.LayoutParams
                layoutParamsUpload.weight = uploadWeight
                txtUpload.layoutParams = layoutParamsUpload

                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) {
            wm.removeView(overlayView)
        }
        handler.removeCallbacksAndMessages(null)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
