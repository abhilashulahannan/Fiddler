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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler

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
                WifiSignalIcon(bars = snapshot.wifi.signalBars)
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
 * Simple BT glyph (the classic angular "bowtie" rune). [connected] swaps the
 * dot below it from dim to bright white — a quick visual for "paired vs idle."
 */
@Composable
private fun BluetoothIcon(connected: Boolean, sizeDp: androidx.compose.ui.unit.Dp = 14.dp) {
    val color = Color.White
    Box(modifier = Modifier.size(sizeDp)) {
        Canvas(modifier = Modifier.size(sizeDp)) {
            val scale = size.width / 14f
            val sw = 1.4f * scale
            val path = Path().apply {
                // Vertical stem
                moveTo(7f * scale, 1f * scale)
                lineTo(7f * scale, 13f * scale)
                // Upper triangle
                moveTo(7f * scale, 1f * scale)
                lineTo(11f * scale, 4.5f * scale)
                lineTo(3f * scale, 9.5f * scale)
                // Lower triangle
                moveTo(7f * scale, 13f * scale)
                lineTo(11f * scale, 9.5f * scale)
                lineTo(3f * scale, 4.5f * scale)
            }
            drawPath(path, color, style = Stroke(width = sw, cap = StrokeCap.Round))
        }
    }
    if (connected) {
        // Small bright dot rendered just under the glyph by the caller's Row spacing.
    }
}

/** WiFi fan-arc icon, reusing the same visual language as DownloadNetworkIcon's
 *  WIFI case, with bar count optionally dimming the outer arcs to show signal. */
@Composable
private fun WifiSignalIcon(bars: Int?, sizeDp: androidx.compose.ui.unit.Dp = 14.dp) {
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

/** Small "N" badge — NFC has no standard universal glyph, so a bold letter
 *  badge is the clearest compact representation at icon-row size. */
@Composable
private fun NfcIcon(sizeDp: androidx.compose.ui.unit.Dp = 14.dp) {
    Box(modifier = Modifier.size(sizeDp)) {
        Canvas(modifier = Modifier.size(sizeDp)) {
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = this@Canvas.size.width * 0.62f
                    typeface = android.graphics.Typeface.create(
                        android.graphics.Typeface.DEFAULT_BOLD,
                        android.graphics.Typeface.BOLD
                    )
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                drawText(
                    "N",
                    this@Canvas.size.width / 2f,
                    this@Canvas.size.height / 2f + paint.textSize / 3f,
                    paint
                )
            }
        }
    }
}

/** Cellular generation label + small signal-bar columns, mirroring
 *  DownloadNetworkIcon's CELLULAR_* case layout. */
@Composable
private fun CellularIcon(generation: CellularGeneration, bars: Int?, sizeDp: androidx.compose.ui.unit.Dp = 16.dp) {
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

            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 7.5f * scale
                    typeface = android.graphics.Typeface.create(
                        android.graphics.Typeface.DEFAULT_BOLD,
                        android.graphics.Typeface.BOLD
                    )
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                drawText(
                    generation.label(),
                    this@Canvas.size.width / 2f,
                    this@Canvas.size.height - 1f * scale,
                    paint
                )
            }
        }
    }
}

/** Plain airplane glyph for the all-radios-off override state. */
@Composable
private fun AirplaneModeIcon(sizeDp: androidx.compose.ui.unit.Dp = 16.dp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(sizeDp)) {
            Canvas(modifier = Modifier.size(sizeDp)) {
                val scale = size.width / 20f
                val sw = 1.4f * scale
                val path = Path().apply {
                    moveTo(10f * scale, 2f * scale)
                    lineTo(11.5f * scale, 9f * scale)
                    lineTo(18f * scale, 12.5f * scale)
                    lineTo(18f * scale, 14f * scale)
                    lineTo(11.5f * scale, 12f * scale)
                    lineTo(10.5f * scale, 17f * scale)
                    lineTo(12.5f * scale, 18.5f * scale)
                    lineTo(12.5f * scale, 19.5f * scale)
                    lineTo(10f * scale, 18.5f * scale)
                    lineTo(7.5f * scale, 19.5f * scale)
                    lineTo(7.5f * scale, 18.5f * scale)
                    lineTo(9.5f * scale, 17f * scale)
                    lineTo(8.5f * scale, 12f * scale)
                    lineTo(2f * scale, 14f * scale)
                    lineTo(2f * scale, 12.5f * scale)
                    lineTo(8.5f * scale, 9f * scale)
                    close()
                }
                drawPath(path, Color.White, style = Stroke(width = sw, cap = StrokeCap.Round))
            }
        }
        Text(text = "Airplane mode", color = Color.White, fontSize = 11.sp)
    }
}