package com.example.fiddler.subapps.Fidland.music

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.subapps.Fidland.TopicPage
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState

class MusicTopicCompose(context: Context) : TopicPage(context) {

    private val musicApps = MusicAppsRepository.getAllApps()
    private var currentPage by mutableStateOf(0)

    @Composable
    override fun Content() {
        val pagerState = rememberPagerState(initialPage = currentPage)

        HorizontalPager(
            count = musicApps.size,
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) { page ->
            val app = musicApps[page]

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(8.dp)
            ) {
                // Album Art + Overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.6f) // 60% height
                ) {
                    Image(
                        painter = painterResource(id = app.albumArtResId ?: android.R.color.black),
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        alignment = Alignment.Center
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.4f) // 40% width overlay
                            .background(Color(0xCC000000))
                            .align(Alignment.CenterEnd)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(4.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = app.songTitle,
                                color = Color.White,
                                fontSize = 18.sp,
                                maxLines = 1
                            )
                            Text(
                                text = app.artistName,
                                color = Color.White,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                            Text(
                                text = app.albumName,
                                color = Color.White,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Bottom Controls (Seekbar + Playback Buttons)
                Column(
                    modifier = Modifier.fillMaxHeight()
                ) {
                    // Seekbar Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = formatTime(app.currentMs),
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier.width(36.dp)
                        )
                        Slider(
                            value = app.currentMs.toFloat(),
                            onValueChange = { /* handle seek */ },
                            valueRange = 0f..app.totalMs.toFloat(),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatTime(app.totalMs),
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier.width(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Playback Buttons
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = { /* prev track */ }) {
                            Image(
                                painter = painterResource(id = com.example.fiddler.R.drawable.skip_previous),
                                contentDescription = "Previous"
                            )
                        }

                        IconButton(onClick = { /* play/pause */ }) {
                            Image(
                                painter = painterResource(
                                    id = if (app.isPlaying)
                                        com.example.fiddler.R.drawable.pause
                                    else
                                        com.example.fiddler.R.drawable.play
                                ),
                                contentDescription = "Play/Pause"
                            )
                        }

                        IconButton(onClick = { /* next track */ }) {
                            Image(
                                painter = painterResource(id = com.example.fiddler.R.drawable.skip_next),
                                contentDescription = "Next"
                            )
                        }
                    }
                }
            }
        }

        currentPage = pagerState.currentPage
    }

    override fun onSwipeLeft() {
        currentPage = (currentPage + 1).coerceAtMost(musicApps.size - 1)
    }

    override fun onSwipeRight() {
        currentPage = (currentPage - 1).coerceAtLeast(0)
    }

    private fun formatTime(ms: Int): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 1000) / 60
        return "%d:%02d".format(minutes, seconds)
    }
}
