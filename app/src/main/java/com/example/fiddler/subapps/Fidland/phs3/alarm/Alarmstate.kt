package com.example.fiddler.subapps.Fidland.phs3.alarm

/**
 * Phs3 module — Alarm — shared state.
 *
 * Represents the next upcoming system alarm. Populated from AlarmManager /
 * AlarmClock content provider (wiring TODO — see AlarmPhs3Trigger).
 *
 * @param label        User-facing label, e.g. "Morning Alarm". Falls back to
 *                      "Alarm" if blank.
 * @param triggerAtMs  Absolute epoch-ms time the alarm will ring.
 */
data class AlarmInfo(
    val label: String,
    val triggerAtMs: Long
)

/**
 * Window (in ms) before [AlarmInfo.triggerAtMs] during which this phs3 module
 * qualifies to appear in the State 3 rotation.
 */
const val ALARM_QUALIFY_WINDOW_MS: Long = 30L * 60L * 1000L

/** Thresholds for icon colour stages — mirrors phs3_alarm_entity.html. */
const val ALARM_YELLOW_THRESHOLD_MS: Long = 15L * 60L * 1000L
const val ALARM_RED_THRESHOLD_MS: Long = 5L * 60L * 1000L

enum class AlarmIconStage { GREEN, YELLOW, RED }

/** Remaining ms until [AlarmInfo.triggerAtMs], clamped to 0. */
fun AlarmInfo.remainingMs(nowMs: Long): Long =
    (triggerAtMs - nowMs).coerceAtLeast(0L)

/** True while this alarm should be offered as a phs3 candidate. */
fun AlarmInfo.qualifies(nowMs: Long): Boolean {
    val remaining = triggerAtMs - nowMs
    return remaining in 0..ALARM_QUALIFY_WINDOW_MS
}

/** Icon colour stage for the given remaining time. */
fun iconStage(remainingMs: Long): AlarmIconStage = when {
    remainingMs > ALARM_YELLOW_THRESHOLD_MS -> AlarmIconStage.GREEN
    remainingMs > ALARM_RED_THRESHOLD_MS    -> AlarmIconStage.YELLOW
    else                                    -> AlarmIconStage.RED
}

/** Whether the wiggle/ring animation should play (last 5 min, incl. 0). */
fun shouldWiggle(remainingMs: Long): Boolean = remainingMs <= ALARM_RED_THRESHOLD_MS

/** Formats remaining time as "MM:SS" (e.g. "28:47"), or "RINGING" at 0. */
fun formatCountdown(remainingMs: Long): String {
    if (remainingMs <= 0L) return "RINGING"
    val totalSecs = remainingMs / 1000L
    val m = totalSecs / 60
    val s = totalSecs % 60
    return "%02d:%02d".format(m, s)
}

/** Formats remaining time as "rings in Xm Ys" for the State 5 subtitle. */
fun formatRingsIn(remainingMs: Long): String {
    if (remainingMs <= 0L) return "ringing now"
    val totalSecs = remainingMs / 1000L
    val m = totalSecs / 60
    val s = totalSecs % 60
    return "rings in ${m}m ${s}s"
}