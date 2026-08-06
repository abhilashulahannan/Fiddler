package com.example.fiddler.subapps.Fidland.phs3.camera

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import com.example.fiddler.subapps.Fidland.phs3.Phs3DebugLog
import com.example.fiddler.subapps.Fidland.phs3.Phs3Priority
import com.example.fiddler.subapps.Fidland.phs3.PriorityClass
import com.example.fiddler.subapps.Fidland.service.FidlandService

/**
 * CameraPhs3Trigger
 *
 * Registers a [CameraManager.AvailabilityCallback] and activates /
 * deactivates the Camera phs3 slot as camera sensors become unavailable
 * (opened, by this app or any other) / available (released) again — the
 * same signal Android's own camera-in-use status-bar/privacy indicator is
 * built on.
 *
 * ── Why AvailabilityCallback, not CameraDevice.StateCallback ─────────────────
 * [android.hardware.camera2.CameraDevice.StateCallback] only reports state
 * for cameras *this app* opens. [CameraManager.AvailabilityCallback] reports
 * system-wide availability — exactly what a privacy indicator needs: "is
 * *any* app using a camera right now," not just this one.
 *
 * ── Torch is a separate signal ────────────────────────────────────────────────
 * Enabling the flashlight (see FlashlightPhs3Trigger /
 * [CameraManager.TorchCallback]) does not, on most devices, mark the camera
 * as unavailable — torch mode and full camera capture are tracked
 * separately by the platform. So Camera and Flashlight can be active
 * independently and neither needs to suppress the other.
 *
 * ── Multi-sensor devices ──────────────────────────────────────────────────────
 * [CameraInfo.activeCameraIds] is a set rather than a single nullable ID so
 * that concurrent use of more than one physical sensor (e.g. front + back)
 * doesn't lose track of one sensor's state when the other releases first —
 * the slot stays active until the set is empty.
 *
 * ── Permissions ────────────────────────────────────────────────────────────────
 * Unlike opening a camera for capture, [CameraManager.registerAvailabilityCallback]
 * does not require the CAMERA permission — this is by design, since it's the
 * same API Android's own privacy indicator relies on and that indicator has
 * to work without the observing app holding camera access itself.
 *
 * ── Wire-up in FidlandService ────────────────────────────────────────────────
 *
 *   private lateinit var cameraTrigger: CameraPhs3Trigger
 *
 *   override fun onCreate() {
 *       ...
 *       cameraTrigger = CameraPhs3Trigger(applicationContext, this)
 *       cameraTrigger.start()
 *   }
 *
 *   override fun onDestroy() {
 *       cameraTrigger.stop()
 *       ...
 *   }
 *
 * ── §B6/§B1 wiring (this pass) — Special Condition, indefinite hold ─────────
 * Design doc §B7: home Submissive, escalating to **Special Condition,
 * sub-score 85, held indefinitely** for the full duration any sensor is in
 * use — third entity to use the indefinite-hold shape, after Call and
 * Battery<5%, and (unlike Battery's Phase-1 placeholder wiring) Camera's
 * sub-score is already confirmed, not TBD, so there's no separate flat
 * home-class bid submitted first: qualification and the Special-Condition
 * promotion are coincident here (same signal — a sensor is either in use or
 * it isn't, no intermediate "qualified but quiet" state to represent), same
 * shape as Flashlight's own "qualifies()/promotion coincident" note.
 * • On the **transition** into active, submits one [Phs3Priority]
 *   (SPECIAL_CONDITION, subScore 85, `holdMs = null`) — `null` is the
 *   documented indefinite-hold value on [Phs3Priority.holdMs] and lists
 *   Camera by name as one of its examples, so no dwell timer/fallback bid
 *   is needed the way Battery's placeholder dwell is: the slot is held
 *   until this trigger itself calls [Phs3Scheduler.withdraw], driven purely
 *   by the camera-availability signal, not a clock.
 * • [Phs3Manager.surfaceEventDriven] takes the slot immediately, same as
 *   Battery — requires "Camera" -> [SurfacePolicy.EVENT_DRIVEN] added to
 *   [Phs3Manager.policyOf] (not part of this file).
 * • On deactivation, withdraws the bid and calls
 *   [Phs3Manager.resumeAfterEventDriven] to hand the slot back to
 *   continuous rotation, then deactivates as before.
 * • [isSurfacing] guards against re-submitting/re-interrupting on every
 *   `onCameraUnavailable`/`onCameraAvailable` callback while a second
 *   sensor keeps the set non-empty (e.g. front releases while back is still
 *   open) — only the true empty→non-empty transition is "the event," same
 *   guard shape as [com.example.fiddler.subapps.Fidland.phs3.battery.BatteryPhs3Trigger].
 *
 * ⚠ NOT wired here (see design doc §B7 Camera entry, flagged open): the
 * conditional additive co-display placement ("right if alone, left if
 * co-displayed") is a rule-based side assignment that doesn't fit §B2's
 * width-driven [com.example.fiddler.subapps.Fidland.phs3.BlockAffinity.DYNAMIC]
 * as written — the doc itself flags this as possibly needing its own
 * classification tier before it's buildable. [CameraPhs3Handler] declares
 * `DYNAMIC` anyway (see its own class doc) as a deliberate stand-in ahead of
 * that tier existing — width-driven placement, not the co-display rule the
 * spec actually wants, and very likely wrong once co-display itself is
 * built. Don't read the `DYNAMIC` value as this open question being
 * resolved.
 *
 * ── Debugging ────────────────────────────────────────────────────────────────
 * Logs to Phs3DebugLog (visible in the Debugging screen): trigger
 * start/stop, and one POLL entry per availability change showing whether
 * the slot is active and which camera IDs are currently in use.
 */
