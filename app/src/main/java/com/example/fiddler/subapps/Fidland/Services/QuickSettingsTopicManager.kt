package com.example.fiddler.subapps.Fidland.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class QuickSettingsManager(
    private val scope: CoroutineScope // Pass FidlandService's serviceScope
) {

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
        // Ensure current page is still valid
        _currentPage.value = _currentPage.value.coerceAtMost(newPages.size - 1)
    }
}
