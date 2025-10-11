package com.example.fiddler.subapps.Fidland.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PlaylistTopicService : Service() {

    // Binder for Activity/Fragment to connect
    private val binder = PlaylistBinder()

    // Playlist data
    private val _playlist = MutableStateFlow<List<String>>(emptyList())
    val playlist: StateFlow<List<String>> = _playlist

    // Current page
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage

    inner class PlaylistBinder : Binder() {
        fun getService(): PlaylistTopicService = this@PlaylistTopicService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    // Update playlist
    fun setPlaylist(pages: List<String>) {
        _playlist.value = pages
    }

    // Programmatic page changes
    fun swipeLeft() {
        _currentPage.value = (_currentPage.value + 1).coerceAtMost(_playlist.value.size - 1)
    }

    fun swipeRight() {
        _currentPage.value = (_currentPage.value - 1).coerceAtLeast(0)
    }
}
