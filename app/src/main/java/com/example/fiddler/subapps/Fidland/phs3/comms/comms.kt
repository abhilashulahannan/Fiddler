package com.example.fiddler.subapps.Fidland.phs3.comms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.R
import com.example.fiddler.subapps.Fidland.phs3.BlockAffinity
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler
import com.example.fiddler.ui.icons.MonoLottieIcon

/**
 * Phs3 module — Comms.
 *
 * Shows which communication radios are currently enabled — Bluetooth, WiFi,
 * NFC, Cellular — with whatever live detail is available for each (connected
 * device name, SSID + signal bars, on/off, generation + signal bars).
 *
 * Driven by [CommsPhs3Trigger] / [CommsAggregator]; this file only renders
 * the [CommsSnapshot] it's constructed with — DownloadPhs3Trigger reconstructs
 * a fresh [CommsPhs3Handler] on every aggregator emission, same pattern as
 * the Download module.
 *
 * ── Airplane mode override ────────────────────────────────────────────────────
 * When [CommsSnapshot.airplaneModeOn] is true, the Indicator collapses to a
 * single airplane icon instead of the per-radio row — showing four crossed
 * icons under an airplane icon is redundant, since airplane mode already
 * implies "everything off" (Bluetooth/WiFi CAN still be manually re-enabled
 * during airplane mode on modern Android, so this is a simplification, not a
 * strict guarantee — see ControlsPanel for the caveat shown to the user).
 *
 * @param snapshot Live snapshot of all four radios + airplane mode.
 */
class CommsPhs3Handler(
    private val snapshot: CommsSnapshot
) : Phs3Handler {

    override val label: String = "Comms"

    // §B7 — renders additively alongside whatever else holds the slot
    // (Camera/Flashlight's mechanic), and is width-competitive like any
    // other DYNAMIC block rather than pinned right — see CommsPhs3Trigger's
    // class doc for why this handler is only ever registered transiently.
    override val coDisplay: Boolean = true
    override val blockAffinity: BlockAffinity = BlockAffinity.DYNAMIC

    // §B7 — spec calls for the icon set itself to be the entire surface,
    // straight to DASHBOARD on swipe-down (Camera/Battery's shape), not a
    // separate reading view. The detail rows below remain implemented and
    // reachable if this default is ever revisited — only the gate flips.
    override fun hasState5Content(): Boolean = false

    @Composable
    override fun Indicator() {
        if (snapshot.airplaneModeOn) {
            AirplaneModeIcon()
            return
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (snapshot.bluetooth.isEnabled) {
                BluetoothIcon(connected = snapshot.bluetooth.connectedDevice != null)
            }
            if (snapshot.wifi.isEnabled) {
                WifiIcon(wifi = snapshot.wifi)
            }
            if (snapshot.nfc.isEnabled) {
                NfcIcon()
            }
            if (snapshot.cellular.hasService) {
                CellularIcon(
                    generation = snapshot.cellular.generation,
                    bars = snapshot.cellular.signalBars,
                )
            }
        }
    }

    @Composable
    override fun State5Content() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (snapshot.airplaneModeOn) {
                CommsDetailRow(
                    title = "Airplane mode",
                    detail = "On — radios may still be manually re-enabled"
                )
            }

            CommsDetailRow(
                title = "Bluetooth",
                detail = when {
                    !snapshot.bluetooth.isEnabled -> "Off"
                    snapshot.bluetooth.connectedDevice != null -> "Connected — ${snapshot.bluetooth.connectedDevice}"
                    else -> "On — no device connected"
                }
            )

            CommsDetailRow(
                title = "WiFi",
                detail = when {
                    !snapshot.wifi.isEnabled -> "Off"
                    snapshot.wifi.ssid != null -> buildString {
                        append(snapshot.wifi.ssid)
                        when (snapshot.wifi.band) {
                            WifiBand.GHZ_2_4 -> append(" · 2.4 GHz")
                            WifiBand.GHZ_5    -> append(" · 5 GHz")
                            null              -> {}
                        }
                        if (snapshot.wifi.rssiDbm != null) append(" · ${snapshot.wifi.rssiDbm} dBm")
                    }
                    else -> "On — not connected"
                }
            )

            CommsDetailRow(
                title = "NFC",
                detail = if (snapshot.nfc.isEnabled) "On" else "Off"
            )

            CommsDetailRow(
                title = "Cellular",
                detail = if (!snapshot.cellular.hasService) "No service" else buildString {
                    append(snapshot.cellular.generation.label())
                    snapshot.cellular.carrierName?.let { append(" · $it") }
                    snapshot.cellular.signalBars?.let { append(" · ${it}/4 bars") }
                }
            )
        }
    }
}

// ── Detail row (ControlsPanel) ──────────────────────────────────────────────

@Composable
private fun CommsDetailRow(title: String, detail: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(text = detail, color = Color(0xFF999999), fontSize = 12.sp)
    }
}

// ── Indicator icons ──────────────────────────────────────────────────────────

/**
 * Bluetooth radio icon — loops `res/raw/comms_bt.json`. [connected] adds a
 * small bright dot in the corner when a device is actively paired, dim/absent
 * otherwise — the old canvas version had a `connected` branch that was a
 * no-op stub; this is the first real implementation of that indicator.
 */
@Composable
private fun BluetoothIcon(connected: Boolean, sizeDp: Dp = 14.dp) {
    Box(modifier = Modifier.size(sizeDp)) {
        MonoLottieIcon(rawRes = R.raw.comms_bt, size = sizeDp)
        if (connected) {
            Canvas(modifier = Modifier.size(sizeDp)) {
                val scale = size.width / 14f
                drawCircle(
                    color = Color.White,
                    radius = 1.4f * scale,
                    center = Offset(12f * scale, 12f * scale),
                )
            }
        }
    }
}

