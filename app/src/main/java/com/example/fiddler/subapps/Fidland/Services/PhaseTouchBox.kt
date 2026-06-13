package com.example.fiddler.subapps.Fidland.service

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Invisible touch-interception layer for swipe-down-to-expand.
 *
 * WHY THIS VIEW EXISTS SEPARATELY
 * ────────────────────────────────
 * The island sits in the status bar zone (y ≈ 0). Any downward swipe
 * starting inside that zone is intercepted by the OS and opens the
 * notification drawer before our overlay gets a chance to see it.
 *
 * This view is positioned by OverlayManagerCompose to sit JUST BELOW
 * the status bar (y = statusBarHeight + islandHeight), so swipes that
 * start here are no longer in the OS-controlled zone.
 *
 * It is sized to match the island width and a fixed touch height
 * (TOUCH_BOX_HEIGHT_DP in IslandConfig) — just enough vertical space
 * for a drag gesture to register.
 *
 * It is only ACTIVE (added to WindowManager) while the island is in
 * a state where swipe-down should do something. OverlayManagerCompose
 * adds/removes it as the phase changes.
 *
 * FLAG_NOT_TOUCH_MODAL lets all touches OUTSIDE this view pass through.
 * FLAG_NOT_FOCUSABLE prevents it stealing keyboard focus.
 * FLAG_LAYOUT_IN_SCREEN + FLAG_LAYOUT_NO_LIMITS let it position freely.
 */
@Composable
fun PhaseTouchBox(
    onSwipeDown: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    // Only trigger on a meaningful downward drag
                    if (dragAmount.y > 18f) {
                        onSwipeDown()
                    }
                }
            }
    )
    // Transparent — no background. The box is purely a gesture target.
}