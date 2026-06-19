package com.example.fiddler.subapps.Fidland.phs3.alarm

import android.app.AlarmManager
import android.content.Context
import com.example.fiddler.subapps.Fidland.phs3.Phs3DebugLog
import com.example.fiddler.subapps.Fidland.service.FidlandService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * AlarmPhs3Trigger
 *
 * Polls [AlarmManager.getNextAlarmClock] every [POLL_INTERVAL_MS] and
 * activates the Alarm phs3 slot when the next alarm is within
 * [ALARM_QUALIFY_WINDOW_MS] (30 minutes), deactivating it once the alarm
 * fires or moves out of the window.
 *
 * ── Why polling, not a BroadcastReceiver ─────────────────────────────────────
 * ACTION_NEXT_ALARM_CLOCK_CHANGED fires reliably but tells us nothing about
 * the actual trigger time without a follow-up AlarmManager query. Polling
 * every 30 seconds is cheap and keeps the logic in one place. A receiver
 * can be added later to reset the poll timer immediately when alarms change.
 *
 * ── Cancel / Snooze wiring ───────────────────────────────────────────────────
 * The handler's onCancel / onSnooze lambdas are intentionally no-ops here
 * because cancelling or rescheduling a system alarm from a third-party app
 * requires the exact PendingIntent the alarm app used — which is not exposed
 * by AlarmManager. The buttons still appear in State 5 UI as a hook for apps
 * that expose their own cancel/snooze intents. Override the lambdas below
 * if/when you identify the target alarm app's intent action.
 *
 * ── Wire-up in FidlandService ────────────────────────────────────────────────
 *
 *   private lateinit var alarmTrigger: AlarmPhs3Trigger
 *
 *   override fun onCreate() {
 *       ...
 *       alarmTrigger = AlarmPhs3Trigger(this, serviceScope, this)
 *       alarmTrigger.start()
 *   }
 *
 *   override fun onDestroy() {
 *       alarmTrigger.stop()
 *       ...
 *   }
 *
 * ── Debugging ────────────────────────────────────────────────────────────────
 * Logs to Phs3DebugLog (visible in the Debugging screen): trigger start/stop,
 * and one POLL entry per [tick] showing whether a system alarm is set, its
 * label, remaining time, and whether it currently qualifies for the phs3 slot.
 */
class AlarmPhs3Trigger(
    private val context: Context,
    private val scope: CoroutineScope,
    private val service: FidlandService,
) {
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private var pollJob: Job? = null

    /** How often to re-check the next alarm clock. */
    private val POLL_INTERVAL_MS = 30_000L

    fun start() {
        Phs3DebugLog.onTriggerStart("Alarm")
        pollJob = scope.launch {
            while (true) {
                tick()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        Phs3DebugLog.onTriggerStop("Alarm")
        pollJob?.cancel()
        pollJob = null
        service.deactivatePhs3("Alarm")
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun tick() {
        val next = alarmManager.nextAlarmClock
        val nowMs = System.currentTimeMillis()

        if (next == null) {
            Phs3DebugLog.onPoll("Alarm", "no system alarm set")
            service.deactivatePhs3("Alarm")
            return
        }

        val info = AlarmInfo(
            label       = resolveLabel(next),
            triggerAtMs = next.triggerTime,
        )

        val remainingMin = info.remainingMs(nowMs) / 60_000L
        val qualifies    = info.qualifies(nowMs)
        Phs3DebugLog.onPoll(
            "Alarm",
            "label=\"${info.label}\" remaining=${remainingMin}min qualifies=$qualifies"
        )

        if (qualifies) {
            service.activatePhs3(
                AlarmPhs3Handler(
                    alarmInfo = info,
                    onCancel  = { /* see kdoc — system alarm cancel is not possible from here */ },
                    onSnooze  = { /* same */ },
                )
            )
        } else {
            service.deactivatePhs3("Alarm")
        }
    }

    /**
     * Attempts to read a human-readable app name for the alarm from the
     * show-intent's creator package (e.g. "Clock"). Falls back to "Alarm".
     */
    private fun resolveLabel(next: AlarmManager.AlarmClockInfo): String {
        return try {
            val pkg = next.showIntent?.creatorPackage ?: return "Alarm"
            context.packageManager
                .getApplicationInfo(pkg, 0)
                .loadLabel(context.packageManager)
                .toString()
        } catch (_: Exception) {
            "Alarm"
        }
    }
}