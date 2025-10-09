package com.example.fiddler.subapps.Fidland

import android.animation.ValueAnimator
import android.app.Service
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.util.TypedValue
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.fiddler.R

class FidlandService : Service(), GestureDetector.OnGestureListener {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var gestureDetector: GestureDetector
    private lateinit var params: WindowManager.LayoutParams

    private lateinit var expandedContainer: LinearLayout
    private lateinit var rightSegment: LinearLayout
    private lateinit var leftSegment: LinearLayout
    private lateinit var cameraMask: View

    private lateinit var eqSegment: View
    private lateinit var timerSegment: View
    private lateinit var callSegment: View
    private lateinit var btSegment: View
    private lateinit var recordSegment: View

    private lateinit var timerIcon: View
    private lateinit var callIcon: View
    private lateinit var btIcon: View
    private lateinit var recordIcon: View

    private lateinit var musicSegment: View
    private lateinit var queueSegment: View
    private lateinit var quickSettingsSegment: View

    private lateinit var txtUpload: TextView
    private lateinit var txtDownload: TextView

    private val segmentViews = mutableListOf<View>()
    private var currentSegmentIndex = 0
    private val handler = Handler(Looper.getMainLooper())
    private var segmentSwitcherRunning = false

    private var networkTrafficEnabled = false
    private var eqEnabled = false
    private var timerEnabled = false
    private var callEnabled = false
    private var btEnabled = false
    private var recordEnabled = false

    private var isExpanded = false
    private var netSpeedUpdater: NetSpeedUpdater? = null

    private var baseCameraCenterX: Int? = null // fixed anchor for all phases

    private val overlayUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateOverlayComponents()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        gestureDetector = GestureDetector(this, this)

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_fidland_pill, null)
        expandedContainer = overlayView.findViewById(R.id.expanded_container)
        rightSegment = overlayView.findViewById(R.id.right_segment)
        leftSegment = overlayView.findViewById(R.id.left_segment)
        cameraMask = overlayView.findViewById(R.id.camera_mask)
        txtUpload = overlayView.findViewById(R.id.txt_upload)
        txtDownload = overlayView.findViewById(R.id.txt_download)

        eqSegment = overlayView.findViewById(R.id.eq_segment)
        timerSegment = overlayView.findViewById(R.id.timer_segment)
        callSegment = overlayView.findViewById(R.id.call_segment)
        btSegment = overlayView.findViewById(R.id.bt_segment)
        recordSegment = overlayView.findViewById(R.id.record_segment)

        segmentViews.addAll(listOf(eqSegment, timerSegment, callSegment, btSegment, recordSegment))

        timerIcon = overlayView.findViewById(R.id.timer_icon)
        callIcon = overlayView.findViewById(R.id.call_icon)
        btIcon = overlayView.findViewById(R.id.bt_icon)
        recordIcon = overlayView.findViewById(R.id.record_icon)

        musicSegment = overlayView.findViewById(R.id.music_segment)
        queueSegment = overlayView.findViewById(R.id.queue_segment)
        quickSettingsSegment = overlayView.findViewById(R.id.quick_settings_segment)

        params = WindowManager.LayoutParams(
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
        params.x = 0
        params.y = 7

        overlayView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        windowManager.addView(overlayView, params)
        startSegmentSwitcher()

        val filter = IntentFilter("com.example.fiddler.FIDLAND_UPDATE_OVERLAY")
        registerReceiver(overlayUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        // Wait for first layout pass to save camera mask center
        overlayView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                overlayView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                // Option 1: Automatic capture (optional)
                // val loc = IntArray(2)
                // cameraMask.getLocationOnScreen(loc)
                // baseCameraCenterX = loc[0] + cameraMask.width / 2

                // Option 2: Fixed manual value (permanent anchor for all phases)
                baseCameraCenterX = 48 // your desired horizontal center in pixels
                // If vertical positioning is needed, you can also store baseCameraCenterY

                placeOverlayByCameraMask(forceRecalculate = true)
                updateOverlayComponents()
            }
        })

    }

    // --- Phase 4 Topic Handling ---
    private val topics = mutableListOf<TopicPage>()
    private var currentTopicIndex = 0

    private fun setupPhase4Topics() {
        val prefs = getSharedPreferences("fidland_prefs", Context.MODE_PRIVATE)
        topics.clear()
        if (prefs.getBoolean("music_player", true)) topics.add(MusicTopic(this))
        if (prefs.getBoolean("music_queue", true)) topics.add(PlaylistTopic(this))
        if (prefs.getInt("app_rows", 3) > 0 && prefs.getInt("app_columns", 4) > 0) topics.add(AppsTopic(this))
        if (prefs.getBoolean("quick_settings", true)) topics.add(QuickSettingsTopic(this))

        // Initially hide all topic views
        expandedContainer.removeAllViews()
    }

    private fun showCurrentTopic() {
        if (topics.isEmpty()) return
        expandedContainer.removeAllViews()
        expandedContainer.addView(topics[currentTopicIndex].getView())
    }

    // Swipe handling inside expanded container
    private fun handlePhase4Swipe(dx: Float, dy: Float) {
        if (!isExpanded || topics.isEmpty()) return

        when {
            dy < -10 -> collapsePill() // swipe up -> collapse
            dy > 10 -> { // swipe down -> next topic
                currentTopicIndex = (currentTopicIndex + 1) % topics.size
                showCurrentTopic()
            }
            dx > 10 -> topics[currentTopicIndex].onSwipeRight() // swipe right -> page prev
            dx < -10 -> topics[currentTopicIndex].onSwipeLeft() // swipe left -> page next
        }
    }

    // Extend your existing onScroll to forward gestures to Phase 4
    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        if (!isExpanded && distanceY > 10) {
            expandPill()
        } else if (isExpanded) {
            handlePhase4Swipe(-distanceX, -distanceY)
        }
        return true
    }

    // Call this after overlay is ready
    private fun initPhase4() {
        setupPhase4Topics()
        showCurrentTopic()
    }