/**
 * WiFi radio icon. Plays the matching band Lottie asset
 * (`comms_wifi24g.json` / `comms_wifi5g.json` — see [WifiBand.toRawRes])
 * when [WifiCommsInfo.band] is known. Falls back to the old hand-drawn
 * fan-arc + bar-count icon ([WifiSignalBarsIcon]) when band detection
 * comes back null — not connected yet, or (rarely) a 6 GHz network outside
 * both known ranges — so the indicator never goes fully blank while WiFi
 * is enabled.
 *
 * Replaces the previous bar-count-only WifiSignalIcon as the primary WiFi
 * glyph, per the handoff doc's band-detection task.
 */
@Composable
private fun WifiIcon(wifi: WifiCommsInfo, sizeDp: Dp = 14.dp) {
    val band = wifi.band
    if (band != null) {
        MonoLottieIcon(rawRes = band.toRawRes(), size = sizeDp)
    } else {
        WifiSignalBarsIcon(bars = wifi.signalBars, sizeDp = sizeDp)
    }
}

/** WiFi fan-arc icon, reusing the same visual language as DownloadNetworkIcon's
 *  WIFI case, with bar count optionally dimming the outer arcs to show signal.
 *  Kept as the fallback for [WifiIcon] when frequency band isn't known yet. */
@Composable
private fun WifiSignalBarsIcon(bars: Int?, sizeDp: Dp = 14.dp) {
    val activeArcs = bars?.let { ((it / 4f) * 3).toInt().coerceIn(1, 3) } ?: 3
    Box(modifier = Modifier.size(sizeDp)) {
        Canvas(modifier = Modifier.size(sizeDp)) {
            val scale = size.width / 20f
            val sw = 1.5f * scale
            val cx = 10f
            val cy = 13f

            drawCircle(color = Color.White, radius = 1.4f * scale, center = Offset(cx * scale, 16f * scale))

            val radii = listOf(3f, 5.5f, 8f)
            radii.forEachIndexed { index, r ->
                val isActive = index < activeArcs
                val arcColor = if (isActive) Color.White else Color.White.copy(alpha = 0.25f)
                val path = Path().apply {
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            center = Offset(cx * scale, cy * scale),
                            radius = r * scale
                        ),
                        startAngleDegrees = 210f,
                        sweepAngleDegrees = 120f,
                        forceMoveTo = true
                    )
                }
                drawPath(path, arcColor, style = Stroke(width = sw, cap = StrokeCap.Round))
            }
        }
    }
}

/** NFC radio icon — loops `res/raw/comms_nfc.json`, replacing the old bold
 *  "N" text badge (NFC has no standard universal glyph, so the badge was a
 *  stand-in until this asset existed). */
@Composable
private fun NfcIcon(sizeDp: Dp = 14.dp) {
    MonoLottieIcon(rawRes = R.raw.comms_nfc, size = sizeDp)
}

/**
 * Signal-bar columns + generation icon, mirroring DownloadNetworkIcon's
 * CELLULAR_* case layout. Bars stay hand-drawn (no asset for those); the
 * generation label is now the matching looping Lottie asset
 * (res/raw/comms_2g.json … comms_5g.json — see [CellularGeneration.toRawRes])
 * instead of a canvas-drawn text glyph. UNKNOWN generation has no asset, so
 * it falls back to the old "—" text label.
 */
@Composable
private fun CellularIcon(generation: CellularGeneration, bars: Int?, sizeDp: Dp = 16.dp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        CellularBars(bars = bars, sizeDp = sizeDp)

        val rawRes = generation.toRawRes()
        if (rawRes != null) {
            MonoLottieIcon(rawRes = rawRes, size = sizeDp)
        } else {
            Text(
                text       = generation.label(),
                color      = Color.White,
                fontSize   = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Signal-strength bar columns, split out of the old combined CellularIcon
 *  canvas so the generation glyph beside it can become a Lottie asset. */
@Composable
private fun CellularBars(bars: Int?, sizeDp: Dp) {
    Box(modifier = Modifier.size(sizeDp)) {
        Canvas(modifier = Modifier.size(sizeDp)) {
            val scale = size.width / 20f
            val activeBars = bars ?: 4

            val barW = 2f * scale
            val barGap = 1.2f * scale
            val barMaxH = 9f * scale
            val barBaseY = 15f * scale
            val barFractions = listOf(0.3f, 0.55f, 0.8f, 1.0f)

            barFractions.forEachIndexed { i, frac ->
                val barH = barMaxH * frac
                val left = (2f + i * (barW / scale + barGap / scale)) * scale
                val isActive = i < activeBars
                drawRect(
                    color = Color.White.copy(alpha = if (isActive) 0.9f else 0.2f),
                    topLeft = Offset(left, barBaseY - barH),
                    size = androidx.compose.ui.geometry.Size(barW, barH)
                )
            }
        }
    }
}

/** Airplane-mode indicator for the all-radios-off override state — loops
 *  `res/raw/comms_airplane.json` next to the same "Airplane mode" label. */
@Composable
private fun AirplaneModeIcon(sizeDp: Dp = 16.dp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        MonoLottieIcon(rawRes = R.raw.comms_airplane, size = sizeDp)
        Text(text = "Airplane mode", color = Color.White, fontSize = 11.sp)
    }
}