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
 * TRUTH CENTER
 * ─────────────
 * The WM view is always STATE4_MAX_WIDTH wide, never moves, and is
 * centered on the "truth center" — the horizontal center of the
 * hole-punch camera:
 *
 *   truth_x  =  screen_center  +  HOLE_PUNCH_X_OFFSET
 *
 * HOLE_PUNCH_X_OFFSET is positive to shift right, negative for left.
 * On devices where the camera sits at the exact screen center, leave it 0.
 *
 * All pill states are centered on truth_x. The pill never moves — only
 * its width animates, growing left and right symmetrically around truth_x.
 *
 * STATES
 * ───────
 * State 1 → width = BASE_SIZE,            height = BASE_SIZE  (circle)
 *
 * State 2 → width = STATE2_WIDTH (fixed),  height = BASE_SIZE
 *           Fixed width; symmetric around truth_x.
 *           Content fills the LEFT ZONE (left of the hole spacer).
 *           Default: net speed display. Location "a" sits left of net speed.
 *
 * State 3 → width = BASE_SIZE + measured right content + padding * 2
 *           Clamped to STATE3_MAX_WIDTH; height = BASE_SIZE.
 *           Dynamic width driven by measured right-zone content.
 *           Content fills the RIGHT ZONE (right of the hole spacer).
 *           Locations "b" and "c" live here.
 *
 * State 2 + 3 (both active simultaneously — the common case):
 *           width = STATE2_LEFT_ARM + BASE_SIZE + right content + padding * 2
 *           where STATE2_LEFT_ARM = (STATE2_WIDTH - BASE_SIZE) / 2
 *           Both LEFT and RIGHT zones are populated.
 *           Left zone: State 2 content + optional location "a" items.
 *           Right zone: phs3 content (locations b and c).
 *           Pill remains centered on truth_x.
 *
 * State 4 → width = STATE4_MAX_WIDTH,     height = STATE4_HEIGHT  (dashboard)
 *           Full pull-down dashboard with tabs (Music, Queue, Apps, etc.).
 *           Touchbox is removed while in this state.
 *
 * State 5 → width = STATE4_MAX_WIDTH,     height = STATE5_HEIGHT  (content strip)
 *           Intermediate between compact pill and full dashboard.
 *           Shows a slim content strip — e.g. synced lyrics, mini controls.
 *           Entered via swipe-down from States 1-2-3 when a qualified handler
 *           is available. Exited via swipe-down (→ State 4) or swipe-up
 *           (→ States 1-2-3). Touchbox attaches to its lower edge.
 *
 * CONTENT ZONES
 * ──────────────
 * Inside any pill Row the layout is always:
 *
 *   [ LEFT ZONE ] [ hole spacer (BASE_SIZE) ] [ RIGHT ZONE ]
 *
 * Which phs2/phs3 data goes into which zone is a design-time decision.
 * The code provides both zones independently — neither is hardwired to
 * a specific phase's content.
 *
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
    //   BASE_SIZE = HOLE_PUNCH_DP + OFFSET_DP * 2.
    //   Corner radius = BASE_SIZE / 2 and NEVER changes after this.
    val OFFSET_DP: Dp = 5.dp

    // ── 3. Hole-punch horizontal offset from screen center ─────────────────
    //   On most devices the camera sits at the exact horizontal screen center,
    //   so leave this at 0.dp.
    //   Positive  →  camera is to the RIGHT of screen center.
    //   Negative  →  camera is to the LEFT  of screen center.
    //   The WM view x position is adjusted by this offset so the hole-punch
    //   circle always stays inside the pill regardless of pill width.
    val HOLE_PUNCH_X_OFFSET: Dp = 0.dp

    // ── 4. Content padding inside the island ───────────────────────────────
    //   Space between the island edge and the content (net speed text,
    //   indicator icons, dashboard panels, etc.).
    val CONTENT_PADDING_HORIZONTAL: Dp = 8.dp
    val CONTENT_PADDING_VERTICAL:   Dp = 2.dp

    // ── 5. State 2 — total island width when net speed is shown ────────────
    //   The island is symmetric around truth_x, so both sides grow equally.
    //   The left zone width = (STATE2_WIDTH - BASE_SIZE) / 2.
    //   Net speed text (two lines: ↓1.2 MB/s  ↑80 KB/s) fits in ~130dp.
    //   Set STATE2_WIDTH = BASE_SIZE + desired left content width * 2.
    //   Automatically clamped to STATE4_MAX_WIDTH.
    val STATE2_WIDTH: Dp = 110.dp

    // ── 6. State 3 — maximum width for the right-zone indicators ───────────
    //   State 3 width is measured from the actual content at runtime.
    //   This value is only a safety cap — the island will never exceed it.
    //   Set equal to STATE4_MAX_WIDTH to remove the cap entirely.
    val STATE3_MAX_WIDTH: Dp = 280.dp

    // ── 7. State 4 — dashboard size ────────────────────────────────────────
    //   The island expands to exactly these dimensions on swipe-down.
    //   STATE2_WIDTH and STATE3_MAX_WIDTH are automatically clamped to
    //   STATE4_MAX_WIDTH so they can never exceed the dashboard width.
    val STATE4_MAX_WIDTH: Dp = 280.dp
    val STATE4_HEIGHT:    Dp = 250.dp

    // ── 8. State 5 — content strip ─────────────────────────────────────────
    //   Intermediate expansion between the compact pill (States 1-2-3) and
    //   the full dashboard (State 4). Shows a slim horizontal strip of
    //   content — synced lyrics, mini playback controls, upcoming nav turn, etc.
    //
    //   Width is always STATE4_MAX_WIDTH (same as dashboard).
    //   Height is a fixed dp value — adjust to fit your tallest State 5 content.
    //
    //   STATE5_HEIGHT tuning guide:
    //     ~56 dp  →  single-line strip (one text row + small padding)
    //     ~80 dp  →  two-line strip (e.g. lyrics line + artist name)
    //     ~100 dp →  compact controls row (album art + track title + play/pause)
    //   Keep well below STATE4_HEIGHT so the two states feel distinct.
    val STATE5_HEIGHT: Dp = 100.dp

    // ── 9. Vertical positioning ─────────────────────────────────────────────
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

    // ── 10. Touch-box height ─────────────────────────────────────────────────
    //   The invisible touch-interception view sits just below the island's
    //   lower edge (outside the OS-controlled status bar zone) and catches
    //   all swipe gestures. Active in States 1-2-3 and State 5.
    //   Removed entirely while in State 4 (dashboard).
    //   ↑ increase if swipe-down is hard to trigger.
    //   ↓ decrease if it accidentally intercepts taps below the island.
    val TOUCH_BOX_HEIGHT_DP: Dp = 75.dp

    // ── Derived (computed from the values above — do not edit) ─────────────
    val BASE_SIZE:     Dp get() = HOLE_PUNCH_DP + OFFSET_DP * 2
    val CORNER_RADIUS: Dp get() = BASE_SIZE / 2

    /**
     * The fixed contribution of State 2 content to the left of the hole spacer.
     * In the symmetric model: left arm = (STATE2_WIDTH - BASE_SIZE) / 2.
     * This is how much the pill extends left of the hole spacer in State 2.
     */
    val STATE2_LEFT_ARM: Dp get() = (clampWidth(STATE2_WIDTH) - BASE_SIZE) / 2

    /** Prevents state 2/3 widths from exceeding the dashboard width. */
    fun clampWidth(w: Dp): Dp = w.coerceAtMost(STATE4_MAX_WIDTH)

    /**
     * Pill height for a given [PillPhase], used by OverlayManagerCompose to
     * position the touch-box view at the pill's actual lower edge.
     *
     * States 1/2/3/BOTH → BASE_SIZE (compact pill, status-bar height)
     * State 5 (STATE5)  → STATE5_HEIGHT (content strip)
     * State 4 (DASHBOARD) → STATE4_HEIGHT (full dashboard — touchbox not
     *                        present in this state, but value used on restore)
     */
    fun heightForPhase(phase: PillPhase): Dp = when (phase) {
        PillPhase.DASHBOARD -> STATE4_HEIGHT
        PillPhase.STATE5    -> STATE5_HEIGHT
        else                -> BASE_SIZE
    }

    // ── Phs3 Music module ──────────────────────────────────────────────────
    //
    //   Component 1 — AlbumArtSpinner (left zone, location "a", next to net speed)
    //   Component 2 — Equalizer       (right zone, location "b", first after hole)
    //   Component 3 — Song/artist text (right zone, location "c", after equalizer)

    // AlbumArtSpinner diameter. Appears in the LEFT zone alongside net speed.
    // Keep below (BASE_SIZE - CONTENT_PADDING_VERTICAL * 2) to avoid clipping.
    val MUSIC_ALBUM_ART_SIZE: Dp = 22.dp

    // Legacy inset — superseded by the automatic edge-shift in PillLeftZoneContent
    // which places the album-art circle centre on the apparent centre of the pill's
    // left rounded vertex.  Kept for reference; not read at runtime.
    val MUSIC_ALBUM_ART_INSET: Dp = 6.dp

    // Gap between the location-a row and NetSpeedDisplay.
    // Applied after all location-a slots; NetSpeed is always separate from the row.
    val MUSIC_ALBUM_ART_GAP: Dp = 16.dp

    // Equalizer bar dimensions.
    val MUSIC_EQ_BAR_COUNT:   Int = 5
    val MUSIC_EQ_BAR_WIDTH:   Dp  = 3.dp
    val MUSIC_EQ_BAR_SPACING: Dp  = 2.dp
    val MUSIC_EQ_MAX_HEIGHT:  Dp  = 14.dp

    // Song/artist text column width in the right zone.
    val MUSIC_TEXT_COLUMN_WIDTH: Dp = 50.dp

    // Gap between equalizer and text column.
    val MUSIC_EQ_TEXT_GAP: Dp = 6.dp

    // Derived — total right-zone indicator width for the music module.
    val MUSIC_EQ_WIDTH: Dp get() =
        MUSIC_EQ_BAR_WIDTH * MUSIC_EQ_BAR_COUNT + MUSIC_EQ_BAR_SPACING * (MUSIC_EQ_BAR_COUNT - 1)
    val MUSIC_INDICATOR_WIDTH: Dp get() =
        MUSIC_EQ_WIDTH + MUSIC_EQ_TEXT_GAP + MUSIC_TEXT_COLUMN_WIDTH

    // Location-a row

    val LOCATION_A_SLOT_SIZE: Dp = 22.dp

    val LOCATION_A_SLOT_GAP: Dp = 4.dp

    // Safety cap so location-a cannot consume the entire left arm.
    // Music + Download + Record + Torch + etc.
    val MAX_LOCATION_A_SLOTS = 5

    fun locationARowWidth(slotCount: Int): Dp {
        if (slotCount <= 0) return 0.dp

        val clamped = slotCount.coerceAtMost(MAX_LOCATION_A_SLOTS)

        return (LOCATION_A_SLOT_SIZE * clamped) +
                (LOCATION_A_SLOT_GAP * (clamped - 1))
    }

}