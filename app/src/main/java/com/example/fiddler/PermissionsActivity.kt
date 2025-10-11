package com.example.fiddler

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile

class PermissionsActivity : ComponentActivity() {

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("fiddler_prefs", MODE_PRIVATE)
    }

    private val safLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                prefs.edit().putString("saf_uri", uri.toString()).apply()
                setupFoldersAndPlaceholder()
                checkWriteSettingsPermission()
            } else {
                Toast.makeText(this, "Folder selection required to continue.", Toast.LENGTH_LONG).show()
                promptSAFExplanation()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        setContent {
            PermissionScreen {
                // Button click triggers SAF or next step
                launchSAF()
            }
        }
    }

    @Composable
    fun PermissionScreen(onGrantClick: () -> Unit) {
        val fontBody = FontFamily(Font(R.font.font_body))
        val fontHead = FontFamily(Font(R.font.font_head))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Permission", fontSize = 54.sp, color = Color.Black, fontFamily = fontBody)
            Text("and", fontSize = 54.sp, color = Color.Black, fontFamily = fontBody)
            Text("Directories", fontSize = 54.sp, color = Color.Black, fontFamily = fontBody)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Fiddler needs some permissions to run smoothly. It will create directories at required locations now, to mitigate annoyance later.",
                fontSize = 18.sp,
                color = Color.Black,
                fontFamily = fontBody,
                modifier = Modifier.padding(16.dp),
            )

            Spacer(modifier = Modifier.height(50.dp))

            Button(
                onClick = { onGrantClick() },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.Black) // <-- fixed
            ) {
                Text("Grant Permissions", fontSize = 16.sp, fontFamily = fontHead)
            }
        }
    }


    private fun checkAndRequestPermissions() {
        val requiredPermissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            requiredPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            requiredPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            requestBatteryOptimizationExemption()
        } else {
            requestPermissions(missingPermissions.toTypedArray(), 100)
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } else {
            checkSAFSetup()
        }
    }

    // Added checkSAFSetup to fix unresolved reference
    private fun checkSAFSetup() {
        val uriString = prefs.getString("saf_uri", null)
        if (uriString.isNullOrEmpty()) {
            promptSAFExplanation()
        } else {
            setupFoldersAndPlaceholder()
            checkWriteSettingsPermission()
        }
    }

    private fun promptSAFExplanation() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Setup Audio Folder")
            .setMessage(
                "For various app functions and ease of use, the app needs access to a folder in DCIM called 'Fiddler'. " +
                        "This is a one-time setup using the system file picker. After you select the folder, the app can only read/write files in that folder safely."
            )
            .setCancelable(false)
            .setPositiveButton("Continue") { _, _ -> launchSAF() }
            .setNegativeButton("Cancel") { d, _ ->
                d.dismiss()
                Toast.makeText(this, "App will not function without folder access.", Toast.LENGTH_LONG).show()
            }
            .create()
        dialog.show()
    }

    private fun launchSAF() {
        safLauncher.launch(null)
    }

    private fun setupFoldersAndPlaceholder() {
        try {
            val uriString = prefs.getString("saf_uri", null) ?: return
            val treeUri = Uri.parse(uriString)
            val rootDoc = DocumentFile.fromTreeUri(this, treeUri) ?: return
            val audioFolder = rootDoc.findFile("Audio") ?: rootDoc.createDirectory("Audio")
            audioFolder?.findFile("Fidtones") ?: audioFolder?.createDirectory("Fidtones")
            audioFolder?.findFile("fiddler_ringtone.ogg")
                ?: audioFolder?.createFile("audio/ogg", "fiddler_ringtone.ogg")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkWriteSettingsPermission() {
        if (!Settings.System.canWrite(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } else {
            checkOverlayPermission()
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        } else {
            checkNotificationListenerPermission()
        }
    }

    private fun checkNotificationListenerPermission() {
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        val hasPermission = enabledListeners.split(":").any { it.contains(packageName) }
        if (!hasPermission) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } else {
            proceedToMain()
        }
    }

    private fun proceedToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
