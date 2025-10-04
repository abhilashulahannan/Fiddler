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
    private val maxOffsetDp = 100 // maximum offset in dp
    private val defaultCenterOffset = -25 // default center offset in dp

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ntspd, container, false)

        val enableSwitch = view.findViewById<CheckBox>(R.id.switch_enable)
        val radioGroupPlacement = view.findViewById<RadioGroup>(R.id.radio_group_placement)
        val seekBarOffset = view.findViewById<SeekBar>(R.id.seekbar_offset)
        val txtOffsetValue = view.findViewById<TextView>(R.id.txt_offset_value)
        val btnOverlay = view.findViewById<Button>(R.id.btn_request_overlay)

        // Function to update overlay instantly
        fun updateOverlay(offset: Int, placement: String) {
            if (!enableSwitch.isChecked) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !Settings.canDrawOverlays(requireContext())
            ) {
                Toast.makeText(requireContext(), "Overlay permission required", Toast.LENGTH_SHORT).show()
                return
            }

            val intent = Intent(requireContext(), NetSpeedService::class.java).apply {
                putExtra("placement", placement)
                putExtra("offset", offset)
            }
            requireContext().startService(intent)
        }

        // Adjust SeekBar according to placement
        fun adjustSeekBarForPlacement() {
            val placement = when (radioGroupPlacement.checkedRadioButtonId) {
                R.id.radio_left -> "left"
                R.id.radio_center -> "center"
                R.id.radio_right -> "right"
                else -> "center"
            }

            when (placement) {
                "left" -> {
                    seekBarOffset.max = maxOffsetDp
                    seekBarOffset.progress = 0
                }
                "center" -> {
                    seekBarOffset.max = maxOffsetDp * 2
                    seekBarOffset.progress = maxOffsetDp + defaultCenterOffset
                }
                "right" -> {
                    seekBarOffset.max = maxOffsetDp
                    seekBarOffset.progress = 0
                }
            }

            val offset = when (placement) {
                "left" -> seekBarOffset.progress
                "center" -> seekBarOffset.progress - maxOffsetDp
                "right" -> maxOffsetDp - seekBarOffset.progress
                else -> seekBarOffset.progress - maxOffsetDp
            }

            txtOffsetValue.text = "$offset dp"
            updateOverlay(offset, placement)
        }

        // Start/stop overlay when checkbox is toggled
        enableSwitch.setOnCheckedChangeListener { _, _ ->
            adjustSeekBarForPlacement() // overlay updates immediately
        }

        // Placement change listener
        radioGroupPlacement.setOnCheckedChangeListener { _, _ ->
            adjustSeekBarForPlacement()
        }

        // SeekBar change listener
        seekBarOffset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val placement = when (radioGroupPlacement.checkedRadioButtonId) {
                    R.id.radio_left -> "left"
                    R.id.radio_center -> "center"
                    R.id.radio_right -> "right"
                    else -> "center"
                }

                val offset = when (placement) {
                    "left" -> progress
                    "center" -> progress - maxOffsetDp
                    "right" -> maxOffsetDp - progress
                    else -> progress - maxOffsetDp
                }

                txtOffsetValue.text = "$offset dp"
                updateOverlay(offset, placement)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

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

        // Initialize SeekBar for default placement
        radioGroupPlacement.check(R.id.radio_center) // default to center
        adjustSeekBarForPlacement()

        return view
    }
}
