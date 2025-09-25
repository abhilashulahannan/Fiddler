package com.yourdomain.yourapp

import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import com.yourdomain.yourapp.subapps.app1.App1Activity
import com.yourdomain.yourapp.subapps.app2.App2Activity
import com.yourdomain.yourapp.subapps.app3.App3Activity

class MainActivity : AppCompatActivity() {

    private lateinit var sidebar: LinearLayout
    private var isExpanded = false
    private val collapsedWidth = 80    // dp
    private val expandedWidth = 240    // dp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sidebar = findViewById(R.id.sidebar_container)

        setupSidebarIcons()

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
                if (e1 != null && e2 != null && e1.x - e2.x > 100) {
                    toggleSidebar()
                    return true
                }
                return false
            }
        })

        findViewById<ViewGroup>(R.id.activity_container).setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun toggleSidebar() {
        val newWidth = if (isExpanded) collapsedWidth else expandedWidth
        sidebar.updateLayoutParams {
            width = (newWidth * resources.displayMetrics.density).toInt()
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
            val icon = ImageView(this).apply {
                setImageResource(iconRes)
                contentDescription = label
                setPadding(16, 16, 16, 16)
                setOnClickListener {
                    startActivity(Intent(this@MainActivity, activityClass))
                }
            }
            sidebar.addView(icon)
        }
    }
}
