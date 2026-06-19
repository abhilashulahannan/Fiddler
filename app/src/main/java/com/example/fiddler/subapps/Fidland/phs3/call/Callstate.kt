package com.example.fiddler.subapps.Fidland.phs3.call

/**
 * Phs3 module — Call — shared state models.
 *
 * Two independent activations of the same handler shape:
 *   - [MissedCallInfo]: one or more missed calls from the same contact,
 *     grouped together (mirrors the "X<n>" badge — repeat-caller count).
 *   - [ActiveCallInfo]: a call currently ringing / in progress / outgoing.
 *
 * Wiring (future): populate from TelecomManager / InCallService /
 * CallLog content provider. See CallPhs3Trigger (TBD) for activation —
 * mirrors MusicPhs3Trigger's pattern of calling
 * `activatePhs3(CallPhs3Handler(...))` / `deactivatePhs3()`.
 */

/** A single missed-call log entry, newest first. */
data class MissedCallEntry(
    val displayName: String?,
    val phoneNumber: String,
    val timestampMs: Long
)

/**
 * One or more missed calls grouped by caller.
 *
 * @param displayName  Contact name, or null if unknown (falls back to number).
 * @param phoneNumber  The caller's number.
 * @param count        Number of missed calls from this caller (shown as "x<n>").
 * @param entries      Individual missed-call entries for the State 5 list,
 *                       newest first. Should have [count] items.
 */
data class MissedCallInfo(
    val displayName: String?,
    val phoneNumber: String,
    val count: Int,
    val entries: List<MissedCallEntry> = emptyList()
)

enum class CallDirection { INCOMING, OUTGOING }
enum class CallConnectionState { RINGING, ACTIVE, ON_HOLD }

/**
 * A call that's currently ringing, connecting, or in progress.
 *
 * @param displayName     Contact name, or null if unknown (falls back to number).
 * @param phoneNumber     The other party's number.
 * @param direction       Incoming or outgoing.
 * @param connectionState Ringing (not yet answered), active (talking), or on hold.
 * @param talkStartMs     Epoch-ms when the call became ACTIVE (talking began).
 *                          Null while RINGING — the duration timer only starts
 *                          once the call connects.
 * @param isMuted         Mic-mute state, for the State 5 mute toggle.
 * @param isSpeakerOn     Speaker state, for the State 5 speaker toggle.
 * @param isRecording     Recording state, for the State 5 record toggle.
 */
data class ActiveCallInfo(
    val displayName: String?,
    val phoneNumber: String,
    val direction: CallDirection,
    val connectionState: CallConnectionState = CallConnectionState.ACTIVE,
    val talkStartMs: Long? = null,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isRecording: Boolean = false
)

/** Elapsed talk time in ms, or 0 if the call hasn't connected yet. */
fun ActiveCallInfo.elapsedMs(nowMs: Long): Long {
    val start = talkStartMs ?: return 0L
    return (nowMs - start).coerceAtLeast(0L)
}

/** Formats elapsed ms as "MM:SS" (or "H:MM:SS" past an hour). */
fun formatDuration(elapsedMs: Long): String {
    val totalSecs = elapsedMs / 1000L
    val h = totalSecs / 3600
    val m = (totalSecs % 3600) / 60
    val s = totalSecs % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** Formats an epoch-ms timestamp as "9:15 AM" for the State 5 missed-call list. */
fun formatClockTime(epochMs: Long): String {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = epochMs
    var hour = cal.get(java.util.Calendar.HOUR)
    if (hour == 0) hour = 12
    val minute = cal.get(java.util.Calendar.MINUTE)
    val amPm = if (cal.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM) "AM" else "PM"
    return "%d:%02d %s".format(hour, minute, amPm)
}

/** "x2", "x5" etc. for the missed-call count badge. Returns "" for count <= 1. */
fun missedCountBadge(count: Int): String = if (count > 1) "x$count" else ""