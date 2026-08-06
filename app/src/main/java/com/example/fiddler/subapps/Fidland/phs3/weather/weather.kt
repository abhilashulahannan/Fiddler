package com.example.fiddler.subapps.Fidland.phs3.weather

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.R
import com.example.fiddler.subapps.Fidland.phs3.BlockAffinity
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler
import com.example.fiddler.ui.icons.MonoLottieIcon

/**
 * Phs3 module — Weather.
 *
 * Always active — [WeatherPhs3Trigger] registers this handler on service start
 * and never deactivates it. Data flows from [WeatherRepository.flow].
 *
 * ── Placement (§B2/§B7) — Blocks (2) ──────────────────────────────────────────
 * §B7 replaces the old location-a icon + fused two-line Indicator with 2
 * independently-placed §B2 blocks via [Phs3Handler.hasSecondaryBlock] — the
 * same split Ring Mode proved first, Battery has since adopted, and Timer
 * just adopted too (Weather is the second entity, after Timer, to move its
 * icon off fixed-left):
 *
 *   • [Indicator] — icon + temperature, [BlockAffinity.DYNAMIC]. Icon and
 *     temperature stay paired as one block (not split further) since
 *     they're read as a single glanceable unit.
 *   • [SecondaryIndicator] — feels-like text alone, [BlockAffinity.RIGHT_ANCHOR]
 *     (unchanged text, now its own block instead of the second line of a
 *     fused Column).
 *
 * `hasLocationA` is no longer overridden (back to the interface default
 * `false`) — no location-a participation once the icon is dynamic.
 *
 * ── State 5 (full-width strip, STATE5_HEIGHT tall) ───────────────────────────
 *   Row 1 — hero + stats:
 *     Left:  large condition icon (colour-coded per [LottieIconColors], e.g.
 *            amber sun, blue rain, purple storm) + big current temperature.
 *     Right: feels-like / humidity / wind stacked as small icon+label rows.
 *   Row 2 — hourly forecast strip:
 *     Next 5 hours, each showing hour label + condition icon + temperature.
 *   Row 3 — sarcastic pun:
 *     Italic, dimmed — e.g. "Great day to touch grass. You won't, but it's there."
 *     Rotates randomly every 15-minute refresh cycle (see [WeatherCondition.pickSarcasm]).
 *   Unchanged by this pass — confirmed good as-is.
 *
 * ── Loading state ─────────────────────────────────────────────────────────────
 *   Before the first fetch completes, [WeatherRepository.flow] emits null.
 *   All composables handle null gracefully with placeholder dashes/dots so
 *   the pill doesn't flash or crash on cold start. [WeatherIcon] falls back
 *   to [R.raw.weather_temp] while condition is unknown.
 */
class WeatherPhs3Handler : Phs3Handler {

    override val label: String = "Weather"

    override val hasSecondaryBlock: Boolean = true
    override val blockAffinity: BlockAffinity = BlockAffinity.DYNAMIC
    override val secondaryBlockAffinity: BlockAffinity = BlockAffinity.RIGHT_ANCHOR

    // ── Indicator (primary block — icon + temperature) ──────────────────────

