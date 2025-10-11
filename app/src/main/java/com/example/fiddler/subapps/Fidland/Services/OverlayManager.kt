package com.example.fiddler.subapps.Fidland.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.view.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*

class OverlayState(
    context: Context
) {
    var isExpanded by mutableStateOf(false)
    var rightSegmentVisibility by mutableStateOf(listOf(false, false, false, false, false))
    var networkTrafficEnabled by mutableStateOf(false)
}

class OverlayManagerCompose(
    private val context: Context,
    private val composeView: ComposeView,
    private val overlayState: OverlayState
) {
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun addToWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            windowManager.addView(composeView, params)
        }
    }

    fun removeFromWindow() {
        if (composeView.isAttachedToWindow) windowManager.removeView(composeView)
    }

    fun registerReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
        context.registerReceiver(receiver, filter)
    }

    fun cleanupReceiver(receiver: BroadcastReceiver) {
        context.unregisterReceiver(receiver)
    }

    /** Update state based on preferences (replaces old visibility logic) */
    fun updateOverlayComponents() {
        val prefs = context.getSharedPreferences("fidland_prefs", Context.MODE_PRIVATE)
        overlayState.rightSegmentVisibility = listOf(
            prefs.getBoolean("equalizer_info", false),
            prefs.getBoolean("timer", false),
            prefs.getBoolean("call", false),
            prefs.getBoolean("bt", false),
            prefs.getBoolean("record", false)
        )
        overlayState.networkTrafficEnabled = prefs.getBoolean("network_traffic", false)
    }
}

@Composable
fun OverlayContent(
    overlayState: OverlayState
) {
    Box(
        modifier = androidx.compose.ui.Modifier
            .wrapContentSize()
            .background(Color.DarkGray)
            .padding(2.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Left Segment Placeholder (can add network traffic, icons, etc.)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (overlayState.networkTrafficEnabled) {
                    Text("↑ 50 KB/s", fontSize = 9.dp.value.sp, color = Color.White)
                    Text("↓ 100 KB/s", fontSize = 9.dp.value.sp, color = Color.White)
                }
            }

            // Center camera mask
            Box(
                modifier = androidx.compose.ui.Modifier
                    .size(25.dp)
                    .background(Color.Gray, CircleShape)
            )

            // Right Segment
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                overlayState.rightSegmentVisibility.forEach { visible ->
                    if (visible) {
                        Box(
                            modifier = androidx.compose.ui.Modifier
                                .size(12.dp)
                                .background(Color.Black, CircleShape)
                        )
                    }
                }
            }
        }

        // Expanded content
        if (overlayState.isExpanded) {
            Column(modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(top = 6.dp)) {
                // Compose pagers for Music/Apps/Playlist/QuickSettings
            }
        }
    }
}
