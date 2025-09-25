package com.example.fiddler.subapps.ntspd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.fiddler.R
import android.widget.TextView

class FragmentNtspd : Fragment() {

    private lateinit var tvNetSpeed: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the fragment layout
        val view = inflater.inflate(R.layout.fragment_ntspd, container, false)

        // Reference to TextView for net speed
        tvNetSpeed = view.findViewById(R.id.tv_net_speed)

        // TODO: Update net speed dynamically here
        tvNetSpeed.text = "Net Speed: 0 KB/s"

        return view
    }

    // Example function to update net speed
    fun updateNetSpeed(speed: String) {
        tvNetSpeed.text = "Net Speed: $speed"
    }
}
