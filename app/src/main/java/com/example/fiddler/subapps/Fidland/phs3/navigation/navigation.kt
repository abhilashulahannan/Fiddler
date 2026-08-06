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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.subapps.Fidland.phs3.BlockAffinity
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler
import com.example.fiddler.ui.icons.MonoLottieIcon

/**
 * Phs3 module — Turn-by-turn Navigation.
 *
 * ── §B7 Blocks (Phase 4 — this pass) — full side-swap from before ────────────
 * All 3 blocks stay fixed-side (none through §B2's DYNAMIC balancer) — ⚠ this
 * is called out as unconfirmed-intentional in the design doc even after this
 * pass; implemented literally as specified rather than resolved here.
 *
 *   • **ETA** — moved to location-a (LEFT ZONE) via [hasLocationA] /
 *     [LocationAContent]. Previously the *direction icon* lived here — full
 *     swap, not an addition.
 *   • **Direction icon** — now the primary block, [Indicator], on the RIGHT
 *     (full side-swap from location-a; reuses the existing [NavDirectionIcon]
 *     asset set unchanged).
 *   • **Directions text** — the secondary block, [SecondaryIndicator], via
 *     [hasSecondaryBlock], RIGHT_ANCHOR. Line 1 = the *full* instruction
 *     (with street name — data already available via [NavStep.instruction],
 *     just not previously used at this length); line 2 = distance
 *     ([NavStep.distanceText]) alone. Decision: kept the existing
 *     instruction/distance split as-is rather than re-parsing "Turn left at X
 *     street, coming in 300m" into an alternate reading — the fields already
 *     carry that split cleanly.
 *
 * ── State 5 (long-press) — expanded, not just retained ────────────────────────
 *   • Hero section (pulled out on its own, "hero, then list" shape — same as
 *     Weather's State 5): big direction icon + full next instruction + ETA/
 *     arrival, with a small turn-icon strip (top-right, next 3 steps —
 *     decision: 3 keeps the strip glanceable; the full capped-at-5 list is
 *     still below it, so nothing is lost, just not duplicated at full size).
 *   • Trip stats — ETA/arrival (already existed). **Distance remaining:**
 *     shipped as an honest partial — sums [NavStep.distanceMeters] across
 *     currently-known steps only (opportunistic/capped at whatever
 *     [NavigationRepository] parsed, not true route-remaining) and labelled
 *     "~" to signal it's approximate. **Total distance travelled:** scoped
 *     OUT — no derivable source exists at all (same flavor of gap as Alarm's
 *     per-alarm tag), unlike distance-remaining where a partial figure is at
 *     least honestly computable.
 *   • Upcoming-list — unchanged shape, minus the now-promoted-into-the-hero
 *     item (`steps.drop(1)`).
 *
 * ── Wiring ───────────────────────────────────────────────────────────────────
 * 1. Add a [NavigationPhs3Trigger] in FidlandService.onCreate / onDestroy
 *    (see NavigationPhs3Trigger.kt for the Phase 4 Special-Condition wiring).
 * 2. In your NotificationListenerService route Maps notifications:
 *      NavigationRepository.onNotification(sbn)
 *      NavigationRepository.onNavigationEnded()
 * Location-a is wired automatically via [hasLocationA] / [LocationAContent].
 */
class NavigationPhs3Handler : Phs3Handler {

    override val label: String = "Navigation"

    // ── Location a — ETA (moved here, Phase 4 — was the direction icon) ─────

    override val hasLocationA: Boolean = true

    @Composable
    override fun LocationAContent() {
        val snapshot by NavigationRepository.flow.collectAsState()
        if (snapshot.etaText.isBlank()) return

        Text(
            text       = snapshot.etaText,
            color      = Color(0xFF4FC3F7),   // light blue — matches Maps accent
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines   = 1,
        )
    }

    // ── Indicator (primary block — direction icon, Phase 4: full side-swap) ──

    override val blockAffinity: BlockAffinity = BlockAffinity.RIGHT_ANCHOR

    @Composable
    override fun Indicator() {
        val snapshot by NavigationRepository.flow.collectAsState()
        val next = snapshot.nextStep ?: return
        NavDirectionIcon(direction = next.direction, sizeDp = 16.dp)
    }

    // ── SecondaryIndicator (secondary block — directions text) ──────────────

    override val hasSecondaryBlock: Boolean = true
    override val secondaryBlockAffinity: BlockAffinity = BlockAffinity.RIGHT_ANCHOR

