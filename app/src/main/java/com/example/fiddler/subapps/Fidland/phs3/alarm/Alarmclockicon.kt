package com.example.fiddler.subapps.Fidland.phs3.alarm

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Phs3 Alarm — alarm-clock bell icon.
 *
 * Mono-colour line drawing of a bell (body, top arc, bottom mouth, two feet,
 * clapper, two alarm tabs, two "ring" wiggle lines either side) — see
 * phs3_alarm_entity.html for the reference SVG this mirrors.
 *
 * Colour animates smoothly between green → yellow → red as [remainingMs]
 * decreases:
 *   > 15 min  → green  (#22C55E)
 *   5–15 min  → yellow (#FACC15)
 *   ≤ 5 min   → red    (#EF4444), plus a wiggle/shake animation and the
 *                two ring-lines fade in.
 *
 * @param remainingMs Time left until the alarm rings, in milliseconds.
 * @param size        Overall icon size. Defaults to 26.dp to match the
 *                     reference (matches IslandConfig.BASE_SIZE in practice).
 */
@Composable
fun AlarmClockIcon(
    remainingMs: Long,
    size: Dp = 26.dp
) {
    val stage = iconStage(remainingMs)
    val wiggle = shouldWiggle(remainingMs)

    val targetColor = when (stage) {
        AlarmIconStage.GREEN  -> Color(0xFF22C55E)
        AlarmIconStage.YELLOW -> Color(0xFFFACC15)
        AlarmIconStage.RED    -> Color(0xFFEF4444)
    }
    val color by animateColorAsState(targetColor, label = "alarm_icon_color")

    // Wiggle: ±6deg rotation, 0.5s ease-in-out, only while ringing-soon.
    val infiniteTransition = rememberInfiniteTransition(label = "alarm_wiggle")
    val wiggleAngle by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alarm_wiggle_angle"
    )
    val rotation = if (wiggle) wiggleAngle else 0f

    Box(
        modifier = Modifier
            .size(size)
            .rotate(rotation)
    ) {
        Canvas(modifier = Modifier.size(size)) {
            // Reference viewBox is 0 0 26 26 — scale to actual size.
            val scale = size.toPx() / 26f
            fun pt(x: Float, y: Float) = Offset(x * scale, y * scale)
            val strokeWidth = 1.6f * scale
            val ringStrokeWidth = 1.4f * scale
            val cap = androidx.compose.ui.graphics.StrokeCap.Round

            // Bell body — ellipse cx=13 cy=14 rx=7 ry=6.5
            drawOval(
                color = color,
                topLeft = pt(13f - 7f, 14f - 6.5f),
                size = androidx.compose.ui.geometry.Size(14f * scale, 13f * scale),
                style = Stroke(width = strokeWidth, cap = cap)
            )

            // Top arc: M6 14 Q6 7 13 7 Q20 7 20 14
            val topArc = androidx.compose.ui.graphics.Path().apply {
                moveTo(pt(6f, 14f).x, pt(6f, 14f).y)
                quadraticBezierTo(pt(6f, 7f).x, pt(6f, 7f).y, pt(13f, 7f).x, pt(13f, 7f).y)
                quadraticBezierTo(pt(20f, 7f).x, pt(20f, 7f).y, pt(20f, 14f).x, pt(20f, 14f).y)
            }
            drawPath(topArc, color, style = Stroke(width = strokeWidth, cap = cap))

            // Bottom mouth: M8.5 20.5 Q13 22.5 17.5 20.5
            val bottomArc = androidx.compose.ui.graphics.Path().apply {
                moveTo(pt(8.5f, 20.5f).x, pt(8.5f, 20.5f).y)
                quadraticBezierTo(pt(13f, 22.5f).x, pt(13f, 22.5f).y, pt(17.5f, 20.5f).x, pt(17.5f, 20.5f).y)
            }
            drawPath(bottomArc, color, style = Stroke(width = strokeWidth, cap = cap))

            // Left foot: M9 20.5 L8 22
            drawLine(color = color, start = pt(9f, 20.5f), end = pt(8f, 22f), strokeWidth = strokeWidth, cap = cap)
            // Right foot: M17 20.5 L18 22
            drawLine(color = color, start = pt(17f, 20.5f), end = pt(18f, 22f), strokeWidth = strokeWidth, cap = cap)

            // Clapper — circle cx=13 cy=19 r=1.1, filled
            drawCircle(color = color, radius = 1.1f * scale, center = pt(13f, 19f))

            // Left tab: M8 8.5 Q5 6 6.5 4
            val leftTab = androidx.compose.ui.graphics.Path().apply {
                moveTo(pt(8f, 8.5f).x, pt(8f, 8.5f).y)
                quadraticBezierTo(pt(5f, 6f).x, pt(5f, 6f).y, pt(6.5f, 4f).x, pt(6.5f, 4f).y)
            }
            drawPath(leftTab, color, style = Stroke(width = strokeWidth, cap = cap))

            // Right tab: M18 8.5 Q21 6 19.5 4
            val rightTab = androidx.compose.ui.graphics.Path().apply {
                moveTo(pt(18f, 8.5f).x, pt(18f, 8.5f).y)
                quadraticBezierTo(pt(21f, 6f).x, pt(21f, 6f).y, pt(19.5f, 4f).x, pt(19.5f, 4f).y)
            }
            drawPath(rightTab, color, style = Stroke(width = strokeWidth, cap = cap))

            // Ring lines — only drawn during the wiggle (red) stage.
            if (wiggle) {
                // Left: M3 12 Q4 11 5 12
                val ringLeft = androidx.compose.ui.graphics.Path().apply {
                    moveTo(pt(3f, 12f).x, pt(3f, 12f).y)
                    quadraticBezierTo(pt(4f, 11f).x, pt(4f, 11f).y, pt(5f, 12f).x, pt(5f, 12f).y)
                }
                drawPath(ringLeft, color, style = Stroke(width = ringStrokeWidth, cap = cap))

                // Right: M21 12 Q22 11 23 12
                val ringRight = androidx.compose.ui.graphics.Path().apply {
                    moveTo(pt(21f, 12f).x, pt(21f, 12f).y)
                    quadraticBezierTo(pt(22f, 11f).x, pt(22f, 11f).y, pt(23f, 12f).x, pt(23f, 12f).y)
                }
                drawPath(ringRight, color, style = Stroke(width = ringStrokeWidth, cap = cap))
            }
        }
    }
}