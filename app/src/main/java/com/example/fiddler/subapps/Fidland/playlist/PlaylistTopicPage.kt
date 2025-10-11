package com.example.fiddler.subapps.Fidland.service

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import kotlinx.coroutines.launch

@Composable
fun PlaylistTopicPage(
    pages: List<String> // Replace with your playlist data
) {
    val pagerState = rememberPagerState()
    val scope = rememberCoroutineScope()

    HorizontalPager(
        count = pages.size,
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    scope.launch {
                        if (dragAmount > 0) {
                            // Swipe Right → previous page
                            val prev = (pagerState.currentPage - 1).coerceAtLeast(0)
                            pagerState.animateScrollToPage(prev)
                        } else if (dragAmount < 0) {
                            // Swipe Left → next page
                            val next = (pagerState.currentPage + 1).coerceAtMost(pages.size - 1)
                            pagerState.animateScrollToPage(next)
                        }
                    }
                }
            }
    ) { page ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = pages[page],
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
