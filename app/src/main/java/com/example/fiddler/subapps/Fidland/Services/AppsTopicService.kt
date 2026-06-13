package com.example.fiddler.subapps.Fidland.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AppsTopicManager(
    private val scope: CoroutineScope
) {

    // List of app topic pages
    private val _pages = MutableStateFlow<List<String>>(emptyList())
    val pages: StateFlow<List<String>> = _pages

    // Current page index
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage

    // Update pages dynamically
    fun setPages(newPages: List<String>) {
        _pages.value = newPages
        _currentPage.value = _currentPage.value.coerceAtMost(newPages.size - 1)
    }

    // Programmatic swipe methods
    fun swipeLeft() {
        _currentPage.value = (_currentPage.value + 1).coerceAtMost(_pages.value.size - 1)
    }

    fun swipeRight() {
        _currentPage.value = (_currentPage.value - 1).coerceAtLeast(0)
    }
}
