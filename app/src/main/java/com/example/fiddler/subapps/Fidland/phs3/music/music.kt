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
import com.example.fiddler.subapps.Fidland.phs3.BlockAffinity
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler
import com.example.fiddler.subapps.Fidland.phs3.shared.AudioVisualizerEngine
import com.example.fiddler.subapps.Fidland.phs3.shared.EqualizerContext
import com.example.fiddler.subapps.Fidland.phs3.shared.EqualizerIndicator
import com.example.fiddler.subapps.Fidland.phs3.shared.EqualizerMode
import com.example.fiddler.subapps.Fidland.ui.IslandConfig

/**
 * ── §B7 Blocks (this pass) ───────────────────────────────────────────────
 * Splits the old fused equalizer+name Row into 2 independently-placed §B2
 * blocks, via [Phs3Handler.hasSecondaryBlock] — the same mechanic Ring Mode
 * proved first and Battery has since adopted (see their class docs for the
 * shared plumbing): [Indicator] (the live equalizer, [BlockAffinity.DYNAMIC])
 * and [SecondaryIndicator] (the title/artist column, [BlockAffinity.RIGHT_ANCHOR]).
 * The two share a lifecycle — they appear/disappear together on rotation,
 * same as the fused Row did — which is exactly what rendering them as
 * [hasSecondaryBlock]'s primary+secondary pair gives for free. Album art is
 * *not* part of this split: it stays in [LocationAContent], tied to
 * qualification rather than to holding the active slot, so it keeps
 * spinning in location-a even while another entity occupies the right zone.
 *
 * In `BOTH_EXPANDED`, the equalizer can now genuinely land in the left zone
 * when the §B2 balancer resolves it there — same live cross-zone behavior
 * Ring Mode/Battery's icon blocks get. `RIGHT_EXPANDED` has no left zone to
 * move into, so both blocks stay put next to each other there, unchanged
 * from before this pass.
 */
class MusicPhs3Handler(
    private val packageName: String,
    private val context: Context,
) : Phs3Handler {

    override val label: String = "Music"
    override val hasLocationA: Boolean = true
    override val locationAPriority: Int = 0

    override val hasSecondaryBlock: Boolean = true
    override val blockAffinity: BlockAffinity = BlockAffinity.DYNAMIC
    override val secondaryBlockAffinity: BlockAffinity = BlockAffinity.RIGHT_ANCHOR

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

    // ── Indicator (primary block — live equalizer, BlockAffinity.DYNAMIC) ───

    @Composable
    override fun Indicator() {
        val app = activeApp()

        // start() / stop() mirror Indicator() entering and leaving composition.
        // engine.stop() pauses the Visualizer on rotation-away; engine.start()
        // resumes it on rotation-back. Same Visualizer instance — no re-init,
        // no session-0 collision on Samsung. Indicator/SecondaryIndicator
        // always render together (see class doc's "share a lifecycle" note),
        // so tying this to Indicator alone is equivalent to tying it to the
        // old fused Row.
        DisposableEffect(engine) {
            engine.start()
            onDispose { engine.stop() }
        }

        Box(modifier = Modifier.width(IslandConfig.MUSIC_EQ_WIDTH)) {
            EqualizerIndicator(
                mode      = equalizerMode,
                barCount  = IslandConfig.MUSIC_EQ_BAR_COUNT,
                maxHeight = IslandConfig.MUSIC_EQ_MAX_HEIGHT,
                color     = if (app?.isPlaying == true) Color.White else Color(0xFF555555),
            )
        }
    }

    // ── SecondaryIndicator (secondary block — title/artist, RIGHT_ANCHOR) ───

    /**
     * Carries [IslandConfig.MUSIC_EQ_TEXT_GAP] as a baked-in start-padding —
     * the old fused Row got this gap for free from `Arrangement.spacedBy`;
     * now that [Indicator] and this block are two separately-placed §B2
     * blocks (`RightIndicatorContent`'s Row has no spacedBy of its own —
     * see overlay_fidland_pill.kt), each block owns its own spacing the same
     * way Ring Mode's/Battery's [SecondaryIndicator] does, so the gap
     * survives even when the two blocks end up rendering in different zones.
     */
    @Composable
    override fun SecondaryIndicator() {
        val app = activeApp()

        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(start = IslandConfig.MUSIC_EQ_TEXT_GAP)
                .width(IslandConfig.MUSIC_TEXT_COLUMN_WIDTH),
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