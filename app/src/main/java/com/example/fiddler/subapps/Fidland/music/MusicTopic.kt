package com.example.fiddler.subapps.Fidland.music

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.LayoutInflater
import android.view.View
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.viewpager2.widget.ViewPager2
import com.example.fiddler.R
import com.example.fiddler.subapps.Fidland.TopicPage

class MusicTopic(
    context: Context,
    private val viewPager: ViewPager2
) : TopicPage(context) {

    private val controller = MusicAppController(context)
    private val adapter = MusicAppPagerAdapter(context)
    private val localBroadcastManager = LocalBroadcastManager.getInstance(context)

    private val musicUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.getStringExtra("pkg") ?: return
            val updatedApp = MusicAppsRepository.getAllApps()
                .find { it.appPackage == packageName } ?: return
            updateMusicApp(updatedApp)
        }
    }

    init {
        viewPager.adapter = adapter

        // Register receiver with LocalBroadcastManager to avoid SecurityException
        val filter = IntentFilter("com.example.fiddler.MEDIA_UPDATE")
        localBroadcastManager.registerReceiver(musicUpdateReceiver, filter)
    }

    private fun updateMusicApp(updatedApp: MusicApp) {
        MusicAppsRepository.updateApp(updatedApp)
        adapter.updateApp(updatedApp)
        val currentApp = getCurrentApp() ?: return
        if (currentApp.appPackage == updatedApp.appPackage) {
            refreshCurrentTrack()
        }
    }

    private fun getCurrentApp(): MusicApp? {
        val index = viewPager.currentItem
        return MusicAppsRepository.getAllApps().getOrNull(index)
    }

    private fun refreshCurrentTrack() {
        val app = getCurrentApp() ?: return
        val updatedApp = controller.getCurrentTrack(app)
        updateMusicApp(updatedApp)
    }

    override fun onSwipeLeft() {
        if (viewPager.currentItem < adapter.itemCount - 1) viewPager.currentItem += 1
    }

    override fun onSwipeRight() {
        if (viewPager.currentItem > 0) viewPager.currentItem -= 1
    }

    override fun onDestroy() {
        super.onDestroy()
        localBroadcastManager.unregisterReceiver(musicUpdateReceiver)
    }

    // Required abstract method from TopicPage
    override fun getView(): View {
        return LayoutInflater.from(context).inflate(R.layout.phase4_music, null, false)
    }
}
