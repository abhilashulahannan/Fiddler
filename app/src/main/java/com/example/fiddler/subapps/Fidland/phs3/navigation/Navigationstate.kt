package com.example.fiddler.subapps.Fidland.phs3.navigation

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