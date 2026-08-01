package com.example.fiddler.subapps.Fidland.phs3.call

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fiddler.R
import com.example.fiddler.ui.icons.MonoLottieIcon

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
    // Colour comes from LottieIconColors.callMissed/callActive via the
    // rawRes lookup — no override needed here.
    //
    // Unlike every other Phs3 glyph, call_call.json / call_missed.json have
    // real content padding baked into their 2000×2000 canvas — the phone
    // glyph only occupies the top-left ~55–74%, empty space bottom-right.
    // FillBounds in MonoLottieIcon fills the *slot*, but can't un-bake that
    // empty space from the asset itself, so we additionally scale the whole
    // composition up within its own bounds to cancel it out. 1.4x is
    // empirical (measured padding was ~24% per side); re-tune if the source
    // Lottie files are ever re-exported with the glyph centered/cropped.
    MonoLottieIcon(
        rawRes   = rawRes,
        size     = size,
        modifier = Modifier.scale(1.4f),
    )
}