package com.example.fiddler.subapps.Fidland.apps

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.viewpager2.widget.ViewPager2
import com.example.fiddler.R
import com.example.fiddler.subapps.Fidland.TopicPage

class AppsTopic(context: Context, private val pager: ViewPager2) : TopicPage(context) {
    private val view: View = LayoutInflater.from(context).inflate(R.layout.phase4_apps, null)

    init {
        pager.adapter = AppsPagerAdapter(context)
    }

    override fun getView(): View = view
    override fun onSwipeLeft() { pager.currentItem += 1 }
    override fun onSwipeRight() { pager.currentItem -= 1 }
}

