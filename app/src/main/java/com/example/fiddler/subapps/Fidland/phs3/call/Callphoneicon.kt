package com.example.fiddler.subapps.Fidland.phs3.call

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
 * Phs3 Call — Location A: phone-handset icon.
 *
 * Plays a looping Lottie animation, matched to call state:
 *   - Missed calls          → red  glyph  (res/raw/call_missed.json)
 *   - Incoming / active calls → green glyph (res/raw/call_call.json)
 *
 * Placed at the start of the right zone, immediately right of the hole-punch
 * spacer — mirrors [AlbumArtSpinner]'s "Component 1" slot in the music module
 * but on the right side (this module lives in the RIGHT ZONE / State 3).
 *
 * @param missed True for the missed-call action (red), false for
 *                incoming/active (green).
 * @param size   Icon size. Defaults to 16.dp — comfortably smaller than
 *                IslandConfig.BASE_SIZE so it sits centered in the pill row.
 */
@Composable
fun CallPhoneIcon(
    missed: Boolean,
    size: Dp = 16.dp
) {
    val rawRes = if (missed) R.raw.call_missed else R.raw.call_call

    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(rawRes)
    )
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