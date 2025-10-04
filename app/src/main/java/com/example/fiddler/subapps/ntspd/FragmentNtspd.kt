package com.example.fiddler.subapps.ntspd

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.fiddler.R

class FragmentNtspd : Fragment() {

    private val overlayRequestCode = 101
    private val maxOffset = 50 // max distance in dp to left or right

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ntspd, container, false)

        val enableSwitch = view.findViewById<CheckBox>(R.id.switch_enable)
        val radioGroupPlacement = view.findViewById<RadioGroup>(R.id.radio_group_placement)
        val seekBarOffset = view.findViewById<SeekBar>(R.id.seekbar_offset)
        val txtOffsetValue = view.findViewById<TextView>(R.id.txt_offset_value)
        val btnApply = view.findViewById<Button>(R.id.btn_apply)
        val btnOverlay = view.findViewById<Button>(R.id.btn_request_overlay)

        // Center SeekBar
        seekBarOffset.max = maxOffset * 2
        seekBarOffset.progress = maxOffset // start thumb in the middle

        // Update offset text as user drags
        seekBarOffset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val actualOffset = progress - maxOffset
                val sign = if (actualOffset > 0) "+" else ""
                txtOffsetValue.text = "$sign$actualOffset dp"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Apply button
        btnApply.setOnClickListener {
            val enabled = enableSwitch.isChecked
            val placement = when (radioGroupPlacement.checkedRadioButtonId) {
                R.id.radio_left -> "left"
                R.id.radio_center -> "center"
                R.id.radio_right -> "right"
                else -> "right"
            }
            val offset = seekBarOffset.progress - maxOffset

            if (enabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    !Settings.canDrawOverlays(requireContext())
                ) {
                    Toast.makeText(
                        requireContext(),
                        "Overlay permission required",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val intent = Intent(requireContext(), NetSpeedService::class.java).apply {
                        putExtra("placement", placement)
                        putExtra("offset", offset)
                    }
                    requireContext().startService(intent)
                    Toast.makeText(requireContext(), "Applied", Toast.LENGTH_SHORT).show()
                }
            } else {
                requireContext().stopService(Intent(requireContext(), NetSpeedService::class.java))
                Toast.makeText(requireContext(), "Disabled", Toast.LENGTH_SHORT).show()
            }
        }

        // Overlay permission button
        btnOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !Settings.canDrawOverlays(requireContext())
            ) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${requireContext().packageName}")
                )
                startActivityForResult(intent, overlayRequestCode)
            } else {
                Toast.makeText(requireContext(), "Overlay permission already granted", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }
}
