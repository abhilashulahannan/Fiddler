package com.example.fiddler.subapps.Fidland.phs3.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fiddler.R
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler
import com.example.fiddler.ui.icons.MonoLottieIcon

/**
 * Phs3 module — Camera sensor indicator.
 *
 * Qualifies while at least one camera sensor is open, system-wide — see
 * [CameraPhs3Trigger] for the CameraManager.AvailabilityCallback wiring
 * (mirrors Android's own camera-in-use privacy indicator).
 *
 * ── Indicator (State 3) ─────────────────────────────────────────────────────
 * Just the looping camera Lottie icon (res/raw/camera.json) — kept
 * deliberately compact, same footprint as FlashlightPhs3Handler.Indicator.
 *
 * ── State 5 ───────────────────────────────────────────────────────────────
 * None — a privacy indicator only needs to be visible while active; there's
 * no detail worth expanding into a panel. [hasState5Content] returns false
 * so swipe-down goes straight to DASHBOARD, same as any other module with
 * nothing to show there.
 */
class CameraPhs3Handler : Phs3Handler {

    override val label: String = "Camera"

    override fun hasState5Content(): Boolean = false

    @Composable
    override fun Indicator() {
        Box(
            modifier         = Modifier.size(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            MonoLottieIcon(rawRes = R.raw.camera, size = 26.dp)
        }
    }
}