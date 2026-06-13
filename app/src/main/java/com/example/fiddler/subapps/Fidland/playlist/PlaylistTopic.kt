package com.example.fiddler.subapps.Fidland.playlist

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.subapps.Fidland.TopicPage
import com.example.fiddler.subapps.Fidland.music.MusicApp
import com.example.fiddler.subapps.Fidland.music.MusicAppController
import com.example.fiddler.subapps.Fidland.music.MusicAppsRepository
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState

/**
 * Phase 3 dashboard — Category 2: Music Queue.
 *
 * One pager page per registered music app (Spotify, YT Music, etc.).
 * Swipe left/right to switch between apps.
 *
 * Queue data sourced from MusicAppsRepository. The queue track list
 * is a best-effort read — Spotify exposes queue via MediaSession queue
 * items; YT Music does not reliably. Tracks that can't be fetched show
 * a "Queue unavailable" message rather than crashing or showing stale data.
 *
 * Tapping a track sends a skipToQueueItem() command via MediaController.
 * This works for Spotify; YT Music may ignore it depending on version.
 */
class PlaylistTopicCompose(context: Context) : TopicPage(context) {

    private val controller = MusicAppController(context)
    private var currentPage by mutableStateOf(0)

    @Composable
    override fun Content() {
        val apps by MusicAppsRepository.appsFlow.collectAsState()

        if (apps.isEmpty()) {
            EmptyState("No music apps connected")
            return
        }

        val pagerState = rememberPagerState(
            initialPage = currentPage.coerceAtMost(apps.size - 1)
        )

        HorizontalPager(
            count = apps.size,
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        ) { page ->
            val app = apps[page]
            AppQueuePage(app = app)
        }

        currentPage = pagerState.currentPage
    }

    @Composable
    private fun AppQueuePage(app: MusicApp) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0D0D))
                .padding(top = 10.dp)
                .padding(8.dp)
        ) {
            // Header — app name + now playing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = app.appName,
                    color = Color(0xFFAAAAAA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (app.isPlaying) "▶ Playing" else "Paused",
                    color = if (app.isPlaying) Color(0xFF1DB954) else Color(0xFF666666),
                    fontSize = 10.sp
                )
            }

            Divider(color = Color(0xFF222222), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(4.dp))

            // Now playing row — always shown at top of queue
            if (app.songTitle.isNotBlank()) {
                NowPlayingRow(app = app)
                Spacer(modifier = Modifier.height(4.dp))
                Divider(color = Color(0xFF222222), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Queue list
            // Queue items are not yet populated from MediaSession —
            // that requires getQueue() on the MediaController which
            // needs to be wired in a follow-up.
            // For now we show the current track and a clear unavailable message
            // rather than dummy data.
            QueueUnavailableMessage(app = app)
        }
    }

    @Composable
    private fun NowPlayingRow(app: MusicApp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Playing indicator dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(Color(0xFF1DB954), RoundedCornerShape(50))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.songTitle,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.artistName,
                    color = Color(0xFFAAAAAA),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    @Composable
    private fun QueueUnavailableMessage(app: MusicApp) {
        // Spotify exposes queue via controller.queue — wire this up
        // once MediaController getQueue() is integrated.
        // YT Music does not expose queue items via MediaSession.
        val message = when (app.packageName) {
            MusicApp.SPOTIFY_PACKAGE ->
                "Queue sync coming soon"
            MusicApp.YTMUSIC_PACKAGE ->
                "YT Music queue unavailable\nvia MediaSession"
            else ->
                "Queue unavailable for this app"
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                color = Color(0xFF555555),
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }

    @Composable
    private fun EmptyState(message: String) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0D0D)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = message, color = Color(0xFF555555), fontSize = 13.sp)
        }
    }

    override fun onSwipeLeft() {
        val size = MusicAppsRepository.getAllApps().size
        if (size == 0) return
        currentPage = (currentPage + 1).coerceAtMost(size - 1)
    }

    override fun onSwipeRight() {
        currentPage = (currentPage - 1).coerceAtLeast(0)
    }
}