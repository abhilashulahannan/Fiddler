package com.example.fiddler.subapps.Fidland.phs3.ringmode

import android.app.NotificationManager
import android.media.AudioManager

// ── Mode enum ─────────────────────────────────────────────────────────────────

/**
 * The four ring-mode states the pill can show.
 * [RING], [VIBRATE], [SILENT] map to [AudioManager] ringer modes.
 * [DND] is orthogonal — it can co-exist with any ringer mode; we surface it
 * as its own top-level bucket because it's what the user cares about most.
 */
enum class RingMode {
    RING,
    VIBRATE,
    SILENT,
    DND;

    /** Short label shown below each button in State 5. */
    val displayName: String get() = when (this) {
        RING    -> "Ring"
        VIBRATE -> "Vibrate"
        SILENT  -> "Silent"
        DND     -> "DND"
    }

    /** Emoji icon used in the Indicator and State 5 buttons. */
    val icon: String get() = when (this) {
        RING    -> "🔔"
        VIBRATE -> "📳"
        SILENT  -> "🔕"
        DND     -> "🌙"
    }
}

// ── DND detail ────────────────────────────────────────────────────────────────

/**
 * When DND is active, the OS suppresses notifications under one of three
 * policies. We surface this because the system itself never shows it clearly.
 */
enum class DndPolicy {
    /** All interruptions allowed (DND technically off — shouldn't normally appear). */
    ALL,
    /** Only priority apps/contacts break through. */
    PRIORITY_ONLY,
    /** Only alarms break through. */
    ALARMS_ONLY,
    /** Nothing breaks through. */
    TOTAL_SILENCE;

    val displayName: String get() = when (this) {
        ALL             -> "All"
        PRIORITY_ONLY   -> "Priority only"
        ALARMS_ONLY     -> "Alarms only"
        TOTAL_SILENCE   -> "Total silence"
    }

    val description: String get() = when (this) {
        ALL             -> "All notifications allowed"
        PRIORITY_ONLY   -> "Only priority contacts & apps"
        ALARMS_ONLY     -> "Only alarms get through"
        TOTAL_SILENCE   -> "Nothing gets through"
    }
}

// ── Snapshot ──────────────────────────────────────────────────────────────────

/**
 * Live snapshot passed into [VolumePhs3Handler].
 *
 * @param mode          Current effective mode (DND takes priority if active).
 * @param dndPolicy     Only meaningful when [mode] == [RingMode.DND].
 * @param ringerVolume  Current ringer volume level (0 .. [ringerMaxVolume]).
 * @param ringerMaxVolume  Max ringer volume on this device (usually 7 or 15).
 */
data class RingmodeSnapshot(
    val mode: RingMode,
    val dndPolicy: DndPolicy = DndPolicy.ALL,
    val ringerVolume: Int = 0,
    val ringerMaxVolume: Int = 15,
)

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Converts [AudioManager] + [NotificationManager] state into a [RingmodeSnapshot].
 * Call this from the trigger whenever either source changes.
 */
fun buildSnapshot(audio: AudioManager, nm: NotificationManager): RingmodeSnapshot {
    val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
    val curVolume = audio.getStreamVolume(AudioManager.STREAM_RING)

    // DND wins — if the interruption filter is anything other than ALL,
    // we classify as DND regardless of ringer mode.
    val filter = nm.currentInterruptionFilter
    val dndActive = filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN

    val dndPolicy = if (dndActive) {
        when (filter) {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> DndPolicy.PRIORITY_ONLY
            NotificationManager.INTERRUPTION_FILTER_ALARMS   -> DndPolicy.ALARMS_ONLY
            NotificationManager.INTERRUPTION_FILTER_NONE     -> DndPolicy.TOTAL_SILENCE
            else                                              -> DndPolicy.ALL
        }
    } else DndPolicy.ALL

    val mode = when {
        dndActive -> RingMode.DND
        audio.ringerMode == AudioManager.RINGER_MODE_VIBRATE -> RingMode.VIBRATE
        audio.ringerMode == AudioManager.RINGER_MODE_SILENT  -> RingMode.SILENT
        else -> RingMode.RING
    }

    return RingmodeSnapshot(
        mode           = mode,
        dndPolicy      = dndPolicy,
        ringerVolume   = curVolume,
        ringerMaxVolume = maxVolume,
    )
}

/**
 * The ordered cycle used by tap-to-cycle in the Indicator:
 * Ring → Vibrate → Silent → Ring (DND is excluded from the tap cycle
 * because enabling DND requires MANAGE_NOTIFICATIONS which the user
 * must explicitly grant. DND can still be activated via State 5.)
 */
val RING_TAP_CYCLE = listOf(RingMode.RING, RingMode.VIBRATE, RingMode.SILENT)