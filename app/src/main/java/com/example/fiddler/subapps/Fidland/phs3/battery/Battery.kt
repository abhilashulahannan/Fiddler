package com.example.fiddler.subapps.Fidland.phs3.battery

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
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
 * Just [BatteryIcon] — a hand-drawn Canvas glyph (see its kdoc) with the
 * live percentage drawn *inside* the body rather than as separate text
 * alongside it, so this stays a single compact glyph like Camera's/
 * Flashlight's Indicator rather than an icon-plus-label pair. The icon's
 * fill bar tracks [BatteryInfo.level] exactly and turns green-and-pulsing
 * while charging, white normally, red at/below the low-battery threshold.
 *
 * ── State 5 ───────────────────────────────────────────────────────────────
 * None — like Camera, this is a glanceable status readout, not something
 * with a detail panel worth expanding into. [hasState5Content] returns
 * false so swipe-down goes straight to DASHBOARD.
 *
 * @param batteryInfo Live snapshot — level + charging state, both used
 *                     directly by [BatteryIcon].
 */
class BatteryPhs3Handler(
    private val batteryInfo: BatteryInfo = EmptyBatteryInfo,
) : Phs3Handler {

    override val label: String = "Battery"

    override fun hasState5Content(): Boolean = false

    @Composable
    override fun Indicator() {
        BatteryIcon(
            level      = batteryInfo.level,
            isCharging = batteryInfo.isCharging,
            size       = 26.dp,
        )
    }
}