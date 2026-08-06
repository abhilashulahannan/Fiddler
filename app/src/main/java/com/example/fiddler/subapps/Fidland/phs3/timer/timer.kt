package com.example.fiddler.subapps.Fidland.phs3.timer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.R
import com.example.fiddler.subapps.Fidland.phs3.BlockAffinity
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler
import com.example.fiddler.ui.icons.MonoLottieIcon

/**
 * Phs3 module — Timer / Stopwatch (Clock app).
 *
 * ── Placement (§B2/§B7) — Blocks (2) ──────────────────────────────────────────
 * §B7 replaces the old design (mode glyph fixed in the location-a row via
 * [Phs3Handler.hasLocationA], separate from a fused location-b/c Indicator)
 * with 2 independently-placed §B2 blocks via [Phs3Handler.hasSecondaryBlock]
 * — the same split Ring Mode proved first and Battery has since adopted (see
 * their own class docs for the shared plumbing):
 *
 *   • [Indicator] — the mode glyph, [BlockAffinity.DYNAMIC]. Timer is the
 *     first entity whose identity icon isn't fixed-left (unlike Call's/
 *     Music's/Alarm's icons) — this was blocked on §B2's placement system
 *     actually existing, not a simple tweak, which is why it landed in
 *     Phase 3 rather than earlier. In `BOTH_EXPANDED` the balancer can now
 *     genuinely move the glyph into the left zone when that keeps the pill
 *     narrower; `RIGHT_EXPANDED` has no left zone to move into, so there it
 *     always stays put next to the content block, same as before this pass.
 *   • [SecondaryIndicator] — the timer content, [BlockAffinity.RIGHT_ANCHOR].
 *     TIMER mode renders live remaining time + [TimerProgressRing] as one
 *     unit; STOPWATCH mode renders elapsed time alone, plus the last lap
 *     (conditional — renders nothing if no laps yet). This is the old
 *     fused Indicator's location-b/c content, now living in its own block.
 *
 * `hasLocationA` is no longer overridden (back to the interface default
 * `false`) — the glyph's old fixed-left slot is retired in favor of its new
 * DYNAMIC placement above.
 *
 * ── State 5 (long-press to open) ──────────────────────────────────────────────
 * **Retained** (`hasState5Content` stays at the interface default `true`) —
 * dropping it would have removed the only place laps are browsable as a
 * list, which was confirmed unacceptable. Header: label + mode glyph. Big
 * time readout. TIMER shows a large progress ring; STOPWATCH shows a
 * scrollable lap list. Pause/Resume + Cancel (TIMER) or Pause/Resume + Lap
 * (STOPWATCH) buttons along the bottom — wired via the constructor lambdas
 * below. These remain no-ops today (Clock app owns its own state, no
 * control API), same as before this pass.
 *
 * ── Wiring ───────────────────────────────────────────────────────────────────
 * 1. Add a [TimerPhs3Trigger] in FidlandService.onCreate / onDestroy
 *    (see TimerPhs3trigger.kt).
 * 2. Feed [TimerRepository] from the Clock app's timer/stopwatch
 *    notifications (see TimerRepository kdoc for the sketch).
 * [Indicator] / [SecondaryIndicator] are wired automatically via
 * [hasSecondaryBlock] — no manual zone wiring needed.
 */
