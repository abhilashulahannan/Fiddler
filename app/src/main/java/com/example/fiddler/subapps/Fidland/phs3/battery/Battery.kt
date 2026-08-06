package com.example.fiddler.subapps.Fidland.phs3.battery

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.subapps.Fidland.phs3.BlockAffinity
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler

/**
 * Phs3 module — Battery indicator.
 *
 * Qualifies while charging, or while discharging at or below
 * [LOW_BATTERY_THRESHOLD_PERCENT] — see [BatteryInfo] for the reasoning
 * behind that trigger, and for the conditional-Dominant / Special-Condition
 * / hard-override escalation logic layered on top of it. See
 * [BatteryPhs3Trigger] for the ACTION_BATTERY_CHANGED wiring, rate
 * estimation, and charge-cycle accounting.
 *
 * ── Indicator (State 3) — §B7 Blocks (this pass) ────────────────────────────
 * §B7 replaces the old fused single-glyph Indicator with 2 independently-
 * placed §B2 blocks — [Indicator] (the animated glyph,
 * [BlockAffinity.DYNAMIC]) and [SecondaryIndicator] (the multi-line detail
 * text that branches on charging vs. discharging,
 * [BlockAffinity.RIGHT_ANCHOR]) — via [Phs3Handler.hasSecondaryBlock], the
 * same split Ring Mode proved first (see its own class doc for the shared
 * plumbing). Migrating off the old fused Row means the icon is no longer
 * hardcoded left-of-text: in `BOTH_EXPANDED`, `overlay_fidland_pill.kt`'s
 * §B2 balancer can now genuinely move the icon into the left zone when that
 * keeps the pill narrower, exactly as it already does for Ring Mode's icon.
 * (`RIGHT_EXPANDED` has no left zone to move into, so there the icon always
 * stays put next to the text, same as before this pass.)
 *
 * Tap target: neither block is tappable today (no change from before this
 * pass — Battery has never had a tap interaction), so there's no Ring-Mode-
 * style "keep both blocks tappable" concern to resolve here.
 *
 * ⚠ Open per §B7 (not resolved here): whether the icon should persist
 * continuously in location-a like Music's album art, or only appear during
 * Special-Condition windows, and whether to reuse this existing glyph or a
 * new asset. Kept as today's always-present glyph pending that call — that
 * question is about location-a placement and is orthogonal to the §B2
 * left/right split this pass builds.
 *
 * ── State 5 ───────────────────────────────────────────────────────────────
 * None — like Camera, this is a glanceable status readout, not something
 * with a detail panel worth expanding into. [hasState5Content] returns
 * false so swipe-down goes straight to DASHBOARD. §B7 doesn't propose a
 * change to this, so it's left unchanged.
 *
 * @param batteryInfo Live snapshot — level, charging state, and the new
 *                     §B7 data-source fields, all used by [Indicator] and
 *                     [SecondaryIndicator].
 */
class BatteryPhs3Handler(
    private val batteryInfo: BatteryInfo = EmptyBatteryInfo,
) : Phs3Handler {

    override val label: String = "Battery"

    override val hasSecondaryBlock: Boolean = true
    override val blockAffinity: BlockAffinity = BlockAffinity.DYNAMIC
    override val secondaryBlockAffinity: BlockAffinity = BlockAffinity.RIGHT_ANCHOR

    override fun hasState5Content(): Boolean = false

    // ── Indicator (primary block — animated glyph) ──────────────────────────

    @Composable
    override fun Indicator() {
        BatteryIcon(
            level      = batteryInfo.level,
            isCharging = batteryInfo.isCharging,
            size       = 26.dp,
        )
    }

    // ── SecondaryIndicator (secondary block — detail text) ──────────────────

    @Composable
    override fun SecondaryIndicator() {
        BatteryDetailText(batteryInfo)
    }
}

/**
 * §B7's "text = right, multi-line" block. Branches on charging state:
 *
 *   • Charging    → time-to-[TIME_TO_FULL_TARGET_PERCENT]%-full estimate,
 *                   then charge-cycle count.
 *   • Discharging → hours-to-0% estimate, then average daily screen-on time.
 *
 * Any field that's null (not enough rate samples yet, or — for screen-on —
 * usage-access permission not granted) is simply omitted from its line
 * rather than shown as a placeholder, so the block never displays a
 * confident-looking number it doesn't actually have.
 *
 * Carries a 6dp start-padding baked in — the old fused Row got this gap
 * for free from `Arrangement.spacedBy(6.dp)`; now that [Indicator] and
 * [BatteryPhs3Handler.SecondaryIndicator] are two separately-placed blocks
 * (`RightIndicatorContent`'s Row has no spacedBy of its own — see
 * `overlay_fidland_pill.kt`), each block owns its own spacing the same way
 * Ring Mode's [SecondaryIndicator] does, so the gap survives even when the
 * two blocks end up rendering in different zones.
 */
@Composable
private fun BatteryDetailText(info: BatteryInfo) {
    Column(modifier = Modifier.padding(start = 6.dp)) {
        val line1 = if (info.isCharging) {
            info.minutesToFull?.let { "→${TIME_TO_FULL_TARGET_PERCENT}% in ${formatDuration(it)}" }
                ?: "Charging"
        } else {
            info.minutesToEmpty?.let { "${formatDuration(it)} left" }
        }
        val line2 = if (info.isCharging) {
            "${info.chargeCycleCount} cycles"
        } else {
            info.avgDailyScreenOnMinutes?.let { "${formatDuration(it)}/day avg" }
        }

        if (line1 != null) {
            Text(
                text       = line1,
                color      = Color.White,
                fontSize   = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines   = 1,
            )
        }
        if (line2 != null) {
            Text(
                text     = line2,
                color    = Color(0xFF888888),
                fontSize = 9.sp,
                maxLines = 1,
            )
        }
    }
}

/** Formats a minute count as "Xh Ym" (or just "Ym" under an hour). */
private fun formatDuration(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}