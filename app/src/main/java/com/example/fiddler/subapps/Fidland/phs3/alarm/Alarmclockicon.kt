package com.example.fiddler.subapps.Fidland.phs3.alarm

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fiddler.R
import com.example.fiddler.ui.icons.MonoLottieIcon

/**
 * Phs3 Alarm — alarm-clock bell icon.
 *
 * Plays the looping `res/raw/alarm.json` bell animation (replaces the
 * hand-drawn bell path — body, arcs, feet, clapper, tabs — that used to be
 * drawn stroke-by-stroke on a Canvas).
 *
 * The urgency signal that made the old Canvas version worth having its own
 * file — colour sweeping green → yellow → red, plus a wiggle in the final
 * stretch — is preserved rather than dropped along with the hand-drawn
 * paths:
 *   • Colour: animated here (green → yellow → red by [remainingMs]) and
 *     passed as an explicit `color` override to [MonoLottieIcon], which
 *     does the actual per-frame stencil recolour — see its kdoc for the
 *     mechanics. This is one of the "dynamic exceptions" noted in
 *     [com.example.fiddler.ui.icons.LottieIconColors]: the colour depends
 *     on runtime state, so it can't be a static config entry.
 *   • Wiggle: unchanged — still a ±6° rotation on the whole icon `Box`,
 *     driven by the same [shouldWiggle] threshold as before.
 *
 * Colour stages, by [remainingMs]:
 *   > 15 min  → green  (#22C55E)
 *   5–15 min  → yellow (#FACC15)
 *   ≤ 5 min   → red    (#EF4444), plus the wiggle animation.
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
        MonoLottieIcon(
            rawRes = R.raw.alarm,
            size   = size,
            color  = color,
        )
    }
}