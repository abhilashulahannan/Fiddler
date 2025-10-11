package com.example.fiddler.subapps.Fidland.service

import android.content.Context
import android.view.View
import androidx.viewpager2.widget.ViewPager2
import com.example.fiddler.subapps.Fidland.TopicPage

class AppsTopicService(
    context: Context,
    private val pager: ViewPager2
) : TopicPage(context) {

    private val view: View = pager // Or inflate the layout you need

    override fun getView(): View {
        return view
    }

    override fun onSwipeLeft() {
        // handle left swipe
    }

    override fun onSwipeRight() {
        // handle right swipe
    }

    override fun onDestroy() {
        // cleanup if needed
    }
}
