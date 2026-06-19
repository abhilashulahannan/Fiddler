package com.example.fiddler.subapps.Fidland.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.Dp
import com.example.fiddler.subapps.Fidland.ui.IslandConfig
import com.example.fiddler.subapps.Fidland.ui.PillPhase

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
    // Y = below the island's CURRENT lower edge, which depends on [phase].
    // States 1/2/3/BOTH are all BASE_SIZE tall, so the box sits right under
    // the pill. States 4/5 are much taller (dashboard / phs3 controls panel),
    // so the box must drop down to the pill's actual bottom edge in those
    // states — otherwise it sits on top of State 5's scrollable content
    // (e.g. NavigationPhs3Handler's upcoming-turns list) and steals its
    // scroll drags. This places it OUTSIDE the OS-controlled status bar zone.
    // X anchored same as island view so it stays visually centered.
    private fun touchBoxParams(phase: PillPhase = PillPhase.CIRCLE) = WindowManager.LayoutParams(
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
        // Position: below the island's lower edge for the given phase.
        y = islandTopY() + IslandConfig.heightForPhase(phase).dpToPx() + (4 * context.resources.displayMetrics.density).toInt()
    }

    // ── Public API ────────────────────────────────────────────────────────

    fun addIslandView(view: ComposeView) {
        if (view.isAttachedToWindow) return
        islandView = view
        windowManager.addView(view, islandParams())
    }

    fun addTouchBoxView(view: ComposeView, phase: PillPhase = PillPhase.CIRCLE) {
        if (view.isAttachedToWindow) return
        touchBoxView = view
        windowManager.addView(view, touchBoxParams(phase))
    }

    /**
     * Moves the existing touch-box view to sit at the pill's CURRENT lower
     * edge for [phase], without removing/re-adding it. Call this on every
     * pillPhase transition (especially entering/leaving EXPANDED_PHS3 /
     * DASHBOARD) so the touch box tracks the pill as it grows/shrinks and
     * never overlaps scrollable State 5 content above it.
     */
    fun repositionTouchBoxForPhase(phase: PillPhase) {
        val view = touchBoxView ?: return
        if (!view.isAttachedToWindow) return
        windowManager.updateViewLayout(view, touchBoxParams(phase))
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

    /** Moves the pill view off the top of the screen (visually hidden). */
    fun hidePill() {
        val view = islandView ?: return
        if (!view.isAttachedToWindow) return
        val params = view.layoutParams as WindowManager.LayoutParams
        params.y = -IslandConfig.BASE_SIZE.dpToPx() * 4  // well above the screen
        windowManager.updateViewLayout(view, params)
    }

    /** Restores the pill view to its normal on-screen position. */
    fun showPill() {
        val view = islandView ?: return
        if (!view.isAttachedToWindow) return
        val params = view.layoutParams as WindowManager.LayoutParams
        params.y = islandTopY()
        windowManager.updateViewLayout(view, params)
    }

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
        return screenWidth / 2 + IslandConfig.HOLE_PUNCH_X_OFFSET.dpToPx() - islandViewWidthPx() / 2
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
        val fromResource = if (id > 0) context.resources.getDimensionPixelSize(id) else 0
        // getIdentifier returns 0 on some devices/API levels, leaving the pill
        // positioned at y=0 — hidden behind the status bar. Fall back to a
        // reasonable default (24dp) so the pill is always visible.
        val fallbackPx = (24 * context.resources.displayMetrics.density).toInt()
        return if (fromResource > 0) fromResource else fallbackPx
    }

    private fun screenWidthPx(): Int =
        context.resources.displayMetrics.widthPixels

    /**
     * TYPE_ACCESSIBILITY_OVERLAY is the only overlay window type that can draw
     * above SystemUI's status bar layer (clock/battery/signal icons). It is
     * ONLY honored when [windowManager] was obtained from the connected
     * FidlandAccessibilityService's own Context — that Context is what holds
     * the accessibility window token. A WindowManager from any other Context
     * (the app's plain Service, Application, etc.) has no such token, and
     * passing TYPE_ACCESSIBILITY_OVERLAY through it makes addView() throw
     * WindowManager.BadTokenException, crashing the caller.
     *
     * FidlandService is responsible for actually sourcing [windowManager] from
     * FidlandAccessibilityService.instance when available (see its onCreate).
     * This getter only decides which window type matches the WindowManager it
     * was actually given — if the accessibility service isn't connected,
     * [windowManager] here is a fallback (token-less) one, so we must request
     * TYPE_APPLICATION_OVERLAY instead, which works with a plain Context but
     * requires the user to have granted SYSTEM_ALERT_WINDOW ("draw over other
     * apps"). If neither the accessibility service nor SYSTEM_ALERT_WINDOW are
     * granted, addView() will still throw — that's expected; the pill simply
     * isn't allowed to overlay anything yet. PermissionsActivity should keep
     * the pill from starting at all in that case.
     *
     * Requires the user to manually enable the accessibility service once in
     * Settings > Accessibility. Behavior on top of the status bar is also
     * OEM-dependent (stock/Pixel honors it reliably; some Samsung/MIUI builds
     * may still clip it) — test on target devices.
     */
    private fun overlayType(): Int {
        val accessibilityServiceConnected = FidlandAccessibilityService.instance != null
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && accessibilityServiceConnected ->
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else ->
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun Dp.dpToPx(): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun Int.dpToPx(): Int =
        (this * context.resources.displayMetrics.density).toInt()
}