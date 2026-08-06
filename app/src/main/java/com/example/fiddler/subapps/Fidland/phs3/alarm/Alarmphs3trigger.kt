package com.example.fiddler.subapps.Fidland.phs3.alarm

import android.app.AlarmManager
import android.content.Context
import com.example.fiddler.subapps.Fidland.phs3.Phs3DebugLog
import com.example.fiddler.subapps.Fidland.phs3.Phs3Priority
import com.example.fiddler.subapps.Fidland.phs3.PriorityClass
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
 * fires or moves out of the window. Qualify logic itself is unchanged from
 * the pre-Phase-4 version — see [AlarmInfo.qualifies].
 *
 * ── Why polling, not a BroadcastReceiver ─────────────────────────────────────
 * ACTION_NEXT_ALARM_CLOCK_CHANGED fires reliably but tells us nothing about
 * the actual trigger time without a follow-up AlarmManager query. Polling
 * every 30 seconds is cheap and keeps the logic in one place.
 *
 * ── §B7 wiring (Phase 4 — this pass) ─────────────────────────────────────────
 * Class: home Submissive/10 is never actually observed (see design doc — same
 * "nothing to show until qualified" shape as Call) since this trigger simply
 * doesn't register anything while unqualified. **Conditional Dominant,
 * sub-score [DOMINANT_SUB_SCORE] (70):** submitted on every qualifying poll.
 * **Special Condition, sub-score [SPECIAL_CONDITION_SUB_SCORE] (75):** the
 * last 5 minutes ([ALARM_RED_THRESHOLD_MS]) — an indefinite hold (`holdMs =
 * null`, same shape as Call's active-call bid), since the natural end is a
 * real-world event (the alarm firing) or a user action, not a fixed timer.
 *
 * ── Snooze / Cancel wiring (Phase 4 — new this pass) ─────────────────────────
 * Previously these were pure UI state in [AlarmPhs3Handler]'s State 5 with no
 * effect on qualification. Now wired for real, entirely locally:
 *
 *   • **Cancel** → [onCancel]. Calls [FidlandService.deactivatePhs3] and
 *     records [cancelledForTriggerAtMs] so the next poll (which would
 *     otherwise immediately re-qualify the same still-real system alarm)
 *     doesn't resurrect it. Cleared automatically once [AlarmManager] reports
 *     a *different* `triggerAtMs` — i.e. a genuinely new alarm.
 *
 *   • **Snooze** → [onSnooze]. "Drops Special Condition without
 *     disqualifying" per the design doc: sets [snoozeSuppressUntilMs]
 *     ([SNOOZE_SUPPRESS_MS], 5 min — matching the State 5 button's own
 *     "snooze 5 min" copy) during which red-stage promotion is suppressed
 *     even if the real countdown is still inside [ALARM_RED_THRESHOLD_MS];
 *     the entity keeps showing at the ordinary conditional-Dominant/70 level
 *     instead. Re-runs [tick] immediately afterwards so the downgrade is
 *     reflected without waiting up to [POLL_INTERVAL_MS].
 *
 * ⚠ Flag (carried over, not resolved this pass): neither action touches the
 * *real* system alarm — [AlarmManager] exposes no cancel/reschedule API to a
 * third-party app for someone else's alarm. A user who Cancels/Snoozes here
 * stops seeing the phs3 warning, but the underlying alarm still rings on its
 * original schedule with no further heads-up, until/unless a real
 * system-level integration exists (would need the target Clock app's own
 * intent surface — see [AlarmPhs3Handler]'s data-source-gap note on tag/name).
 *
 * ── Tag/name block — Phase 4 decision ────────────────────────────────────────
 * [AlarmClockInfo] exposes nothing beyond `triggerTime`/`showIntent` — there
 * is no public per-alarm custom label ("Wake up"), only the *source app's*
 * name via [resolveLabel] ("Clock"). Decision: ship the tag/name block as an
 * honest partial using that existing data — [AlarmPhs3Handler.SecondaryIndicator]
 * renders it only when [resolveLabel] actually resolved something more
 * specific than the generic "Alarm" fallback, otherwise nothing (matches the
 * design doc's "renders nothing if unavailable"). No new data source needed.
 *
 * ── Upcoming-alarms-24h list — Phase 4 decision ──────────────────────────────
 * Scoped OUT this pass. Unlike tag/name (partial real data exists), there is
 * no public "all pending alarms" API on any Android version — only the single
 * next alarm via [AlarmManager.getNextAlarmClock]. There's no partial version
 * of "list every pending alarm" to ship when the platform can only ever see
 * one. State 5 keeps its existing next-alarm header; no upcoming-list section
 * was added.
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
 * one POLL entry per [tick] (label, remaining time, qualify/cancel state), and
 * dedicated entries for user Cancel/Snooze actions.
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

    /** The `triggerAtMs` the user tapped Cancel for, or null. See class doc. */
    private var cancelledForTriggerAtMs: Long? = null

    /** Epoch-ms until which red-stage promotion is suppressed after Snooze, or null. */
    private var snoozeSuppressUntilMs: Long? = null

    /** `triggerAtMs` last seen — used to detect a genuinely new alarm and clear the above. */
    private var lastSeenTriggerAtMs: Long? = null

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
        clearAlarmState()
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun tick() {
        val next = alarmManager.nextAlarmClock
        val nowMs = System.currentTimeMillis()

        if (next == null) {
            Phs3DebugLog.onPoll("Alarm", "no system alarm set")
            clearAlarmState()
            return
        }

        val info = AlarmInfo(
            label       = resolveLabel(next),
            triggerAtMs = next.triggerTime,
        )

        // A genuinely new alarm (different triggerAtMs) clears any local
        // cancel/snooze state left over from the previous one — see class doc.
        if (lastSeenTriggerAtMs != info.triggerAtMs) {
            if (cancelledForTriggerAtMs != null && cancelledForTriggerAtMs != info.triggerAtMs) {
                cancelledForTriggerAtMs = null
            }
            if (lastSeenTriggerAtMs != null) {
                // Different alarm than last poll — a stale snooze suppression
                // window from the previous alarm no longer applies.
                snoozeSuppressUntilMs = null
            }
            lastSeenTriggerAtMs = info.triggerAtMs
        }

        val remainingMs  = info.remainingMs(nowMs)
        val remainingMin = remainingMs / 60_000L
        val qualifies    = info.qualifies(nowMs)
        val isCancelled  = cancelledForTriggerAtMs == info.triggerAtMs

        Phs3DebugLog.onPoll(
            "Alarm",
            "label=\"${info.label}\" remaining=${remainingMin}min qualifies=$qualifies cancelled=$isCancelled"
        )

        if (!qualifies || isCancelled) {
            clearAlarmState()
            return
        }

        val handler = AlarmPhs3Handler(
            alarmInfo = info,
            onCancel  = { onCancel(info.triggerAtMs) },
            onSnooze  = { onSnooze() },
        )
        service.activatePhs3(handler)

        val dominantBid = Phs3Priority(
            handler       = handler,
            priorityClass = PriorityClass.DOMINANT,
            subScore      = DOMINANT_SUB_SCORE,
        )

        val stage      = iconStage(remainingMs)
        val suppressed = snoozeSuppressUntilMs?.let { nowMs < it } == true

        if (stage == AlarmIconStage.RED && !suppressed) {
            // Indefinite hold — ends on alarm firing (disqualifies above) or
            // user Cancel/Snooze, not a timer. See class doc.
            service.phs3Manager.scheduler.submit(
                Phs3Priority(
                    handler       = handler,
                    priorityClass = PriorityClass.SPECIAL_CONDITION,
                    subScore      = SPECIAL_CONDITION_SUB_SCORE,
                    holdMs        = null,
                    fallback      = null,
                )
            )
        } else {
            service.phs3Manager.scheduler.submit(dominantBid)
        }
    }

    /** User tapped Cancel in State 5 — see class doc's "Snooze / Cancel wiring". */
    private fun onCancel(triggerAtMs: Long) {
        Phs3DebugLog.onPoll("Alarm", "user cancel (local-only — real system alarm untouched)")
        cancelledForTriggerAtMs = triggerAtMs
        snoozeSuppressUntilMs = null
        clearAlarmState()
    }

    /** User tapped Snooze in State 5 — see class doc's "Snooze / Cancel wiring". */
    private fun onSnooze() {
        Phs3DebugLog.onPoll(
            "Alarm",
            "user snooze (local-only — real system alarm untouched); suppressing red-stage for ${SNOOZE_SUPPRESS_MS / 60_000}min"
        )
        snoozeSuppressUntilMs = System.currentTimeMillis() + SNOOZE_SUPPRESS_MS
        // Re-run immediately so the downgrade from Special Condition back to
        // conditional-Dominant is reflected right away, not after up to
        // POLL_INTERVAL_MS of staleness.
        tick()
    }

    private fun clearAlarmState() {
        service.phs3Manager.scheduler.withdraw("Alarm")
        service.deactivatePhs3("Alarm")
    }

    /**
     * Attempts to read a human-readable app name for the alarm from the
     * show-intent's creator package (e.g. "Clock"). Falls back to "Alarm".
     * This is the *source app's* name, not a per-alarm custom tag — see
     * class doc's "Tag/name block" note for why that's the data we have.
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

    private companion object {
        /** Conditional-Dominant sub-score — matches design doc §B7 Alarm entry. */
        const val DOMINANT_SUB_SCORE = 70

        /** Red-stage (last 5 min) Special-Condition sub-score — §B7 Alarm entry. */
        const val SPECIAL_CONDITION_SUB_SCORE = 75

        /**
         * How long a Snooze suppresses red-stage promotion — matches the State 5
         * button's own "snooze 5 min" copy. Purely a local suppression window;
         * does not change [AlarmInfo.triggerAtMs] or the real system alarm's
         * schedule — see class doc's ⚠ flag.
         */
        const val SNOOZE_SUPPRESS_MS = 5L * 60L * 1000L
    }
}