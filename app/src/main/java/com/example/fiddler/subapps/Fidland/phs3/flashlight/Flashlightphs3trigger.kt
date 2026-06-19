package com.example.fiddler.subapps.Fidland.phs3.flashlight

import android.content.Context
import android.hardware.camera2.CameraManager
import com.example.fiddler.subapps.Fidland.service.FidlandService

/**
 * Phs3 trigger — Flashlight.
 *
 * Registers a [CameraManager.TorchCallback] and calls
 * `activatePhs3(FlashlightPhs3Handler(…))` / `deactivatePhs3()` as the
 * device torch turns on / off, mirroring the MusicPhs3Trigger /
 * AlarmPhs3Trigger pattern.
 *
 * ── Strength propagation ──────────────────────────────────────────────────
 * When the State 5 meter fires [onStrengthChanged], this trigger applies the
 * new level via [CameraManager.turnOnTorchWithStrengthLevel] (API 33+) if the
 * device supports it, then reconstructs the handler so the UI reflects the
 * applied value.
 *
 * On older devices (API < 33) or devices without torch-strength support,
 * the strength value is silently ignored — the meter still moves visually but
 * hardware intensity stays at full. Strength support can be detected via
 * [CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL].
 *
 * ── Usage ─────────────────────────────────────────────────────────────────
 * ```kotlin
 * // In FidlandService.onCreate / onStartCommand:
 * flashlightTrigger = FlashlightPhs3Trigger(applicationContext, this)
 * flashlightTrigger.start()
 *
 * // In FidlandService.onDestroy:
 * flashlightTrigger.stop()
 * ```
 *
 * @param context Android context — used to obtain the system [CameraManager].
 * @param service The running [FidlandService], used to call activatePhs3 /
 *                deactivatePhs3.
 */
class FlashlightPhs3Trigger(
    private val context: Context,
    private val service: FidlandService,
) {
    private val cameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    /** Tracks the last applied strength level so we can keep the meter in sync. */
    private var currentStrength: Int = FLASHLIGHT_STRENGTH_STEPS

    /** Camera ID whose torch is currently on, or null if off. */
    private var activeCameraId: String? = null

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (enabled) {
                activeCameraId = cameraId
                pushActive()
            } else {
                // Only deactivate if it's the same camera we were tracking.
                if (activeCameraId == cameraId || activeCameraId == null) {
                    activeCameraId = null
                    service.deactivatePhs3("Flashlight")
                }
            }
        }

        override fun onTorchStrengthLevelChanged(cameraId: String, newStrengthLevel: Int) {
            // Keep our local strength in sync when the OS reports a change
            // (e.g. another app adjusts torch strength externally).
            if (cameraId == activeCameraId) {
                currentStrength = newStrengthLevel.coerceIn(1, FLASHLIGHT_STRENGTH_STEPS)
                pushActive()
            }
        }
    }

    /** Register the torch callback. Call from FidlandService.onCreate. */
    fun start() {
        cameraManager.registerTorchCallback(torchCallback, null)
    }

    /** Unregister the torch callback. Call from FidlandService.onDestroy. */
    fun stop() {
        cameraManager.unregisterTorchCallback(torchCallback)
        activeCameraId = null
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    /**
     * (Re)constructs the handler with the latest [currentStrength] and pushes
     * it to the service. Called both on torch-on and on strength changes.
     */
    private fun pushActive() {
        service.activatePhs3(
            FlashlightPhs3Handler(
                flashlightInfo    = FlashlightInfo(strengthLevel = currentStrength),
                onStrengthChanged = { newLevel -> applyStrength(newLevel) }
            )
        )
    }

    /**
     * Applies [newLevel] to the hardware torch (API 33+) and updates the
     * handler so the State 5 meter immediately reflects the new value.
     */
    private fun applyStrength(newLevel: Int) {
        currentStrength = newLevel.coerceIn(1, FLASHLIGHT_STRENGTH_STEPS)

        // API 33+: CameraManager.turnOnTorchWithStrengthLevel
        val cameraId = activeCameraId ?: return
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                cameraManager.turnOnTorchWithStrengthLevel(cameraId, currentStrength)
                // The TorchCallback.onTorchStrengthLevelChanged will fire and call pushActive,
                // but we also push immediately so the UI doesn't lag.
            }
        } catch (e: Exception) {
            // Torch strength not supported on this device — strength is UI-only.
        }

        // Always push so the meter updates even if the hardware call was a no-op.
        pushActive()
    }
}