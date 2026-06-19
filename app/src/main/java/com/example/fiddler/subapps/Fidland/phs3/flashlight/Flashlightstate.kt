package com.example.fiddler.subapps.Fidland.phs3.flashlight

/**
 * Phs3 module — Flashlight — shared state.
 *
 * [FlashlightInfo] is the live snapshot passed into [FlashlightPhs3Handler].
 * The hosting trigger (FlashlightPhs3Trigger) is responsible for populating it
 * from a CameraManager.TorchCallback and calling activatePhs3 / deactivatePhs3
 * as the torch turns on / off.
 *
 * Strength works in discrete steps so we can map directly to
 * CameraManager CONTROL_AE_MODE / vendor torch-strength extensions where
 * supported. On devices that do not support per-step strength, the value is
 * still stored and surfaced in the UI for future use.
 *
 * @param strengthLevel  Current brightness step, in [1 .. FLASHLIGHT_STRENGTH_STEPS].
 */
data class FlashlightInfo(
    val strengthLevel: Int = FLASHLIGHT_STRENGTH_STEPS   // default = full brightness
)

/** Number of discrete strength steps shown on the State 5 meter. */
const val FLASHLIGHT_STRENGTH_STEPS: Int = 5

/**
 * Returns true while the torch is on — the phs3 module qualifies exactly
 * when the torch is active, so the trigger should only construct and pass a
 * [FlashlightInfo] while [CameraManager.TorchCallback.onTorchChanged] reports
 * enabled = true.
 *
 * (Kept here for symmetry with the other modules' `qualifies` helpers.)
 */
fun FlashlightInfo.qualifies(): Boolean = true   // presence of the object implies torch is on

/** Fraction in [0f, 1f] for the given [strengthLevel]. */
fun FlashlightInfo.strengthFraction(): Float =
    strengthLevel.toFloat() / FLASHLIGHT_STRENGTH_STEPS.toFloat()

/** Human-readable label for the strength level, e.g. "3 / 5". */
fun FlashlightInfo.strengthLabel(): String = "$strengthLevel / $FLASHLIGHT_STRENGTH_STEPS"