    @Composable
    override fun SecondaryIndicator() {
        val snapshot by NavigationRepository.flow.collectAsState()
        val next = snapshot.nextStep ?: return

        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(start = 6.dp),
        ) {
            Text(
                text       = next.instruction,
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

    // ── State 5 — hero + trip stats + turn-icon strip + upcoming turns ──────

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

        val next = snapshot.nextStep
        val upcoming = snapshot.steps.drop(1) // hero already shows the promoted item

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            // ── Hero: big direction icon + next instruction, turn strip top-right ──
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top,
            ) {
                if (next != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NavDirectionIcon(direction = next.direction, sizeDp = 30.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text       = next.instruction,
                                color      = Color.White,
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines   = 2,
                                overflow   = TextOverflow.Ellipsis,
                                lineHeight = 15.sp,
                            )
                            Text(
                                text     = next.distanceText,
                                color    = Color(0xFF888888),
                                fontSize = 10.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }

                // Turn-icon strip — next 3 steps (incl. current). See class doc:
                // kept short since the full capped-at-5 list is in the LazyColumn below.
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    snapshot.steps.take(3).forEach { step ->
                        NavDirectionIcon(direction = step.direction, sizeDp = 14.dp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ── Trip stats: ETA/arrival + honest-partial distance remaining ──
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text       = formatDistanceRemaining(snapshot.steps),
                    color      = Color(0xFF888888),
                    fontSize   = 9.sp,
                )
                if (snapshot.arrivalTime.isNotBlank()) {
                    Text(
                        text       = "Arrive ${snapshot.arrivalTime}",
                        color      = Color(0xFF4FC3F7),
                        fontSize   = 9.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ── Upcoming turns — unchanged shape, minus the promoted item ───
            if (upcoming.isEmpty()) {
                Text(
                    text     = "No further turns parsed yet",
                    color    = Color(0xFF555555),
                    fontSize = 10.sp,
                )
            } else {
                Text(
                    text     = "Upcoming turns",
                    color    = Color(0xFF888888),
                    fontSize = 9.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier            = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(upcoming) { _, step ->
                        NavStepCard(step = step, isNext = false)
                    }
                }
            }
        }
    }
}

/**
 * §B7 Phase 4 — honest-partial "distance remaining" trip stat. Sums
 * [NavStep.distanceMeters] across every *currently-known* step only — this
 * is opportunistic/capped by whatever [NavigationRepository] managed to
 * parse (today, up to 5 steps from `EXTRA_BIG_TEXT`), not the true
 * remaining-route distance, which no data source here can provide. Labelled
 * with "~" for exactly that reason — see class doc's "Trip stats" note.
 */
private fun formatDistanceRemaining(steps: List<NavStep>): String {
    val totalMeters = steps.sumOf { it.distanceMeters }
    if (totalMeters <= 0) return ""
    val text = if (totalMeters >= 1000) {
        "~%.1f km".format(totalMeters / 1000f)
    } else {
        "~${totalMeters} m"
    }
    return "$text to go (next ${steps.size} turn${if (steps.size == 1) "" else "s"})"
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
        NavDirectionIcon(
            direction = step.direction,
            sizeDp    = 18.dp,
            modifier  = Modifier.padding(horizontal = 8.dp),
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
//  Direction icon
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Shared direction icon — plays the matching turn Lottie asset
 * (res/raw/nav_*.json — see [TurnDirection.toRawRes]) on loop, flipped
 * horizontally for U_TURN_RIGHT (see [TurnDirection.isMirrored]) since there's
 * only one U-turn asset. Falls back to [TurnDirection.toArrow]'s "•" text
 * glyph for UNKNOWN, which has no asset.
 */
@Composable
private fun NavDirectionIcon(direction: TurnDirection, sizeDp: Dp, modifier: Modifier = Modifier) {
    val rawRes = direction.toRawRes()
    if (rawRes == null) {
        Text(
            text     = direction.toArrow(),
            fontSize = (sizeDp.value * 0.75f).sp,
            color    = Color.White,
            modifier = modifier,
        )
        return
    }

    MonoLottieIcon(
        rawRes   = rawRes,
        modifier = modifier
            .size(sizeDp)
            .graphicsLayer(scaleX = if (direction.isMirrored()) -1f else 1f),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  TurnDirection display helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Unicode arrow — kept as the UNKNOWN-direction fallback for [NavDirectionIcon]
 *  (mirrors WeatherCondition.toEmoji(): the old glyph set stays around for
 *  cases with no matching asset, rather than being deleted outright). */
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

/** Short label — kept for other call sites; no longer used directly by
 *  [NavigationPhs3Handler.Indicator], which now shows only the icon
 *  (Phase 4 — the short label's job moved into SecondaryIndicator's full
 *  instruction text). */
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