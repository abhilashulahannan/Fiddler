package com.example.fiddler.subapps.Fidland.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║              FIDLAND ISLAND — TUNING PARAMETERS             ║
 * ║  All values you may need to adjust are in this one object.  ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * HOW SIZING WORKS
 * ─────────────────
 * The island is always a single rounded rectangle.
 * Corner radius = BASE_SIZE / 2  (never changes).
 *
 * State 1  →  width = BASE_SIZE,            height = BASE_SIZE      (looks like a circle)
 * State 2  →  width = STATE2_WIDTH,         height = BASE_SIZE      (grows LEFT)
 * State 3  →  width = BASE_SIZE + <measured content + padding>,     (grows RIGHT)
 *                                           height = BASE_SIZE
 *             clamped to STATE3_MAX_WIDTH
 * State 4  →  width = STATE4_MAX_WIDTH,     height = STATE4_HEIGHT  (full dashboard)
 *
 * State 2 and 4 widths are fixed constants below.
 * State 3 width is measured from live content — STATE3_MAX_WIDTH is its ceiling.
 * STATE2_WIDTH and STATE3_MAX_WIDTH are automatically clamped to STATE4_MAX_WIDTH.
 */
object IslandConfig {

    // ── 1. Hole-punch camera diameter ──────────────────────────────────────
    //   Physical diameter of the S24 Ultra hole-punch camera in dp.
    //   ↑ increase if the island doesn't fully cover the camera cutout.
    //   ↓ decrease if it covers too much of the status bar.
    val HOLE_PUNCH_DP: Dp = 18.dp

    // ── 2. Offset — padding added around the hole-punch on every side ──────
    //   Makes the circle slightly bigger than the bare camera hole.
    //   BASE_SIZE = HOLE_PUNCH_DP + OFFSET * 2.
    //   Corner radius = BASE_SIZE / 2 and NEVER changes after this.
    val OFFSET_DP: Dp = 5.dp

    // ── 3. Content padding inside the island ───────────────────────────────
    //   Space between the island edge and the content (net speed text,
    //   indicator icons, dashboard panels, etc.).
    val CONTENT_PADDING_HORIZONTAL: Dp = 8.dp
    val CONTENT_PADDING_VERTICAL:   Dp = 2.dp

    // ── 4. State 2 — total island width when net speed is shown ────────────
    //   The island grows LEFT by (STATE2_WIDTH - BASE_SIZE).
    //   Net speed text (two lines: ↓1.2 MB/s  ↑80 KB/s) fits in ~130dp.
    //   Add CONTENT_PADDING_HORIZONTAL × 2 + BASE_SIZE for the hole spacer.
    //   ↑ increase if the net speed text is clipped.
    //   ↓ decrease if there is too much empty space to the left.
    //   Automatically clamped to STATE4_MAX_WIDTH.
    val STATE2_WIDTH: Dp = 100.dp

    // ── 5. State 3 — maximum width for right-side indicators ───────────────
    //   State 3 width is measured from the actual indicator composable at
    //   runtime, so no fixed width is needed. This value is only a safety
    //   cap — the island will never exceed it in state 3.
    //   Set equal to STATE4_MAX_WIDTH to remove the cap entirely.
    val STATE3_MAX_WIDTH: Dp = 280.dp

    // ── 6. State 4 — dashboard size ────────────────────────────────────────
    //   The island expands to exactly these dimensions on swipe-down.
    //   STATE2_WIDTH and STATE3_MAX_WIDTH are automatically clamped to
    //   STATE4_MAX_WIDTH so they can never exceed the dashboard width.
    val STATE4_MAX_WIDTH: Dp = 280.dp
    val STATE4_HEIGHT:    Dp = 250.dp

    // ── 7. Vertical positioning ─────────────────────────────────────────────
    //   The island's center should align with the hole-punch camera center.
    //
    //   HOLE_PUNCH_CENTER_RATIO: fraction of the status-bar height where the
    //   hole-punch center sits. S24 Ultra hole center ≈ 50% of status bar.
    //   ↑ increase (toward 1.0) if island appears too high.
    //   ↓ decrease (toward 0.0) if island appears too low.
    val HOLE_PUNCH_CENTER_RATIO: Float = 0.50f

    //   Y_CORRECTION_DP: direct pixel nudge after ratio calculation.
    //   Positive = move island DOWN, negative = move island UP.
    //   Use this for fine-tuning after HOLE_PUNCH_CENTER_RATIO is set.
    val Y_CORRECTION_DP: Dp = 0.dp

    // ── 8. Touch-box height ─────────────────────────────────────────────────
    //   The invisible touch-interception view sits just below the island
    //   (outside the status-bar OS-controlled zone) and catches swipe-down
    //   gestures. This is its height; width = STATE2_WIDTH.
    //   ↑ increase if swipe-down is hard to trigger.
    //   ↓ decrease if it accidentally intercepts taps below the island.
    val TOUCH_BOX_HEIGHT_DP: Dp = 75.dp

    // ── Derived (computed from the values above — do not edit) ─────────────
    val BASE_SIZE:     Dp get() = HOLE_PUNCH_DP + OFFSET_DP * 2
    val CORNER_RADIUS: Dp get() = BASE_SIZE / 2

    /** Prevents state 2/3 widths from exceeding the dashboard width. */
    fun clampWidth(w: Dp): Dp = w.coerceAtMost(STATE4_MAX_WIDTH)
}