package com.example.fiddler.subapps.Fidland.phs3.navigation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler

/**
 * Phs3 module — Turn-by-turn Navigation.
 *
 * ── Location a (left of hole-punch) ──────────────────────────────────────────
 *   Direction arrow for the next turn. Call [LocationAIndicator] from the pill's
 *   left-zone slot (same pattern as FootballPhs3Handler.LocationAIndicator).
 *   Arrow choices: ←  ↰  ↱  →  ↑  ⇦ (mild left) ⇨ (mild right)  U-turn glyphs.
 *
 * ── Location b (immediate right of hole-punch) ────────────────────────────────
 *   ETA to destination, e.g. "14 min". Updated every notification poll from Maps.
 *
 * ── Location c (right of b) ──────────────────────────────────────────────────
 *   Two-line text: top = short manoeuvre label ("Turn left"), bottom = distance
 *   to that manoeuvre ("in 350 m"). Rendered via [Indicator].
 *
 * ── State 5 (ControlsPanel — long-press to open) ─────────────────────────────
 *   Scrollable card list of all upcoming turns. Each card shows:
 *     • Direction arrow  •  Instruction text  •  Distance
 *     • Traffic colour strip (blue / yellow / red) on the left edge
 *
 * ── Wiring ───────────────────────────────────────────────────────────────────
 * 1. Add a [NavigationPhs3Trigger] in FidlandService.onCreate / onDestroy
 *    (see NavigationPhs3Trigger.kt).
 * 2. In your NotificationListenerService route Maps notifications:
 *      NavigationRepository.onNotification(sbn)
 *      NavigationRepository.onNavigationEnded()
 * 3. In the pill left-zone composable:
 *      if (activePhs3Handler is NavigationPhs3Handler) {
 *          (activePhs3Handler as NavigationPhs3Handler).LocationAIndicator()
 *      }
 */
class NavigationPhs3Handler : Phs3Handler {

    override val label: String = "Navigation"

    // ── Location a — direction arrow ──────────────────────────────────────────

    @Composable
    fun LocationAIndicator() {
        val snapshot by NavigationRepository.flow.collectAsState()
        val next = snapshot.nextStep ?: return

        Text(
            text      = next.direction.toArrow(),
            fontSize  = 16.sp,
            color     = Color.White,
        )
    }

    // ── Indicator — location b (ETA) + location c (manoeuvre + distance) ─────

    @Composable
    override fun Indicator() {
        val snapshot by NavigationRepository.flow.collectAsState()
        val next = snapshot.nextStep

        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Location b — ETA
            if (snapshot.etaText.isNotBlank()) {
                Text(
                    text       = snapshot.etaText,
                    color      = Color(0xFF4FC3F7),   // light blue — matches Maps accent
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                )
            }

            // Divider dot
            if (snapshot.etaText.isNotBlank() && next != null) {
                Text(text = "·", color = Color(0xFF555555), fontSize = 11.sp)
            }

            // Location c — two-line manoeuvre + distance
            if (next != null) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text       = next.direction.toShortLabel(),
                        color      = Color.White,
                        fontSize   = 9.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 10.sp,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                    )
                    Text(
                        text       = next.distanceText,
                        color      = Color(0xFFAAAAAA),
                        fontSize   = 8.sp,
                        lineHeight = 9.sp,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    // ── State 5 — upcoming turns list ─────────────────────────────────────────

    @Composable
    override fun State5Content() {
        val snapshot by NavigationRepository.flow.collectAsState()

        if (snapshot.steps.isEmpty()) {
            Box(
                modifier          = Modifier.fillMaxSize(),
                contentAlignment  = Alignment.Center,
            ) {
                Text(
                    text      = "Waiting for navigation…",
                    color     = Color(0xFF666666),
                    fontSize  = 12.sp,
                )
            }
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            // Header row — arrival time
            if (snapshot.arrivalTime.isNotBlank()) {
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(
                        text       = "Upcoming turns",
                        color      = Color(0xFF888888),
                        fontSize   = 9.sp,
                    )
                    Text(
                        text       = "Arrive ${snapshot.arrivalTime}",
                        color      = Color(0xFF4FC3F7),
                        fontSize   = 9.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier            = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(snapshot.steps) { index, step ->
                    NavStepCard(step = step, isNext = index == 0)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Step card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NavStepCard(step: NavStep, isNext: Boolean) {
    val cardBg    = if (isNext) Color(0xFF1A2233) else Color(0xFF111111)
    val textColor = if (isNext) Color.White else Color(0xFFCCCCCC)

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cardBg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Traffic colour strip on the left edge
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(48.dp)
                .background(
                    color = when (step.trafficSeverity) {
                        TrafficSeverity.CLEAR    -> Color(0xFF1565C0)  // blue
                        TrafficSeverity.MODERATE -> Color(0xFFF9A825)  // yellow
                        TrafficSeverity.HEAVY    -> Color(0xFFB71C1C)  // red
                    },
                    shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
                )
        )

        // Arrow
        Text(
            text     = step.direction.toArrow(),
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        // Instruction + distance
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        ) {
            Text(
                text       = step.instruction,
                color      = textColor,
                fontSize   = 10.sp,
                fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                lineHeight = 12.sp,
            )
            if (step.distanceText.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text      = step.distanceText,
                    color     = Color(0xFF888888),
                    fontSize  = 9.sp,
                    maxLines  = 1,
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  TurnDirection display helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Unicode arrow shown in location a and inside cards. */
fun TurnDirection.toArrow(): String = when (this) {
    TurnDirection.STRAIGHT      -> "↑"
    TurnDirection.MILD_LEFT     -> "↖"
    TurnDirection.LEFT          -> "←"
    TurnDirection.SHARP_LEFT    -> "↰"
    TurnDirection.U_TURN_LEFT   -> "⤶"
    TurnDirection.MILD_RIGHT    -> "↗"
    TurnDirection.RIGHT         -> "→"
    TurnDirection.SHARP_RIGHT   -> "↱"
    TurnDirection.U_TURN_RIGHT  -> "⤷"
    TurnDirection.UNKNOWN       -> "•"
}

/** Short label shown on the top line of location c. */
fun TurnDirection.toShortLabel(): String = when (this) {
    TurnDirection.STRAIGHT      -> "Continue"
    TurnDirection.MILD_LEFT     -> "Slight left"
    TurnDirection.LEFT          -> "Turn left"
    TurnDirection.SHARP_LEFT    -> "Sharp left"
    TurnDirection.U_TURN_LEFT   -> "U-turn"
    TurnDirection.MILD_RIGHT    -> "Slight right"
    TurnDirection.RIGHT         -> "Turn right"
    TurnDirection.SHARP_RIGHT   -> "Sharp right"
    TurnDirection.U_TURN_RIGHT  -> "U-turn"
    TurnDirection.UNKNOWN       -> "Follow road"
}