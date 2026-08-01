package com.example.fiddler.subapps.Fidland.phs3.battery

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Phs3 Battery — hand-drawn battery glyph, replacing the two static
 * `battery_charge.json` / `battery_normal.json` Lottie loops.
 *
 * ── Why not the Lottie assets ────────────────────────────────────────────
 * Both res/raw compositions turned out to be fully static — every property
 * in both files has `"a": 0` (no keyframes) — so looping them never
 * produced any motion, and the fill you see baked into their paths is a
 * fixed ~66%/~73% shape, not tied to the real battery level. Matching the
 * actual charge (per the phs3 handoff) needs a fill that can be computed
 * per-frame, which a static Lottie composition can't give us without a
 * named keypath to drive via dynamic properties — and these layers are
 * unnamed. So this draws the glyph on Canvas instead, same technique as
 * [com.example.fiddler.subapps.Fidland.phs3.download.DownloadProgressRing]
 * and [com.example.fiddler.subapps.Fidland.phs3.download.DownloadNetworkIcon].
 *
 * ── Shape ─────────────────────────────────────────────────────────────────
 * Outline body + terminal nub on a 26×16 virtual grid — chunkier than a
 * "realistic" battery glyph (was 24×10) specifically so the percentage
 * label has somewhere to live: [level]% is drawn centred *inside* the body
 * rather than as separate text alongside the icon, so the indicator stays
 * a single compact glyph instead of an icon-plus-label pair.
 *
 * ── Fill + label ──────────────────────────────────────────────────────────
 * A solid inner bar fills left→right to [level] percent, inset from the
 * outline like a real battery gauge. The percentage text sits centred over
 * the whole inner area regardless of how far the fill has progressed, so it
 * straddles both the filled (coloured) and unfilled (transparent) regions —
 * drawn with a dark outline pass before the fill pass (same double-draw
 * technique as the old bolt icon used) so it stays legible against either
 * background. No separate bolt glyph anymore: charging is communicated by
 * colour + pulse, and having both a bolt and a number crowded a 26dp glyph.
 *
 * ── Colour (state-driven, like [com.example.fiddler.subapps.Fidland.phs3.alarm.AlarmClockIcon]) ──
 *   • Charging               → green, gently pulsing (breathing alpha) —
 *     the actual "charging animation," since the old asset had none.
 *   • Discharging, ≤20%      → red — matches [LOW_BATTERY_THRESHOLD_PERCENT].
 *   • Discharging, otherwise → white.
 * The outline itself stays a dim neutral grey regardless of state, same as
 * the track ring in DownloadProgressRing — only the fill communicates state.
 *
 * @param level      0..100.
 * @param isCharging Whether to show the charging pulse / green fill.
 * @param size       Overall icon size; defaults to 26.dp to match the old
 *                   Indicator footprint (icon is square — same value used
 *                   for both width and height).
 */
