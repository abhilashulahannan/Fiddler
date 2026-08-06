package com.example.fiddler.subapps.Fidland.phs3.flashlight

import android.content.Context
import android.hardware.camera2.CameraManager
import com.example.fiddler.subapps.Fidland.phs3.Phs3Priority
import com.example.fiddler.subapps.Fidland.phs3.PriorityClass
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
 *
 * ── §B6/§B1 wiring (this pass) — Special Condition, indefinite hold ─────────
 * Design doc §B7: home Submissive/30, escalating to **Special Condition,
 * sub-score 70, held indefinitely** while torch-on — number now confirmed
 * (deliberately lower than Camera's 85: a torch-on indicator is a lighter
 * signal than a privacy indicator). Same "qualifies()/promotion coincident"
 * shape as [com.example.fiddler.subapps.Fidland.phs3.camera.CameraPhs3Trigger]:
 * since [FlashlightInfo.qualifies] is always true, there's no separate quiet
 * Submissive/30 state ever actually observed, so — matching Camera's
 * pattern now that the number exists — the off→on transition submits the
 * SPECIAL_CONDITION bid directly rather than a flat home-class bid first.
 * • `holdMs = null` (the default) — indefinite, ends on withdraw(), not a
 *   timer, same as Camera's.
 * • [Phs3Manager.surfaceEventDriven] takes the slot immediately, same as
 *   before this pass — no change to that half of the wiring.
 * • [isSurfacing] still guards against re-submitting/re-interrupting on
 *   every strength-level tick (`onTorchStrengthLevelChanged` calls
 *   [pushActive] too) — only the off→on transition takes the slot.
 * • On torch-off, withdraws the bid and calls
 *   [Phs3Manager.resumeAfterEventDriven], same as before.
 *
 * ⚠ NOT wired here (see design doc §B7 Flashlight entry, flagged open): the
 * additive co-display mechanic is now *declared* on
 * [com.example.fiddler.subapps.Fidland.phs3.flashlight.FlashlightPhs3Handler]
 * (`coDisplay = true`), but rendering it — including the block-ordering
 * rule for two co-displaying DYNAMIC blocks (§B8 #11, resolved as "equal
 * footing, competes on width like any other DYNAMIC block") — is still
 * unbuilt. That's a rendering-layer change (`Phs3Manager`/
 * `overlay_fidland_pill.kt`), not a trigger-layer one, so it doesn't belong
 * in this file.
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

    /** True once the current activation has already taken the slot — guards against re-interrupting on every strength-change republish while still on. */
    private var isSurfacing = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (enabled) {
                activeCameraId = cameraId
                pushActive()
            } else {
                // Only deactivate if it's the same camera we were tracking.
                if (activeCameraId == cameraId || activeCameraId == null) {
                    activeCameraId = null
                    if (isSurfacing) {
                        isSurfacing = false
                        service.phs3Manager.scheduler.withdraw("Flashlight")
                        service.phs3Manager.resumeAfterEventDriven()
                    }
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
        if (isSurfacing) {
            isSurfacing = false
            service.phs3Manager.scheduler.withdraw("Flashlight")
            service.phs3Manager.resumeAfterEventDriven()
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    /**
     * (Re)constructs the handler with the latest [currentStrength] and pushes
     * it to the service. Called both on torch-on and on strength changes.
     */
    private fun pushActive() {
        val freshHandler = FlashlightPhs3Handler(
            flashlightInfo    = FlashlightInfo(strengthLevel = currentStrength),
            onStrengthChanged = { newLevel -> applyStrength(newLevel) }
        )
        service.activatePhs3(freshHandler)

        if (!isSurfacing) {
            // Genuine off→on transition — this is the "event." Strength-only
            // republishes (isSurfacing already true) don't re-take the slot.
            // §B7 confirmed sub-score 70, held indefinitely (holdMs default
            // null) — same coincident-qualification shape as Camera, see
            // class doc.
            isSurfacing = true
            service.phs3Manager.scheduler.submit(
                Phs3Priority(
                    handler       = freshHandler,
                    priorityClass = PriorityClass.SPECIAL_CONDITION,
                    subScore      = 70,
                )
            )
            service.phs3Manager.surfaceEventDriven(freshHandler)
        }
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