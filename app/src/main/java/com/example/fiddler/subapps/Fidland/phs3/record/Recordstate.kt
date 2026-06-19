package com.example.fiddler.subapps.Fidland.phs3.record

// ─────────────────────────────────────────────────────────────────────────────
//  Recording state enum
// ─────────────────────────────────────────────────────────────────────────────

enum class RecordingState {
    /** No recorder notification detected. Module not visible in pill. */
    IDLE,
    /** Recorder app is actively capturing. Lottie pulse + timer running. */
    RECORDING,
    /** Recorder app is paused mid-session. Pulse slowed, timer frozen. */
    PAUSED,
}

// ─────────────────────────────────────────────────────────────────────────────
//  Full session snapshot — emitted by RecorderNotificationSource
// ─────────────────────────────────────────────────────────────────────────────

data class RecordingSnapshot(
    val state: RecordingState,

    /**
     * Elapsed time parsed from the notification text, in milliseconds.
     * Updated every time the recorder app posts a new notification tick
     * (typically every second).
     */
    val elapsedMs: Long,

    /**
     * Pre-formatted elapsed string as it appeared in the notification,
     * e.g. "02:14". Shown verbatim — no reformatting needed.
     */
    val elapsedFormatted: String,

    /** Always 1 — the phone recorder doesn't have a "take" concept. */
    val takeNumber: Int,

    /** Display label shown as the panel header, e.g. "Recording". */
    val takeLabel: String,

    /**
     * Package name of the recorder app whose notification triggered this
     * snapshot, e.g. "com.samsung.android.app.voicenote".
     * Used by the ControlsPanel to launch the correct app on tap.
     * Empty in [EmptyRecordingSnapshot].
     */
    val sourcePackage: String = "",
) {
    /** True while this module should be visible in the phs3 pill. */
    val isActive: Boolean get() = state != RecordingState.IDLE
}

/** Returned when no recorder notification is active. */
val EmptyRecordingSnapshot = RecordingSnapshot(
    state            = RecordingState.IDLE,
    elapsedMs        = 0L,
    elapsedFormatted = "00:00",
    takeNumber       = 0,
    takeLabel        = "Recording",
    sourcePackage    = "",
)

// ─────────────────────────────────────────────────────────────────────────────
//  Formatting helper (kept for display use in tests / previews)
// ─────────────────────────────────────────────────────────────────────────────

/** Formats milliseconds as MM:SS (e.g. 134_000 → "02:14"). */
fun formatElapsed(ms: Long): String {
    val totalSecs = (ms / 1000L).coerceAtLeast(0L)
    val m = totalSecs / 60
    val s = totalSecs % 60
    return "%02d:%02d".format(m, s)
}