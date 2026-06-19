package com.example.fiddler.subapps.Fidland.phs3.timer

/**
 * Phs3 module — Timer / Stopwatch — shared state models.
 *
 * Wiring (future): populate [TimerSnapshot] from the Clock app — either by
 * listening to its notifications (countdown timer posts an updating
 * notification with the remaining time; stopwatch posts one with elapsed
 * time) or, if available, by binding to the system AlarmManager / a
 * ClockTimerPhs3Trigger. The trigger should call
 * `service.activatePhs3(TimerPhs3Handler())` when a timer or stopwatch
 * starts and `service.deactivatePhs3("Timer")` when it is cancelled or
 * finishes — mirroring the AlarmPhs3Trigger / NavigationPhs3Trigger pattern.
 */

// ─────────────────────────────────────────────────────────────────────────────
//  Mode — which Clock app feature is currently running
// ─────────────────────────────────────────────────────────────────────────────

enum class TimerMode {
    TIMER,      // countdown
    STOPWATCH,  // count-up
}

// ─────────────────────────────────────────────────────────────────────────────
//  Run state
// ─────────────────────────────────────────────────────────────────────────────

enum class TimerRunState {
    RUNNING,
    PAUSED,
    FINISHED,   // countdown hit zero — ringing
}

// ─────────────────────────────────────────────────────────────────────────────
//  A single recorded lap (stopwatch only)
// ─────────────────────────────────────────────────────────────────────────────

data class LapEntry(
    val index: Int,
    /** This lap's own duration, e.g. "00:42.31" */
    val lapTimeText: String,
    /** Cumulative elapsed time when this lap was recorded, e.g. "02:18.04" */
    val splitTimeText: String,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Full timer/stopwatch snapshot — updated every tick from the repository
// ─────────────────────────────────────────────────────────────────────────────

data class TimerSnapshot(
    val mode: TimerMode,
    val runState: TimerRunState,

    /** Optional user-given label, e.g. "Pasta". Empty if none. */
    val label: String = "",

    // ── Countdown-timer specific ───────────────────────────────────────────
    /** Remaining time, formatted for location b, e.g. "04:32" */
    val remainingText: String = "",
    /** Remaining duration in ms — used to drive location c's progress circle. */
    val remainingMs: Long = 0L,
    /** Original total duration in ms the countdown was set for. */
    val totalDurationMs: Long = 0L,

    // ── Stopwatch specific ──────────────────────────────────────────────────
    /** Elapsed time, formatted for location b, e.g. "02:18.04" */
    val elapsedText: String = "",
    val elapsedMs: Long = 0L,
    /** Recorded laps, most recent last. Empty if no laps taken. */
    val laps: List<LapEntry> = emptyList(),

    /** True while a timer or stopwatch is active (running, paused, or just finished). */
    val isActive: Boolean = false,
) {
    /** Progress in [0f, 1f] remaining — for location c's circular ring (TIMER mode). */
    val progressFraction: Float
        get() = if (totalDurationMs <= 0L) 0f
        else (remainingMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)

    /** Most recently recorded lap, if any — shown in location c (STOPWATCH mode). */
    val lastLap: LapEntry? get() = laps.lastOrNull()
}

/** Returned when neither a timer nor a stopwatch is running. */
val EmptyTimerSnapshot = TimerSnapshot(
    mode      = TimerMode.TIMER,
    runState  = TimerRunState.FINISHED,
    isActive  = false,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Formatting helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Formats milliseconds as "MM:SS" (or "H:MM:SS" past one hour) — used for the
 * countdown timer's [TimerSnapshot.remainingText] in location b.
 */
fun formatTimerMs(ms: Long): String {
    val totalSecs = (ms.coerceAtLeast(0L)) / 1000L
    val h = totalSecs / 3600
    val m = (totalSecs % 3600) / 60
    val s = totalSecs % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/**
 * Formats milliseconds as "MM:SS.CC" (centiseconds) — used for the
 * stopwatch's [TimerSnapshot.elapsedText] and lap times, matching the
 * Clock app's stopwatch display precision.
 */
fun formatStopwatchMs(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val totalSecs = clamped / 1000L
    val centis = (clamped % 1000L) / 10L
    val h = totalSecs / 3600
    val m = (totalSecs % 3600) / 60
    val s = totalSecs % 60
    return if (h > 0) {
        "%d:%02d:%02d.%02d".format(h, m, s, centis)
    } else {
        "%02d:%02d.%02d".format(m, s, centis)
    }
}