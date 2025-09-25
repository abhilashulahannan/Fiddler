package com.example.fiddler.subapps.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.fragment.app.Fragment
import com.example.fiddler.R
import com.example.fiddler.subapps.ntspd.FragmentNtspd
import com.example.fiddler.subapps.rngtns.FragmentRngtns

class FragmentHome : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        val subAppNtspd = view.findViewById<CheckBox>(R.id.cb_ntspd)
        val subAppRngtns = view.findViewById<CheckBox>(R.id.cb_rngtns)

        // Initially, set as enabled or not
        subAppNtspd.isChecked = true
        subAppRngtns.isChecked = true

        return view
    }
}
