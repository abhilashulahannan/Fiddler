package com.example.fiddler.subapps.Fidland.phs3.timer

import com.example.fiddler.subapps.Fidland.NotificationListenerService
import com.example.fiddler.subapps.Fidland.phs3.Phs3DebugLog
import com.example.fiddler.subapps.Fidland.phs3.Phs3Manager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * TimerPhs3Trigger
 *
 * Watches [TimerRepository.flow] and registers / unregisters
 * [TimerPhs3Handler] with [Phs3Manager] based on whether a timer or
 * stopwatch is currently active.
 *
 * Also creates [TimerNotificationSource] and hooks it into the app's
 * [NotificationListenerService] so Clock app notifications feed
 * [TimerRepository] automatically.
 *
 * ── Wire-up in FidlandService ────────────────────────────────────────────────
 *
 *   // Declaration:
 *   private lateinit var timerTrigger: TimerPhs3Trigger
 *
 *   // In onCreate(), after phs3Manager is created:
 *   timerTrigger = TimerPhs3Trigger(serviceScope, phs3Manager)
 *   timerTrigger.start()
 *
 *   // In onDestroy():
 *   if (::timerTrigger.isInitialized) timerTrigger.stop()
 *
 * ── Imports to add to FidlandService ─────────────────────────────────────────
 *   import com.example.fiddler.subapps.Fidland.phs3.timer.TimerPhs3Trigger
 *
 * ── Addition to NotificationListenerService companion object ─────────────────
 *   var timerNotificationSource: TimerNotificationSource? = null
 *
 * ── Additions to NotificationListenerService callbacks ───────────────────────
 *   override fun onNotificationPosted(sbn: StatusBarNotification?) {
 *       sbn ?: return
 *       timerNotificationSource?.onNotificationPosted(sbn)   // ← add this line
 *       downloadSource?.onNotificationPosted(sbn)
 *       ...
 *   }
 *
 *   override fun onNotificationRemoved(sbn: StatusBarNotification?) {
 *       sbn ?: return
 *       timerNotificationSource?.onNotificationRemoved(sbn)  // ← add this line
 *       downloadSource?.onNotificationRemoved(sbn)
 *       ...
 *   }
 */
class TimerPhs3Trigger(
    private val scope: CoroutineScope,
    private val manager: Phs3Manager,
) {
    private val notificationSource = TimerNotificationSource()
    private var watchJob: Job? = null

    // Pre-build the handler once — actions are no-ops for now since the
    // Clock app controls its own timer. We open the Clock app as fallback.
    private val handler = TimerPhs3Handler(
        onPauseResume = { /* Clock app owns pause/resume — no public API */ },
        onCancel      = { /* same */ },
        onLap         = { /* same */ },
    )

    fun start() {
        Phs3DebugLog.onTriggerStart("Timer")

        // Hook our notification source into the existing listener.
        NotificationListenerService.timerNotificationSource = notificationSource

        watchJob = scope.launch {
            TimerRepository.flow.collect { snapshot ->
                Phs3DebugLog.onPoll(
                    "Timer",
                    "active=${snapshot.isActive} mode=${snapshot.mode} " +
                            "state=${snapshot.runState} remaining=${snapshot.remainingText} " +
                            "elapsed=${snapshot.elapsedText}"
                )
                if (snapshot.isActive) {
                    manager.register(handler)
                } else {
                    manager.unregister(handler.label)
                }
            }
        }
    }

    fun stop() {
        Phs3DebugLog.onTriggerStop("Timer")
        NotificationListenerService.timerNotificationSource = null
        watchJob?.cancel()
        watchJob = null
        manager.unregister(handler.label)
        TimerRepository.onEnded()
    }
}