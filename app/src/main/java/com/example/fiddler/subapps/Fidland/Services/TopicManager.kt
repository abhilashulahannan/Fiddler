package com.example.fiddler.subapps.Fidland.service

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.google.accompanist.pager.*

@Composable
fun TopicManagerCompose(
    topics: List<@Composable () -> Unit>,
    isExpanded: Boolean,
    onCollapse: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }

    if (topics.isEmpty()) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(isExpanded) {
                detectDragGestures { change, dragAmount ->
                    val (dx, dy) = dragAmount
                    if (!isExpanded) return@detectDragGestures

                    when {
                        dy < -10 -> onCollapse()
                        dy > 10 -> {
                            currentIndex = (currentIndex + 1) % topics.size
                        }
                        dx > 10 -> { /* Optional: call current topic swipe right logic */ }
                        dx < -10 -> { /* Optional: call current topic swipe left logic */ }
                    }
                }
            }
    ) {
        topics[currentIndex]()
    }
}
