package com.example.fiddler.subapps.ntspd

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.R

@Composable
fun NtspdScreen() {
    val context = LocalContext.current
    val maxOffsetDp = 100
    val defaultCenterOffset = -25

    var enableChecked by remember { mutableStateOf(false) }
    var placement by remember { mutableStateOf("center") }
    var offset by remember { mutableStateOf(maxOffsetDp + defaultCenterOffset) }

    val scrollState = rememberScrollState()

    fun updateOverlay(offsetValue: Int, placementValue: String) {
        if (!enableChecked) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(context)
        ) {
            Toast.makeText(context, "Overlay permission required", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(context, NetSpeedService::class.java).apply {
            putExtra("placement", placementValue)
            putExtra("offset", offsetValue)
        }
        context.startService(intent)
    }

    fun adjustOffsetForPlacement(newPlacement: String, progress: Int) {
        val newOffset = when (newPlacement) {
            "left" -> progress
            "center" -> progress - maxOffsetDp
            "right" -> maxOffsetDp - progress
            else -> progress
        }
        offset = newOffset
        updateOverlay(offset, newPlacement)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        Text("Internet", fontSize = 54.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Configurable elements like Traffic info in status bar.",
            fontSize = 20.sp
        )

        Spacer(modifier = Modifier.height(32.dp))
        Text("Network Traffic Info", fontSize = 28.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Add configurable traffic info onto your status bar.",
            fontSize = 20.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = enableChecked,
                onCheckedChange = {
                    enableChecked = it
                    adjustOffsetForPlacement(placement, offset)
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Enable Network Speed Indicator", fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !Settings.canDrawOverlays(context)
            ) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text("Request Overlay Permission", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Placement in status bar:", fontSize = 22.sp)
        Row {
            RadioButton(selected = placement == "left", onClick = { placement = "left"; adjustOffsetForPlacement("left", 0) })
            Text("Left", fontSize = 20.sp, modifier = Modifier.padding(start = 4.dp, end = 8.dp))
            RadioButton(selected = placement == "center", onClick = { placement = "center"; adjustOffsetForPlacement("center", maxOffsetDp + defaultCenterOffset) })
            Text("Center", fontSize = 20.sp, modifier = Modifier.padding(start = 4.dp, end = 8.dp))
            RadioButton(selected = placement == "right", onClick = { placement = "right"; adjustOffsetForPlacement("right", 0) })
            Text("Right", fontSize = 20.sp, modifier = Modifier.padding(start = 4.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Offset from notch/camera hole:", fontSize = 22.sp)

        Slider(
            value = offset.toFloat(),
            onValueChange = {
                offset = it.toInt()
                adjustOffsetForPlacement(placement, offset)
            },
            valueRange = 0f..(maxOffsetDp * 2).toFloat()
        )

        Text("Offset: $offset dp", fontSize = 18.sp)
    }
}
