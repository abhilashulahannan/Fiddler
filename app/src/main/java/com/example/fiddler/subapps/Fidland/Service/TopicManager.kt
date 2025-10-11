package com.example.fiddler.subapps.Fidland.service

import android.content.Context
import android.widget.FrameLayout
import androidx.viewpager2.widget.ViewPager2
import com.example.fiddler.subapps.Fidland.TopicPage

class TopicManager(
    private val context: Context,
    private val overlayView: android.view.View,
    private val container: FrameLayout
) {
    private val topics = mutableListOf<TopicPage>()
    private var currentIndex = 0
    var collapsePill: (() -> Unit)? = null

    fun setupTopics(
        musicPager: ViewPager2? = null,
        playlistPager: ViewPager2? = null,
        appsPager: ViewPager2? = null,
        quickSettingsPager: ViewPager2? = null
    ) {
        topics.clear()
        val prefs = context.getSharedPreferences("fidland_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("music_player", true) && musicPager != null)
            topics.add(MusicTopicService(context, musicPager))
        if (prefs.getBoolean("music_queue", true) && playlistPager != null)
            topics.add(PlaylistTopicService(context, playlistPager))
        if (prefs.getInt("app_rows", 3) > 0 && prefs.getInt("app_columns", 4) > 0 && appsPager != null)
            topics.add(AppsTopicService(context, appsPager))
        if (prefs.getBoolean("quick_settings", true) && quickSettingsPager != null)
            topics.add(QuickSettingsTopicService(context, quickSettingsPager))
        container.removeAllViews()
        showCurrentTopic()
    }

    fun showCurrentTopic() {
        if (topics.isEmpty()) return
        container.removeAllViews()
        container.addView(topics[currentIndex].getView())
    }

    fun handleSwipe(dx: Float, dy: Float, isExpanded: Boolean) {
        if (!isExpanded || topics.isEmpty()) return
        when {
            dy < -10 -> collapsePill?.invoke()
            dy > 10 -> { currentIndex = (currentIndex + 1) % topics.size; showCurrentTopic() }
            dx > 10 -> topics[currentIndex].onSwipeRight()
            dx < -10 -> topics[currentIndex].onSwipeLeft()
        }
    }
}
