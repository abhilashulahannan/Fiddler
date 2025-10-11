package com.example.fiddler.subapps.Fidland.service

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.google.accompanist.pager.*

@Composable
fun AppsTopicPage(
    pages: List<String>, // or any data you want to display per page
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {}
) {
    val pagerState = rememberPagerState()

    HorizontalPager(
        count = pages.size,
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    if (dragAmount > 0) {
                        onSwipeRight()
                    } else if (dragAmount < 0) {
                        onSwipeLeft()
                    }
                }
            }
    ) { page ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.LightGray)
        ) {
            Text(
                text = pages[page],
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
