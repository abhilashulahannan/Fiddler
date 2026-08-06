package com.example.fiddler.subapps.Fidland.phs3.idle

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.R
import com.example.fiddler.subapps.Fidland.phs3.BlockAffinity
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler
import com.example.fiddler.ui.icons.MonoLottieIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * IdlePhs3Handler
 *
 * §B7 redesign of the old `IdleThoughtsHandler` fallback. Always registered
 * at service start (see [com.example.fiddler.subapps.Fidland.phs3.idle
 * .IdlePhs3Trigger]), a rotating stream of one-liners typewritten into the
 * pill, sourced from [IdleThoughts]' 393-entry pool.
 *
 * ── What changed from the old carve-out ──────────────────────────────────────
 * Idle used to be a hardcoded special case in Phs3Manager: shown continuously
 * when alone, force-surfaced one 10s turn every 5min otherwise. §B7 retires
 * that outright — Idle is now home Dominant/10 (contends on equal footing
 * with everything else at Dominant, see design doc's flag on this), and
 * escalates to Special Condition when nothing new has displayed *anywhere on
 * the pill* for 60s (see [IdlePhs3Trigger] — that's where the global
 * staleness clock and the scheduler bids live, not here).
 *
 * ── Blocks (2) — replacing the old fused Row ─────────────────────────────────
 * [Indicator] (primary, [BlockAffinity.DYNAMIC]) — just the looping
 *   `nhi_lama.json` glyph now. No shared state: it plays continuously
 *   whenever Idle holds the slot, independent of the typewriter cycle.
 * [SecondaryIndicator] (secondary, [BlockAffinity.RIGHT_ANCHOR]) — the
 *   streaming thought text, always right.
 * These are two independently-measured/placed blocks via
 * [Phs3Handler.hasSecondaryBlock] — same split Ring Mode/Battery/Timer/
 * Record use. The balancer can now legitimately land the icon left while
 * the text stays right (first entity in the doc where that happens to a
 * single handler's own blocks — see design doc §B7 Idle flag).
 *
 * ── Thought clock lives on the handler, not the composable ──────────────────
 * [currentThought] is a [StateFlow] driven by [advanceThought], called by
 * [IdlePhs3Trigger]'s own always-running coroutine on a 5-minute interval —
 * *not* from a `LaunchedEffect` inside [Indicator]/[SecondaryIndicator] like
 * the old version. Idle is Continuous-Dominant now: its content should keep
 * advancing on its own clock whether or not it's currently holding the
 * slot, the same way every other always-registered entity's state is
 * independent of whether it's currently shown. [SecondaryIndicator] and
 * [State5Content] just react to whatever [currentThought] currently holds.
 *
 * One side effect worth flagging: because the typewriter reveal is a
 * `LaunchedEffect(thought)` inside a composable that gets disposed whenever
 * Idle isn't holding the slot, returning to an unchanged thought re-plays
 * the reveal from scratch rather than resuming "already fully revealed" —
 * not specified either way in the doc, picked as the simpler option.
 *
 * ── State 5 — flips false→true ───────────────────────────────────────────────
 * Larger icon + full thought text. Tapping the text calls [advanceThought]
 * directly (same source the 5-minute background clock uses, so the two
 * never disagree about "the current thought") and shows the new one
 * instantly, no typewriter — doc flags this as unresolved either way; a
 * deliberate-reading view showing a partial reveal reads as more broken
 * than a snap change. No dashboard tab mapping — falls through to
 * last-used tab like every other unmapped entity.
 *
 * ── Qualify ───────────────────────────────────────────────────────────────────
 * Unchanged — always registered, never disqualifies. Only *how* Idle
 * behaves once qualified changes here (see [IdlePhs3Trigger] for the
 * Dominant/Special-Condition bidding that replaces the old carve-out).
 */
class IdlePhs3Handler : Phs3Handler {

    override val label: String = "Idle"

    // Unchanged — Idle has never used the location-a row.
    override val hasLocationA: Boolean = false

    // §B7 Blocks (2) — icon (primary/DYNAMIC) + text (secondary/RIGHT_ANCHOR),
    // replacing the old single fused Indicator() Row.
    override val hasSecondaryBlock: Boolean = true
    override val blockAffinity: BlockAffinity = BlockAffinity.DYNAMIC
    override val secondaryBlockAffinity: BlockAffinity = BlockAffinity.RIGHT_ANCHOR

    // §B7 — flips false→true.
    override fun hasState5Content(): Boolean = true

    // ── Thought clock ────────────────────────────────────────────────────────

    private val _currentThought = MutableStateFlow(IdleThoughts.random())

    /** The thought currently streaming/shown. Advanced by [IdlePhs3Trigger]'s clock or a State5 tap. */
    val currentThought: StateFlow<String> = _currentThought

    /**
     * Picks a new thought, excluding the current one. Called by
     * [IdlePhs3Trigger]'s background interval and by [State5Content]'s tap
     * handler — both go through this single entry point so the compact
     * view and State5 are always looking at the same value.
     */
    fun advanceThought() {
        _currentThought.value = (IdleThoughts.thoughtsShort + IdleThoughts.thoughtsLong)
            .filterNot { it == _currentThought.value }
            .random()
    }

    // ── Indicator — primary block, icon only ──────────────────────────────────

    @Composable
    override fun Indicator() {
        Box(contentAlignment = Alignment.Center) {
            NhiLamaIcon(size = IDLE_ICON_SIZE)
        }
    }

    // ── SecondaryIndicator — secondary block, streaming text ─────────────────

    @Composable
    override fun SecondaryIndicator() {
        val thought by currentThought.collectAsState()
        var visibleLength by remember(thought) { mutableStateOf(0) }

        LaunchedEffect(thought) {
            visibleLength = 0
            for (i in 1..thought.length) {
                delay(CHAR_DELAY_MS)
                visibleLength = i
            }
            // Fully revealed — nothing more to do here; IdlePhs3Trigger's
            // clock (not this composable) decides when the *next* thought
            // gets picked, so this loop simply ends after the reveal.
        }

        val displayedText = forceMinTwoLines(thought.take(visibleLength), thought)
        val fontSize = if (thought.length < 45) FONT_SIZE_SHORT else FONT_SIZE_LONG

        AnimatedContent(
            targetState = displayedText,
            transitionSpec = {
                fadeIn(tween(80)) togetherWith fadeOut(tween(60))
            },
            label = "idle_thought_stream",
        ) { text ->
            Box(
                modifier = Modifier
                    .wrapContentWidth()
                    .widthIn(max = INDICATOR_MAX_WIDTH)
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = fontSize,
                    maxLines = MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.sp,
                )
            }
        }
    }

    // ── State 5 — reading view ────────────────────────────────────────────────

    @Composable
    override fun State5Content() {
        val thought by currentThought.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.wrapContentWidth(),
                contentAlignment = Alignment.Center,
            ) {
                NhiLamaIcon(size = STATE5_ICON_SIZE)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { advanceThought() }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
            ) {
                Text(
                    text = thought,
                    color = Color.White,
                    fontSize = STATE5_FONT_SIZE,
                    fontWeight = FontWeight.Normal,
                )
            }

            Text(
                text = "tap for another thought",
                color = Color(0xFF555555),
                fontSize = 9.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }
    }

    /**
     * If [partial] (the in-progress typewriter reveal of [full]) doesn't yet
     * contain a forced line break, insert one near the midpoint of [full] so
     * the displayed text always spans at least [MIN_LINES] lines once enough
     * of it has streamed in. Unchanged from the pre-§B7 implementation — see
     * the original class doc for the full reasoning (string heuristic, not a
     * measured layout pass; Compose's `minLines` doesn't force extra
     * wrapping on text that already fits on fewer lines).
     */
    private fun forceMinTwoLines(partial: String, full: String): String {
        val mid = full.length / 2
        val breakAt = run {
            val before = full.lastIndexOf(' ', mid)
            val after = full.indexOf(' ', mid)
            when {
                before == -1 && after == -1 -> -1
                before == -1 -> after
                after == -1 -> before
                (mid - before) <= (after - mid) -> before
                else -> after
            }
        }
        if (breakAt <= 0) return partial

        return if (partial.length <= breakAt) {
            partial
        } else {
            partial.substring(0, breakAt) + "\n" + partial.substring(breakAt + 1)
        }
    }

    companion object {
        /** Milliseconds per character during typewriter reveal. */
        private const val CHAR_DELAY_MS = 38L

        /** Font size for short thoughts (< 45 chars, thoughtsShort pool). */
        private val FONT_SIZE_SHORT = 9.sp

        /** Font size for longer thoughts (thoughtsLong pool). */
        private val FONT_SIZE_LONG = 6.5.sp

        /** Minimum lines a thought should wrap to once fully revealed. */
        private const val MIN_LINES = 2

        /** Maximum lines the thought is allowed to wrap to in the compact pill. */
        private const val MAX_LINES = 3

        /** Maximum width the idle text is allowed to occupy in the right zone. */
        private val INDICATOR_MAX_WIDTH = 120.dp

        /** Size of the nhi_lama icon in the compact pill. */
        private val IDLE_ICON_SIZE = 14.dp

        /** Size of the nhi_lama icon in State5. */
        private val STATE5_ICON_SIZE = 40.dp

        /** Font size for the full thought text in State5. */
        private val STATE5_FONT_SIZE = 15.sp

        /**
         * How long a thought stays current before the next one is picked.
         * Now consumed by [IdlePhs3Trigger]'s background clock, not a
         * `LaunchedEffect` here — see class doc.
         */
        const val THOUGHT_INTERVAL_MS = 5 * 60 * 1_000L
    }
}

/** Looping `nhi_lama.json` glyph — unchanged from the pre-§B7 implementation. */
@Composable
private fun NhiLamaIcon(size: androidx.compose.ui.unit.Dp) {
    MonoLottieIcon(rawRes = R.raw.nhi_lama, size = size)
}