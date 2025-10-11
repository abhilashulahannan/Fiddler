package com.example.fiddler.subapps.Fidland.apps

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.subapps.Fidland.TopicPage
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState

class AppsTopic(context: Context) : TopicPage(context) {

    // Example: 3 pages
    private val pageCount = 3
    private var currentPage = 0

    @Composable
    override fun Content() {
        val pagerState = rememberPagerState(initialPage = currentPage)

        HorizontalPager(
            count = pageCount,
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Apps Page ${page + 1}",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        }

        // Update currentPage for programmatic swipes
        currentPage = pagerState.currentPage
    }

    override fun onSwipeLeft() {
        currentPage = (currentPage + 1).coerceAtMost(pageCount - 1)
    }

    override fun onSwipeRight() {
        currentPage = (currentPage - 1).coerceAtLeast(0)
    }
}
