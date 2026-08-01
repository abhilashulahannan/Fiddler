package com.example.fiddler.subapps.Fidland.phs3.battery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler

/**
 * Phs3 module — Battery indicator.
 *
 * Qualifies while charging, or while discharging at or below
 * [LOW_BATTERY_THRESHOLD_PERCENT] — see [BatteryInfo] for the reasoning
 * behind that trigger. See [BatteryPhs3Trigger] for the
 * ACTION_BATTERY_CHANGED wiring.
 *
 * ── Indicator (State 3) ─────────────────────────────────────────────────────
 * Just the looping battery Lottie icon — [BatteryInfo.rawRes] picks
 * `battery_charge.json` while charging or `battery_normal.json` while
 * discharging-and-low. Same compact footprint as
 * CameraPhs3Handler.Indicator / FlashlightPhs3Handler.Indicator.
 *
 * ── State 5 ───────────────────────────────────────────────────────────────
 * None — like Camera, this is a glanceable status icon, not something with
 * a detail panel worth expanding into. [hasState5Content] returns false so
 * swipe-down goes straight to DASHBOARD.
 *
 * @param batteryInfo Live snapshot — level + charging state. Only the
 *                    [BatteryInfo.rawRes] derived from it is used here;
 *                    the raw level isn't surfaced as text to keep the
 *                    Indicator as compact as Camera's.
 */
class BatteryPhs3Handler(
    private val batteryInfo: BatteryInfo = EmptyBatteryInfo,
) : Phs3Handler {

    override val label: String = "Battery"

    override fun hasState5Content(): Boolean = false

    @Composable
    override fun Indicator() {
        val composition by rememberLottieComposition(
            LottieCompositionSpec.RawRes(batteryInfo.rawRes)
        )
        val progress by animateLottieCompositionAsState(
            composition = composition,
            iterations  = LottieConstants.IterateForever,
            isPlaying   = true,
        )
        Box(
            modifier         = Modifier.size(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            LottieAnimation(
                composition = composition,
                progress    = { progress },
                modifier    = Modifier.size(26.dp),
            )
        }
    }
}