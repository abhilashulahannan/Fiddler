package com.example.fiddler.core

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import com.example.fiddler.subapps.Fidland.service.FidlandService

// Shared state for sub-apps enable/disable
object SubAppState {
    val ntspdEnabled = mutableStateOf(true)
    val rngtnsEnabled = mutableStateOf(true)
    val fidlandEnabled = mutableStateOf(false)
    val secgrpEnabled = mutableStateOf(false)

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("fidland_prefs", Context.MODE_PRIVATE)
        fidlandEnabled.value = prefs.getBoolean("enabled", false)
    }

    fun setFidlandEnabled(context: Context, checked: Boolean) {
        val prefs = context.getSharedPreferences("fidland_prefs", Context.MODE_PRIVATE)
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
                fidlandEnabled.value = false
                prefs.edit().putBoolean("enabled", false).apply()
            } else {
                fidlandEnabled.value = true
                prefs.edit().putBoolean("enabled", true).apply()
                context.startService(serviceIntent)
            }
        } else {
            fidlandEnabled.value = false
            prefs.edit().putBoolean("enabled", false).apply()
            context.stopService(serviceIntent)
        }
    }
}