// In onCreate(), after updateOverlayComponents() or layout listener, call:
    initPhase4()


    override fun onDestroy() {
        super.onDestroy()
        stopSegmentSwitcher()
        netSpeedUpdater?.stop()
        if (::overlayView.isInitialized && overlayView.isAttachedToWindow) windowManager.removeView(overlayView)
        unregisterReceiver(overlayUpdateReceiver)
    }

    // Gesture callbacks
    override fun onDown(e: MotionEvent): Boolean = true
    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent): Boolean = true
    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        if (distanceY > 10 && !isExpanded) expandPill()
        else if (distanceY < -10 && isExpanded) collapsePill()
        return true
    }
    override fun onLongPress(e: MotionEvent) {
        startActivity(Intent(this, com.example.fiddler.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean = false

    private fun expandPill() {
        expandedContainer.visibility = View.VISIBLE
        overlayView.animate().scaleY(1.1f).setDuration(150).withEndAction { overlayView.scaleY = 1f }.start()
        isExpanded = true
    }

    private fun collapsePill() {
        expandedContainer.visibility = View.GONE
        overlayView.animate().scaleY(0.9f).setDuration(150).withEndAction { overlayView.scaleY = 1f }.start()
        isExpanded = false
    }

    private fun startSegmentSwitcher() {
        if (segmentSwitcherRunning) return
        segmentSwitcherRunning = true
        handler.post(object : Runnable {
            override fun run() {
                segmentViews.forEach { it.visibility = View.GONE }
                if (segmentViews.isNotEmpty()) {
                    segmentViews[currentSegmentIndex].visibility = View.VISIBLE
                    currentSegmentIndex = (currentSegmentIndex + 1) % segmentViews.size
                }
                handler.postDelayed(this, 5000)
            }
        })
    }

    private fun stopSegmentSwitcher() {
        segmentSwitcherRunning = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun updateOverlayComponents() {
        val prefs = getSharedPreferences("fidland_prefs", Context.MODE_PRIVATE)
        networkTrafficEnabled = prefs.getBoolean("network_traffic", false)
        eqEnabled = prefs.getBoolean("equalizer_info", false)
        timerEnabled = prefs.getBoolean("timer", false)
        callEnabled = prefs.getBoolean("call", false)
        btEnabled = prefs.getBoolean("bt", false)
        recordEnabled = prefs.getBoolean("record", false)

        toggleNetworkTraffic(networkTrafficEnabled)

        val phase = when {
            networkTrafficEnabled && !(eqEnabled || timerEnabled || callEnabled || btEnabled || recordEnabled) -> 2
            else -> 1
        }

        // Left icons visibility
        timerIcon.visibility = if (timerEnabled) View.VISIBLE else View.GONE
        callIcon.visibility = if (callEnabled) View.VISIBLE else View.GONE
        btIcon.visibility = if (btEnabled) View.VISIBLE else View.GONE
        recordIcon.visibility = if (recordEnabled) View.VISIBLE else View.GONE

        // Right segments visibility
        eqSegment.visibility = if (eqEnabled) View.VISIBLE else View.GONE
        timerSegment.visibility = if (timerEnabled) View.VISIBLE else View.GONE
        callSegment.visibility = if (callEnabled) View.VISIBLE else View.GONE
        btSegment.visibility = if (btEnabled) View.VISIBLE else View.GONE
        recordSegment.visibility = if (recordEnabled) View.VISIBLE else View.GONE

        val rightCount = listOf(eqEnabled, timerEnabled, callEnabled, btEnabled, recordEnabled).count { it }
        animateSegmentWidth(rightSegment, rightCount * 14)

        // Phase 2 left expansion relative to camera mask
        overlayView.post {
            if (phase >= 2 && baseCameraCenterX != null) {
                val density = resources.displayMetrics.density
                val minLeftWidthPx = (50 * density).toInt()
                val pad = (6 * density).toInt()

                if (leftSegment.width < minLeftWidthPx) leftSegment.layoutParams.width = minLeftWidthPx
                leftSegment.setPadding(pad, leftSegment.paddingTop, pad, leftSegment.paddingBottom)

                // Position left segment relative to camera mask center
                val cameraLeftX = baseCameraCenterX!! - cameraMask.width / 2
                leftSegment.x = (cameraLeftX - leftSegment.width).toFloat()

                leftSegment.requestLayout()
            } else {
                leftSegment.layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT
                leftSegment.setPadding(0, leftSegment.paddingTop, 0, leftSegment.paddingBottom)
                leftSegment.requestLayout()
            }

            placeOverlayByCameraMask()
        }
    }

    private fun toggleNetworkTraffic(enabled: Boolean) {
        if (enabled) {
            if (netSpeedUpdater == null) netSpeedUpdater = NetSpeedUpdater(this, txtUpload, txtDownload, leftSegment, overlayView)
            netSpeedUpdater?.start()
        } else {
            netSpeedUpdater?.stop()
        }

        txtUpload.setTextSize(TypedValue.COMPLEX_UNIT_PX, txtUpload.textSize * 0.9f)
        txtDownload.setTextSize(TypedValue.COMPLEX_UNIT_PX, txtDownload.textSize * 0.9f)

        txtUpload.visibility = if (enabled) View.VISIBLE else View.GONE
        txtDownload.visibility = if (enabled) View.VISIBLE else View.GONE

        // Refresh layout to prevent drift
        placeOverlayByCameraMask()
    }

    private fun animateSegmentWidth(segment: View, targetWidthDp: Int, duration: Long = 250) {
        val targetWidthPx = (targetWidthDp * resources.displayMetrics.density).toInt()
        ValueAnimator.ofInt(segment.width, targetWidthPx).apply {
            this.duration = duration
            addUpdateListener {
                segment.layoutParams.width = it.animatedValue as Int
                segment.requestLayout()
            }
            start()
        }
    }

    private fun placeOverlayByCameraMask(forceRecalculate: Boolean = false) {
        overlayView.post {
            baseCameraCenterX?.let { cameraX ->
                val overlayWidth = overlayView.measuredWidth.takeIf { it > 0 } ?: overlayView.width
                val newX = cameraX - overlayWidth / 2
                if (::windowManager.isInitialized && ::params.isInitialized && overlayView.isAttachedToWindow) {
                    params.x = newX
                    params.gravity = Gravity.TOP
                    try { windowManager.updateViewLayout(overlayView, params) } catch (_: Exception) {}
                }
            }
        }
    }
}
