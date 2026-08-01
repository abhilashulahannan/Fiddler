package com.example.fiddler.subapps.Fidland.phs3.camera

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import com.example.fiddler.subapps.Fidland.phs3.Phs3DebugLog
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

    /** IDs currently reported unavailable (i.e. opened by some process). */
    private val activeCameraIds = mutableSetOf<String>()

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
        service.deactivatePhs3(handler.label)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun publish() {
        val info = CameraInfo(activeCameraIds = activeCameraIds.toSet())
        Phs3DebugLog.onPoll("Camera", "active=${info.isActive} ids=${info.activeCameraIds}")

        if (info.isActive) {
            service.activatePhs3(handler)
        } else {
            service.deactivatePhs3(handler.label)
        }
    }
}