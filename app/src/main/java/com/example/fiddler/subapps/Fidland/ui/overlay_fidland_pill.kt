package com.example.fiddler.subapps.Fidland.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fiddler.subapps.Fidland.phs2.NetSpeedDisplay


enum class PillPhase {
    CIRCLE,         // State 1: circle, covers hole-punch
    LEFT_EXPANDED,  // State 2: pill grows LEFT  — net speed
    RIGHT_EXPANDED, // State 3: pill grows RIGHT — dynamic indicators
    DASHBOARD       // State 4: full dashboard island
}

enum class RightIndicator {
    EQUALIZER, NOTIFICATIONS, CHARGING, CALL, RECORDER, HOTSPOT
}

/**
 * The Fidland island — always a single rounded rectangle.
 *
 * One shape, one background, one corner radius. The shape grows from
 * a fixed truth point (center of the hole-punch camera) outward.
 * It never teleports — only animates width and height.
 *
 * State 1: width = height = BASE_SIZE  →  looks like a circle
 * State 2: width grows LEFT to STATE2_WIDTH, height stays BASE_SIZE
 * State 3: width grows RIGHT by measuring indicator content at runtime,
 *          clamped to STATE3_MAX_WIDTH, height stays BASE_SIZE
 * State 4: width = STATE4_MAX_WIDTH, height = STATE4_HEIGHT
 *
 * See IslandConfig for all adjustable parameters.
 *
 * IMPORTANT — WindowManager anchor:
 *   This composable assumes the WM view is anchored at the right edge of the
 *   hole-punch (Gravity.TOP | Gravity.END with x = screen_half - hole_center_x).
 *   The island grows leftward for state 2 (right edge fixed), and rightward for
 *   state 3 (left edge of hole fixed). State 4 expands both sides equally.
 *   OverlayManagerCompose handles the WM positioning.
 */
@Composable
fun FidlandIsland(
    phase: PillPhase,
    activeIndicators: List<RightIndicator> = emptyList(),
    currentIndicator: Int = 0,
    dashboardContent: @Composable (BoxScope.() -> Unit)? = null
) {
    val density = LocalDensity.current

    // State 3 content width is measured at runtime
    var measuredState3ContentWidth by remember { mutableStateOf(0.dp) }

    val targetWidth: Dp = when (phase) {
        PillPhase.CIRCLE         -> IslandConfig.BASE_SIZE
        PillPhase.LEFT_EXPANDED  -> IslandConfig.clampWidth(IslandConfig.STATE2_WIDTH)
        PillPhase.RIGHT_EXPANDED -> IslandConfig.clampWidth(
            IslandConfig.BASE_SIZE + measuredState3ContentWidth +
                    IslandConfig.CONTENT_PADDING_HORIZONTAL * 2
        )
        PillPhase.DASHBOARD      -> IslandConfig.STATE4_MAX_WIDTH
    }

    val targetHeight: Dp = when (phase) {
        PillPhase.DASHBOARD -> IslandConfig.STATE4_HEIGHT
        else                -> IslandConfig.BASE_SIZE
    }

    val animSpec = spring<Dp>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness    = Spring.StiffnessMedium
    )
    val animatedWidth  by animateDpAsState(targetWidth,  animSpec, label = "island_w")
    val animatedHeight by animateDpAsState(targetHeight, animSpec, label = "island_h")

    // ── Outer container: fills the full WM view width, centers the island ──
    // The WM view is always STATE4_MAX_WIDTH wide and never moves (see
    // OverlayManagerCompose). This Box fills that exact space. The island
    // shape is placed at the horizontal center of this Box, so as it animates
    // left (state 2) or right (state 3) the hole-punch circle stays fixed.
    Box(
        modifier = Modifier
            .width(IslandConfig.STATE4_MAX_WIDTH)
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        // ── Single shape — the ONLY Box with a black background ──────────────
        // CompositionLocalProvider ensures all content inside the black pill
        // inherits white as the content color. Modifier.background() alone does
        // NOT propagate contentColor (only Surface does), so without this any
        // child using LocalContentColor.current would get the theme default
        // (dark on a light theme) instead of white.
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Box(
                modifier = Modifier
                    .width(animatedWidth)
                    .height(animatedHeight)
                    .background(Color.Black, RoundedCornerShape(IslandConfig.CORNER_RADIUS))
            ) {
                when (phase) {

                    // State 1 — shape only, no content
                    PillPhase.CIRCLE -> Unit

                    // State 2 — net speed on the LEFT, hole-punch spacer on the RIGHT
                    PillPhase.LEFT_EXPANDED -> {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    horizontal = IslandConfig.CONTENT_PADDING_HORIZONTAL,
                                    vertical   = IslandConfig.CONTENT_PADDING_VERTICAL
                                ),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            NetSpeedDisplay()
                            // Invisible spacer that reserves the hole-punch area on the right.
                            // This keeps the visual hole "at home" even when pill is wide.
                            Spacer(modifier = Modifier.size(IslandConfig.BASE_SIZE))
                        }
                    }

                    // State 3 — hole-punch spacer on the LEFT, indicator(s) on the RIGHT.
                    // Indicator content is measured via onSizeChanged so the island width
                    // exactly fits the content (no hardcoded width needed for state 3).
                    PillPhase.RIGHT_EXPANDED -> {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    horizontal = IslandConfig.CONTENT_PADDING_HORIZONTAL,
                                    vertical   = IslandConfig.CONTENT_PADDING_VERTICAL
                                ),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Spacer(modifier = Modifier.size(IslandConfig.BASE_SIZE))

                            // Wrap in a measuring box so island width tracks content width
                            Box(
                                modifier = Modifier
                                    .wrapContentSize()
                                    .onSizeChanged { size ->
                                        measuredState3ContentWidth =
                                            with(density) { size.width.toDp() }
                                    }
                            ) {
                                RightIndicatorContent(activeIndicators, currentIndicator)
                            }
                        }
                    }

                    // State 4 — full dashboard, content fills the island
                    PillPhase.DASHBOARD -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            content  = dashboardContent ?: {}
                        )
                    }
                }
            }
        }
    } // end outer centering Box
} // end FidlandIsland

@Composable
private fun RightIndicatorContent(
    activeIndicators: List<RightIndicator>,
    currentIndicator: Int
) {
    if (activeIndicators.isEmpty()) return
    // Fill in when phs3 is built:
    // EQUALIZER     -> AnimatedEqualizerBars()
    // NOTIFICATIONS -> NotificationIconStack()
    // CHARGING      -> ChargingIcon()
    // CALL          -> CallerInfo()
    // RECORDER      -> GlowingRedDot()
    // HOTSPOT       -> HotspotIcon()
}