    @Composable
    override fun Indicator() {
        val snapshot by WeatherRepository.flow.collectAsState()

        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(condition = snapshot?.condition, size = 13.dp)
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text       = if (snapshot != null) "${snapshot!!.tempC}°" else "· · ·",
                color      = Color.White,
                fontSize   = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
        }
    }

    // ── SecondaryIndicator (secondary block — feels-like) ────────────────────

    @Composable
    override fun SecondaryIndicator() {
        val snapshot by WeatherRepository.flow.collectAsState()

        Text(
            text     = if (snapshot != null) "feels ${snapshot!!.feelsLikeC}°" else "",
            color    = Color(0xFFAAAAAA),
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 70.dp)
                .padding(start = 6.dp),
        )
    }

    // ── State 5 ───────────────────────────────────────────────────────────────

    @Composable
    override fun State5Content() {
        val snapshot by WeatherRepository.flow.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            if (snapshot == null) {
                LoadingRow()
                return@Column
            }

            val snap = snapshot!!

            // ── Row 1: hero icon + temp (left), stacked stats (right) ──────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                // Hero — large colour-coded condition icon + big temperature.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MonoLottieIcon(
                        rawRes = snap.condition.toRawRes(),
                        size   = 38.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text       = "${snap.tempC}°",
                        color      = Color.White,
                        fontSize   = 26.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines   = 1,
                    )
                }

                // Stats — feels-like, humidity, wind stacked to the right.
                Column(
                    horizontalAlignment  = Alignment.End,
                    verticalArrangement  = Arrangement.spacedBy(3.dp),
                ) {
                    DetailChip(iconRes = R.raw.weather_temp, label = "feels ${snap.feelsLikeC}°")
                    DetailChip(iconRes = R.raw.weather_humid, label = "${snap.humidityPct}%")
                    DetailChip(iconRes = R.raw.weather_wind, label = "${snap.windSpeedKmh} ${snap.windDir}")
                }
            }

            // ── Row 2: hourly forecast strip ──────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                if (snap.nextHours.isEmpty()) {
                    Text(
                        text     = "no hourly data",
                        color    = Color(0xFF555555),
                        fontSize = 10.sp,
                    )
                } else {
                    snap.nextHours.forEach { slot ->
                        HourlySlotView(slot)
                    }
                }
            }

            // ── Row 3: sarcastic pun ──────────────────────────────────────
            Text(
                text      = snap.sarcasm,
                color     = Color(0xFF666666),
                fontSize  = 9.sp,
                fontStyle = FontStyle.Italic,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── Private composables ───────────────────────────────────────────────────────

/**
 * Shared condition icon — plays the matching weather Lottie asset
 * (res/raw/weather_*.json) on loop. Falls back to [R.raw.weather_temp]
 * while [condition] is null (pre-fetch / loading state).
 */
@Composable
private fun WeatherIcon(condition: WeatherCondition?, size: Dp) {
    MonoLottieIcon(
        rawRes = condition?.toRawRes() ?: R.raw.weather_temp,
        size   = size,
    )
}

/**
 * Single chip in the current-detail bar (Row 1).
 * Optionally shows a small looping Lottie icon before the label
 * (condition / humidity / wind); omit [iconRes] for plain-text chips
 * like "feels like".
 */
@Composable
private fun DetailChip(label: String, bold: Boolean = false, iconRes: Int? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (iconRes != null) {
            MonoLottieIcon(rawRes = iconRes, size = 12.dp)
            Spacer(modifier = Modifier.width(2.dp))
        }
        Text(
            text       = label,
            color      = if (bold) Color.White else Color(0xFFCCCCCC),
            fontSize   = 10.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            maxLines   = 1,
            overflow   = TextOverflow.Clip,
        )
    }
}

/** One column in the hourly forecast strip (Row 2). */
@Composable
private fun HourlySlotView(slot: HourlySlot) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text     = slot.hour,
            color    = Color(0xFF888888),
            fontSize = 8.sp,
            maxLines = 1,
        )
        WeatherIcon(condition = slot.condition, size = 11.dp)
        Text(
            text     = "${slot.tempC}°",
            color    = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** Shown in all three rows before the first fetch completes. */
@Composable
private fun LoadingRow() {
    Box(
        modifier          = Modifier.fillMaxSize(),
        contentAlignment  = Alignment.Center,
    ) {
        Text(
            text     = "fetching weather…",
            color    = Color(0xFF555555),
            fontSize = 11.sp,
        )
    }
}

/** Thin horizontal divider — shared style with AlarmPhs3Handler. */
@Composable
private fun Divider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Color(0xFF2A2A2A))
    )
}