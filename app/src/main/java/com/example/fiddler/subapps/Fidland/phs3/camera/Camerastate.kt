package com.example.fiddler.subapps.Fidland.phs3.camera

/**
 * Phs3 module — Camera sensor indicator — shared state.
 *
 * Mirrors Android's own camera-in-use privacy indicator: qualifies while
 * any camera sensor is open, system-wide (this app or another), not just
 * while this app itself is capturing. See [CameraPhs3Trigger] for the
 * CameraManager.AvailabilityCallback wiring that populates this snapshot.
 *
 * @param activeCameraIds IDs of every camera sensor currently reported
 *                         unavailable (i.e. opened by some process). Usually
 *                         has at most one entry on typical phones, but is a
 *                         set to correctly represent multi-sensor concurrent
 *                         use (e.g. front + back simultaneously) without
 *                         losing track of either when one releases first.
 */
data class CameraInfo(
    val activeCameraIds: Set<String> = emptySet(),
) {
    /** Whether the phs3 slot should be showing at all. */
    val isActive: Boolean get() = activeCameraIds.isNotEmpty()
}

/** Returned when no camera sensor is currently in use. */
val EmptyCameraInfo = CameraInfo()