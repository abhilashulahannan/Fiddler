package com.example.fiddler.subapps.Fidland.music

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.subapps.Fidland.TopicPage
import com.example.fiddler.subapps.Fidland.phs3.shared.AudioVisualizerEngine
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

// ─── EQ seekbar config ────────────────────────────────────────────────────────

/**
 * Visual configuration for [LiveEqualizerSeekBar].
 *
 * @param barCount        Number of vertical bars drawn across the width.
 * @param barSpacingDp    Pixel gap between bars.
 * @param maxHeightFrac   Maximum bar height as a fraction of the composable height (0..1).
 *                        1.0 = bars can reach the very top; 0.6 = capped at 60%.
 * @param minHeightFrac   Minimum bar height fraction when silent / paused.
 * @param cornerRadius    Corner radius applied to each bar (px).
 * @param activeColor     Color for bars left of the playhead (played portion).
 * @param inactiveColor   Color for bars right of the playhead (unplayed portion).
 */
data class EqSeekBarConfig(
    val barCount: Int = 48,
    val barSpacingDp: Dp = 2.dp,
    val maxHeightFrac: Float = 0.92f,
    val minHeightFrac: Float = 0.06f,
    val cornerRadius: Float = 2f,
    val activeColor: Color = Color(0xFF1DB954),
    val inactiveColor: Color = Color(0xFF333333),
)

// Per-app colour presets — pass into EqSeekBarConfig.activeColor
object EqColors {
    val Spotify  = Color(0xFF1DB954)
    val YTMusic  = Color(0xFFFF0000)
    val Fallback = Color(0xFFAAAAAA)

    fun forPackage(packageName: String) = when (packageName) {
        MusicApp.SPOTIFY_PACKAGE  -> Spotify
        MusicApp.YTMUSIC_PACKAGE  -> YTMusic
        else                      -> Fallback
    }
}

// ─── Live EQ seekbar ──────────────────────────────────────────────────────────

/**
 * Equalizer-style seekbar driven by real FFT data from [AudioVisualizerEngine].
 *
 * Bar heights are smoothly lerped toward the latest FFT amplitudes inside a
 * coroutine loop (16 ms ticks ≈ 60 fps). This avoids calling animateFloatAsState
 * inside a List constructor, which violates Compose's rules of hooks.
 *
 * Bars left of the playhead → [config.activeColor].
 * Bars right              → [config.inactiveColor].
 * Tap / drag anywhere to seek.
 */
@Composable
fun LiveEqualizerSeekBar(
    valueMs: Int,
    totalMs: Int,
    isPlaying: Boolean,
    engine: AudioVisualizerEngine,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
    config: EqSeekBarConfig = EqSeekBarConfig(),
) {
    val rawAmplitudes by engine.amplitudes.collectAsState()

    // Smoothed bar heights stored as Compose state so Canvas redraws on change.
    // Initialised to minHeightFrac so bars start at their resting position.
    val smoothed = remember(config.barCount) {
        mutableStateOf(FloatArray(config.barCount) { config.minHeightFrac })
    }

    // Lerp loop — runs at ~60 fps, blends smoothed values toward the latest
    // FFT targets. Alpha 0.25 gives a snappy but not jarring response.
    // Cancels automatically when the composable leaves composition.
    LaunchedEffect(isPlaying, config.barCount) {
        val alpha = 0.25f
        while (true) {
            val raw = rawAmplitudes          // snapshot; FloatArray is read-only here
            val current = smoothed.value.copyOf()
            var changed = false
            for (i in 0 until config.barCount) {
                val target = if (isPlaying && raw.isNotEmpty()) {
                    val srcIdx = (i.toFloat() / config.barCount * raw.size)
                        .toInt().coerceIn(0, raw.size - 1)
                    raw[srcIdx].coerceIn(config.minHeightFrac, config.maxHeightFrac)
                } else {
                    config.minHeightFrac
                }
                val next = current[i] + (target - current[i]) * alpha
                if (kotlin.math.abs(next - current[i]) > 0.001f) changed = true
                current[i] = next
            }
            if (changed) smoothed.value = current
            delay(16L)   // ~60 fps
        }
    }

    // Drag state — override position locally for instant visual feedback
    var dragPositionMs by remember { mutableStateOf<Int?>(null) }
    val displayMs = dragPositionMs ?: valueMs

    Canvas(
        modifier = modifier
            .pointerInput(totalMs) {
                detectTapGestures { offset ->
                    onSeek(((offset.x / size.width) * totalMs).toInt().coerceIn(0, totalMs))
                }
            }
            .pointerInput(totalMs) {
                detectHorizontalDragGestures(
                    onDragEnd    = { dragPositionMs?.let { onSeek(it) }; dragPositionMs = null },
                    onDragCancel = { dragPositionMs = null },
                ) { change, _ ->
                    dragPositionMs = ((change.position.x / size.width) * totalMs)
                        .toInt().coerceIn(0, totalMs)
                }
            }
    ) {
        val fracs     = smoothed.value
        val progress  = if (totalMs > 0) displayMs.toFloat() / totalMs else 0f
        val spacingPx = config.barSpacingDp.toPx()
        val barW      = ((size.width - spacingPx * (config.barCount - 1)) / config.barCount)
            .coerceAtLeast(1f)

        for (i in 0 until config.barCount) {
            val frac  = fracs[i].coerceIn(config.minHeightFrac, config.maxHeightFrac)
            val barH  = frac * size.height
            val x     = i * (barW + spacingPx)
            val y     = size.height - barH
            val color = if (i.toFloat() / config.barCount < progress)
                config.activeColor else config.inactiveColor

            drawRoundRect(
                color        = color,
                topLeft      = Offset(x, y),
                size         = Size(barW, barH),
                cornerRadius = CornerRadius(config.cornerRadius),
            )
        }
    }
}

