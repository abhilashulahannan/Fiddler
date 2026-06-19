package com.example.fiddler.subapps.Fidland.phs3.call

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Phs3 Call — Location A: phone-handset icon.
 *
 * A simple filled handset glyph, mono-coloured.
 *   - Missed calls  → red  (#EF4444)
 *   - Incoming / active calls → green (#22C55E)
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
    val color = if (missed) Color(0xFFEF4444) else Color(0xFF22C55E)

    Box(modifier = Modifier.size(size)) {
        Canvas(modifier = Modifier.size(size)) {
            val scale = size.toPx() / 24f
            fun pt(x: Float, y: Float) = Offset(x * scale, y * scale)

            // Classic filled "handset" glyph (Material-style phone icon),
            // drawn on a 24x24 grid.
            val path = Path().apply {
                moveTo(pt(6.6f, 10.8f).x, pt(6.6f, 10.8f).y)
                cubicTo(
                    pt(8.1f, 13.7f).x, pt(8.1f, 13.7f).y,
                    pt(10.3f, 15.9f).x, pt(10.3f, 15.9f).y,
                    pt(13.2f, 17.4f).x, pt(13.2f, 17.4f).y
                )
                lineTo(pt(15.5f, 15.1f).x, pt(15.5f, 15.1f).y)
                cubicTo(
                    pt(15.8f, 14.8f).x, pt(15.8f, 14.8f).y,
                    pt(16.3f, 14.7f).x, pt(16.3f, 14.7f).y,
                    pt(16.7f, 14.9f).x, pt(16.7f, 14.9f).y
                )
                lineTo(pt(20.4f, 17.0f).x, pt(20.4f, 17.0f).y)
                cubicTo(
                    pt(20.9f, 17.3f).x, pt(20.9f, 17.3f).y,
                    pt(21.1f, 17.9f).x, pt(21.1f, 17.9f).y,
                    pt(20.9f, 18.4f).x, pt(20.9f, 18.4f).y
                )
                cubicTo(
                    pt(20.3f, 19.9f).x, pt(20.3f, 19.9f).y,
                    pt(18.9f, 21.0f).x, pt(18.9f, 21.0f).y,
                    pt(17.3f, 21.0f).x, pt(17.3f, 21.0f).y
                )
                cubicTo(
                    pt(9.4f, 21.0f).x, pt(9.4f, 21.0f).y,
                    pt(3.0f, 14.6f).x, pt(3.0f, 14.6f).y,
                    pt(3.0f, 6.7f).x, pt(3.0f, 6.7f).y
                )
                cubicTo(
                    pt(3.0f, 5.1f).x, pt(3.0f, 5.1f).y,
                    pt(4.1f, 3.7f).x, pt(4.1f, 3.7f).y,
                    pt(5.6f, 3.1f).x, pt(5.6f, 3.1f).y
                )
                cubicTo(
                    pt(6.1f, 2.9f).x, pt(6.1f, 2.9f).y,
                    pt(6.7f, 3.1f).x, pt(6.7f, 3.1f).y,
                    pt(7.0f, 3.6f).x, pt(7.0f, 3.6f).y
                )
                lineTo(pt(9.1f, 7.3f).x, pt(9.1f, 7.3f).y)
                cubicTo(
                    pt(9.3f, 7.7f).x, pt(9.3f, 7.7f).y,
                    pt(9.2f, 8.2f).x, pt(9.2f, 8.2f).y,
                    pt(8.9f, 8.5f).x, pt(8.9f, 8.5f).y
                )
                lineTo(pt(6.6f, 10.8f).x, pt(6.6f, 10.8f).y)
                close()
            }
            drawPath(path, color, style = Fill)

            // Missed-call slash: a short diagonal stroke through the icon,
            // matching the "missed call" convention (top-right to bottom-left
            // arrow-like cut). Keep simple — a single rounded stroke.
            if (missed) {
                drawLine(
                    color = color,
                    start = pt(15f, 4f),
                    end = pt(20.5f, 9.5f),
                    strokeWidth = 1.8f * scale,
                    cap = StrokeCap.Round
                )
                // Arrowhead pointing down-left (missed/declined call arrow).
                val arrow = Path().apply {
                    moveTo(pt(20.5f, 9.5f).x, pt(20.5f, 9.5f).y)
                    lineTo(pt(20.5f, 5.5f).x, pt(20.5f, 5.5f).y)
                    moveTo(pt(20.5f, 9.5f).x, pt(20.5f, 9.5f).y)
                    lineTo(pt(16.5f, 9.5f).x, pt(16.5f, 9.5f).y)
                }
                drawPath(
                    arrow,
                    color,
                    style = Stroke(
                        width = 1.8f * scale,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}