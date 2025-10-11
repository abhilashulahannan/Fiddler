package com.example.fiddler.subapps.Fidland.playlist

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.subapps.Fidland.TopicPage
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState

class PlaylistTopicCompose(context: Context) : TopicPage(context) {

    private val playlistApps = listOf("Spotify", "YT Music") // replace with dynamic apps if needed
    private var currentPage by mutableStateOf(0)

    @Composable
    override fun Content() {
        val pagerState = rememberPagerState(initialPage = currentPage)

        HorizontalPager(
            count = playlistApps.size,
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
                    text = "Playlist Page: ${playlistApps[page]}",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        }

        // Keep track of the current page for programmatic swipes
        currentPage = pagerState.currentPage
    }

    override fun onSwipeLeft() {
        currentPage = (currentPage + 1).coerceAtMost(playlistApps.size - 1)
    }

    override fun onSwipeRight() {
        currentPage = (currentPage - 1).coerceAtLeast(0)
    }
}
