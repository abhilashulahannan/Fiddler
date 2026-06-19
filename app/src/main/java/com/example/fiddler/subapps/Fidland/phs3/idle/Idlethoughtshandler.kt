package com.example.fiddler.subapps.Fidland.phs3.idle

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler
import kotlinx.coroutines.delay

/**
 * IdleThoughtsHandler
 *
 * A fallback [Phs3Handler] that is always registered in [FidlandService].
 * When no other phs3 handler qualifies, this one keeps the right zone alive
 * with a rotating stream of nihilistic / absurdist one-liners rendered with
 * a typewriter effect that mimics an LLM streaming tokens.
 *
 * ── Integration ──────────────────────────────────────────────────────────────
 * Register it once in FidlandService.onCreate() AFTER all other triggers are
 * started, so it sits at the back of the priority queue:
 *
 *   phs3Manager.register(IdleThoughtsHandler())
 *
 * Because it is always registered it will always appear in [qualifiedHandlers].
 * When other real handlers (call, alarm, music, etc.) are also qualified,
 * Phs3Manager gives Idle its own cadence rather than a normal round-robin
 * turn — see Phs3Manager's idle-surfacing logic. Net speed display on/off
 * has no bearing on Idle's qualification or visibility; it is a purely
 * separate, fixed left-zone element.
 *
 * ── Thought cycle ────────────────────────────────────────────────────────────
 * • A new random thought is picked every [THOUGHT_INTERVAL_MS] (5 minutes)
 *   from the combined [IdleThoughts.thoughtsShort] + [IdleThoughts.thoughtsLong]
 *   pools (393 entries total).
 * • Short thoughts (≤ 45 chars) render at [FONT_SIZE_SHORT]; long thoughts
 *   (46–80 chars) render at [FONT_SIZE_LONG].
 * • The thought is revealed character-by-character at [CHAR_DELAY_MS] per
 *   character, simulating an LLM streaming response, wrapping to between
 *   [MIN_LINES] and [MAX_LINES] lines.
 * • After the full thought is visible it stays until the next cycle.
 *
 * ── Layout note ──────────────────────────────────────────────────────────────
 * The shared RightZone container (overlay_fidland_pill.kt) measures its
 * content with unbounded width so other handlers' single-line indicators
 * self-size the pill correctly. That means a plain Text() here would never
 * wrap — it always has "infinite" room. To get real multi-line wrapping we
 * give the Text its own fixed width, sized from
 * IslandConfig.STATE3_MAX_WIDTH minus the pill's own chrome (hole spacer +
 * horizontal content padding), rather than relying on RightZone's box.
 *
 * ── State 5 ──────────────────────────────────────────────────────────────────
 * hasState5Content() returns false — swiping down goes straight to the
 * dashboard, keeping the idle state unobtrusive.
 */
class IdleThoughtsHandler : Phs3Handler {

    override val label: String = "Idle"

    // No location-a slot — keeps the left zone clean when idle.
    override val hasLocationA: Boolean = false

    override fun hasState5Content(): Boolean = false