// ─── Music topic ─────────────────────────────────────────────────────────────

/**
 * Phase 3 dashboard — Category 1: Music Player.
 *
 * Layout (top → bottom inside HorizontalPager):
 *   ┌──────────────────────────────────────────┐
 *   │  app name                      ● ○ ○     │  ← header row
 *   ├──────────────────────────────────────────┤
 *   │                                          │
 *   │            album art (cropped)           │  ← expands to fill remaining
 *   │                      ┌─────────────────┐ │    space above controls
 *   │                      │ title / artist  │ │  ← semi-transparent overlay
 *   │                      └─────────────────┘ │
 *   │▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒│  ← live EQ seekbar, bottom edge
 *   ├──────────────────────────────────────────┤
 *   │  0:00                              3:20  │  ← time labels
 *   ├──────────────────────────────────────────┤
 *   │  ⇄    ⏮    ⏯    ⏭    ♥              │  ← controls
 *   └──────────────────────────────────────────┘
 */
class MusicTopicCompose(context: Context) : TopicPage(context) {

    private val controller = MusicAppController(context)
    private var currentPage by mutableStateOf(0)

    // One shared engine — barCount here is what the engine captures.
    // The EqSeekBarConfig.barCount is the visual bar density and can differ.
    // Started lazily on first Content() call; stopped in onDestroy().
    private val visualizerEngine = AudioVisualizerEngine(context, barCount = 64)
    private var engineStarted = false

    override fun onDestroy() { visualizerEngine.stop() }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        // Start the visualizer engine once when Content first enters composition.
        // LaunchedEffect(Unit) runs exactly once per composition lifetime.
        // onDestroy() stops it when the service tears down.
        LaunchedEffect(Unit) {
            if (!engineStarted) {
                visualizerEngine.start()
                engineStarted = true
            }
        }

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

        val playingIndex = apps.indexOfFirst { it.isPlaying }.takeIf { it >= 0 }
        if (playingIndex != null) currentPage = playingIndex

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
            val app      = apps[page]
            val hasTrack = app.songTitle.isNotBlank()

            // Per-app EQ colour preset
            val eqActiveColor = EqColors.forPackage(app.packageName)

            val eqConfig = EqSeekBarConfig(
                barCount      = 50,          // visual bar density — tweak freely
                barSpacingDp  = 2.dp,        // gap between bars
                maxHeightFrac = 0.5f,       // bars reach 92% of the EQ strip height at peak
                minHeightFrac = 0.06f,       // minimum bar height when silent/paused
                cornerRadius  = 5f,          // rounded bar tops
                activeColor   = eqActiveColor,
                inactiveColor = Color(0x55FFFFFF), // dim white for unplayed portion
            )

