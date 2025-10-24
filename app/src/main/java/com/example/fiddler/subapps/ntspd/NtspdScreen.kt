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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun NtspdScreen() {
    val context = LocalContext.current
    val maxOffsetDp = 100
    val defaultCenterOffset = -25

    var enableChecked by remember { mutableStateOf(false) }
    var placement by remember { mutableStateOf("center") }
    var offset by remember { mutableStateOf(maxOffsetDp + defaultCenterOffset) }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Observe changes to offset or placement using snapshotFlow
    LaunchedEffect(offset, placement, enableChecked) {
        snapshotFlow { Triple(offset, placement, enableChecked) }
            .distinctUntilChanged()
            .collectLatest { (newOffset, newPlacement, enabled) ->
                if (enabled) updateOverlay(context, newOffset, newPlacement)
            }
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
        Text("Configurable elements like Traffic info in status bar.", fontSize = 20.sp)

        Spacer(modifier = Modifier.height(32.dp))
        Text("Network Traffic Info", fontSize = 28.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Add configurable traffic info onto your status bar.", fontSize = 20.sp)

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = enableChecked,
                onCheckedChange = { enableChecked = it }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = placement == "left", onClick = { placement = "left" })
            Text("Left", fontSize = 20.sp, modifier = Modifier.padding(start = 4.dp, end = 8.dp))
            RadioButton(selected = placement == "center", onClick = { placement = "center" })
            Text("Center", fontSize = 20.sp, modifier = Modifier.padding(start = 4.dp, end = 8.dp))
            RadioButton(selected = placement == "right", onClick = { placement = "right" })
            Text("Right", fontSize = 20.sp, modifier = Modifier.padding(start = 4.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Offset from notch/camera hole:", fontSize = 22.sp)

        Slider(
            value = offset.toFloat(),
            onValueChange = { offset = it.toInt() },
            valueRange = 0f..(maxOffsetDp * 2).toFloat()
        )

        Text("Offset: $offset dp", fontSize = 18.sp)
    }
}

// Function to update overlay service
private fun updateOverlay(context: android.content.Context, offset: Int, placement: String) {
    val intent = Intent(context, NetSpeedService::class.java).apply {
        putExtra("placement", placement)
        putExtra("offset", offset)
    }
    context.startService(intent)
}
