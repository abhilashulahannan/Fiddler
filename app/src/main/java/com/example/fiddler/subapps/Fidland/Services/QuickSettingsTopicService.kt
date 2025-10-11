package com.example.fiddler.subapps.Fidland.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class QuickSettingsService : Service() {

    // Binder to allow Activity/Fragment to access the service
    private val binder = QuickSettingsBinder()

    inner class QuickSettingsBinder : Binder() {
        fun getService(): QuickSettingsService = this@QuickSettingsService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    // Quick settings pages
    private val _pages = MutableStateFlow(listOf("Wi-Fi", "Bluetooth", "Airplane Mode"))
    val pages: StateFlow<List<String>> = _pages

    // Current page index
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage

    // Programmatic swipe methods
    fun swipeLeft() {
        _currentPage.value = (_currentPage.value + 1).coerceAtMost(_pages.value.size - 1)
    }

    fun swipeRight() {
        _currentPage.value = (_currentPage.value - 1).coerceAtLeast(0)
    }

    // Optional: update pages dynamically
    fun setPages(newPages: List<String>) {
        _pages.value = newPages
    }
}