            // Live seek position ticks every 500 ms while playing
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
                    .padding(horizontal = 1.dp)
            ) {
                // ── Header: app name + pager dots ────────────────────────────
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    Text(
                        text     = app.appName,
                        color    = Color(0xFFAAAAAA),
                        fontSize = 11.sp,
                    )
                    if (apps.size > 1) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            repeat(apps.size) { index ->
                                val selected = index == pagerState.currentPage
                                Box(
                                    modifier = Modifier
                                        .size(if (selected) 6.dp else 5.dp)
                                        .clip(CircleShape)
                                        .background(if (selected) Color.White else Color(0xFF555555))
                                )
                            }
                        }
                    }
                }

                // ── Album art box — expands to fill remaining vertical space
                //    above the controls. The live EQ seekbar is overlaid at its
                //    bottom edge, edge-to-edge with the art. ──────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)                // take all remaining height
                        .clip(RectangleShape)
                        .clickable { controller.launchApp(app) }
                ) {
                    // Album art (or placeholder)
                    val bitmap = app.albumArt
                    if (bitmap != null) {
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

                    if (!hasTrack) {
                        Box(
                            modifier         = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text     = "Nothing playing",
                                color    = Color(0xFF666666),
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        // Track info overlay — right 40%, vertically centred
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
                                    text     = app.songTitle,
                                    color    = Color.White,
                                    fontSize = 13.sp,
                                    maxLines = 2,
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

                    // ── Live EQ seekbar — pinned to the bottom of the album art, edge-to-edge.
                    LiveEqualizerSeekBar(
                        valueMs   = livePositionMs,
                        totalMs   = app.totalMs,
                        isPlaying = app.isPlaying,
                        engine    = visualizerEngine,
                        onSeek    = { newMs ->
                            livePositionMs = newMs
                            controller.seekTo(app, newMs)
                        },
                        config   = eqConfig,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .align(Alignment.BottomCenter),
                    )
                }

                // ── Time labels — below album art, aligned to outer edges ────
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Text(
                        text     = formatTime(livePositionMs),
                        color    = Color(0xFFAAAAAA),
                        fontSize = 10.sp,
                    )
                    Text(
                        text     = formatTime(app.totalMs),
                        color    = Color(0xFFAAAAAA),
                        fontSize = 10.sp,
                    )
                }

                // ── Playback controls: [shuffle] [prev] [play/pause] [next] [heart]
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment     = Alignment.CenterVertically,
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    val favoriteAction = app.customActions.firstOrNull {
                        it.name.contains("favorite", ignoreCase = true) ||
                                it.name.contains("like",     ignoreCase = true) ||
                                it.name.contains("library",  ignoreCase = true) ||
                                it.name.contains("saved",    ignoreCase = true)
                    }
                    val shuffleAction = app.customActions.firstOrNull {
                        it.name.contains("shuffle", ignoreCase = true)
                    }

                    // Shuffle
                    IconButton(
                        onClick  = { if (shuffleAction != null) controller.toggleFavorite(app, shuffleAction) },
                        modifier = Modifier.size(35.dp),
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = when {
                                shuffleAction == null  -> Color(0xFF444444)
                                shuffleAction.isActive -> eqActiveColor
                                else                   -> Color(0xFFAAAAAA)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Previous
                    IconButton(
                        onClick  = { controller.previousTrack(app) },
                        modifier = Modifier.size(35.dp),
                    ) {
                        Image(
                            painter            = painterResource(id = com.example.fiddler.R.drawable.skip_previous),
                            contentDescription = "Previous",
                            modifier           = Modifier.size(26.dp)
                        )
                    }

                    // Play / Pause
                    IconButton(
                        onClick  = { controller.togglePlayPause(app) },
                        modifier = Modifier.size(35.dp),
                    ) {
                        Image(
                            painter = painterResource(
                                id = if (app.isPlaying)
                                    com.example.fiddler.R.drawable.pause
                                else
                                    com.example.fiddler.R.drawable.play
                            ),
                            contentDescription = if (app.isPlaying) "Pause" else "Play",
                            modifier           = Modifier.size(35.dp)
                        )
                    }

                    // Next
                    IconButton(
                        onClick  = { controller.nextTrack(app) },
                        modifier = Modifier.size(35.dp),
                    ) {
                        Image(
                            painter            = painterResource(id = com.example.fiddler.R.drawable.skip_next),
                            contentDescription = "Next",
                            modifier           = Modifier.size(26.dp)
                        )
                    }

                    // Heart / Like
                    IconButton(
                        onClick  = { if (favoriteAction != null) controller.toggleFavorite(app, favoriteAction) },
                        modifier = Modifier.size(35.dp),
                    ) {
                        Icon(
                            imageVector = if (favoriteAction?.isActive == true)
                                Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Like",
                            tint = when {
                                favoriteAction == null  -> Color(0xFF444444)
                                favoriteAction.isActive -> eqActiveColor
                                else                    -> Color.White
                            },
                            modifier = Modifier.size(20.dp)
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
        return "%d:%02d".format(totalSecs / 60, totalSecs % 60)
    }
}