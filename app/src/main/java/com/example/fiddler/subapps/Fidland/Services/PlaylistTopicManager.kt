package com.example.fiddler.subapps.Fidland.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PlaylistTopicManager(
    private val scope: CoroutineScope // Pass FidlandService's serviceScope
) {

    // Playlist data
    private val _playlist = MutableStateFlow<List<String>>(emptyList())
    val playlist: StateFlow<List<String>> = _playlist

    // Current page index
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage

    // Update playlist
    fun setPlaylist(pages: List<String>) {
        _playlist.value = pages
        // Ensure current page is still valid
        _currentPage.value = _currentPage.value.coerceAtMost(pages.size - 1)
    }

    // Programmatic page changes
    fun swipeLeft() {
        _currentPage.value = (_currentPage.value + 1).coerceAtMost(_playlist.value.size - 1)
    }

    fun swipeRight() {
        _currentPage.value = (_currentPage.value - 1).coerceAtLeast(0)
    }
}
