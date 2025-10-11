package com.example.fiddler.subapps.Fidland.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.util.TypedValue
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.example.fiddler.R

class OverlayManager(
    val context: Context,
    val overlayView: View,
    val gestureDetector: GestureDetector
) {
    val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val expandedContainer: FrameLayout = overlayView.findViewById(R.id.expanded_container)
    val leftSegment: LinearLayout = overlayView.findViewById(R.id.left_segment)
    val rightSegmentViews: List<View> = listOf(
        overlayView.findViewById(R.id.eq_segment),
        overlayView.findViewById(R.id.timer_segment),
        overlayView.findViewById(R.id.call_segment),
        overlayView.findViewById(R.id.bt_segment),
        overlayView.findViewById(R.id.record_segment)
    )
    val txtUpload: TextView = overlayView.findViewById(R.id.txt_upload)
    val txtDownload: TextView = overlayView.findViewById(R.id.txt_download)

    fun addToWindow() {
        if (!overlayView.isAttachedToWindow) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            windowManager.addView(overlayView, params)
        }
    }

    fun removeFromWindow() {
        if (overlayView.isAttachedToWindow) windowManager.removeView(overlayView)
    }

    fun registerReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
        context.registerReceiver(receiver, filter)
    }

    fun cleanupReceiver(receiver: BroadcastReceiver) {
        context.unregisterReceiver(receiver)
    }

    fun postLayout(block: () -> Unit) = overlayView.post(block)

    fun updateOverlayComponents() {
        // You can move your old "updateOverlayComponents()" logic here
        val prefs = context.getSharedPreferences("fidland_prefs", Context.MODE_PRIVATE)
        rightSegmentViews.forEachIndexed { i, view ->
            view.visibility = if (prefs.getBoolean(listOf("equalizer_info","timer","call","bt","record")[i], false))
                View.VISIBLE else View.GONE
        }
        txtUpload.visibility = if (prefs.getBoolean("network_traffic", false)) View.VISIBLE else View.GONE
        txtDownload.visibility = txtUpload.visibility
    }

    fun expandContainer(block: (FrameLayout) -> Unit) = block(expandedContainer)
    fun collapseContainer(block: (FrameLayout) -> Unit) = block(expandedContainer)
}
