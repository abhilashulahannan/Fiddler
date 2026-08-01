package com.example.fiddler.subapps.Fidland.phs3.football

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.fiddler.R

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

    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(rawRes))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations  = LottieConstants.IterateForever,
        isPlaying   = true,
    )

    Box(modifier = Modifier.size(size)) {
        LottieAnimation(
            composition = composition,
            progress    = { progress },
            modifier    = Modifier.size(size),
        )
    }
}