package com.example.fiddler.subapps.Fidland.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.FrameLayout
import androidx.core.view.isVisible

class PhaseTouchBox(
    private val context: Context,
    private val windowManager: WindowManager,
    private val gestureDetector: GestureDetector
) {
    lateinit var view: FrameLayout
        private set

    private val handler = Handler(Looper.getMainLooper())

    private val width = (200 * context.resources.displayMetrics.density).toInt()
    private val height = (75 * context.resources.displayMetrics.density).toInt()
    private val widthOffset = width - (100 * context.resources.displayMetrics.density).toInt()

    /** Setup phase touch box and add it to WindowManager */
    fun setup() {
        view = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = WindowManager.LayoutParams(
                width,
                height,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = 0
                y = 0
            }

            setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                true
            }
        }

        windowManager.addView(view, view.layoutParams)
    }

    /** Place the touch box at a specific position relative to overlay */
    fun place(overlayX: Float, overlayY: Float, overlayHeight: Int) {
        view.post {
            view.x = overlayX + (view.width / 2f) - widthOffset
            view.y = overlayY + overlayHeight
            bringToFrontIfVisible()
        }
    }

    /** Make touch box visible or gone */
    fun show(visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.GONE
        bringToFrontIfVisible()
    }

    /** Flash touch box with semi-transparent black for a short time */
    fun flash(duration: Long = 1000) {
        view.setBackgroundColor(Color.parseColor("#80000000"))
        view.visibility = View.VISIBLE
        bringToFrontIfVisible()
        handler.postDelayed({
            view.setBackgroundColor(Color.TRANSPARENT)
        }, duration)
    }

    /** Ensure touch box is on top of overlay */
    fun bringToFrontIfVisible() {
        if (::view.isInitialized && view.isVisible) {
            view.bringToFront()
        }
    }

    /** Clean up touch box from window manager */
    fun remove() {
        if (::view.isInitialized && view.isAttachedToWindow) {
            windowManager.removeView(view)
        }
    }
}
