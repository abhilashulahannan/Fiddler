package com.example.fiddler.subapps.Fidland

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.example.fiddler.R
import com.example.fiddler.subapps.Fidland.service.FidlandService

@Composable
fun FidlandScreen(context: Context) {
    val prefsName = "fidland_prefs"
    val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    val editor = prefs.edit()

    var enabled by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
    var networkTraffic by remember { mutableStateOf(prefs.getBoolean("network_traffic", false)) }
    var equalizerInfo by remember { mutableStateOf(prefs.getBoolean("equalizer_info", false)) }
    var notifications by remember { mutableStateOf(prefs.getBoolean("notifications", false)) }
    var musicPlayer by remember { mutableStateOf(prefs.getBoolean("music_player", false)) }
    var musicQueue by remember { mutableStateOf(prefs.getBoolean("music_queue", false)) }
    var quickSettings by remember { mutableStateOf(prefs.getBoolean("quick_settings", false)) }

    var rows by remember { mutableStateOf(prefs.getInt("app_rows", 3).toString()) }
    var columns by remember { mutableStateOf(prefs.getInt("app_columns", 4).toString()) }

    // Launcher apps — persisted as a StringSet of package names
    var launcherApps: Set<String> by remember {
        mutableStateOf(
            prefs.getStringSet("launcher_apps", emptySet())?.toSet() ?: emptySet()
        )
    }

    // Controls visibility of the app picker dialog
    var showAppPicker by remember { mutableStateOf(false) }

    var animationSpeed by remember { mutableStateOf(prefs.getInt("animation_speed", 50).toFloat()) }
    var transparency by remember { mutableStateOf(prefs.getInt("transparency", 80).toFloat()) }
    var cornerRadius by remember { mutableStateOf(prefs.getInt("corner_radius", 40).toFloat()) }

    val scrollState = rememberScrollState()
    val bodyFont = FontFamily(Font(R.font.font_body))
    val handFont = FontFamily(Font(R.font.font_handwriting))

    if (showAppPicker) {
        AppPickerDialog(
            context = context,
            currentlySelected = launcherApps,
            onDismiss = { showAppPicker = false },
            onConfirm = { selected ->
                launcherApps = selected.toSet()
                editor.putStringSet("launcher_apps", selected).apply()
                restartFidlandService(context)
                showAppPicker = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(50.dp))

        Text(
            text = "FidLand",
            fontSize = 48.sp,
            fontFamily = bodyFont,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "My attempt at dynamic island",
            fontSize = 18.sp,
            fontFamily = handFont,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 10.dp)
        )

        FidlandCheckbox("Enable Fidland", enabled) { checked ->
            enabled = checked
            editor.putBoolean("enabled", checked).apply()
            val serviceIntent = Intent(context, FidlandService::class.java)
            if (checked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    !Settings.canDrawOverlays(context)
                ) {
                    Toast.makeText(
                        context,
                        "Please grant overlay permission",
                        Toast.LENGTH_SHORT
                    ).show()
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    enabled = false
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                context.stopService(serviceIntent)
            }
        }

        Spacer(modifier = Modifier.height(15.dp))

        // --- Status bar section ---
        SectionTitle("All Things Status Bar", bodyFont)
        SectionSubtitle(
            "Dynamic configurations for all status bar elements — network, equalizer, and indicators.",
            handFont
        )

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

        Spacer(modifier = Modifier.height(15.dp))

        // --- Music section ---
        SectionTitle("Music App Integration", bodyFont)
        SectionSubtitle(
            "Integrate Spotify, YouTube Music, etc. Quick player actions and queue access.",
            handFont
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

        Spacer(modifier = Modifier.height(15.dp))

        // --- Appearance sliders ---
        SliderItem(
            title = "Animation Speed",
            value = animationSpeed,
            range = 0f..100f,
            onChange = {
                animationSpeed = it
                editor.putInt("animation_speed", it.toInt()).apply()
                restartFidlandService(context)
            },
            font = handFont
        )

        SliderItem(
            title = "Transparency",
            value = transparency,
            range = 0f..100f,
            onChange = {
                transparency = it
                editor.putInt("transparency", it.toInt()).apply()
                restartFidlandService(context)
            },
            font = handFont
        )

        SliderItem(
            title = "Corner Radius",
            value = cornerRadius,
            range = 0f..100f,
            onChange = {
                cornerRadius = it
                editor.putInt("corner_radius", it.toInt()).apply()
                restartFidlandService(context)
            },
            font = handFont
        )

        Spacer(modifier = Modifier.height(28.dp))

        // --- Quick settings ---
        FidlandCheckbox("Quick Settings Module", quickSettings) {
            quickSettings = it
            editor.putBoolean("quick_settings", it).apply()
            restartFidlandService(context)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- App launcher config ---
        SectionTitle("App Launcher", bodyFont)
        SectionSubtitle(
            "Configure the app grid shown in the dashboard launcher page.",
            handFont
        )

        FidlandTextField("Rows", rows, handFont) {
            rows = it
            editor.putInt("app_rows", it.toIntOrNull() ?: 3).apply()
            restartFidlandService(context)
        }

        FidlandTextField("Columns", columns, handFont) {
            columns = it
            editor.putInt("app_columns", it.toIntOrNull() ?: 4).apply()
            restartFidlandService(context)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Selected apps summary
        Text(
            text = "${launcherApps.size} app${if (launcherApps.size == 1) "" else "s"} selected",
            fontSize = 14.sp,
            fontFamily = handFont,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Button(
            onClick = { showAppPicker = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "Choose Apps",
                fontFamily = handFont,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}

// ---------------------------------------------------------------------------
// App picker dialog
// ---------------------------------------------------------------------------

@Composable
private fun AppPickerDialog(
    context: Context,
    currentlySelected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    // Load installed apps once
    val installedApps = remember {
        context.packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { context.packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { context.packageManager.getApplicationLabel(it).toString().lowercase() }
    }

    var selected: Set<String> by remember { mutableStateOf(currentlySelected.toSet()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Select Apps",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(installedApps) { appInfo ->
                        val pkg = appInfo.packageName
                        val label = context.packageManager
                            .getApplicationLabel(appInfo).toString()
                        val icon = context.packageManager.getApplicationIcon(appInfo)
                        val isChecked = pkg in selected

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (isChecked) {
                                        selected - pkg
                                    } else {
                                        selected + pkg
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = icon.toBitmap(36, 36).asImageBitmap(),
                                contentDescription = label,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = {
                                    selected = if (it) {
                                        selected + pkg
                                    } else {
                                        selected - pkg
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onConfirm(selected) }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Reusable components — unchanged from original
// ---------------------------------------------------------------------------

@Composable
fun FidlandCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(
            text = label,
            fontSize = 18.sp,
            fontFamily = FontFamily(Font(R.font.font_handwriting)),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun SectionTitle(text: String, font: FontFamily) {
    Text(
        text = text,
        fontSize = 26.sp,
        fontFamily = font,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
fun SectionSubtitle(text: String, font: FontFamily) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontFamily = font,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
fun SliderItem(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    font: FontFamily
) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${value.toInt()}",
                fontSize = 18.sp,
                fontFamily = font,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = { onChange(it) },
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.Black,
                activeTrackColor = Color.DarkGray,
                inactiveTrackColor = Color.LightGray
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun FidlandTextField(
    label: String,
    value: String,
    font: FontFamily,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = label,
            fontSize = 18.sp,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurface
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = font)
        )
    }
}

fun restartFidlandService(context: Context) {
    val intent = Intent(context, FidlandService::class.java)
    context.stopService(intent)
    context.startService(intent)
}