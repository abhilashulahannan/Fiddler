package com.example.fiddler.subapps.Fidland.phs3.music

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.subapps.Fidland.music.MusicApp
import com.example.fiddler.subapps.Fidland.music.MusicAppsRepository
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler
import com.example.fiddler.subapps.Fidland.phs3.shared.AudioVisualizerEngine
import com.example.fiddler.subapps.Fidland.phs3.shared.EqualizerContext
import com.example.fiddler.subapps.Fidland.phs3.shared.EqualizerIndicator
import com.example.fiddler.subapps.Fidland.phs3.shared.EqualizerMode
import com.example.fiddler.subapps.Fidland.ui.IslandConfig

class MusicPhs3Handler(
    private val packageName: String,
    private val context: Context,
) : Phs3Handler {

    override val label: String = "Music"
    override val hasLocationA: Boolean = true
    override val locationAPriority: Int = 0

    // Engine and mode live on the handler instance so they survive rotation.
    // Phs3Manager holds a reference to this handler for the duration of the
    // track session — engine.start()/stop() are called by DisposableEffect
    // when Indicator() enters/leaves composition, but the Visualizer instance
    // and StateFlow are never recreated mid-session.
    private val engine = AudioVisualizerEngine(
        context  = context,
        barCount = IslandConfig.MUSIC_EQ_BAR_COUNT,
    )

    // Pass the StateFlow directly — EqualizerIndicator collects it with
    // collectAsState(), so recomposition is driven by the flow itself,
    // not a polling loop that can die after rotation.
    private val equalizerMode = EqualizerMode.Live(
        amplitudes = engine.amplitudes,
        context    = EqualizerContext.MUSIC,
    )

    @Composable
    override fun LocationAContent() {
        val app = activeApp()
        AlbumArtSpinner(
            app  = app,
            size = 22.dp
        )
    }

    @Composable
    private fun activeApp(): MusicApp? {
        val apps by MusicAppsRepository.appsFlow.collectAsState()
        return apps.firstOrNull { it.packageName == packageName }
    }

    @Composable
    override fun Indicator() {
        val app = activeApp()

        // start() / stop() mirror Indicator() entering and leaving composition.
        // engine.stop() pauses the Visualizer on rotation-away; engine.start()
        // resumes it on rotation-back. Same Visualizer instance — no re-init,
        // no session-0 collision on Samsung.
        DisposableEffect(engine) {
            engine.start()
            onDispose { engine.stop() }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IslandConfig.MUSIC_EQ_TEXT_GAP),
            modifier = Modifier.width(IslandConfig.MUSIC_INDICATOR_WIDTH),
        ) {
            Box(modifier = Modifier.width(IslandConfig.MUSIC_EQ_WIDTH)) {
                EqualizerIndicator(
                    mode      = equalizerMode,
                    barCount  = IslandConfig.MUSIC_EQ_BAR_COUNT,
                    maxHeight = IslandConfig.MUSIC_EQ_MAX_HEIGHT,
                    color     = if (app?.isPlaying == true) Color.White else Color(0xFF555555),
                )
            }

            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.width(IslandConfig.MUSIC_TEXT_COLUMN_WIDTH),
            ) {
                Text(
                    text       = app?.songTitle?.ifBlank { "Not playing" } ?: "Not playing",
                    color      = Color.White,
                    fontSize   = 9.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.fillMaxWidth(),
                )
                Text(
                    text       = app?.artistName?.ifBlank { "" } ?: "",
                    color      = Color(0xFFAAAAAA),
                    fontSize   = 7.sp,
                    lineHeight = 8.sp,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    @Composable
    override fun State5Content() {
        val app = activeApp()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 22.dp)
                .padding(8.dp),
        ) {
            SyncedLyricsView(app)
        }
    }
}