package com.example.fiddler.subapps.Fidland

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.viewpager2.widget.ViewPager2
import com.example.fiddler.R

class AppsTopic(context: Context) : TopicPage(context) {
    private val view: View = LayoutInflater.from(context).inflate(R.layout.phase4_apps, null)
    private val viewPager: ViewPager2 = view.findViewById(R.id.apps_viewpager)

    init {
        viewPager.adapter = AppsPagerAdapter(context)
    }

    override fun getView(): View = view
    override fun onSwipeLeft() { viewPager.currentItem += 1 }
    override fun onSwipeRight() { viewPager.currentItem -= 1 }
}
