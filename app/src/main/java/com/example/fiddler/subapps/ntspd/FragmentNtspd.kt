package com.example.fiddler.subapps.ntspd

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.fiddler.R

class FragmentNtspd : Fragment() {

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

        btnApply.setOnClickListener {
            val enabled = enableSwitch.isChecked
            val placement = when (radioGroupPlacement.checkedRadioButtonId) {
                R.id.radio_left -> "left"
                R.id.radio_center -> "center"
                R.id.radio_right -> "right"
                else -> "right"
            }
            val offset = seekBarOffset.progress

            if (enabled) {
                val intent = Intent(requireContext(), NetSpeedService::class.java).apply {
                    putExtra("placement", placement)
                    putExtra("offset", offset)
                }
                requireContext().startService(intent)
            } else {
                requireContext().stopService(Intent(requireContext(), NetSpeedService::class.java))
            }
        }

        seekBarOffset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                txtOffsetValue.text = "$progress dp"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        return view
    }
}
