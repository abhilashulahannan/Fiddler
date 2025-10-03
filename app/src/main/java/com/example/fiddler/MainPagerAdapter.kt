package com.example.fiddler

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.fiddler.subapps.Fidland.FidlandFragment
import com.example.fiddler.subapps.home.FragmentHome
import com.example.fiddler.subapps.ntspd.FragmentNtspd
import com.example.fiddler.subapps.rngtns.RngtnsFragment
import com.example.fiddler.subapps.SecGrp.SecgrpFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val fragments = listOf(
        FragmentHome(),
        FragmentNtspd(),
        RngtnsFragment(),
        FidlandFragment(),
        SecgrpFragment()

    )

    override fun getItemCount(): Int = fragments.size

    override fun createFragment(position: Int): Fragment = fragments[position]
}
