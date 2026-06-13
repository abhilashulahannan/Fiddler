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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.R
import com.example.fiddler.core.SubAppState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun NtspdScreen() {
    val context = LocalContext.current
    val fontBody = FontFamily(Font(R.font.font_body))
    val fontHandwriting = FontFamily(Font(R.font.font_handwriting))

    val defaultCenterOffset = -25
    var offset by remember { mutableStateOf(defaultCenterOffset) }
    var placement by remember { mutableStateOf("center") }
    val enableChecked = SubAppState.ntspdEnabled

    val scrollState = rememberScrollState()

    // Update overlay when parameters change
    LaunchedEffect(offset, placement, enableChecked.value) {
        snapshotFlow { Triple(offset, placement, enableChecked.value) }
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

        Text(
            text = "Internet",
            fontFamily = fontBody,
            fontSize = 54.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Configurable elements like Traffic info in status bar.",
            fontFamily = fontHandwriting,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Network Traffic Info",
            fontFamily = fontBody,
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Add configurable traffic info onto your status bar.",
            fontFamily = fontHandwriting,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Enable checkbox
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = SubAppState.ntspdEnabled.value,
                onCheckedChange = { SubAppState.ntspdEnabled.value = it },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
            Text(
                text = "Enable Network Speed Indicator",
                fontFamily = fontHandwriting,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Overlay permission button
        OutlinedButton(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    !Settings.canDrawOverlays(context)
                ) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                } else {
                    Toast.makeText(
                        context,
                        "Overlay permission already granted",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            shape = MaterialTheme.shapes.medium,
            border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                text = "Request Overlay Permission",
                fontFamily = fontHandwriting,
                fontSize = 18.sp,
                color = Color.Black
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Placement in status bar:",
            fontFamily = fontBody,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 5.dp)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = placement == "left",
                onClick = { placement = "left" },
                colors = RadioButtonDefaults.colors(
                    selectedColor = Color.Black,
                    unselectedColor = Color.Black
                )
            )
            Text(
                text = "Left",
                fontFamily = fontHandwriting,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            RadioButton(
                selected = placement == "center",
                onClick = { placement = "center" },
                colors = RadioButtonDefaults.colors(
                    selectedColor = Color.Black,
                    unselectedColor = Color.Black
                )
            )
            Text(
                text = "Center",
                fontFamily = fontHandwriting,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            RadioButton(
                selected = placement == "right",
                onClick = { placement = "right" },
                colors = RadioButtonDefaults.colors(
                    selectedColor = Color.Black,
                    unselectedColor = Color.Black
                )
            )
            Text(
                text = "Right",
                fontFamily = fontHandwriting,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dynamic offset slider
        val valueRange = when (placement) {
            "left" -> 0f..200f
            "center" -> -100f..100f
            "right" -> -200f..0f
            else -> 0f..200f
        }

        LaunchedEffect(placement) {
            val newRange = valueRange
            offset = offset.coerceIn(newRange.start.toInt(), newRange.endInclusive.toInt())
        }

        Text(
            text = "Offset from notch/camera hole:",
            fontFamily = fontBody,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 5.dp)
        )

        Slider(
            value = offset.toFloat().coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { offset = it.toInt() },
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.Black,
                activeTrackColor = Color.DarkGray,
                inactiveTrackColor = Color.LightGray
            )
        )

        Text(
            text = when (placement) {
                "left" -> "Offset: ${offset} dp (Rightward)"
                "center" -> "Offset: ${offset} dp (± from center)"
                "right" -> "Offset: ${offset} dp (Leftward)"
                else -> "Offset: ${offset} dp"
            },
            fontFamily = fontHandwriting,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun updateOverlay(context: android.content.Context, offset: Int, placement: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
        !Settings.canDrawOverlays(context)
    ) {
        Toast.makeText(context, "Overlay permission required", Toast.LENGTH_SHORT).show()
        return
    }

    val intent = Intent(context, NetSpeedService::class.java).apply {
        putExtra("placement", placement)
        putExtra("offset", offset)
    }
    context.startService(intent)
}
