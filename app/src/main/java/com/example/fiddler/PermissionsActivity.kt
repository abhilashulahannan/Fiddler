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

    // ── SAF launcher ──────────────────────────────────────────────────────────
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

    // ── Runtime permission launcher ───────────────────────────────────────────
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            // Inform the user about each denied permission and what feature it affects
            val deniedMessages = buildDeniedMessages(results)
            if (deniedMessages.isNotEmpty()) {
                Toast.makeText(
                    this,
                    deniedMessages.joinToString("\n"),
                    Toast.LENGTH_LONG
                ).show()
            }
            // Always proceed regardless of what was denied
            requestBatteryOptimizationExemption()
        }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If everything is already set up from a previous install, go straight to main
        if (isFullySetUp()) {
            proceedToMain()
            return
        }

        setContent {
            PermissionScreen(onGrantClick = { startPermissionChain() })
        }
    }

    // ── Full setup check ──────────────────────────────────────────────────────
    private fun isFullySetUp(): Boolean {
        // All runtime permissions granted
        val allGranted = buildPermissionList().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        // SAF folder selected
        val safReady = !prefs.getString("saf_uri", null).isNullOrEmpty()
        // System-level permissions
        val writeSettings = Settings.System.canWrite(this)
        val overlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(this) else true
        val notifListener = hasNotificationListenerPermission()
        val accessibilityEnabled = hasFidlandAccessibilityPermission()

        return allGranted && safReady && writeSettings && overlay && notifListener && accessibilityEnabled
    }

    // ── Permission chain entry point (called on button tap) ───────────────────
    private fun startPermissionChain() {
        val missing = buildPermissionList().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            requestBatteryOptimizationExemption()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    // ── Permission list builder ───────────────────────────────────────────────
    private fun buildPermissionList(): List<String> {
        val list = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,   // ← add this
            Manifest.permission.ACCESS_FINE_LOCATION,
            // Requested alongside FINE so the system's "Precise vs
            // Approximate" location dialog (Android 12+) is shown correctly.
            // FINE alone still auto-grants COARSE, but requesting both is the
            // documented/expected pattern and matches the manifest, which
            // already declares COARSE.
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            // Needed by CellularCommsSource to read data network type (2G/3G/4G/5G).
            // Without this, generation reads UNKNOWN on many OEM builds — see
            // CellularCommsSource kdoc for the per-vendor inconsistency.
            Manifest.permission.READ_PHONE_STATE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list += listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.READ_MEDIA_AUDIO
        } else {
            list += listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            )
        }
        return list
    }

    // ── Denied permission messages ────────────────────────────────────────────
    private fun buildDeniedMessages(results: Map<String, Boolean>): List<String> {
        val messages = mutableListOf<String>()
        results.forEach { (permission, granted) ->
            if (!granted) {
                val msg = when (permission) {
                    Manifest.permission.CAMERA ->
                        "Camera denied — camera features will be unavailable."
                    Manifest.permission.READ_CONTACTS ->
                        "Contacts denied — contact-based features will be unavailable."
                    Manifest.permission.CALL_PHONE ->
                        "Call Phone denied — in-app calling will be unavailable."
                    Manifest.permission.READ_CALL_LOG ->
                        "Call log denied — missed calls and caller names won't be shown."
                    Manifest.permission.ACCESS_FINE_LOCATION ->
                        "Location denied — location-based features will be unavailable."
                    Manifest.permission.RECORD_AUDIO ->
                        "Microphone denied — audio recording will be unavailable."
                    Manifest.permission.READ_PHONE_STATE ->
                        "Phone state denied — cellular generation (4G/5G) won't be shown, signal bars still will."
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE ->
                        "Bluetooth denied — Bluetooth features will be unavailable."
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_EXTERNAL_STORAGE ->
                        "Storage read denied — audio files may not load."
                    Manifest.permission.WRITE_EXTERNAL_STORAGE ->
                        "Storage write denied — saving files may not work."
                    else -> null
                }
                msg?.let { messages += it }
            }
        }
        return messages
    }

    // ── Battery optimisation ──────────────────────────────────────────────────
    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
            // Continue chain after the user returns — handled in onResume
        } else {
            checkSAFSetup()
        }
    }

    // ── onResume picks up after battery/system dialogs return ─────────────────
    override fun onResume() {
        super.onResume()
        // Only auto-advance if the user has already tapped Grant Permissions
        // (i.e. the permission chain is in progress, not the initial screen)
        if (prefs.getBoolean("permissions_chain_started", false)) {
            advanceChain()
        }
    }

    private fun advanceChain() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        when {
            !pm.isIgnoringBatteryOptimizations(packageName) -> {
                // Still not granted — just move on
                checkSAFSetup()
            }
            prefs.getString("saf_uri", null).isNullOrEmpty() -> checkSAFSetup()
            !Settings.System.canWrite(this) -> checkWriteSettingsPermission()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this) ->
                checkOverlayPermission()
            !hasNotificationListenerPermission() -> checkNotificationListenerPermission()
            !hasFidlandAccessibilityPermission() -> checkFidlandAccessibilityPermission()
            else -> proceedToMain()
        }
    }

    // ── SAF setup ────────────────────────────────────────────────────────────
    private fun checkSAFSetup() {
        prefs.edit().putBoolean("permissions_chain_started", true).apply()
        if (prefs.getString("saf_uri", null).isNullOrEmpty()) {
            promptSAFExplanation()
        } else {
            setupFoldersAndPlaceholder()
            checkWriteSettingsPermission()
        }
    }

    private fun promptSAFExplanation() {
        AlertDialog.Builder(this)
            .setTitle("Setup Audio Folder")
            .setMessage(
                "For various app functions and ease of use, the app needs access to a folder " +
                        "in DCIM called 'Fiddler'. This is a one-time setup using the system file picker. " +
                        "After you select the folder, the app can only read/write files in that folder safely."
            )
            .setCancelable(false)
            .setPositiveButton("Continue") { _, _ -> safLauncher.launch(null) }
            .setNegativeButton("Cancel") { d, _ ->
                d.dismiss()
                Toast.makeText(this, "App will not function without folder access.", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private fun setupFoldersAndPlaceholder() {
        try {
            val uriString = prefs.getString("saf_uri", null) ?: return
            val rootDoc = DocumentFile.fromTreeUri(this, Uri.parse(uriString)) ?: return
            val audioFolder = rootDoc.findFile("Audio") ?: rootDoc.createDirectory("Audio")
            audioFolder?.findFile("Fidtones") ?: audioFolder?.createDirectory("Fidtones")
            audioFolder?.findFile("fiddler_ringtone.ogg")
                ?: audioFolder?.createFile("audio/ogg", "fiddler_ringtone.ogg")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ── Write settings ────────────────────────────────────────────────────────
    private fun checkWriteSettingsPermission() {
        if (!Settings.System.canWrite(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } else {
            checkOverlayPermission()
        }
    }

    // ── Overlay ───────────────────────────────────────────────────────────────
    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            checkNotificationListenerPermission()
        }
    }

    // ── Notification listener ─────────────────────────────────────────────────
    private fun checkNotificationListenerPermission() {
        if (!hasNotificationListenerPermission()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } else {
            checkFidlandAccessibilityPermission()
        }
    }

    private fun hasNotificationListenerPermission(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return enabled.split(":").any { it.contains(packageName) }
    }

    // ── Fidland accessibility service ─────────────────────────────────────────
    // Grants TYPE_ACCESSIBILITY_OVERLAY so the Fidland pill can render above
    // the status bar's SystemUI icon layer (clock/battery/signal). This
    // service does not read or act on screen content — see
    // FidlandAccessibilityService.kt. There's no permission dialog for this;
    // the user must flip it on manually in system settings, same pattern as
    // the notification listener above.
    private fun checkFidlandAccessibilityPermission() {
        if (!hasFidlandAccessibilityPermission()) {
            AlertDialog.Builder(this)
                .setTitle("Enable Fidland Overlay Service")
                .setMessage(
                    "To draw the Fidland pill above the status bar icons, enable " +
                            "the \"Fidland Overlay\" accessibility service. This service " +
                            "does not read your screen content — it only grants the pill " +
                            "permission to draw in that area."
                )
                .setCancelable(false)
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Skip") { d, _ ->
                    d.dismiss()
                    // Non-fatal: pill still works, just sits under the status
                    // bar icons without this, so we let the user continue.
                    proceedToMain()
                }
                .show()
        } else {
            proceedToMain()
        }
    }

    private fun hasFidlandAccessibilityPermission(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        // Settings.Secure stores entries as "pkg/fully.qualified.ClassName"
        // (the RESOLVED class name, not the manifest's ".relative" shorthand).
        val serviceId = "$packageName/com.example.fiddler.subapps.Fidland.service.FidlandAccessibilityService"
        return enabled.split(':').any { it.equals(serviceId, ignoreCase = true) }
    }

    // ── Finish ────────────────────────────────────────────────────────────────
    private fun proceedToMain() {
        prefs.edit().remove("permissions_chain_started").apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    // ── UI ────────────────────────────────────────────────────────────────────
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
            Text("and",        fontSize = 54.sp, color = Color.Black, fontFamily = fontBody)
            Text("Directories",fontSize = 54.sp, color = Color.Black, fontFamily = fontBody)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Fiddler needs some permissions to run smoothly. It will create directories " +
                        "at required locations now, to mitigate annoyance later.",
                fontSize = 18.sp,
                color = Color.Black,
                fontFamily = fontBody,
                modifier = Modifier.padding(16.dp)
            )

            Spacer(modifier = Modifier.height(50.dp))

            Button(
                onClick = { onGrantClick() },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.Black)
            ) {
                Text("Grant Permissions", fontSize = 16.sp, fontFamily = fontHead)
            }
        }
    }
}