package com.yourdomain.yourapp

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import com.yourdomain.yourapp.subapps.app1.App1Activity
import com.yourdomain.yourapp.subapps.app2.App2Activity
import com.yourdomain.yourapp.subapps.app3.App3Activity

class MainActivity : AppCompatActivity() {

    private lateinit var sidebar: LinearLayout
    private lateinit var activityContainer: FrameLayout
    private var isExpanded = false
    private val collapsedWidthDp = 80
    private val expandedWidthDp = 240

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sidebar = findViewById(R.id.sidebar_container)
        activityContainer = findViewById(R.id.activity_container)

        setupSidebarIcons()
        setupGestureDetection()
    }

    private fun setupGestureDetection() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
                val density = resources.displayMetrics.density
                if (e1 != null && e2 != null && e1.x - e2.x > 100 * density) {
                    toggleSidebar()
                    return true
                }
                return false
            }
        })

        activityContainer.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    private fun toggleSidebar() {
        val startWidth = sidebar.width
        val targetWidth = if (isExpanded) (collapsedWidthDp * resources.displayMetrics.density).toInt()
        else (expandedWidthDp * resources.displayMetrics.density).toInt()

        ValueAnimator.ofInt(startWidth, targetWidth).apply {
            duration = 300
            addUpdateListener { valueAnimator ->
                sidebar.layoutParams.width = valueAnimator.animatedValue as Int
                sidebar.requestLayout()
            }
            start()
        }
        isExpanded = !isExpanded
    }

    private fun setupSidebarIcons() {
        val apps = listOf(
            Triple("App 1", R.drawable.ic_app1, App1Activity::class.java),
            Triple("App 2", R.drawable.ic_app2, App2Activity::class.java),
            Triple("App 3", R.drawable.ic_app3, App3Activity::class.java)
        )

        apps.forEach { (label, iconRes, activityClass) ->
            val iconContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(8, 8, 8, 8)
                background = getDrawable(R.drawable.sketchy_button)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(8, 8, 8, 8)
                }
                setOnClickListener {
                    startActivity(Intent(this@MainActivity, activityClass))
                }
            }

            val icon = ImageView(this).apply {
                setImageResource(iconRes)
                contentDescription = label
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    80
                )
            }

            iconContainer.addView(icon)
            sidebar.addView(iconContainer)
        }
    }
}
