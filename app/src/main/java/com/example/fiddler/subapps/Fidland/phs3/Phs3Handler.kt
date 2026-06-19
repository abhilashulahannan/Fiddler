package com.example.fiddler.subapps.Fidland.phs3

import androidx.compose.runtime.Composable

/**
 * Contract that every phs3 module must implement.
 *
 * HOW IT FITS INTO THE PILL LIFECYCLE
 * ─────────────────────────────────────
 * 1. A phs3 module becomes active when its triggering condition is met
 *    (e.g. an incoming call, a running timer, music playback started).
 *    Phs3Manager registers it and exposes it as [activeHandler]. The pill
 *    transitions to RIGHT_EXPANDED (State 3) or BOTH_EXPANDED (State 2+3).
 *
 * 2. While in the compact pill states the pill shows a compact indicator
 *    via [Indicator] in the right zone (location b / c).
 *
 * 3. The user swipes down → FidlandService checks [hasState5Content].
 *    If true  → pill expands to STATE5 (content strip, [State5Content]).
 *    If false → pill expands directly to DASHBOARD (State 4).
 *
 * 4. While in STATE5 the user can:
 *    • Swipe down → expand to DASHBOARD (State 4), jumping to the
 *      entity-relevant tab (music → Music, football → Football).
 *    • Swipe up   → collapse back to compact pill states.
 *
 * 5. Long-press in compact or STATE5 → toggle rotation lock via
 *    Phs3Manager.lockRotation(). Does not enter a separate panel state.
 *
 * LOCATION A ROW (new)
 * ─────────────────────
 * When multiple handlers qualify simultaneously, each handler that opts in
 * via [hasLocationA] = true contributes a small icon/widget to a horizontal
 * row in the LEFT ZONE (location a), to the left of NetSpeedDisplay.
 *
 * • Override [hasLocationA] to true and implement [LocationAContent] to
 *   participate. Keep the composable tiny — it sits in a fixed
 *   LOCATION_A_SLOT_SIZE square slot inside the row.
 * • Music is always placed first in the row (handled by overlay_fidland_pill).
 *   All other qualifying handlers follow in registration order.
 * • NetSpeedDisplay no longer receives a dynamic offset; it sits at a fixed
 *   position immediately to the left of the hole-punch spacer.
 *
 * IMPLEMENTATION NOTES
 * ─────────────────────
 * • [Indicator] must be tiny — it appears inside the narrow State 3 pill
 *   alongside the hole-punch spacer. Icon + brief text at most.
 * • [State5Content] has the full STATE4_MAX_WIDTH × STATE5_HEIGHT canvas.
 *   Use it for a content strip: synced lyrics, upcoming nav turn, mini
 *   playback controls, score ticker, etc.
 *   Previously named ControlsPanel — renamed as part of Navigation overhaul v1.
 * • [hasState5Content] defaults to false — handlers that have no strip
 *   content get a direct swipe-down to dashboard with no extra work.
 * • [label] is used for logging, a11y, and dashboard tab matching in
 *   FidlandService.tabForActiveHandler(). Keep it short and stable
 *   ("Music", "Call", "Football", etc.).
 */
interface Phs3Handler {

    /** Short human-readable name for this phs3 module. Used in logs and a11y. */
    val label: String

    /**
     * Compact indicator shown in the RIGHT_EXPANDED / BOTH_EXPANDED pill
     * (right zone, locations b and c).
     * Keep it small — the pill auto-sizes to fit, capped at STATE3_MAX_WIDTH.
     */
    @Composable
    fun Indicator()

    /**
     * Returns true if this handler wants to contribute a small icon/widget
     * to the location-a row in the LEFT ZONE when multiple handlers qualify.
     *
     * When true, [LocationAContent] will be called to render the slot.
     * Default: false.
     */
    val hasLocationA: Boolean get() = false

    /**
     * Location-a ordering.
     * Lower value = rendered earlier.
     *
     * Music should use 0.
     * Everything else can keep default 100.
     */
    val locationAPriority: Int
        get() = 100

    /**
     * Content rendered inside a fixed LOCATION_A_SLOT_SIZE square slot in
     * the location-a row (LEFT ZONE, left of NetSpeedDisplay).
     *
     * Only called when [hasLocationA] is true and this handler is in the
     * qualified list. Keep it compact — icon, spinner, or 1-2 char badge.
     *
     * Default implementation is empty — override alongside [hasLocationA].
     */
    @Composable
    fun LocationAContent() {}

    /**
     * Returns true if this handler has content to show in State 5 (the
     * content strip). FidlandService calls this on swipe-down from compact
     * states to decide whether to open STATE5 or go straight to DASHBOARD.
     *
     * Default: false — handlers with no strip content skip State 5 entirely.
     * Override to true in handlers that implement [State5Content].
     */
    fun hasState5Content(): Boolean = true

    /**
     * Content strip shown in STATE5.
     * Canvas size: IslandConfig.STATE4_MAX_WIDTH × IslandConfig.STATE5_HEIGHT.
     * Rendered inside a fillMaxSize Box.
     *
     * Only called when [hasState5Content] returns true.
     * Default implementation is empty — override alongside [hasState5Content].
     *
     * Previously named ControlsPanel — renamed as part of Navigation overhaul v1.
     *
     * Examples: synced lyrics row, upcoming navigation turn, live score ticker,
     * recording waveform, active call duration + mute/end buttons.
     */
    @Composable
    fun State5Content() {}
}