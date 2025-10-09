package com.example.fiddler.subapps.Fidland

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.viewpager2.widget.ViewPager2
import com.example.fiddler.R

class MusicTopic(context: Context) : TopicPage(context) {
    private val view: View = LayoutInflater.from(context).inflate(R.layout.phase4_music, null)
    private val viewPager: ViewPager2 = view.findViewById(R.id.music_viewpager)

    init {
        viewPager.adapter = MusicAppPagerAdapter(context) // separate adapter per music apps
    }

    override fun getView(): View = view
    override fun onSwipeLeft() { viewPager.currentItem += 1 }
    override fun onSwipeRight() { viewPager.currentItem -= 1 }
}
