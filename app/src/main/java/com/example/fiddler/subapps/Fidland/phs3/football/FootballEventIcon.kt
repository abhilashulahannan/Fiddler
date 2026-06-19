package com.example.fiddler.subapps.Fidland.phs3.football

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Phs3 Football — Location-a event icon.
 *
 * Renders one of three icons depending on [type]:
 *   GOAL          → ⚽ football (white circle + black pentagon patches).
 *                    Slowly spins to catch the eye.
 *   YELLOW_CARD   → Yellow rounded-rectangle card, slight tilt.
 *   RED_CARD      → Red rounded-rectangle card, slight tilt.
 *   YELLOW_RED_CARD → Stacked yellow + red (yellow behind, red in front).
 *
 * Drawn on a 20×20 virtual grid, scaled to [size] (default 20.dp to match
 * the compact pill height in State 3).
 *
 * Only shown for [FLASH_DURATION_MS] after an event arrives; the hosting
 * composable handles the timed visibility — this composable just draws.
 */
@Composable
fun FootballEventIcon(
    type: EventType,
    size: Dp = 20.dp,
) {
    when (type) {
        EventType.GOAL             -> FootballIcon(size)
        EventType.YELLOW_CARD      -> CardIcon(size, primary = Color(0xFFFFD600))
        EventType.RED_CARD         -> CardIcon(size, primary = Color(0xFFE53935))
        EventType.YELLOW_RED_CARD  -> DoubleCardIcon(size)
        else                       -> FootballIcon(size) // fallback
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Football icon
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FootballIcon(size: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "ball_spin")
    val angle by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 3_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ball_angle",
    )

    Box(modifier = Modifier.size(size)) {
        Canvas(modifier = Modifier.size(size)) {
            rotate(angle, pivot = center) {
                drawFootball(this)
            }
        }
    }
}

private fun DrawScope.drawFootball(scope: DrawScope) {
    val s = size.minDimension
    val r = s / 2f
    val cx = s / 2f; val cy = s / 2f

    // White circle
    scope.drawCircle(color = Color.White, radius = r, center = Offset(cx, cy))

    // Black outline
    scope.drawCircle(
        color  = Color.Black,
        radius = r,
        center = Offset(cx, cy),
        style  = Stroke(width = s * 0.06f),
    )

    // Central pentagon (simplified as a small filled circle for pill scale)
    scope.drawCircle(color = Color.Black, radius = r * 0.22f, center = Offset(cx, cy))

    // Five outer patches — evenly distributed around the ball at ~65% radius.
    val patchR = r * 0.13f
    val dist   = r * 0.58f
    for (i in 0 until 5) {
        val theta = Math.toRadians((i * 72.0) - 90.0)
        val px    = cx + dist * Math.cos(theta).toFloat()
        val py    = cy + dist * Math.sin(theta).toFloat()
        scope.drawCircle(color = Color.Black, radius = patchR, center = Offset(px, py))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Card icon (yellow / red)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CardIcon(size: Dp, primary: Color) {
    Box(modifier = Modifier.size(size)) {
        Canvas(modifier = Modifier.size(size)) {
            drawCard(
                color      = primary,
                tiltDeg    = -12f,
                scaleW     = 0.55f,
                scaleH     = 0.80f,
                offsetX    = size.toPx() * 0.0f,
                offsetY    = size.toPx() * 0.02f,
            )
        }
    }
}

@Composable
private fun DoubleCardIcon(size: Dp) {
    Box(modifier = Modifier.size(size)) {
        Canvas(modifier = Modifier.size(size)) {
            // Yellow card — behind, shifted slightly left
            drawCard(
                color   = Color(0xFFFFD600),
                tiltDeg = -18f,
                scaleW  = 0.50f,
                scaleH  = 0.75f,
                offsetX = size.toPx() * -0.08f,
                offsetY = size.toPx() * 0.05f,
            )
            // Red card — in front, shifted slightly right
            drawCard(
                color   = Color(0xFFE53935),
                tiltDeg = -6f,
                scaleW  = 0.50f,
                scaleH  = 0.75f,
                offsetX = size.toPx() * 0.08f,
                offsetY = size.toPx() * 0.02f,
            )
        }
    }
}

private fun DrawScope.drawCard(
    color: Color,
    tiltDeg: Float,
    scaleW: Float,
    scaleH: Float,
    offsetX: Float,
    offsetY: Float,
) {
    val s  = size.minDimension
    val w  = s * scaleW
    val h  = s * scaleH
    val cx = s / 2f + offsetX
    val cy = s / 2f + offsetY
    val left   = cx - w / 2f
    val top    = cy - h / 2f
    val corner = w * 0.18f

    rotate(tiltDeg, pivot = Offset(cx, cy)) {
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    rect         = Rect(left, top, left + w, top + h),
                    cornerRadius = CornerRadius(corner, corner),
                )
            )
        }
        drawPath(path, color = color)
        drawPath(path, color = Color.Black.copy(alpha = 0.25f), style = Stroke(width = s * 0.04f))
    }
}