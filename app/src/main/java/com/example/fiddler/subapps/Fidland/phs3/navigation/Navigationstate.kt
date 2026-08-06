package com.example.fiddler.subapps.Fidland.phs3.navigation

import com.example.fiddler.R

// ─────────────────────────────────────────────────────────────────────────────
//  Turn direction
// ─────────────────────────────────────────────────────────────────────────────

enum class TurnDirection {
    STRAIGHT,
    MILD_LEFT,
    LEFT,
    SHARP_LEFT,
    U_TURN_LEFT,
    MILD_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    U_TURN_RIGHT,
    UNKNOWN,
}

/**
 * Lottie asset (res/raw) matching each direction. There's one U-turn asset
 * (`nav_uturn.json`, drawn for U_TURN_LEFT) — U_TURN_RIGHT reuses it and is
 * flipped horizontally at render time (see [isMirrored] / NavDirectionIcon
 * in navigation.kt) rather than shipping a second, mirrored asset.
 * UNKNOWN has no asset — the handler falls back to [toArrow]'s "•" glyph.
 */
fun TurnDirection.toRawRes(): Int? = when (this) {
    TurnDirection.STRAIGHT      -> R.raw.nav_straight
    TurnDirection.MILD_LEFT     -> R.raw.nav_mildleft
    TurnDirection.LEFT          -> R.raw.nav_left
    TurnDirection.SHARP_LEFT    -> R.raw.nav_sharpleft
    TurnDirection.U_TURN_LEFT   -> R.raw.nav_uturn
    TurnDirection.MILD_RIGHT    -> R.raw.nav_mildright
    TurnDirection.RIGHT         -> R.raw.nav_right
    TurnDirection.SHARP_RIGHT   -> R.raw.nav_sharpright
    TurnDirection.U_TURN_RIGHT  -> R.raw.nav_uturn
    TurnDirection.UNKNOWN       -> null
}

/** Whether [toRawRes]'s asset needs a horizontal flip for this direction —
 *  true only for U_TURN_RIGHT, which mirrors the U_TURN_LEFT asset. */
fun TurnDirection.isMirrored(): Boolean = this == TurnDirection.U_TURN_RIGHT

// ─────────────────────────────────────────────────────────────────────────────
//  Traffic severity (matches Google Maps colouring)
// ─────────────────────────────────────────────────────────────────────────────

enum class TrafficSeverity {
    CLEAR,    // blue  — free flow
    MODERATE, // yellow — slowing
    HEAVY,    // red    — congestion
}

// ─────────────────────────────────────────────────────────────────────────────
//  A single upcoming turn / step
// ─────────────────────────────────────────────────────────────────────────────

data class NavStep(
    /** Human-readable instruction, e.g. "Turn left onto MG Road" */
    val instruction: String,
    /** Distance to this step from current position, e.g. "1.2 km" or "350 m" */
    val distanceText: String,
    val distanceMeters: Int,
    val direction: TurnDirection,
    val trafficSeverity: TrafficSeverity = TrafficSeverity.CLEAR,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Full navigation snapshot — updated every poll cycle
// ─────────────────────────────────────────────────────────────────────────────

data class NavigationSnapshot(
    /** ETA string shown in location b, e.g. "14 min" */
    val etaText: String,
    /** Absolute arrival time string, e.g. "3:45 PM" */
    val arrivalTime: String,
    /** Ordered list of upcoming steps — index 0 is the immediately next turn */
    val steps: List<NavStep>,
    /** True while Google Maps is actively navigating */
    val isActive: Boolean,
) {
    val nextStep: NavStep? get() = steps.firstOrNull()
}

/** Returned when navigation is not running */
val EmptySnapshot = NavigationSnapshot(
    etaText = "",
    arrivalTime = "",
    steps = emptyList(),
    isActive = false,
)

/**
 * §B7 Phase 4 — "nearing the turn" approach threshold, previously undefined
 * (same gap-shape as Weather's high-wind/heatwave constants). A step's
 * [NavStep.distanceMeters] at or below this promotes to the indefinite-hold
 * Special Condition — see [com.example.fiddler.subapps.Fidland.phs3.navigation.NavigationPhs3Trigger].
 *
 * 150m chosen as the "prepare to turn" range most turn-by-turn nav UIs use
 * for their own final heads-up cue — close enough that the turn is genuinely
 * imminent, far enough to still be actionable (not simultaneous with the
 * turn itself). Revisit if a different figure gets signed off — same
 * "resolve before building rather than leave unbuilt" steer used elsewhere
 * in this pass (see Weather's threshold constants for the same reasoning
 * shape).
 */
const val NAV_APPROACH_THRESHOLD_METERS: Int = 150