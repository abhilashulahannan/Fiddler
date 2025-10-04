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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile

class PermissionsActivity : AppCompatActivity() {

    private val REQUIRED_PERMISSIONS = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.ACCESS_FINE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
    }.toTypedArray()

    private val REQUEST_CODE_PERMISSIONS = 100
    private val REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = 101
    private val REQUEST_OVERLAY_PERMISSION = 102

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
        setContentView(R.layout.activity_permissions)
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            requestBatteryOptimizationExemption()
        } else {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) requestBatteryOptimizationExemption()
            else checkAndRequestPermissions()
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivityForResult(intent, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        } else {
            checkSAFSetup()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_IGNORE_BATTERY_OPTIMIZATIONS -> checkSAFSetup()
            REQUEST_OVERLAY_PERMISSION -> checkOverlayPermissionGranted()
        }
    }

    private fun checkSAFSetup() {
        val uriString = prefs.getString("saf_uri", null)
        val safConfigured = uriString?.let { savedUri ->
            val treeUri = Uri.parse(savedUri)
            contentResolver.persistedUriPermissions.any {
                it.uri == treeUri && it.isWritePermission
            }
        } ?: false

        if (safConfigured) {
            setupFoldersAndPlaceholder()
            checkWriteSettingsPermission()
        } else {
            promptSAFExplanation()
        }
    }

    private fun promptSAFExplanation() {
        val dialog = AlertDialog.Builder(this, R.style.WhiteDialogTheme)
            .setTitle("Setup Audio Folder")
            .setMessage(
                "For various app functions and ease of use, the app needs access to a folder in DCIM called 'Fiddler'.\n\n" +
                        "This is a one-time setup using the system file picker. " +
                        "After you select the folder, the app can only read/write files in that folder safely."
            )
            .setCancelable(false)
            .setPositiveButton("Continue") { _, _ -> launchSAF() }
            .setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
                Toast.makeText(
                    this,
                    "App will not function without folder access.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .create()

        dialog.setOnShowListener {
            dialog.findViewById<TextView>(android.R.id.message)?.apply {
                setTextColor(resources.getColor(android.R.color.black))
                typeface = resources.getFont(R.font.font_handwriting)
            }
            dialog.findViewById<TextView>(
                resources.getIdentifier("alertTitle", "id", "android")
            )?.apply {
                setTextColor(resources.getColor(android.R.color.black))
                typeface = resources.getFont(R.font.font_body)
            }
        }

        dialog.show()
    }

    private fun launchSAF() {
        safLauncher.launch(null)
    }

    /** SAF-compatible folder setup using DocumentFile */
    private fun setupFoldersAndPlaceholder() {
        try {
            val uriString = prefs.getString("saf_uri", null)
            if (uriString == null) {
                Toast.makeText(this, "SAF folder not selected.", Toast.LENGTH_LONG).show()
                return
            }

            val treeUri = Uri.parse(uriString)
            val rootDoc = DocumentFile.fromTreeUri(this, treeUri) ?: return

            val audioFolder = rootDoc.findFile("Audio") ?: rootDoc.createDirectory("Audio")
            audioFolder?.findFile("Fidtones") ?: audioFolder?.createDirectory("Fidtones")
            audioFolder?.findFile("fiddler_ringtone.ogg") ?: audioFolder?.createFile("audio/ogg", "fiddler_ringtone.ogg")

            Toast.makeText(this, "Folders and placeholder ready!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to setup folders.", Toast.LENGTH_LONG).show()
        }
    }

    /** CHECK AND REQUEST WRITE_SETTINGS PERMISSION */
    private fun checkWriteSettingsPermission() {
        if (!Settings.System.canWrite(this)) {
            Toast.makeText(this, "Grant permission to change system ringtone", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } else {
            checkOverlayPermission()
        }
    }

    /** CHECK AND REQUEST OVERLAY PERMISSION */
    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(this)
        ) {
            Toast.makeText(this, "Grant overlay permission for network speed indicator", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
        } else {
            proceedToMain()
        }
    }

    private fun checkOverlayPermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(this)
        ) {
            Toast.makeText(this, "Overlay permission not granted. Some features may not work.", Toast.LENGTH_LONG).show()
        }
        proceedToMain()
    }

    private fun proceedToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
