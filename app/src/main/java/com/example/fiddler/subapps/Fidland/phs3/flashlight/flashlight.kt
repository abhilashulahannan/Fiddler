package com.example.fiddler.subapps.Fidland.phs3.flashlight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.fiddler.R
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler

/**
 * Phs3 module — Flashlight.
 *
 * Qualifies while the device torch is running. The hosting service
 * (FlashlightPhs3Trigger) should call `activatePhs3(FlashlightPhs3Handler(…))`
 * when [CameraManager.TorchCallback.onTorchChanged] fires with enabled = true,
 * and `deactivatePhs3()` when it fires with enabled = false.
 *
 * ─────────────────────────────────────────────────────
 * Location b — Indicator (State 3)
 * ─────────────────────────────────────────────────────
 * Plays the flashlight Lottie animation (res/raw/flashlight.json) on loop.
 * Kept deliberately compact — just the animated icon — so it fits cleanly
 * in the b-slot immediately to the right of the hole punch.
 *
 * ─────────────────────────────────────────────────────
 * State 5 — ControlsPanel (long-press expand)
 * ─────────────────────────────────────────────────────
 * A segmented brightness meter with [FLASHLIGHT_STRENGTH_STEPS] tappable
 * segments. Tapping any segment sets the brightness to that level and
 * calls [onStrengthChanged] so the caller can apply it via CameraManager.
 *
 * @param flashlightInfo   Live snapshot — holds the current strength level.
 * @param onStrengthChanged Callback invoked with the new [1..FLASHLIGHT_STRENGTH_STEPS]
 *                          level whenever the user adjusts the meter. The caller
 *                          should apply it (CameraManager torch-strength API /
 *                          vendor extension) and reconstruct this handler with
 *                          an updated [FlashlightInfo] so the meter reflects reality.
 */
class FlashlightPhs3Handler(
    private val flashlightInfo: FlashlightInfo = FlashlightInfo(),
    private val onStrengthChanged: (Int) -> Unit = {}
) : Phs3Handler {

    override val label: String = "Flashlight"

    // ── Location b — State 3 Indicator ──────────────────────────────────────

    @Composable
    override fun Indicator() {
        val composition by rememberLottieComposition(
            LottieCompositionSpec.RawRes(R.raw.flashlight_lottie)
        )
        val progress by animateLottieCompositionAsState(
            composition  = composition,
            iterations   = LottieConstants.IterateForever,
            isPlaying    = true,
        )

        Box(
            modifier          = Modifier.size(26.dp),
            contentAlignment  = Alignment.Center
        ) {
            LottieAnimation(
                composition = composition,
                progress    = { progress },
                modifier    = Modifier.size(26.dp)
            )
        }
    }

    // ── State 5 — ControlsPanel ─────────────────────────────────────────────

    @Composable
    override fun State5Content() {
        // Local UI state — starts at whatever the current hardware level is.
        var selectedLevel by remember { mutableIntStateOf(flashlightInfo.strengthLevel) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {

            // ── Header ───────────────────────────────────────────────────────
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Lottie icon in a tinted circle — mirrors alarm / call header style.
                val composition by rememberLottieComposition(
                    LottieCompositionSpec.RawRes(R.raw.flashlight_lottie)
                )
                val progress by animateLottieCompositionAsState(
                    composition = composition,
                    iterations  = LottieConstants.IterateForever,
                    isPlaying   = true,
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2B2B1A)),
                    contentAlignment = Alignment.Center
                ) {
                    LottieAnimation(
                        composition = composition,
                        progress    = { progress },
                        modifier    = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = "Flashlight",
                        color      = Color.White,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text     = "brightness",
                        color    = Color(0xFF888888),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }

                // Current level label on the right.
                Text(
                    text       = "$selectedLevel / $FLASHLIGHT_STRENGTH_STEPS",
                    color      = Color(0xFFFFCC00),
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            FlashlightDivider()

            // ── Strength meter ────────────────────────────────────────────────
            // Segmented bar: FLASHLIGHT_STRENGTH_STEPS tappable rectangles.
            // Filled segments are amber-yellow; empty segments are dim grey.
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text     = "torch strength",
                color    = Color(0xFF666666),
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            StrengthMeter(
                steps          = FLASHLIGHT_STRENGTH_STEPS,
                selectedLevel  = selectedLevel,
                onLevelSelected = { level ->
                    selectedLevel = level
                    onStrengthChanged(level)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))
            FlashlightDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // ── Quick-level labels row ────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                listOf("Low" to 1, "Mid" to 3, "Max" to FLASHLIGHT_STRENGTH_STEPS).forEach { (label, level) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selectedLevel == level) Color(0xFF3A3010)
                                else Color(0xFF1A1A1A)
                            )
                            .clickable {
                                selectedLevel = level
                                onStrengthChanged(level)
                            }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text       = label,
                            color      = if (selectedLevel == level) Color(0xFFFFCC00) else Color(0xFF888888),
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ── Segmented strength meter ──────────────────────────────────────────────────

/**
 * A row of [steps] tappable rounded rectangles representing discrete brightness
 * levels. Segments up to and including [selectedLevel] are lit amber-yellow;
 * the rest are dim grey.
 *
 * Tapping any segment calls [onLevelSelected] with the 1-based level index.
 */
@Composable
private fun StrengthMeter(
    steps: Int,
    selectedLevel: Int,
    onLevelSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier.height(32.dp)
    ) {
        repeat(steps) { i ->
            val level    = i + 1                    // 1-based
            val isActive = level <= selectedLevel

            // Brightness: ramp from 60 % to 100 % across active segments.
            val alpha = if (isActive) {
                0.55f + (level.toFloat() / steps.toFloat()) * 0.45f
            } else {
                1f
            }
            val segmentColor = if (isActive) Color(0xFFFFCC00).copy(alpha = alpha)
            else         Color(0xFF2A2A2A)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(segmentColor)
                    .clickable { onLevelSelected(level) }
            )
        }
    }
}

// ── Canvas-based strength meter (alternative, for future use) ─────────────────

/**
 * Canvas-rendered version of the meter — kept for reference / animation use.
 * Not currently wired into [ControlsPanel] but ready to swap in.
 */
@Composable
@Suppress("unused")
private fun StrengthMeterCanvas(
    steps: Int,
    selectedLevel: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.height(32.dp)) {
        val totalWidth   = size.width
        val segW         = (totalWidth - (steps - 1) * 5.dp.toPx()) / steps
        val segH         = size.height
        val cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())

        for (i in 0 until steps) {
            val level    = i + 1
            val isActive = level <= selectedLevel
            val alpha    = if (isActive) 0.55f + (level.toFloat() / steps.toFloat()) * 0.45f else 1f
            val color    = if (isActive) Color(0xFFFFCC00).copy(alpha = alpha) else Color(0xFF2A2A2A)
            val left     = i * (segW + 5.dp.toPx())

            drawRoundRect(
                color       = color,
                topLeft     = Offset(left, 0f),
                size        = Size(segW, segH),
                cornerRadius = cornerRadius
            )
        }
    }
}

// ── Shared divider ────────────────────────────────────────────────────────────

/** Thin divider matching the rest of the phs3 State 5 panels. */
@Composable
private fun FlashlightDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Color(0xFF2A2A2A))
    )
}