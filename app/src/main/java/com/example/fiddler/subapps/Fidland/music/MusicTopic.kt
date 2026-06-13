package com.example.fiddler.subapps.Fidland.music

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.example.fiddler.subapps.Fidland.TopicPage
import kotlinx.coroutines.delay

/**
 * Phase 3 dashboard — Category 1: Music Player.
 *
 * Fixes applied vs original:
 *   1. Migrated from accompanist HorizontalPager to androidx.compose.foundation.pager.
 *   2. Album art — renders app.albumArt (Bitmap) via asImageBitmap() instead of
 *      a drawable res ID that was always null.
 *   3. Album art uses ContentScale.Crop so it fills the box from centre and
 *      bleeds out to the edges without distortion (letterboxing/stretching gone).
 *   4. Seek bar thumb is smaller via a custom thumb with SliderDefaults.Thumb
 *      sized to 4×14 dp — a slim pill rather than the default 4×44 dp target.
 *   5. Seek bar live tracking — a LaunchedEffect ticks every 500 ms while
 *      isPlaying, calling app.livePositionMs() to interpolate the position
 *      between MediaSession callbacks rather than freezing at last-known value.
 *   6. Time display driven by the same live position so elapsed time counts up
 *      smoothly while a track is playing.
 */
class MusicTopicCompose(context: Context) : TopicPage(context) {

    private val controller = MusicAppController(context)
    private var currentPage by mutableStateOf(0)

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val apps by MusicAppsRepository.appsFlow.collectAsState()

        if (apps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text("No music apps connected", color = Color.Gray, fontSize = 13.sp)
            }
            return
        }

        val pagerState = rememberPagerState(
            initialPage = currentPage.coerceAtMost(apps.size - 1),
            pageCount   = { apps.size }
        )

        HorizontalPager(
            state    = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        ) { page ->
            val app = apps[page]

            // Live seek position — ticks every 500 ms while playing
            var livePositionMs by remember(app.packageName) {
                mutableStateOf(app.livePositionMs())
            }
            LaunchedEffect(app.isPlaying, app.positionBaseMs, app.positionBaseTime) {
                if (app.isPlaying) {
                    while (true) {
                        livePositionMs = app.livePositionMs()
                        delay(500)
                    }
                } else {
                    livePositionMs = app.currentMs
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(top = 10.dp)
                    .padding(8.dp)
            ) {
                // --- Album art + track info overlay ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.6f)
                        // Clip so the cropped image doesn't overflow the box bounds
                        .clip(RectangleShape)
                ) {
                    val bitmap = app.albumArt
                    if (bitmap != null) {
                        // ContentScale.Crop fills the box from the centre of the image
                        // and bleeds out to whichever edge runs short, no distortion.
                        Image(
                            bitmap             = bitmap.asImageBitmap(),
                            contentDescription = "Album Art",
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF1A1A1A))
                        )
                    }

                    // Track info overlay on right 40%
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.4f)
                            .background(Color(0xCC000000))
                            .align(Alignment.CenterEnd)
                            .padding(6.dp)
                    ) {
                        Column(
                            modifier            = Modifier.fillMaxHeight(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text     = app.songTitle.ifBlank { "—" },
                                color    = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text     = app.artistName.ifBlank { "—" },
                                color    = Color(0xFFCCCCCC),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text     = app.albumName.ifBlank { "—" },
                                color    = Color(0xFF999999),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // --- Seek bar ---
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text     = formatTime(livePositionMs),
                        color    = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier.width(36.dp)
                    )
                    Slider(
                        value         = livePositionMs.toFloat(),
                        onValueChange = { newMs ->
                            livePositionMs = newMs.toInt()
                            controller.seekTo(app, newMs.toInt())
                        },
                        valueRange = 0f..app.totalMs.toFloat().coerceAtLeast(1f),
                        // Slim custom thumb — 4 dp wide × 14 dp tall pill.
                        // The default Material3 thumb has a 44 dp touch target
                        // which looks oversized in a compact dashboard.
                        thumb = {
                            SliderDefaults.Thumb(
                                interactionSource = remember {
                                    androidx.compose.foundation.interaction.MutableInteractionSource()
                                },
                                colors  = SliderDefaults.colors(thumbColor = Color.White),
                                enabled = true,
                                thumbSize = DpSize(width = 4.dp, height = 14.dp)
                            )
                        },
                        colors   = SliderDefaults.colors(
                            thumbColor         = Color.White,
                            activeTrackColor   = Color.White,
                            inactiveTrackColor = Color(0xFF444444)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text     = formatTime(app.totalMs),
                        color    = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier.width(36.dp)
                    )
                }

                // --- Playback controls ---
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically,
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = { controller.previousTrack(app) }) {
                        Image(
                            painter            = painterResource(id = com.example.fiddler.R.drawable.skip_previous),
                            contentDescription = "Previous"
                        )
                    }

                    IconButton(onClick = { controller.togglePlayPause(app) }) {
                        Image(
                            painter = painterResource(
                                id = if (app.isPlaying)
                                    com.example.fiddler.R.drawable.pause
                                else
                                    com.example.fiddler.R.drawable.play
                            ),
                            contentDescription = if (app.isPlaying) "Pause" else "Play"
                        )
                    }

                    IconButton(onClick = { controller.nextTrack(app) }) {
                        Image(
                            painter            = painterResource(id = com.example.fiddler.R.drawable.skip_next),
                            contentDescription = "Next"
                        )
                    }
                }
            }
        }

        currentPage = pagerState.currentPage
    }

    override fun onSwipeLeft() {
        currentPage = (currentPage + 1).coerceAtMost(
            MusicAppsRepository.appsFlow.value.size - 1
        )
    }

    override fun onSwipeRight() {
        currentPage = (currentPage - 1).coerceAtLeast(0)
    }

    private fun formatTime(ms: Int): String {
        val totalSecs = (ms / 1000).coerceAtLeast(0)
        val minutes   = totalSecs / 60
        val seconds   = totalSecs % 60
        return "%d:%02d".format(minutes, seconds)
    }
}