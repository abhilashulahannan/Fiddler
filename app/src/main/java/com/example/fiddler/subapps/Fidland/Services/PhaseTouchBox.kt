package com.example.fiddler.subapps.Fidland.service

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Invisible touch-interception layer attached to the lower edge of the pill.
 *
 * ── Why this view exists ──────────────────────────────────────────────────────
 * The pill lives in the status bar zone (y ≈ 0). Any touch starting there is
 * intercepted by the OS (notification drawer) before the overlay sees it.
 * OverlayManagerCompose positions this view just BELOW the pill's lower edge,
 * outside the OS-controlled zone, so gestures starting here reach us first.
 *
 * FLAG_NOT_TOUCH_MODAL — touches outside this view pass through to the app.
 * FLAG_NOT_FOCUSABLE   — keyboard focus is never stolen.
 *
 * ── Active states ─────────────────────────────────────────────────────────────
 * Attached and active:  States 1-2-3 (CIRCLE / LEFT / RIGHT / BOTH_EXPANDED)
 *                       State 5 (STATE5 strip)
 * Disabled and hidden:  State 4 (DASHBOARD) — removed by FidlandService on
 *                       dashboard open, re-added on collapse.
 *
 * ── Gestures ──────────────────────────────────────────────────────────────────
 * Swipe down
 *   • States 1-2-3 → open State 5 if a qualified handler is available,
 *                    otherwise open State 4 (dashboard).
 *   • State 5      → open State 4. Entity-aware: FidlandService picks the
 *                    correct dashboard tab (music, football, etc.) based on
 *                    the active phs3 handler before expanding.
 *
 * Swipe up
 *   • State 5      → collapse to States 1-2-3. No hide.
 *   • States 1-2-3 → hide pill for 5 s then restore.
 *                    ONLY fires when the gesture STARTED in States 1-2-3
 *                    (phase is captured via onDragStart callback to
 *                    FidlandService.onTouchBoxDragStart) — prevents a State 5
 *                    collapse from continuing into a hide.
 *   • State 4      → touchbox is not present; swipe-up is handled inside the
 *                    dashboard composable instead.
 *
 * Swipe right  → Phs3Manager.cycleNext()     (next qualified handler)
 * Swipe left   → Phs3Manager.cyclePrevious() (previous qualified handler)
 * Long press   → Phs3Manager.lockRotation()  (toggle rotation lock)
 *                Available in States 1-2-3 and State 5.
 *
 * ── Threshold ─────────────────────────────────────────────────────────────────
 * 18 px on the dominant axis, measured CUMULATIVELY from where the finger
 * first touched down (not per-move-event). Vertical takes priority when
 * |dy| > |dx|. Once a direction fires for a gesture it is latched — the same
 * physical swipe cannot fire a second callback (e.g. cannot hop
 * 1-2-3 -> STATE5 -> DASHBOARD in one continuous swipe).
 */
@Composable
fun PhaseTouchBox(
    onSwipeDown  : () -> Unit,
    onSwipeUp    : () -> Unit,
    onLongPress  : () -> Unit,
    onSwipeRight : () -> Unit = {},
    onSwipeLeft  : () -> Unit = {},
    onDragStart  : () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Long press — available in all states the touchbox is active
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() })
            }
            // Directional swipes
            //
            // IMPORTANT: `dragAmount` in onDrag is the delta since the LAST
            // pointer-move event, not the total distance since the finger
            // touched down. A single physical swipe is delivered as several
            // move events, so summing deltas and firing on each one that
            // crosses 18px let one swipe call onSwipeDown()/onSwipeUp() TWICE
            // (e.g. 123 -> STATE5 -> DASHBOARD in one gesture). We now track
            // the cumulative offset since onDragStart and fire each
            // directional callback at most once per gesture (latched via
            // `consumed`), resetting on the next onDragStart.
            .pointerInput(Unit) {
                var totalDrag = Offset.Zero
                var consumed = false
                detectDragGestures(
                    onDragStart = {
                        totalDrag = Offset.Zero
                        consumed = false
                        onDragStart()
                    },
                    onDrag = onDrag@{ _, dragAmount ->
                        if (consumed) return@onDrag
                        totalDrag += dragAmount
                        val absX = kotlin.math.abs(totalDrag.x)
                        val absY = kotlin.math.abs(totalDrag.y)
                        when {
                            // Vertical takes priority when clearly dominant
                            absY > absX && totalDrag.y >  18f -> { consumed = true; onSwipeDown() }
                            absY > absX && totalDrag.y < -18f -> { consumed = true; onSwipeUp() }
                            // Horizontal
                            absX > absY && totalDrag.x >  18f -> { consumed = true; onSwipeRight() }
                            absX > absY && totalDrag.x < -18f -> { consumed = true; onSwipeLeft() }
                        }
                    }
                )
            }
    )
    // Transparent — no background. Purely a gesture capture surface.
}