class CameraPhs3Trigger(
    private val context: Context,
    private val service: FidlandService,
) {
    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /** Runs the availability callback on the main thread, like FlashlightPhs3Trigger's torch callback. */
    private val mainHandler = Handler(Looper.getMainLooper())

    // Stateless handler — just an on/off icon, so one instance is reused
    // for the whole trigger lifetime (same pattern as TimerPhs3Handler /
    // NavigationPhs3Handler, which don't need per-activation reconstruction).
    private val handler = CameraPhs3Handler()

    /** §B7's confirmed Special-Condition bid — sub-score 85, held indefinitely. */
    private val specialConditionPriority = Phs3Priority(
        handler = handler,
        priorityClass = PriorityClass.SPECIAL_CONDITION,
        subScore = 85,
        holdMs = null, // indefinite — ends on withdraw(), not a timer; see class doc.
    )

    /** IDs currently reported unavailable (i.e. opened by some process). */
    private val activeCameraIds = mutableSetOf<String>()

    /** True once the current activation has already taken the slot — guards against re-interrupting on every re-publish while still active. */
    private var isSurfacing = false

    private val availabilityCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            activeCameraIds.remove(cameraId)
            publish()
        }

        override fun onCameraUnavailable(cameraId: String) {
            activeCameraIds.add(cameraId)
            publish()
        }
    }

    fun start() {
        Phs3DebugLog.onTriggerStart("Camera")
        cameraManager.registerAvailabilityCallback(availabilityCallback, mainHandler)
    }

    fun stop() {
        Phs3DebugLog.onTriggerStop("Camera")
        cameraManager.unregisterAvailabilityCallback(availabilityCallback)
        activeCameraIds.clear()
        if (isSurfacing) {
            isSurfacing = false
            service.phs3Manager.scheduler.withdraw(handler.label)
            service.phs3Manager.resumeAfterEventDriven()
        }
        service.deactivatePhs3(handler.label)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun publish() {
        val info = CameraInfo(activeCameraIds = activeCameraIds.toSet())
        Phs3DebugLog.onPoll("Camera", "active=${info.isActive} ids=${info.activeCameraIds}")

        if (info.isActive) {
            service.activatePhs3(handler)

            if (!isSurfacing) {
                // Genuine inactive→active transition — this is the "event."
                isSurfacing = true
                service.phs3Manager.scheduler.submit(specialConditionPriority)
                service.phs3Manager.surfaceEventDriven(handler)
            }
        } else {
            if (isSurfacing) {
                isSurfacing = false
                service.phs3Manager.scheduler.withdraw(handler.label)
                service.phs3Manager.resumeAfterEventDriven() // triggers the partitioner's own interrupt fallback — see BatteryPhs3Trigger's class doc
            }
            service.deactivatePhs3(handler.label)
        }
    }
}