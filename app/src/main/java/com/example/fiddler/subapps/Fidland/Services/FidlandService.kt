package com.example.fiddler.subapps.Fidland.service

import android.app.Service
import android.content.*
import android.os.*
import android.view.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.R
import com.google.accompanist.pager.*
import androidx.core.animation.doOnEnd
import android.animation.ValueAnimator
import android.graphics.PixelFormat
import android.os.Build
import android.content.IntentFilter
import android.view.Gravity
import android.view.WindowManager


class FidlandService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private var isExpanded by mutableStateOf(false)

    private val overlayUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // You can trigger recomposition or update state here
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        composeView = ComposeView(this).apply {
            setContent {
                PillOverlay(
                    isExpanded = isExpanded,
                    onExpand = { expandPill() },
                    onCollapse = { collapsePill() }
                )
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        windowManager.addView(composeView, params)

        val filter = IntentFilter("com.example.fiddler.FIDLAND_UPDATE_OVERLAY")
        registerReceiver(overlayUpdateReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(composeView)
        unregisterReceiver(overlayUpdateReceiver)
    }

    private fun expandPill() {
        if (isExpanded) return
        isExpanded = true
        // Optionally animate height using ValueAnimator if needed
    }

    private fun collapsePill() {
        if (!isExpanded) return
        isExpanded = false
        // Optionally animate height using ValueAnimator if needed
    }
}

@Composable
fun PillOverlay(isExpanded: Boolean, onExpand: () -> Unit, onCollapse: () -> Unit) {
    Box(
        modifier = Modifier
            .wrapContentSize()
            .background(Color.DarkGray)
            .padding(2.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Left segment with icons/text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Upload", fontSize = 9.sp, color = Color.White)
                Text("Download", fontSize = 9.sp, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(4) { Box(modifier = Modifier.size(12.dp).background(Color.Black, CircleShape)) }
                }
            }

            // Center camera mask
            Box(
                modifier = Modifier.size(25.dp).background(Color.Gray, CircleShape)
            )

            // Right segment
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(5) { Box(modifier = Modifier.size(12.dp).background(Color.Black, CircleShape)) }
            }
        }

        if (isExpanded) {
            // Expanded content
            Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                val pagerState = rememberPagerState()
                HorizontalPager(count = 1, state = pagerState, modifier = Modifier.height(200.dp)) { page ->
                    Box(modifier = Modifier.fillMaxSize().background(Color.LightGray), contentAlignment = Alignment.Center) {
                        Text("Page $page")
                    }
                }
            }
        }
    }
}
