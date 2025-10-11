package com.example.fiddler.subapps.Fidland.service

import androidx.compose.runtime.*
import kotlinx.coroutines.delay

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
