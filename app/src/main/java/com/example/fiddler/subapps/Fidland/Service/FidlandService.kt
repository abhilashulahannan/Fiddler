package com.example.fiddler.subapps.Fidland.service

import android.animation.ValueAnimator
import android.app.Service
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.fiddler.subapps.Fidland.service.OverlayManager
import com.example.fiddler.subapps.Fidland.service.TopicManager
import androidx.core.animation.doOnEnd
import androidx.core.view.isVisible
import com.example.fiddler.R

class FidlandService : Service(), GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {

    private lateinit var overlayManager: OverlayManager
    private lateinit var topicManager: TopicManager
    private lateinit var gestureDetector: GestureDetector

    private lateinit var phaseTouchBox: PhaseTouchBox
    private lateinit var segmentManager: SegmentManager
    private lateinit var networkManager: NetworkTrafficManagerService

    private var isExpanded = false

    private val overlayUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            overlayManager.updateOverlayComponents()
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        gestureDetector = GestureDetector(this, this).apply {
            setOnDoubleTapListener(this@FidlandService)
        }

        val overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_fidland_pill, null)

        overlayManager = OverlayManager(
            context = this,
            overlayView = overlayView,
            gestureDetector = gestureDetector
        )

        phaseTouchBox = PhaseTouchBox(this, overlayManager.windowManager, gestureDetector).apply { setup() }

        segmentManager = SegmentManager(overlayManager.rightSegmentViews, Handler(Looper.getMainLooper())).apply { start() }

        networkManager = NetworkTrafficManagerService(
            this,
            overlayManager.txtUpload,
            overlayManager.txtDownload,
            overlayManager.leftSegment,
            overlayManager.overlayView
        )
        networkManager.toggle(getPrefsBoolean("network_traffic"))

        topicManager = TopicManager(this, overlayView, overlayManager.expandedContainer).apply {
            collapsePill = { collapsePill() }
            setupTopics()
        }

        val filter = IntentFilter("com.example.fiddler.FIDLAND_UPDATE_OVERLAY")
        overlayManager.registerReceiver(overlayUpdateReceiver, filter)
        overlayManager.postLayout {
            phaseTouchBox.flash()
            phaseTouchBox.place(overlayManager.overlayView.x, overlayManager.overlayView.y, overlayManager.overlayView.height)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        segmentManager.stop()
        networkManager.stop()
        phaseTouchBox.remove()
        overlayManager.cleanupReceiver(overlayUpdateReceiver)
    }

    // --- Pill Expansion/Collapse ---
    private fun expandPill() {
        if (isExpanded) return
        isExpanded = true
        overlayManager.expandContainer { expandedContainer ->
            expandedContainer.visibility = View.VISIBLE
            expandedContainer.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val targetHeight = expandedContainer.measuredHeight
            val animator = ValueAnimator.ofInt(0, targetHeight)
            animator.duration = 200
            animator.addUpdateListener { expandedContainer.layoutParams.height = it.animatedValue as Int; expandedContainer.requestLayout() }
            animator.start()
        }
    }

    private fun collapsePill() {
        if (!isExpanded) return
        isExpanded = false
        overlayManager.collapseContainer { expandedContainer ->
            val animator = ValueAnimator.ofInt(expandedContainer.height, 0)
            animator.duration = 200
            animator.addUpdateListener { expandedContainer.layoutParams.height = it.animatedValue as Int; expandedContainer.requestLayout() }
            animator.doOnEnd { expandedContainer.visibility = View.GONE }
            animator.start()
        }
    }

    // --- Gesture Callbacks ---
    override fun onDown(e: MotionEvent) = true
    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent) = true
    override fun onLongPress(e: MotionEvent) {
        startActivity(Intent(this, com.example.fiddler.MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        if (!isExpanded && (Math.abs(distanceX) > 10 || Math.abs(distanceY) > 10)) expandPill()
        else topicManager.handleSwipe(-distanceX, -distanceY, isExpanded)
        return true
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float) = false
    override fun onSingleTapConfirmed(e: MotionEvent) = if (!isExpanded) { expandPill(); true } else true
    override fun onDoubleTap(e: MotionEvent) = false
    override fun onDoubleTapEvent(e: MotionEvent) = false

    private fun getPrefsBoolean(key: String) = getSharedPreferences("fidland_prefs", Context.MODE_PRIVATE).getBoolean(key, false)
}
