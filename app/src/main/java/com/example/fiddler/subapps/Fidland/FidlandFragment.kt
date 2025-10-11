package com.example.fiddler.subapps.Fidland

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.fiddler.R
import android.app.Service
import com.example.fiddler.subapps.Fidland.service.FidlandService
import com.google.android.material.slider.Slider

class FidlandFragment : Fragment() {

    private lateinit var enableSwitch: CheckBox
    private lateinit var cbNetworkTraffic: CheckBox
    private lateinit var cbEqualizerInfo: CheckBox
    private lateinit var cbNotifications: CheckBox
    private lateinit var cbMusicPlayer: CheckBox
    private lateinit var cbMusicQueue: CheckBox
    private lateinit var cbQuickSettings: CheckBox
    private lateinit var inputRows: EditText
    private lateinit var inputColumns: EditText
    private lateinit var sbAnimationSpeed: Slider
    private lateinit var sbTransparency: Slider
    private lateinit var sbCornerRadius: Slider

    private val prefsName = "fidland_prefs"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: android.os.Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_fidland, container, false)

        // Bind views
        enableSwitch = view.findViewById(R.id.switch_enable)
        cbNetworkTraffic = view.findViewById(R.id.cb_network_traffic)
        cbEqualizerInfo = view.findViewById(R.id.cb_equalizer_info)
        cbNotifications = view.findViewById(R.id.cb_notifications)
        cbMusicPlayer = view.findViewById(R.id.cb_music_player)
        cbMusicQueue = view.findViewById(R.id.cb_music_queue)
        cbQuickSettings = view.findViewById(R.id.cb_quick_settings)
        inputRows = view.findViewById(R.id.input_rows)
        inputColumns = view.findViewById(R.id.input_columns)
        sbAnimationSpeed = view.findViewById(R.id.sb_animation_speed)
        sbTransparency = view.findViewById(R.id.sb_transparency)
        sbCornerRadius = view.findViewById(R.id.sb_corner_radius)

        // Load saved settings
        loadSettings()

        // Enable switch: start/stop FidlandService
        enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            val serviceIntent = Intent(requireContext(), FidlandService::class.java)
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(requireContext())) {
                    Toast.makeText(requireContext(), "Please grant overlay permission", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    enableSwitch.isChecked = false
                } else {
                    requireContext().startService(serviceIntent)
                }
            } else {
                requireContext().stopService(serviceIntent)
            }
        }

        // Add realtime listeners
        addRealtimeChangeListeners()

        return view
    }

    private fun loadSettings() {
        val prefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)

        enableSwitch.isChecked = prefs.getBoolean("enabled", true)
        cbNetworkTraffic.isChecked = prefs.getBoolean("network_traffic", true)
        cbEqualizerInfo.isChecked = prefs.getBoolean("equalizer_info", true)
        cbNotifications.isChecked = prefs.getBoolean("notifications", true)
        cbMusicPlayer.isChecked = prefs.getBoolean("music_player", true)
        cbMusicQueue.isChecked = prefs.getBoolean("music_queue", true)
        cbQuickSettings.isChecked = prefs.getBoolean("quick_settings", true)

        inputRows.setText(prefs.getInt("app_rows", 3).toString())
        inputColumns.setText(prefs.getInt("app_columns", 4).toString())

        sbAnimationSpeed.value = prefs.getInt("animation_speed", 50).toFloat()
        sbTransparency.value = prefs.getInt("transparency", 80).toFloat()
        sbCornerRadius.value = prefs.getInt("corner_radius", 40).toFloat()
    }

    private fun addRealtimeChangeListeners() {
        val prefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        val checkBoxes = listOf(
            cbNetworkTraffic to "network_traffic",
            cbEqualizerInfo to "equalizer_info",
            cbNotifications to "notifications",
            cbMusicPlayer to "music_player",
            cbMusicQueue to "music_queue",
            cbQuickSettings to "quick_settings"
        )

        checkBoxes.forEach { (checkBox, key) ->
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                editor.putBoolean(key, isChecked).apply()
                resetFidland()
            }
        }

        val sliders = listOf(
            sbAnimationSpeed to "animation_speed",
            sbTransparency to "transparency",
            sbCornerRadius to "corner_radius"
        )

        sliders.forEach { (slider, key) ->
            slider.addOnChangeListener { _, value, _ ->
                editor.putInt(key, value.toInt()).apply()
                resetFidland()
            }
        }

        inputRows.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                editor.putInt("app_rows", s.toString().toIntOrNull() ?: 3).apply()
                resetFidland()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        inputColumns.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                editor.putInt("app_columns", s.toString().toIntOrNull() ?: 4).apply()
                resetFidland()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun resetFidland() {
        val serviceIntent = Intent(requireContext(), FidlandService::class.java)
        requireContext().stopService(serviceIntent)
        requireContext().startService(serviceIntent)
    }
}
