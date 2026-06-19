package com.example.fiddler.subapps.Fidland.phs3.download

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Phs3 Download — Location 1 icon: current network type.
 *
 * Draws a mono-coloured line icon matched to [networkType]:
 *   WIFI          → Classic fan-arc WiFi glyph (3 arcs + dot), white.
 *   CELLULAR_3G   → Bold "3G" text label drawn on canvas, white.
 *   CELLULAR_4G   → Bold "4G" text label drawn on canvas, white.
 *   CELLULAR_5G   → Bold "5G" text label drawn on canvas, white.
 *   UNKNOWN       → A simple question-mark dot, dim grey.
 *
 * Drawn on a 20×20 virtual grid, scaled to [size].
 *
 * @param networkType The active network type for the download.
 * @param size        Icon size; defaults to 18.dp to sit neatly in the
 *                     indicator row alongside the ETA text and progress ring.
 */
@Composable
fun DownloadNetworkIcon(
    networkType: DownloadNetworkType,
    size: Dp = 18.dp
) {
    val color = if (networkType == DownloadNetworkType.UNKNOWN) Color(0xFF666666) else Color.White

    Box(modifier = Modifier.size(size)) {
        Canvas(modifier = Modifier.size(size)) {
            val scale = size.toPx() / 20f
            fun pt(x: Float, y: Float) = Offset(x * scale, y * scale)
            val sw = 1.5f * scale
            val cap = StrokeCap.Round

            when (networkType) {

                // ── WiFi fan arcs + dot ─────────────────────────────────────
                DownloadNetworkType.WIFI -> {
                    val cx = 10f
                    val cy = 13f // centre of the fan arcs

                    // Dot
                    drawCircle(color = color, radius = 1.4f * scale, center = pt(cx, 16f))

                    // Inner arc — r ≈ 3
                    val innerArc = Path().apply {
                        val r = 3f * scale
                        // Arc from ~220° to ~320° centred on (cx,cy)
                        arcTo(
                            rect = androidx.compose.ui.geometry.Rect(
                                center = pt(cx, cy),
                                radius = r
                            ),
                            startAngleDegrees = 210f,
                            sweepAngleDegrees = 120f,
                            forceMoveTo = true
                        )
                    }
                    drawPath(innerArc, color, style = Stroke(width = sw, cap = cap))

                    // Middle arc — r ≈ 5.5
                    val midArc = Path().apply {
                        val r = 5.5f * scale
                        arcTo(
                            rect = androidx.compose.ui.geometry.Rect(
                                center = pt(cx, cy),
                                radius = r
                            ),
                            startAngleDegrees = 210f,
                            sweepAngleDegrees = 120f,
                            forceMoveTo = true
                        )
                    }
                    drawPath(midArc, color, style = Stroke(width = sw, cap = cap))

                    // Outer arc — r ≈ 8
                    val outerArc = Path().apply {
                        val r = 8f * scale
                        arcTo(
                            rect = androidx.compose.ui.geometry.Rect(
                                center = pt(cx, cy),
                                radius = r
                            ),
                            startAngleDegrees = 210f,
                            sweepAngleDegrees = 120f,
                            forceMoveTo = true
                        )
                    }
                    drawPath(outerArc, color, style = Stroke(width = sw, cap = cap))
                }

                // ── Cellular text labels (3G / 4G / 5G) ────────────────────
                DownloadNetworkType.CELLULAR_3G,
                DownloadNetworkType.CELLULAR_4G,
                DownloadNetworkType.CELLULAR_5G -> {
                    val label = when (networkType) {
                        DownloadNetworkType.CELLULAR_3G -> "3G"
                        DownloadNetworkType.CELLULAR_4G -> "4G"
                        else                            -> "5G"
                    }

                    // Draw signal-bar columns behind the label as background context.
                    // Three bars of increasing height, left-aligned.
                    val barW = 2f * scale
                    val barGap = 1.2f * scale
                    val barMaxH = 9f * scale
                    val barBaseY = 15f * scale
                    val barHeights = listOf(0.4f, 0.7f, 1.0f)
                    barHeights.forEachIndexed { i, frac ->
                        val barH = barMaxH * frac
                        val left = (2f + i * (barW / scale + barGap / scale)) * scale
                        drawRect(
                            color = color.copy(alpha = 0.25f),
                            topLeft = Offset(left, barBaseY - barH),
                            size = Size(barW, barH)
                        )
                    }

                    // Text label centred in the icon.
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            this.color = android.graphics.Color.WHITE
                            textSize = 8f * scale
                            typeface = android.graphics.Typeface.create(
                                android.graphics.Typeface.DEFAULT_BOLD,
                                android.graphics.Typeface.BOLD
                            )
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                        drawText(
                            label,
                            (this@Canvas.size.width / 2),   // ← qualify with this@Canvas
                            (this@Canvas.size.height / 2) + (paint.textSize / 3),
                            paint
                        )
                    }
                }

                // ── Unknown / no network ─────────────────────────────────────
                DownloadNetworkType.UNKNOWN -> {
                    // Small dot in the centre to indicate indeterminate state.
                    drawCircle(
                        color = color,
                        radius = 2f * scale,
                        center = pt(10f, 10f)
                    )
                }
            }
        }
    }
}