class TimerPhs3Handler(
    private val onPauseResume: () -> Unit = {},
    private val onCancel: () -> Unit = {},
    private val onLap: () -> Unit = {},
) : Phs3Handler {

    override val label: String = "Timer"

    override val hasSecondaryBlock: Boolean = true
    override val blockAffinity: BlockAffinity = BlockAffinity.DYNAMIC
    override val secondaryBlockAffinity: BlockAffinity = BlockAffinity.RIGHT_ANCHOR

    // ── Indicator (primary block — mode glyph) ────────────────────────────────

    @Composable
    override fun Indicator() {
        val snapshot by TimerRepository.flow.collectAsState()
        if (!snapshot.isActive) return

        TimerModeIcon(sizeDp = 14.dp)
    }

    // ── SecondaryIndicator (secondary block — time text + ring / last lap) ────

    @Composable
    override fun SecondaryIndicator() {
        val snapshot by TimerRepository.flow.collectAsState()

        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier               = Modifier.padding(start = 6.dp),
        ) {
            // Live time text — remaining (TIMER) or elapsed (STOPWATCH)
            Text(
                text       = if (snapshot.mode == TimerMode.TIMER) snapshot.remainingText else snapshot.elapsedText,
                color      = if (snapshot.runState == TimerRunState.FINISHED) Color(0xFFEF5350) else Color.White,
                fontSize   = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
            )

            // TIMER: progress ring · STOPWATCH: last lap (conditional, blank if none yet)
            if (snapshot.mode == TimerMode.TIMER) {
                TimerProgressRing(
                    progressFraction = snapshot.progressFraction,
                    finished         = snapshot.runState == TimerRunState.FINISHED,
                    size             = 18.dp,
                )
            } else {
                snapshot.lastLap?.let { lap ->
                    Text(
                        text     = lap.lapTimeText,
                        color    = Color(0xFF888888),
                        fontSize = 9.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }

    // ── State 5 — full timer/stopwatch panel ──────────────────────────────────

    @Composable
    override fun State5Content() {
        val snapshot by TimerRepository.flow.collectAsState()

        if (!snapshot.isActive) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No timer running…", color = Color(0xFF666666), fontSize = 12.sp)
            }
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // Header — label + mode
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text     = if (snapshot.label.isNotBlank()) snapshot.label
                    else if (snapshot.mode == TimerMode.TIMER) "Timer" else "Stopwatch",
                    color    = Color(0xFF888888),
                    fontSize = 10.sp,
                )
                TimerModeIcon(sizeDp = 12.dp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (snapshot.mode == TimerMode.TIMER) {
                TimerPanelBody(snapshot = snapshot)
            } else {
                StopwatchPanelBody(snapshot = snapshot)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer buttons
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PanelButton(
                    text     = if (snapshot.runState == TimerRunState.RUNNING) "Pause" else "Resume",
                    modifier = Modifier.weight(1f),
                    onClick  = onPauseResume,
                )
                if (snapshot.mode == TimerMode.TIMER) {
                    PanelButton(text = "Cancel", modifier = Modifier.weight(1f), onClick = onCancel)
                } else {
                    PanelButton(text = "Lap", modifier = Modifier.weight(1f), onClick = onLap)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  TIMER mode — State 5 body (big ring + remaining time)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TimerPanelBody(snapshot: TimerSnapshot) {
    Box(
        modifier         = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        TimerProgressRing(
            progressFraction = snapshot.progressFraction,
            finished         = snapshot.runState == TimerRunState.FINISHED,
            size             = 90.dp,
        )
        Text(
            text       = snapshot.remainingText,
            color      = if (snapshot.runState == TimerRunState.FINISHED) Color(0xFFEF5350) else Color.White,
            fontSize   = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  STOPWATCH mode — State 5 body (elapsed time + lap list)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StopwatchPanelBody(snapshot: TimerSnapshot) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text       = snapshot.elapsedText,
                color      = Color.White,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (snapshot.laps.isEmpty()) {
            Text(
                text     = "No laps yet",
                color    = Color(0xFF666666),
                fontSize = 10.sp,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier            = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(snapshot.laps.reversed()) { _, lap ->
                    LapRow(lap = lap)
                }
            }
        }
    }
}

@Composable
private fun LapRow(lap: LapEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF111111))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(text = "Lap ${lap.index}", color = Color(0xFF888888), fontSize = 9.sp)
        Text(text = lap.lapTimeText, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
        Text(
            text      = lap.splitTimeText,
            color     = Color(0xFF666666),
            fontSize  = 9.sp,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shared footer button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PanelButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E))
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Mode icon — shared between LocationAIndicator and the State5 header
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Timer/stopwatch mode icon — loops `res/raw/timer_countdown_stopwatch.json`.
 * A single combined asset covers both [TimerMode.TIMER] and
 * [TimerMode.STOPWATCH] (replacing the old ⏱ / ⏲ glyph switch), so this
 * composable doesn't branch on mode at all.
 */
@Composable
private fun TimerModeIcon(sizeDp: Dp) {
    MonoLottieIcon(rawRes = R.raw.timer_countdown_stopwatch, size = sizeDp)
}

// ─────────────────────────────────────────────────────────────────────────────
//  Circular progress ring — drains as remaining time decreases (TIMER mode).
//  Draw approach mirrors DownloadProgressRing.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TimerProgressRing(
    progressFraction: Float,
    finished: Boolean = false,
    size: Dp = 22.dp,
) {
    val clamped = progressFraction.coerceIn(0f, 1f)
    val arcColor = if (finished) Color(0xFFEF5350) else Color(0xFF4FC3F7)
    val trackColor = Color(0xFF2A2A2A)

    Canvas(modifier = Modifier.size(size)) {
        val strokeWidth = 2.2f * (size.toPx() / 22f)
        val inset = strokeWidth / 2f
        val oval = Size(
            width  = this.size.width  - inset * 2,
            height = this.size.height - inset * 2,
        )
        val topLeft = Offset(inset, inset)

        // Track
        drawArc(
            color      = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter  = false,
            topLeft    = topLeft,
            size       = oval,
            style      = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        // Remaining-time arc — full circle at start, drains to nothing at zero.
        if (clamped > 0f) {
            drawArc(
                color      = arcColor,
                startAngle = -90f,
                sweepAngle = 360f * clamped,
                useCenter  = false,
                topLeft    = topLeft,
                size       = oval,
                style      = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
    }
}