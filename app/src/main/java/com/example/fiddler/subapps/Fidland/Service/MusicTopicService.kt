package com.example.fiddler.subapps.Fidland.service

import android.content.Context
import android.view.View
import androidx.viewpager2.widget.ViewPager2
import com.example.fiddler.subapps.Fidland.TopicPage

class MusicTopicService(
    context: Context,
    private val pager: ViewPager2
) : TopicPage(context) {

    override fun getView(): View = pager

    override fun onSwipeLeft() {
        val itemCount = pager.adapter?.itemCount ?: return
        pager.currentItem = (pager.currentItem + 1).coerceAtMost(itemCount - 1)
    }

    override fun onSwipeRight() {
        pager.currentItem = (pager.currentItem - 1).coerceAtLeast(0)
    }
}
