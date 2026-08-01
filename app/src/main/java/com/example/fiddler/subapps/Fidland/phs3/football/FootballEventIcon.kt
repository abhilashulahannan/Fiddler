package com.example.fiddler.subapps.Fidland.phs3.football

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fiddler.R
import com.example.fiddler.ui.icons.LottieIconColors
import com.example.fiddler.ui.icons.MonoLottieIcon

/**
 * Phs3 Football — Location-a event icon.
 *
 * Plays one of two looping Lottie assets depending on [type]:
 *   GOAL                              → `football_gooal.json`
 *   YELLOW_CARD / RED_CARD /
 *   YELLOW_RED_CARD                   → `football_card_ry.json`
 *                                        (single combined asset covers all
 *                                        three card variants — no per-type
 *                                        branching needed, same idiom as
 *                                        TimerModeIcon's one asset covering
 *                                        both TIMER and STOPWATCH)
 *   SUBSTITUTION / OTHER              → falls back to `football_gooal.json`,
 *                                        same fallback the previous
 *                                        Canvas-drawn version used.
 *
 * Sized to [size] (default 20.dp to match the compact pill height in
 * State 3); call sites range from 10.dp (Matchdetailcard's event-type icon)
 * up to 18.dp (FootballPhs3Handler's flash indicator).
 *
 * Only shown for [FLASH_DURATION_MS] after an event arrives; the hosting
 * composable handles the timed visibility — this composable just plays.
 */
@Composable
fun FootballEventIcon(
    type: EventType,
    size: Dp = 20.dp,
) {
    val rawRes = when (type) {
        EventType.GOAL,
        EventType.SUBSTITUTION,
        EventType.OTHER            -> R.raw.football_gooal
        EventType.YELLOW_CARD,
        EventType.RED_CARD,
        EventType.YELLOW_RED_CARD  -> R.raw.football_card_ry
    }

    // football_card_ry is one shared asset for all three card variants, so
    // the yellow-vs-red distinction has to be picked here rather than via
    // the default rawRes -> colour lookup (which can only give the asset
    // one colour).
    val color = when (type) {
        EventType.YELLOW_CARD                        -> LottieIconColors.footballCardYellow
        EventType.RED_CARD, EventType.YELLOW_RED_CARD -> LottieIconColors.footballCardRed
        EventType.GOAL, EventType.SUBSTITUTION,
        EventType.OTHER                                -> LottieIconColors.footballGoal
    }

    MonoLottieIcon(rawRes = rawRes, size = size, color = color)
}