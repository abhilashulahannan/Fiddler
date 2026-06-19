package com.example.fiddler.subapps.Fidland.phs3.shared

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow

/**
 * Animated equalizer bar visualizer for phs3 modules.
 *
 * Bars grow symmetrically outward from a center axis — up and down simultaneously.
 * [maxHeight] is the total bar height at full amplitude (half above, half below center).
 *
 * Usage:
 *   // Simulated (no real audio source needed):
 *   EqualizerIndicator(mode = EqualizerMode.Simulated())
 *
 *   // Live (pass the StateFlow from AudioVisualizerEngine directly):
 *   EqualizerIndicator(mode = EqualizerMode.Live(engine.amplitudes))
 *
 * Each context carries its own animation character:
 *   - CALL   : moderate speed, mid-range bounce — voice cadence
 *   - MUSIC  : faster, wider swing — rhythmic energy
 *   - RECORD : slow, breathing pulse — ambient / monitoring feel
 */

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

enum class EqualizerContext { CALL, MUSIC, RECORD }

sealed class EqualizerMode {
    /** Placeholder: drives bars with internal sine waves, no real audio needed. */
    data class Simulated(val context: EqualizerContext = EqualizerContext.MUSIC) : EqualizerMode()

    /**
     * Live: pass the StateFlow<FloatArray> from AudioVisualizerEngine directly.
     * No polling loop — Compose collects the flow and recomposes on each FFT frame.
     */
    data class Live(
        val amplitudes: StateFlow<FloatArray>,
        val context: EqualizerContext = EqualizerContext.MUSIC,
    ) : EqualizerMode()
}

/**
 * Compact equalizer strip — typically placed inside an [Indicator] row.
 *
 * @param mode         How amplitude data is sourced (simulated or live).
 * @param barCount     Number of vertical bars (5–7 looks best at small sizes).
 * @param barWidth     Width of each bar.
 * @param barSpacing   Gap between bars.
 * @param maxHeight    Total bar height at full amplitude (split evenly above + below center).
 * @param minHeight    Minimum total bar height when silent (keeps bars visible).
 * @param color        Bar fill color.
 */
@Composable
fun EqualizerIndicator(
    mode: EqualizerMode = EqualizerMode.Simulated(),
    barCount: Int = 6,
    barWidth: Dp = 3.dp,
    barSpacing: Dp = 2.dp,
    maxHeight: Dp = 16.dp,
    minHeight: Dp = 3.dp,
    color: Color = Color.White,
) {
    val ctx = when (mode) {
        is EqualizerMode.Simulated -> mode.context
        is EqualizerMode.Live      -> mode.context
    }
    val animParams = animParamsFor(ctx)

    // ── Simulated mode: one phase-staggered infinite animation per bar ────────
    val infiniteTransition = rememberInfiniteTransition(label = "eq")
    val simulatedFractions: List<Float> = List(barCount) { i ->
        infiniteTransition.animateFloat(
            initialValue = animParams.minFraction,
            targetValue  = animParams.maxFraction,
            animationSpec = infiniteRepeatable(
                animation  = tween(
                    durationMillis = animParams.durationMs + i * animParams.phaseOffsetMs,
                    easing         = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "eq_bar_$i",
        ).value
    }

    // ── Live mode: collect StateFlow directly — no poll loop ──────────────────
    // collectAsState() subscribes to the flow and triggers recomposition on
    // every new FFT emission. Safe to call unconditionally; when mode is
    // Simulated the flow value is simply ignored.
    val liveAmplitudes by (
            if (mode is EqualizerMode.Live) mode.amplitudes
            else null
            ).let { flow ->
            flow?.collectAsState() ?: androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(FloatArray(barCount) { 0f })
            }
        }

    // ── Resolve per-bar fractions ─────────────────────────────────────────────
    val fractions: List<Float> = when (mode) {
        is EqualizerMode.Simulated -> simulatedFractions
        is EqualizerMode.Live      -> List(barCount) { i ->
            liveAmplitudes.getOrElse(i) { liveAmplitudes.lastOrNull() ?: 0f }.coerceIn(0f, 1f)
        }
    }

    // ── Draw ─────────────────────────────────────────────────────────────────
    val totalWidth = barWidth * barCount + barSpacing * (barCount - 1)

    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(barSpacing),
        modifier = Modifier
            .width(totalWidth)
            .height(maxHeight),
    ) {
        // animateFloatAsState must be called once per bar at the top level of
        // the composable tree — calling it inside forEach is valid here because
        // barCount is stable for the lifetime of this composable instance.
        val animatedFractions = fractions.mapIndexed { i, fraction ->
            val animated by animateFloatAsState(
                targetValue   = fraction,
                animationSpec = tween(durationMillis = 80, easing = LinearOutSlowInEasing),
                label         = "bar_anim_$i",
            )
            if (mode is EqualizerMode.Live) animated else fraction
        }

        animatedFractions.forEach { fraction ->
            val halfMax = maxHeight / 2f
            val halfMin = minHeight / 2f

            Canvas(
                modifier = Modifier
                    .width(barWidth)
                    .height(maxHeight),
            ) {
                val halfBarPx = (halfMin.toPx() + (halfMax.toPx() - halfMin.toPx()) * fraction)
                    .coerceAtLeast(halfMin.toPx())
                val cx = size.width / 2f
                val cy = size.height / 2f
                val rx = size.width / 2f

                drawRoundRect(
                    color        = color,
                    topLeft      = Offset(cx - rx, cy - halfBarPx),
                    size         = Size(size.width, halfBarPx * 2f),
                    cornerRadius = CornerRadius(rx, rx),
                )
            }
        }
    }
}

private data class AnimParams(
    val minFraction: Float,
    val maxFraction: Float,
    val durationMs: Int,
    val phaseOffsetMs: Int,
)

private fun animParamsFor(ctx: EqualizerContext): AnimParams = when (ctx) {
    EqualizerContext.CALL -> AnimParams(
        minFraction   = 0.15f,
        maxFraction   = 0.80f,
        durationMs    = 420,
        phaseOffsetMs = 70,
    )
    EqualizerContext.MUSIC -> AnimParams(
        minFraction   = 0.10f,
        maxFraction   = 1.00f,
        durationMs    = 280,
        phaseOffsetMs = 50,
    )
    EqualizerContext.RECORD -> AnimParams(
        minFraction   = 0.08f,
        maxFraction   = 0.60f,
        durationMs    = 700,
        phaseOffsetMs = 110,
    )
}