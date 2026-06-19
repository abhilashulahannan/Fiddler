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

@Composable
fun NtspdScreen() {
    val context = LocalContext.current
    val fontBody = FontFamily(Font(R.font.font_body))
    val fontHandwriting = FontFamily(Font(R.font.font_handwriting))

    val prefs = remember {
        context.getSharedPreferences("fiddler_prefs", android.content.Context.MODE_PRIVATE)
    }

    // Restore persisted settings
    var placement by remember { mutableStateOf(prefs.getString("ntspd_placement", "center") ?: "center") }
    var offset by remember { mutableIntStateOf(prefs.getInt("ntspd_offset", -25)) }

    val enableChecked = SubAppState.ntspdEnabled

    val valueRange = when (placement) {
        "left"  -> 0f..200f
        "center" -> -100f..100f
        else    -> -200f..0f
    }

    // Clamp offset when placement changes
    LaunchedEffect(placement) {
        offset = offset.coerceIn(valueRange.start.toInt(), valueRange.endInclusive.toInt())
    }

    // Start/stop service and push settings changes
    LaunchedEffect(enableChecked.value, offset, placement) {
        if (enableChecked.value) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                Toast.makeText(context, "Overlay permission required", Toast.LENGTH_SHORT).show()
                SubAppState.ntspdEnabled.value = false
                return@LaunchedEffect
            }
            val intent = Intent(context, NetSpeedService::class.java).apply {
                putExtra("placement", placement)
                putExtra("offset", offset)
            }
            context.startService(intent)
        } else {
            context.stopService(Intent(context, NetSpeedService::class.java))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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

        // Enable toggle
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = enableChecked.value,
                onCheckedChange = { enableChecked.value = it },
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

        // Overlay permission button — only shown when permission is missing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                },
                shape = MaterialTheme.shapes.medium,
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
            ) {
                Text(
                    text = "Grant Overlay Permission",
                    fontFamily = fontHandwriting,
                    fontSize = 18.sp,
                    color = Color.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Placement in status bar:",
            fontFamily = fontBody,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf("left" to "Left", "center" to "Center", "right" to "Right").forEach { (value, label) ->
                RadioButton(
                    selected = placement == value,
                    onClick = {
                        placement = value
                        prefs.edit().putString("ntspd_placement", value).apply()
                    },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = Color.Black,
                        unselectedColor = Color.Black
                    )
                )
                Text(
                    text = label,
                    fontFamily = fontHandwriting,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Offset from notch/camera hole:",
            fontFamily = fontBody,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Slider(
            value = offset.toFloat(),
            onValueChange = {
                offset = it.toInt()
                prefs.edit().putInt("ntspd_offset", offset).apply()
            },
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.Black,
                activeTrackColor = Color.DarkGray,
                inactiveTrackColor = Color.LightGray
            )
        )

        Text(
            text = when (placement) {
                "left"  -> "Offset: $offset dp (Rightward)"
                "center" -> "Offset: $offset dp (± from center)"
                else    -> "Offset: $offset dp (Leftward)"
            },
            fontFamily = fontHandwriting,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}