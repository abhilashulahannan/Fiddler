package com.example.fiddler.subapps.Fidland

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.R
import com.example.fiddler.subapps.Fidland.service.FidlandService

@Composable
fun FidlandScreen(context: Context) {
    val prefsName = "fidland_prefs"
    val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    val editor = prefs.edit()

    // States
    var enabled by remember { mutableStateOf(prefs.getBoolean("enabled", true)) }
    var networkTraffic by remember { mutableStateOf(prefs.getBoolean("network_traffic", true)) }
    var equalizerInfo by remember { mutableStateOf(prefs.getBoolean("equalizer_info", true)) }
    var notifications by remember { mutableStateOf(prefs.getBoolean("notifications", true)) }
    var musicPlayer by remember { mutableStateOf(prefs.getBoolean("music_player", true)) }
    var musicQueue by remember { mutableStateOf(prefs.getBoolean("music_queue", true)) }
    var quickSettings by remember { mutableStateOf(prefs.getBoolean("quick_settings", true)) }

    var rows by remember { mutableStateOf(prefs.getInt("app_rows", 3).toString()) }
    var columns by remember { mutableStateOf(prefs.getInt("app_columns", 4).toString()) }

    var animationSpeed by remember { mutableStateOf(prefs.getInt("animation_speed", 50).toFloat()) }
    var transparency by remember { mutableStateOf(prefs.getInt("transparency", 80).toFloat()) }
    var cornerRadius by remember { mutableStateOf(prefs.getInt("corner_radius", 40).toFloat()) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        Text(
            text = "FidLand",
            fontSize = 54.sp,
            fontFamily = FontFamily(Font(R.font.font_body)),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "My attempt at dynamic island.",
            fontSize = 20.sp,
            fontFamily = FontFamily(Font(R.font.font_handwriting)),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Enable Fidland
        FidlandCheckbox("Enable Fidland", enabled) { checked ->
            enabled = checked
            editor.putBoolean("enabled", checked).apply()
            val serviceIntent = Intent(context, FidlandService::class.java)
            if (checked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                    Toast.makeText(context, "Please grant overlay permission", Toast.LENGTH_SHORT).show()
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    enabled = false
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                context.stopService(serviceIntent)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "All things status bar",
            fontSize = 28.sp,
            fontFamily = FontFamily(Font(R.font.font_body)),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Dynamic configurations for all status bar elements — network, equalizer, and indicators.",
            fontSize = 20.sp,
            fontFamily = FontFamily(Font(R.font.font_handwriting)),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        FidlandCheckbox("Network Traffic Module", networkTraffic) {
            networkTraffic = it
            editor.putBoolean("network_traffic", it).apply()
            restartFidlandService(context)
        }

        FidlandCheckbox("Equalizer / Media Info", equalizerInfo) {
            equalizerInfo = it
            editor.putBoolean("equalizer_info", it).apply()
            restartFidlandService(context)
        }

        FidlandCheckbox("Notifications Display", notifications) {
            notifications = it
            editor.putBoolean("notifications", it).apply()
            restartFidlandService(context)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Music App Integration",
            fontSize = 28.sp,
            fontFamily = FontFamily(Font(R.font.font_body)),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Integrate media apps like Spotify or YouTube Music. Quick player actions, seek bar, and queue access.",
            fontSize = 20.sp,
            fontFamily = FontFamily(Font(R.font.font_handwriting)),
            color = MaterialTheme.colorScheme.onSurface
        )

        FidlandCheckbox("Enable Music Player Controls", musicPlayer) {
            musicPlayer = it
            editor.putBoolean("music_player", it).apply()
            restartFidlandService(context)
        }

        FidlandCheckbox("Enable Music Queue Page", musicQueue) {
            musicQueue = it
            editor.putBoolean("music_queue", it).apply()
            restartFidlandService(context)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("Animation Speed", fontFamily = FontFamily(Font(R.font.font_handwriting)), color = MaterialTheme.colorScheme.onSurface)
        Slider(
            value = animationSpeed,
            onValueChange = {
                animationSpeed = it
                editor.putInt("animation_speed", it.toInt()).apply()
                restartFidlandService(context)
            },
            valueRange = 0f..100f
        )

        Text("Transparency", fontFamily = FontFamily(Font(R.font.font_handwriting)), color = MaterialTheme.colorScheme.onSurface)
        Slider(
            value = transparency,
            onValueChange = {
                transparency = it
                editor.putInt("transparency", it.toInt()).apply()
                restartFidlandService(context)
            },
            valueRange = 0f..100f
        )

        Text("Corner Radius", fontFamily = FontFamily(Font(R.font.font_handwriting)), color = MaterialTheme.colorScheme.onSurface)
        Slider(
            value = cornerRadius,
            onValueChange = {
                cornerRadius = it
                editor.putInt("corner_radius", it.toInt()).apply()
                restartFidlandService(context)
            },
            valueRange = 0f..100f
        )

        Spacer(modifier = Modifier.height(32.dp))

        FidlandCheckbox("Quick Settings Module", quickSettings) {
            quickSettings = it
            editor.putBoolean("quick_settings", it).apply()
            restartFidlandService(context)
        }

        Spacer(modifier = Modifier.height(16.dp))

        FidlandTextField("Rows", rows) {
            rows = it
            editor.putInt("app_rows", it.toIntOrNull() ?: 3).apply()
            restartFidlandService(context)
        }

        FidlandTextField("Columns", columns) {
            columns = it
            editor.putInt("app_columns", it.toIntOrNull() ?: 4).apply()
            restartFidlandService(context)
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}

@Composable
fun FidlandCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(
            text = label,
            fontSize = 20.sp,
            fontFamily = FontFamily(Font(R.font.font_handwriting)),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun FidlandTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, fontFamily = FontFamily(Font(R.font.font_handwriting)), color = MaterialTheme.colorScheme.onSurface)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily(Font(R.font.font_handwriting)))
        )
    }
}

fun restartFidlandService(context: Context) {
    val serviceIntent = Intent(context, FidlandService::class.java)
    context.stopService(serviceIntent)
    context.startService(serviceIntent)
}
