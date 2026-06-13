package com.example.fiddler.subapps.Fidland.manager

import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Drives the phase 3 right-segment rotation.
 * Cycles through [segmentCount] indices every [switchIntervalMs] ms.
 * Some indicators (recorder, timer) will pin themselves by calling [pin]/[unpin].
 *
 * SegmentManager.kt (the old rememberSegmentManager composable) is now
 * retired — this class covers both the coroutine-based switching and the
 * composable convenience wrapper below.
 */
class SegmentSwitcher(
    val segmentCount: Int,
    private val scope: CoroutineScope,
    private val switchIntervalMs: Long = 5000L
) {
    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex

    // When pinned, auto-switching pauses so persistent indicators
    // (recorder, timer, stopwatch) stay visible until dismissed
    private var pinned = false

    init {
        require(segmentCount > 0) { "segmentCount must be > 0" }
        startSwitching()
    }

    private fun startSwitching() {
        scope.launch {
            while (true) {
                delay(switchIntervalMs)
                if (!pinned) {
                    _currentIndex.value = (_currentIndex.value + 1) % segmentCount
                }
            }
        }
    }

    // Manual control
    fun next() {
        _currentIndex.value = (_currentIndex.value + 1) % segmentCount
    }

    fun previous() {
        _currentIndex.value = (_currentIndex.value - 1 + segmentCount) % segmentCount
    }

    fun setSegment(index: Int) {
        _currentIndex.value = index.coerceIn(0, segmentCount - 1)
    }

    // Pin to current indicator — use for recorder, timer, active call, etc.
    fun pin() { pinned = true }
    fun unpin() { pinned = false }
}

/**
 * Composable convenience wrapper — use this inside a Composable scope
 * when you need the current segment index without holding a SegmentSwitcher
 * instance (e.g. lightweight previews or tests).
 * For the main service path, use the SegmentSwitcher class directly.
 */
@Composable
fun rememberSegmentManager(
    segmentCount: Int,
    switchIntervalMs: Long = 5000L
): State<Int> {
    var currentIndex by remember { mutableStateOf(0) }

    LaunchedEffect(segmentCount) {
        while (true) {
            delay(switchIntervalMs)
            currentIndex = (currentIndex + 1) % segmentCount
        }
    }

    return derivedStateOf { currentIndex }
}