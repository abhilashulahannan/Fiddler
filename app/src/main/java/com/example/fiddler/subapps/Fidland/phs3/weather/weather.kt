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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler

/**
 * Phs3 module — Weather.
 *
 * Always active — [WeatherPhs3Trigger] registers this handler on service start
 * and never deactivates it. Data flows from [WeatherRepository.flow].
 *
 * ── Location A (left zone, 22 dp slot) ───────────────────────────────────────
 *   Current condition emoji — ☀️ ⛅ ☁️ 🌫️ 🌦️ 🌧️ ⛈️ ❄️
 *   Keeps the left zone informative even when another handler owns the right zone.
 *
 * ── Indicator (right zone, locations B + C) ───────────────────────────────────
 *   Two-line layout matching the music handler's song/artist pattern:
 *     Line 1 (white, bold):  emoji + temperature,  e.g. "🌤 32°"
 *     Line 2 (grey):         feels-like,            e.g. "feels 29°"
 *
 * ── State 5 (full-width strip, STATE5_HEIGHT tall) ───────────────────────────
 *   Row 1 — current detail bar:
 *     [emoji  temp]  [💧 humidity]  [💨 wind speed + dir]  [feels like]
 *   Row 2 — hourly forecast strip:
 *     Next 5 hours, each showing hour label + condition emoji + temperature.
 *   Row 3 — sarcastic pun:
 *     Italic, dimmed — e.g. "Great day to touch grass. You won't, but it's there."
 *     Rotates randomly every 15-minute refresh cycle (see [WeatherCondition.pickSarcasm]).
 *
 * ── Loading state ─────────────────────────────────────────────────────────────
 *   Before the first fetch completes, [WeatherRepository.flow] emits null.
 *   All composables handle null gracefully with placeholder dashes/dots so
 *   the pill doesn't flash or crash on cold start.
 */
class WeatherPhs3Handler : Phs3Handler {

    override val label: String = "Weather"

    // Always show the condition emoji in the left-zone location-a slot.
    override val hasLocationA: Boolean = true
    override val locationAPriority: Int = 90  // after music (0), before most others (100)

    // ── Location A — condition emoji ──────────────────────────────────────────

    @Composable
    override fun LocationAContent() {
        val snapshot by WeatherRepository.flow.collectAsState()
        Text(
            text     = snapshot?.condition?.toEmoji() ?: "🌡️",
            fontSize = 14.sp,
        )
    }

    // ── Indicator — emoji + temp / feels-like ─────────────────────────────────

    @Composable
    override fun Indicator() {
        val snapshot by WeatherRepository.flow.collectAsState()

        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 90.dp),
        ) {
            // Line 1: emoji + temperature
            Text(
                text       = if (snapshot != null)
                    "${snapshot!!.condition.toEmoji()} ${snapshot!!.tempC}°"
                else "· · ·",
                color      = Color.White,
                fontSize   = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            // Line 2: feels-like
            Text(
                text     = if (snapshot != null) "feels ${snapshot!!.feelsLikeC}°" else "",
                color    = Color(0xFFAAAAAA),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
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

            // ── Row 1: current detail bar ─────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                // Condition + temp
                DetailChip(
                    label = "${snap.condition.toEmoji()} ${snap.tempC}°",
                    bold  = true,
                )
                // Feels-like
                DetailChip(label = "feels ${snap.feelsLikeC}°")
                // Humidity
                DetailChip(label = "💧 ${snap.humidityPct}%")
                // Wind
                DetailChip(label = "💨 ${snap.windSpeedKmh} ${snap.windDir}")
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

/** Single chip in the current-detail bar (Row 1). */
@Composable
private fun DetailChip(label: String, bold: Boolean = false) {
    Text(
        text       = label,
        color      = if (bold) Color.White else Color(0xFFCCCCCC),
        fontSize   = 10.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        maxLines   = 1,
        overflow   = TextOverflow.Clip,
    )
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
        Text(
            text     = slot.condition.toEmoji(),
            fontSize = 11.sp,
        )
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