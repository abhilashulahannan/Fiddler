package com.example.fiddler.subapps.Fidland.phs3.timer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * TimerRepository
 *
 * Single source of truth for the Clock app's timer/stopwatch state, mirroring
 * NavigationRepository's role for Maps. A future notification-listener route
 * (Clock app's countdown / stopwatch notification) or a Clock-app API binding
 * should push updates here; TimerPhs3Trigger only watches [flow] and never
 * computes timer state itself.
 *
 * ── Wiring from a NotificationListenerService (sketch) ──────────────────────
 *
 *   when {
 *       isCountdownNotification(sbn) -> TimerRepository.onTimerTick(
 *           remainingMs     = parsedRemainingMs,
 *           totalDurationMs = parsedTotalMs,
 *           label           = parsedLabel,
 *           running         = !sbn.notification.extras.getBoolean("paused"),
 *       )
 *       isStopwatchNotification(sbn) -> TimerRepository.onStopwatchTick(
 *           elapsedMs = parsedElapsedMs,
 *           running   = !sbn.notification.extras.getBoolean("paused"),
 *       )
 *       isClockNotificationRemoved(sbn) -> TimerRepository.onEnded()
 *   }
 */
object TimerRepository {

    private val _flow = MutableStateFlow(EmptyTimerSnapshot)
    val flow: StateFlow<TimerSnapshot> = _flow

    // ── Countdown timer events ───────────────────────────────────────────────

    /**
     * Call on every countdown-timer update.
     *
     * @param remainingMs     Time left in ms.
     * @param totalDurationMs The duration the timer was originally set for —
     *                         required to compute location c's progress ring.
     * @param label           Optional user label (e.g. "Pasta"). Empty if none.
     * @param running         False if the timer is paused.
     */
    fun onTimerTick(
        remainingMs: Long,
        totalDurationMs: Long,
        label: String = "",
        running: Boolean = true,
    ) {
        val finished = remainingMs <= 0L
        _flow.update {
            TimerSnapshot(
                mode            = TimerMode.TIMER,
                runState        = when {
                    finished  -> TimerRunState.FINISHED
                    running   -> TimerRunState.RUNNING
                    else      -> TimerRunState.PAUSED
                },
                label           = label,
                remainingText   = formatTimerMs(remainingMs),
                remainingMs     = remainingMs.coerceAtLeast(0L),
                totalDurationMs = totalDurationMs,
                isActive        = true,
            )
        }
    }

    // ── Stopwatch events ──────────────────────────────────────────────────────

    /**
     * Call on every stopwatch update.
     *
     * @param elapsedMs Time elapsed in ms since the stopwatch started
     *                   (cumulative across pauses).
     * @param running   False if the stopwatch is paused.
     * @param laps      Full ordered list of laps recorded so far.
     */
    fun onStopwatchTick(
        elapsedMs: Long,
        running: Boolean = true,
        laps: List<LapEntry> = emptyList(),
    ) {
        _flow.update {
            TimerSnapshot(
                mode        = TimerMode.STOPWATCH,
                runState    = if (running) TimerRunState.RUNNING else TimerRunState.PAUSED,
                elapsedText = formatStopwatchMs(elapsedMs),
                elapsedMs   = elapsedMs.coerceAtLeast(0L),
                laps        = laps,
                isActive    = true,
            )
        }
    }

    /** Appends a single new lap to the current stopwatch snapshot. */
    fun addLap(lap: LapEntry) {
        _flow.update { current ->
            if (current.mode != TimerMode.STOPWATCH) return@update current
            current.copy(laps = current.laps + lap)
        }
    }

    // ── Shared ────────────────────────────────────────────────────────────────

    /** Call when the timer/stopwatch notification is dismissed or reset. */
    fun onEnded() {
        _flow.value = EmptyTimerSnapshot
    }
}