    @Composable
    override fun Indicator() {
        // ── State: pick a thought and stream it character by character ─────────
        var fullThought by remember { mutableStateOf(IdleThoughts.random()) }
        var visibleLength by remember { mutableStateOf(0) }
        val rawVisible = fullThought.take(visibleLength)
        val displayedText = forceMinTwoLines(rawVisible, fullThought)

        val fontSize = when {
            fullThought.length < 45  -> FONT_SIZE_SHORT
            fullThought.length <= 80 -> FONT_SIZE_LONG
            else                     -> FONT_SIZE_LONG
        }

        // Single driving loop: reveal current thought, dwell, pick next, repeat.
        // Keeping this as one loop (rather than two separate LaunchedEffects)
        // avoids timer drift now that the dwell window is minutes long.
        LaunchedEffect(Unit) {
            while (true) {
                visibleLength = 0
                for (i in 1..fullThought.length) {
                    delay(CHAR_DELAY_MS)
                    visibleLength = i
                }
                delay(THOUGHT_INTERVAL_MS - (fullThought.length * CHAR_DELAY_MS))
                fullThought = (IdleThoughts.thoughtsShort + IdleThoughts.thoughtsLong)
                    .filterNot { it == fullThought }
                    .random()
            }
        }

        AnimatedContent(
            targetState = displayedText,
            transitionSpec = {
                fadeIn(tween(80)) togetherWith fadeOut(tween(60))
            },
            label = "idle_thought_stream"
        ) { text ->
            Box(modifier = Modifier.wrapContentWidth().widthIn(max = INDICATOR_MAX_WIDTH).padding(horizontal = 8.dp)) {
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

    /**
     * If [partial] (the in-progress typewriter reveal of [full]) doesn't
     * yet contain a forced line break, insert one near the midpoint of
     * [full] so the displayed text always spans at least [MIN_LINES]
     * lines once enough of it has streamed in — regardless of whether the
     * text would naturally wrap on its own.
     *
     * All current IdleThoughts entries (19-47 chars) comfortably fit on a
     * single line at FONT_SIZE/INDICATOR_WIDTH (~60-75 char capacity), so
     * without this forced break every thought would render as 1 line,
     * which doesn't satisfy "at least 2 lines." Hence the break is always
     * inserted (not just when the text is "long enough to need it") —
     * the only exception is strings too short to contain a space to break
     * on, where a forced break isn't meaningful.
     *
     * Operates on the full thought's nearest-to-midpoint space so the
     * break point is stable across the whole typewriter reveal (it
     * doesn't shift around as more characters stream in), and so it
     * never splits a word.
     *
     * This is a string-level heuristic, not a measured layout pass —
     * Compose has no built-in "minLines for wrapped text" (its `minLines`
     * only reserves vertical space for short content, it does not force
     * extra wrapping on text that would otherwise fit on fewer lines).
     * Doing a real measure-then-decide pass would need a
     * SubcomposeLayout; this approach is simpler and adequate for short,
     * single-style strings like these.
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
        if (breakAt <= 0) return partial // no space to break on — too short to force a second line

        return if (partial.length <= breakAt) {
            partial
        } else {
            partial.substring(0, breakAt) + "\n" + partial.substring(breakAt + 1)
        }
    }

    companion object {
        /** Milliseconds per character during typewriter reveal. */
        private const val CHAR_DELAY_MS = 38L

        /** Font size for short thoughts (≤ 45 chars, thoughtsShort pool). */
        private val FONT_SIZE_SHORT = 9.sp

        /** Font size for longer thoughts (46–80 chars, thoughtsLong pool). */
        private val FONT_SIZE_LONG = 6.5.sp

        /** Minimum lines a thought should wrap to once fully revealed. */
        private const val MIN_LINES = 2

        /**
         * Maximum lines the thought is allowed to wrap to in the compact
         * pill. At FONT_SIZE = 6.5sp, 3 lines fits within the right zone's
         * height ceiling (BASE_SIZE - CONTENT_PADDING_VERTICAL * 2 = 24dp)
         * under standard Compose line-height multipliers (~1.15-1.33x).
         * Do not raise FONT_SIZE without re-checking this fits — 7sp+
         * overflows the 24dp budget at 3 lines.
         */
        private const val MAX_LINES = 3

        /**
         * Maximum width the idle text is allowed to occupy in location b.
         * The pill sizes symmetrically, so keeping this well below
         * STATE3_MAX_WIDTH / 2 prevents the right half from dominating.
         */
        private val INDICATOR_MAX_WIDTH = 120.dp

        /**
         * How long a thought stays current before the next one is picked,
         * measured from when it first starts streaming in. The typewriter
         * reveal time is subtracted from this so the *total* time between
         * thought changes is exactly this value, not this-plus-typewriter.
         */
        private const val THOUGHT_INTERVAL_MS = 5 * 60 * 1_000L
    }
}