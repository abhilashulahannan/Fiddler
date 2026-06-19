package com.example.fiddler.subapps.Fidland.phs3.record

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler
import com.example.fiddler.subapps.Fidland.phs3.shared.AudioVisualizerEngine
import com.example.fiddler.subapps.Fidland.phs3.shared.EqualizerContext
import com.example.fiddler.subapps.Fidland.phs3.shared.EqualizerIndicator
import com.example.fiddler.subapps.Fidland.phs3.shared.EqualizerMode

/**
 * Phs3 module — Phone Recorder Observer.
 *
 * This module does NOT record audio itself. It observes the phone's built-in
 * Voice Recorder app via [RecorderNotificationSource] and surfaces its state
 * in the phs3 pill.
 *
 * ── Location a  (left of hole-punch) ─────────────────────────────────────────
 *   Lottie `recording_pulse.json` — red breathing dot.
 *   • RECORDING → plays at full speed.
 *   • PAUSED    → plays at 0.3× (slow breathing).
 *
 * ── Location b  (immediate right of hole-punch) ────────────────────────────
 *   [EqualizerIndicator] driven by [AudioVisualizerEngine] (session 0 = system
 *   mix, which captures the mic stream while the recorder is active).
 *   Falls back to Simulated(RECORD) while paused.
 *
 * ── Location c  (right of b) ──────────────────────────────────────────────
 *   Elapsed time string parsed from the recorder notification, e.g. "02:14".
 *   Greyed-out while PAUSED.
 *
 * ── State 5 (ControlsPanel — long-press the pill to open) ─────────────────
 *   Shows elapsed time + state badge.
 *   Single full-width button: "Open Recorder" — launches the recorder app.
 *   (We cannot send pause/stop commands to another app's recording session.)
 *
 * ── Lottie asset ──────────────────────────────────────────────────────────
 *   app/src/main/assets/lottie/recording_pulse.json
 */
class RecordPhs3Handler(
    private val source: RecorderNotificationSource,
    context: android.content.Context,
) : Phs3Handler {

    override val label: String = "Record"

    // Engine lives on the handler — survives rotation, no re-init on re-entry.
    private val engine = AudioVisualizerEngine(context, barCount = 5)

    private val liveMode = EqualizerMode.Live(
        amplitudes = engine.amplitudes,
        context    = EqualizerContext.RECORD,
    )

    // ── Location a — Lottie pulse ─────────────────────────────────────────────

    @Composable
    fun LocationAIndicator() {
        val snapshot by source.flow.collectAsState()
        if (!snapshot.isActive) return

        val composition by rememberLottieComposition(
            LottieCompositionSpec.Asset("lottie/recording_pulse.json")
        )
        val speed = if (snapshot.state == RecordingState.PAUSED) 0.3f else 1f

        LottieAnimation(
            composition = composition,
            iterations  = LottieConstants.IterateForever,
            speed       = speed,
            modifier    = Modifier.size(20.dp),
        )
    }

    // ── Indicator — location b (visualizer) + location c (timer) ─────────────

    @Composable
    override fun Indicator() {
        val snapshot by source.flow.collectAsState()

        val isRecording = snapshot.state == RecordingState.RECORDING
        val isPaused    = snapshot.state == RecordingState.PAUSED

        val timerColor by animateColorAsState(
            targetValue   = if (isPaused) Color(0xFF666666) else Color.White,
            animationSpec = tween(durationMillis = 300),
            label         = "record_timer_color",
        )

        // Start engine when active, stop when paused/idle or composable leaves.
        // Engine.start() is idempotent so toggling recording state is safe.
        DisposableEffect(engine) {
            engine.start()
            onDispose { engine.stop() }
        }

        Row(
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // ── Location b — live visualizer ─────────────────────────────────
            if (isRecording) {
                EqualizerIndicator(
                    mode      = liveMode,
                    barCount  = 5,
                    maxHeight = 14.dp,
                    color     = Color(0xFFE84A4A),
                )
            } else {
                // Paused — gentle simulated breathing
                EqualizerIndicator(
                    mode      = EqualizerMode.Simulated(EqualizerContext.RECORD),
                    barCount  = 5,
                    maxHeight = 10.dp,
                    color     = Color(0xFF884444),
                )
            }

            // ── Location c — elapsed timer ────────────────────────────────────
            Text(
                text       = snapshot.elapsedFormatted,
                fontSize   = 11.sp,
                color      = timerColor,
                fontWeight = if (isRecording) FontWeight.Medium else FontWeight.Normal,
                maxLines   = 1,
            )
        }
    }

    // ── State 5 — controls panel ──────────────────────────────────────────────

    @Composable
    override fun State5Content() {
        val snapshot = source.flow.collectAsState().value
        val context  = LocalContext.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text       = snapshot.takeLabel,
                        color      = Color.White,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                    )
                    Text(
                        text     = snapshot.elapsedFormatted,
                        color    = Color(0xFF888888),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                StateBadge(snapshot.state)
            }

            S5Divider()

            // ── "Open Recorder" button ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E1E1E))
                    .clickable {
                        val pkg = snapshot.sourcePackage.ifEmpty { return@clickable }
                        val intent = context.packageManager
                            .getLaunchIntentForPackage(pkg)
                        intent?.let { context.startActivity(it) }
                    }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text       = "Open Recorder  →",
                    color      = Color(0xFFE84A4A),
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            // ── Hint ──────────────────────────────────────────────────────────
            Text(
                text     = "Use the recorder app to pause or stop.",
                color    = Color(0xFF555555),
                fontSize = 9.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 6.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Private composable helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StateBadge(state: RecordingState) {
    val (label, bg, fg) = when (state) {
        RecordingState.RECORDING -> Triple("● REC",    Color(0xFF3A1010), Color(0xFFFF3B30))
        RecordingState.PAUSED    -> Triple("⏸ PAUSED", Color(0xFF1A1A2E), Color(0xFF4FC3F7))
        RecordingState.IDLE      -> Triple("",          Color.Transparent, Color.Transparent)
    }
    if (label.isEmpty()) return

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text          = label,
            color         = fg,
            fontSize      = 9.sp,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun S5Divider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Color(0xFF2A2A2A))
    )
}