@Composable
fun BatteryIcon(
    level: Int,
    isCharging: Boolean,
    size: Dp = 26.dp,
) {
    val clampedLevel = level.coerceIn(0, 100)

    val targetColor = when {
        isCharging -> Color(0xFF4ADE80) // green
        clampedLevel <= LOW_BATTERY_THRESHOLD_PERCENT -> Color(0xFFFC5C5C) // red
        else -> Color.White
    }
    val fillColor by animateColorAsState(targetColor, label = "battery_fill_color")

    // Smoothly animate toward the real level rather than snapping — broadcasts
    // arrive in coarse steps, so this keeps the bar reading as "live" rather
    // than jumpy.
    val fillFraction by animateFloatAsState(clampedLevel / 100f, label = "battery_fill_fraction")

    // Charging pulse — the actual animation the old static asset never had.
    // Breathing alpha on the fill only, while charging.
    val infiniteTransition = rememberInfiniteTransition(label = "battery_charge_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "battery_charge_pulse_alpha",
    )
    val fillAlpha = if (isCharging) pulseAlpha else 1f

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            // Grid: 26 wide × 16 tall — taller than the original 24×10 pass
            // so the body has enough vertical room for a legible label.
            val gridWidth = 26f
            val gridHeight = 16f
            val scale = this.size.width / gridWidth
            fun px(v: Float) = v * scale

            // Centre the content block vertically within the square canvas —
            // same reasoning as before: without this the shape would hug the
            // canvas's top edge instead of sitting on the row's centreline.
            val contentHeightPx = px(gridHeight)
            val yOffset = (this.size.height - contentHeightPx) / 2f
            fun py(v: Float) = px(v) + yOffset

            val outlineColor = Color(0xFF9A9A9A) // dim grey — matches DownloadProgressRing's track
            val outlineStroke = px(1.3f)
            val cornerRadius = CornerRadius(px(2f), px(2f))

            // ── Body outline: rounded rect, x∈[1,22], y∈[3,13] on the grid ──
            val bodyRect = Rect(
                left = px(1f),
                top = py(3f),
                right = px(22f),
                bottom = py(13f),
            )
            drawRoundRect(
                color = outlineColor,
                topLeft = bodyRect.topLeft,
                size = Size(bodyRect.width, bodyRect.height),
                cornerRadius = cornerRadius,
                style = Stroke(width = outlineStroke),
            )

            // ── Terminal nub, protruding right of the body ──
            val nubRect = Rect(
                left = px(22f),
                top = py(6f),
                right = px(25f),
                bottom = py(10f),
            )
            drawRoundRect(
                color = outlineColor,
                topLeft = nubRect.topLeft,
                size = Size(nubRect.width, nubRect.height),
                cornerRadius = CornerRadius(px(1f), px(1f)),
            )

            // ── Fill bar: inset from the outline, width = level% ──
            val insetX = px(1.6f)
            val insetY = px(1.6f)
            val innerLeft = bodyRect.left + insetX
            val innerTop = bodyRect.top + insetY
            val innerWidth = bodyRect.width - insetX * 2
            val innerHeight = bodyRect.height - insetY * 2

            if (fillFraction > 0f) {
                val filledWidth = (innerWidth * fillFraction).coerceAtLeast(px(0.6f))
                drawRoundRect(
                    color = fillColor.copy(alpha = fillAlpha),
                    topLeft = Offset(innerLeft, innerTop),
                    size = Size(filledWidth, innerHeight),
                    cornerRadius = CornerRadius(px(0.9f), px(0.9f)),
                )
            }

            // ── Percentage label, centred over the whole inner area ──
            // Drawn as an outline pass (dark, slightly thicker) then a fill
            // pass (light) on top, so it reads whether it's sitting over the
            // coloured fill or the transparent background behind it.
            val label = "${clampedLevel}%"
            val textCenterX = innerLeft + innerWidth / 2f
            val textCenterY = innerTop + innerHeight / 2f
            // Smaller font, as agreed — sized off the inner height so it
            // scales with [size] rather than a fixed sp value.
            val textSizePx = innerHeight * 0.62f

            drawContext.canvas.nativeCanvas.apply {
                val basePaint = android.graphics.Paint().apply {
                    textSize = textSizePx
                    typeface = android.graphics.Typeface.create(
                        android.graphics.Typeface.DEFAULT_BOLD,
                        android.graphics.Typeface.BOLD,
                    )
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                val baselineY = textCenterY + textSizePx * 0.36f

                // Outline pass — dark, for contrast against light fill.
                basePaint.style = android.graphics.Paint.Style.STROKE
                basePaint.strokeWidth = textSizePx * 0.16f
                basePaint.color = android.graphics.Color.argb(
                    (0.85f * fillAlpha * 255).toInt().coerceIn(0, 255), 0, 0, 0,
                )
                drawText(label, textCenterX, baselineY, basePaint)

                // Fill pass — light, for contrast against dark/transparent background.
                basePaint.style = android.graphics.Paint.Style.FILL
                basePaint.color = android.graphics.Color.argb(
                    (fillAlpha * 255).toInt().coerceIn(0, 255), 255, 255, 255,
                )
                drawText(label, textCenterX, baselineY, basePaint)
            }
        }
    }
}