package com.example.fiddler.subapps.Fidland.phs3.music

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.subapps.Fidland.music.MusicApp
import com.example.fiddler.subapps.Fidland.music.lyrics.LyricLine
import com.example.fiddler.subapps.Fidland.music.lyrics.LyricsRepository
import com.example.fiddler.subapps.Fidland.music.lyrics.LyricsState
import kotlinx.coroutines.delay

/**
 * Phs3 Music — Component 4: time-synced lyrics panel.
 *
 * Shown in State 5 (EXPANDED_PHS3), entered by long-pressing the pill while
 * the music phs3 indicator (State 3) is active. See [MusicPhs3Handler.ControlsPanel].
 *
 * Lyrics are sourced from LRCLIB via [LyricsRepository], which serves from
 * the on-device cache of the 1000 most-listened songs when available (works
 * fully offline) and falls back to a network fetch otherwise.
 *
 * The currently active line is highlighted and auto-scrolled to center using
 * [app]'s live playback position ([MusicApp.livePositionMs]).
 */
@Composable
fun SyncedLyricsView(app: MusicApp?) {
    val context = LocalContext.current
    val repo = remember { LyricsRepository.get(context) }

    var lyricsState by remember { mutableStateOf<LyricsState>(LyricsState.Loading) }

    // Re-fetch whenever the track identity changes.
    val trackKey = "${app?.songTitle.orEmpty()}|${app?.artistName.orEmpty()}"
    LaunchedEffect(trackKey) {
        if (app == null || app.songTitle.isBlank()) {
            lyricsState = LyricsState.NotFound
            return@LaunchedEffect
        }
        lyricsState = LyricsState.Loading
        lyricsState = repo.getLyrics(
            trackName = app.songTitle,
            artistName = app.artistName,
            albumName = app.albumName,
            durationSec = app.totalMs / 1000
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (val state = lyricsState) {
            is LyricsState.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )

            is LyricsState.NotFound -> Text(
                text = "No lyrics found",
                color = Color(0xFF888888),
                fontSize = 13.sp
            )

            is LyricsState.Instrumental -> Text(
                text = "Instrumental",
                color = Color(0xFF888888),
                fontSize = 13.sp
            )

            is LyricsState.Plain -> PlainLyricsList(state.text)

            is LyricsState.Synced -> SyncedLyricsList(app, state.lines)
        }
    }
}

@Composable
private fun PlainLyricsList(text: String) {
    val lines = remember(text) { text.lines() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp)
    ) {
        items(lines) { line ->
            Text(
                text = line,
                color = Color(0xFFCCCCCC),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun SyncedLyricsList(app: MusicApp?, lines: List<LyricLine>) {
    val listState = rememberLazyListState()

    // Live playback position, ticking while playing — mirrors MusicTopicCompose's
    // seek-bar interpolation so the highlight stays in sync between MediaSession
    // callbacks.
    var livePositionMs by remember(app?.packageName) { mutableStateOf(app?.livePositionMs() ?: 0) }
    LaunchedEffect(app?.isPlaying, app?.positionBaseMs, app?.positionBaseTime) {
        if (app?.isPlaying == true) {
            while (true) {
                livePositionMs = app.livePositionMs()
                delay(200)
            }
        } else {
            livePositionMs = app?.currentMs ?: 0
        }
    }

    // Index of the active line: the last line whose timeMs <= current position.
    val activeIndex = remember(lines, livePositionMs) {
        var idx = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= livePositionMs) idx = i else break
        }
        idx
    }

    // Auto-scroll so the active line sits roughly centered in the panel.
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            scrollToCentered(listState, activeIndex)
        }
    }

    if (lines.isEmpty()) {
        Text(text = "No lyrics found", color = Color(0xFF888888), fontSize = 13.sp)
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(
            bottom = 24.dp,
            start = 16.dp,
            end = 16.dp
        )
    ) {
        item {
            Spacer(modifier = Modifier.height(25.dp))
        }

        itemsIndexed(lines) { index, line ->
            val isActive = index == activeIndex

            Text(
                text = line.text.ifBlank { "···" },
                color = if (isActive) Color.White else Color(0xFF777777),
                fontSize = if (isActive) 15.sp else 13.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }
    }
}

/** Scrolls so [index] lands near the vertical center of the visible viewport. */
private suspend fun scrollToCentered(listState: LazyListState, index: Int) {
    val layoutInfo = listState.layoutInfo
    val viewportHeight = layoutInfo.viewportSize.height
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    val itemHeight = item?.size ?: 0
    val offset = -(viewportHeight / 2) + (itemHeight / 2) +100
    listState.animateScrollToItem(index, offset)
}