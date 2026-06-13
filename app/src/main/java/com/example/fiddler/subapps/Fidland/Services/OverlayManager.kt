package com.example.fiddler.subapps.Fidland.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.Dp
import com.example.fiddler.subapps.Fidland.ui.IslandConfig

/**
 * Manages the two WindowManager views for the Fidland island.
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │  View 1: islandView                                         │
 * │    • Hosts FidlandIsland composable (the visible shape)     │
 * │    • WRAP_CONTENT — resizes itself as the island animates   │
 * │    • Anchored: TOP | CENTER_HORIZONTAL, y = islandTopY()    │
 * │    • FLAG_NOT_FOCUSABLE: touches outside pass through       │
 * ├─────────────────────────────────────────────────────────────┤
 * │  View 2: touchBoxView                                       │
 * │    • Hosts PhaseTouchBox composable (invisible touch target) │
 * │    • Sits BELOW the status bar so OS doesn't steal swipes   │
 * │    • Added when swipe-down should be active                 │
 * │    • Removed when island is already expanded or in circle   │
 * │    • Width tracks current island state, height = TOUCH_BOX  │
 * └─────────────────────────────────────────────────────────────┘
 *
 * ALL TUNABLE POSITION VALUES ARE IN IslandConfig.
 * The only thing to adjust here is if the island appears misaligned
 * vertically — tweak IslandConfig.HOLE_PUNCH_DP or OFFSET_DP first,
 * then IslandConfig.Y_CORRECTION_DP if those don't cover it.
 */
class OverlayManagerCompose(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var islandView: ComposeView? = null
    private var touchBoxView: ComposeView? = null

    // ── Island view params ────────────────────────────────────────────────
    //
    // KEY DESIGN: the WM view is ALWAYS STATE4_MAX_WIDTH wide and never moves.
    // Its left edge is fixed at (screen_center - STATE4_MAX_WIDTH / 2), which
    // means the view center is always at screen_center — the hole-punch center
    // stays at a fixed pixel through all state transitions.
    //
    // We use Gravity.START (not CENTER_HORIZONTAL) because CENTER_HORIZONTAL
    // recalculates x every frame as the view width changes, causing the OS to
    // drift the whole view left/right during state 2 and 3 animations.
    // With a fixed width and fixed x, the OS never repositions anything.
    private fun islandParams() = WindowManager.LayoutParams(
        islandViewWidthPx(),
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = islandViewLeftX()
        y = islandTopY()
    }

    // ── Touch-box view params ─────────────────────────────────────────────
    // Width = current island pill width (state 1/2/3 sized, not full dashboard).
    // Height = TOUCH_BOX_HEIGHT_DP — just enough for a drag to register.
    // Y = below the island in its pill state (status bar bottom + pill height).
    // This places it OUTSIDE the OS-controlled status bar zone.
    // X anchored same as island view so it stays visually centered.
    private fun touchBoxParams() = WindowManager.LayoutParams(
        IslandConfig.STATE2_WIDTH.dpToPx(),   // wide enough for the pill in any pill state
        IslandConfig.TOUCH_BOX_HEIGHT_DP.dpToPx(),
        overlayType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        // Center the touch box horizontally over the hole-punch
        x = screenWidthPx() / 2 - IslandConfig.STATE2_WIDTH.dpToPx() / 2
        // Position: below the island.
        y = islandTopY() + IslandConfig.BASE_SIZE.dpToPx() + (4 * context.resources.displayMetrics.density).toInt()
    }

    // ── Public API ────────────────────────────────────────────────────────

    fun addIslandView(view: ComposeView) {
        if (view.isAttachedToWindow) return
        islandView = view
        windowManager.addView(view, islandParams())
    }

    fun addTouchBoxView(view: ComposeView) {
        if (view.isAttachedToWindow) return
        touchBoxView = view
        windowManager.addView(view, touchBoxParams())
    }

    fun removeTouchBoxView() {
        touchBoxView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        touchBoxView = null
    }

    fun removeAll() {
        islandView?.let   { if (it.isAttachedToWindow) windowManager.removeView(it) }
        touchBoxView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        islandView   = null
        touchBoxView = null
    }

    // Alias so FidlandService compiles without change
    fun addPillView(view: ComposeView) = addIslandView(view)

    // ── Positioning math ──────────────────────────────────────────────────

    /**
     * The WM view width is always STATE4_MAX_WIDTH — the maximum the island
     * can ever be. This is fixed and never changes, so the OS never needs to
     * reposition the view as the composable animates.
     */
    private fun islandViewWidthPx(): Int = IslandConfig.STATE4_MAX_WIDTH.dpToPx()

    /**
     * X position for the WM view (Gravity.START).
     * We want the view center to land on screen_center (= screen_width / 2),
     * so: x = screen_width/2 - islandViewWidth/2
     */
    private fun islandViewLeftX(): Int {
        val screenWidth = screenWidthPx()
        return screenWidth / 2 - islandViewWidthPx() / 2
    }

    /**
     * Top Y of the island view, in pixels.
     *
     * Goal: the island's vertical center aligns with the hole-punch camera center.
     *
     * hole_center_y  ≈  status_bar_height × IslandConfig.HOLE_PUNCH_CENTER_RATIO
     * island_top_y   =  hole_center_y  -  BASE_SIZE_px / 2
     *
     * If the island sits too high or too low, adjust:
     *   1. IslandConfig.HOLE_PUNCH_CENTER_RATIO  (moves hole center estimate up/down)
     *   2. IslandConfig.Y_CORRECTION_DP          (direct pixel nudge, positive = down)
     */
    private fun islandTopY(): Int {
        val statusBarPx  = statusBarHeight()
        val holeCenterY  = (statusBarPx * IslandConfig.HOLE_PUNCH_CENTER_RATIO).toInt()
        val baseSizePx   = IslandConfig.BASE_SIZE.dpToPx()
        val correction   = IslandConfig.Y_CORRECTION_DP.dpToPx()
        return (holeCenterY - baseSizePx / 2 + correction).coerceAtLeast(0)
    }

    private fun statusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }

    private fun screenWidthPx(): Int =
        context.resources.displayMetrics.widthPixels

    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

    private fun Dp.dpToPx(): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun Int.dpToPx(): Int =
        (this * context.resources.displayMetrics.density).toInt()
}