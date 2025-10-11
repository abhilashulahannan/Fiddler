package com.example.fiddler.subapps.Fidland.quicksettings

import android.content.Context
import android.view.View
import com.example.fiddler.R
import androidx.viewpager2.widget.ViewPager2
import com.example.fiddler.subapps.Fidland.TopicPage
import com.example.fiddler.subapps.fidland.quicksettings.QuickSettingsAdapter
import com.example.fiddler.subapps.fidland.quicksettings.QuickSettingsCallback
import com.example.fiddler.subapps.fidland.quicksettings.QuickSettingItem


class QuickSettingsTopic(
    context: Context,
    private val pager: ViewPager2
) : TopicPage(context), QuickSettingsCallback {

    // Define your quick setting items
    private val items = listOf(
        QuickSettingItem(R.drawable.wifi_on, "Wi-Fi"),
        QuickSettingItem(R.drawable.blue, "Bluetooth"),
        QuickSettingItem(R.drawable.flashlight_on, "Flashlight")
        // add more tiles as needed
    )

    init {
        // Initialize adapter with items and callback
        pager.adapter = QuickSettingsAdapter(items, this)
    }

    override fun getView(): View = pager

    override fun onSwipeLeft() {
        pager.currentItem += 1
    }

    override fun onSwipeRight() {
        pager.currentItem -= 1
    }

    override fun onQuickSettingClicked(item: QuickSettingItem) {
        // Handle quick setting click
    }

    override fun onDestroy() {
        // optional cleanup
    }
}
