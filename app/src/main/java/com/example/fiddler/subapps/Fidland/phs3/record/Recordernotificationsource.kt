package com.example.fiddler.subapps.Fidland.phs3.record

import android.app.Notification
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * RecorderNotificationSource
 *
 * Observes the phone's built-in Voice Recorder app notification and maps it
 * into a [RecordingSnapshot] for the phs3 pill.
 *
 * ── How it works ─────────────────────────────────────────────────────────────
 * The stock recorder (Samsung, MIUI, Pixel, etc.) posts an ONGOING notification
 * while recording. It contains:
 *   • The elapsed time in EXTRA_TITLE or EXTRA_TEXT  (e.g. "02:14")
 *   • The app's package name, which we whitelist below.
 *
 * We can't send pause/resume commands — the recorder app controls itself.
 * The ControlsPanel therefore shows an "Open Recorder" button instead of
 * Pause/Stop/New buttons.
 *
 * ── Wire-up ───────────────────────────────────────────────────────────────────
 * In NotificationListenerService:
 *
 *   companion object {
 *       var recorderSource: RecorderNotificationSource? = null
 *   }
 *
 *   override fun onNotificationPosted(sbn: StatusBarNotification?) {
 *       sbn ?: return
 *       recorderSource?.onNotificationPosted(sbn)
 *       ...
 *   }
 *
 *   override fun onNotificationRemoved(sbn: StatusBarNotification?) {
 *       sbn ?: return
 *       recorderSource?.onNotificationRemoved(sbn)
 *       ...
 *   }
 *
 * In FidlandService.onCreate():
 *
 *   val recorderSource = RecorderNotificationSource()
 *   NotificationListenerService.recorderSource = recorderSource
 *   val recorderTrigger = RecordPhs3Trigger(
 *       scope      = serviceScope,
 *       source     = recorderSource,
 *       manager    = phs3Manager,
 *   )
 *   recorderTrigger.start()
 *
 * ── Tested packages ───────────────────────────────────────────────────────────
 *  • com.samsung.android.app.voicenote   — Samsung Voice Recorder
 *  • com.google.android.apps.recorder    — Pixel Recorder
 *  • com.miui.soundrecorder              — MIUI Sound Recorder
 *  • com.oneplus.soundrecorder           — OnePlus Recorder
 *  • com.motorola.soundrecorder          — Motorola Recorder
 *
 * Add more to [RECORDER_PACKAGES] as needed; the parsing logic is generic.
 */
class RecorderNotificationSource {

    companion object {
        val RECORDER_PACKAGES = setOf(
            "com.samsung.android.app.voicenote",
            "com.google.android.apps.recorder",
            "com.miui.soundrecorder",
            "com.oneplus.soundrecorder",
            "com.motorola.soundrecorder",
            "com.sec.android.app.voicenote",    // older Samsung
            "com.android.soundrecorder",         // AOSP fallback
        )

        // Regex patterns to extract MM:SS or HH:MM:SS from notification text.
        // Recorder apps format elapsed time differently — we try both.
        private val TIME_PATTERN = Regex("""(\d{1,2}:\d{2}(?::\d{2})?)""")
    }

    private val _flow = MutableStateFlow(EmptyRecordingSnapshot)
    val flow: StateFlow<RecordingSnapshot> = _flow.asStateFlow()

    // Track the last active notification key so we know when to clear.
    private var activeKey: String? = null

    // ── Called from NotificationListenerService ───────────────────────────────

    fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in RECORDER_PACKAGES) return

        val n = sbn.notification ?: return
        val isOngoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        if (!isOngoing) return

        val extras = n.extras
        val title  = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text   = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()  ?: ""

        // Determine state from action labels or channel name.
        // Most recorders post "Recording" vs "Paused" somewhere in the text.
        val combined   = "$title $text".lowercase()
        val isPaused   = combined.contains("pause") || combined.contains("paused")
        val state      = if (isPaused) RecordingState.PAUSED else RecordingState.RECORDING

        // Parse elapsed time — try title first, then text.
        val elapsedFormatted = TIME_PATTERN.find(title)?.value
            ?: TIME_PATTERN.find(text)?.value
            ?: ""
        val elapsedMs = parseFormattedTime(elapsedFormatted)

        activeKey = "${sbn.packageName}:${sbn.id}"

        _flow.value = RecordingSnapshot(
            state            = state,
            elapsedMs        = elapsedMs,
            elapsedFormatted = elapsedFormatted.ifEmpty { "00:00" },
            takeNumber       = 1,
            takeLabel        = "Recording",
            sourcePackage    = sbn.packageName,
        )
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName !in RECORDER_PACKAGES) return
        val key = "${sbn.packageName}:${sbn.id}"
        if (activeKey == key) {
            activeKey = null
            _flow.value = EmptyRecordingSnapshot
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Converts "MM:SS" or "HH:MM:SS" to milliseconds.
     * Returns 0 if the string doesn't match.
     */
    private fun parseFormattedTime(s: String): Long {
        val parts = s.split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]) * 1_000L
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1_000L
            else -> 0L
        }
    }
}