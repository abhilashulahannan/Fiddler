// subapps/Fidland/phs3/idle/Idlephs3trigger.kt
package com.example.fiddler.subapps.Fidland.phs3.idle

import com.example.fiddler.subapps.Fidland.phs3.Phs3DebugLog
import com.example.fiddler.subapps.Fidland.phs3.Phs3Priority
import com.example.fiddler.subapps.Fidland.phs3.PriorityClass
import com.example.fiddler.subapps.Fidland.service.FidlandService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * IdlePhs3Trigger
 *
 * Owns the two clocks [IdlePhs3Handler] itself deliberately doesn't:
 * the 5-minute thought-advance timer, and the new §B7 global-staleness
 * signal that promotes Idle to Special Condition. Same trigger/handler
 * split as every other entity (e.g. [com.example.fiddler.subapps.Fidland
 * .phs3.battery.BatteryPhs3Trigger]).
 *
 * ⚠⚠ **BUILT AHEAD OF PHASE 0 SIGN-OFF — READ BEFORE WIRING** ⚠⚠
 * design doc §B8 #5 / Phase 0 lists "Idle: Ambient vs. Continuous-Dominant"
 * as an explicit open decision, and Phase 5's own status line says Idle
 * "remains blocked" on it. [IdlePhs3Handler] was already built committing
 * to the Continuous-Dominant reading (its own kdoc says so directly) —
 * this trigger continues that assumption rather than re-litigating it, but
 * the sign-off itself still doesn't exist anywhere in the doc. Flagging
 * here rather than silently treating it as resolved (same spirit as
 * Record's missing sign-off note, doc §B7/Phase 5 #15).
 *
 * Two consequences of that assumption that are **not yet true elsewhere
 * in the codebase** and will need follow-up passes, not just this file:
 * 1. `Phs3manager.kt` still runs its own hardcoded Idle carve-out
 *    (`isIdleSurfacing`/`restartIdleSurfaceTimer`/`IDLE_LABEL` exclusion
 *    from `realHandlers()`) and its class doc still says that loop is
 *    "UNCHANGED" pending this exact decision. `Phs3surfacepolicy.kt`'s
 *    `SurfacePolicy.AMBIENT` doc says the same thing verbatim. Left as-is,
 *    that old carve-out will keep excluding Idle from rotation and firing
 *    its own 5min/10s cycle *concurrently* with this trigger's staleness
 *    watcher below — two competing timers driving the same handler.
 *    Retiring that carve-out (per [IdlePhs3Handler]'s own class doc,
 *    "this retires today's 5min/10s carve-out outright") is real surgery
 *    on `Phs3manager.kt`, not something this file can do from outside it.
 * 2. `FidlandService.kt` still imports and registers the old
 *    `IdleThoughtsHandler` class directly (`phs3Manager.register
 *    (IdleThoughtsHandler())`), which no longer exists as of the
 *    [IdlePhs3Handler] rename — that line won't compile until it's
 *    swapped for constructing/starting this trigger instead.
 * Do not wire this trigger into `FidlandService` and call it done without
 * also taking those two passes — the handler alone compiling is not the
 * same as the feature working.
 *
 * ── Thought clock ─────────────────────────────────────────────────────────
 * A single persistent [IdlePhs3Handler] instance (unlike Battery's
 * fresh-handler-per-broadcast pattern) — Idle's per-thought state already
 * lives in the handler's own [IdlePhs3Handler.currentThought] StateFlow, so
 * there's no immutable snapshot to rebuild each tick. This trigger just
 * calls [IdlePhs3Handler.advanceThought] every
 * [IdlePhs3Handler.THOUGHT_INTERVAL_MS] on its own always-running
 * coroutine, per the handler's "thought clock lives on the handler, not
 * the composable" note — advancing happens whether or not Idle currently
 * holds the display slot.
 *
 * ── Home bid ─────────────────────────────────────────────────────────────
 * Flat, unconditional home Dominant/[IDLE_DOMINANT_SUB_SCORE] — submitted
 * once at [start] and never resubmitted on a timer, since (unlike Battery)
 * there's no conditional axis under it to re-evaluate. It's only ever
 * re-submitted here to *undo* the Special Condition promotion below (see
 * [watchStaleness]).
 *
 * ── Global-staleness Special Condition — the new plumbing §B7 flags ───────
 * Every other entity's Special Condition is keyed off *that entity's own*
 * state (Battery's threshold crossing, Call's incoming ring, ...). Idle's
 * is keyed off the **absence** of any of that, system-wide — there's no
 * existing per-entity promotion shape to reuse, so this is a bespoke poll
 * loop rather than a broadcast receiver or state-flow collector on a
 * specific source.
 *
 * Implementation: poll [Phs3Manager.activeHandler] every
 * [STALENESS_POLL_INTERVAL_MS]. Any observed label other than Idle's own
 * counts as "something new displayed" and resets the staleness clock —
 * Idle showing (its ordinary Dominant turn *or* its own Special-Condition
 * promotion) deliberately does not reset its own clock, or it could never
 * time out of a promotion once nothing else ever qualifies again. Once
 * [STALE_THRESHOLD_MS] elapses with no such reset, submit a
 * SPECIAL_CONDITION bid and take the slot via [surfaceEventDriven] — same
 * mechanism Battery uses for its threshold crossings. Falls back the
 * moment a non-Idle label is next observed (**condition-based**, per the
 * doc's "the moment anything new displays, Idle's promotion ends" —
 * deliberately *not* a [Phs3Priority.holdMs] timer the way Battery's
 * threshold-crossing dwell is, since nothing here says the promotion
 * should end on a clock rather than on the triggering condition clearing).
 *
 * ⚠ Flag: polling [Phs3Manager.activeHandler] every
 * [STALENESS_POLL_INTERVAL_MS] rather than collecting its StateFlow
 * directly is a deliberate simplification — a collector would react
 * instantly to every change, which is more correct but means auditing
 * every other entity's publish path for spurious re-emits of the *same*
 * label (Battery republishes on every broadcast even while showing
 * unchanged data — see its trigger's `publish()`) so those don't
 * masquerade as "something new." Polling sidesteps that audit at the cost
 * of up to [STALENESS_POLL_INTERVAL_MS] of slop on both the reset and the
 * promotion. Swap to a collector if that slop turns out to matter.
 *
 * ⚠ Flag: [IDLE_SPECIAL_CONDITION_SUB_SCORE] has no value proposed
 * anywhere in the doc (§B7's Idle section describes the *condition* for
 * promotion but never gives it a number the way Battery's 60 or Record's
 * 80 are given). Picked low (20) deliberately — Idle's promotion only
 * ever fires when literally nothing else is qualified at Special
 * Condition (that's the definition of "nothing new anywhere"), so it
 * never actually needs to outrank a real one; the number mostly just
 * needs to exist so [Phs3Scheduler] has something to sort. Revisit if
 * that reasoning turns out wrong once this is wired up and tested
 * alongside a real Special-Condition entity.
 *
 * ── Qualify ─────────────────────────────────────────────────────────────
 * Unchanged — registers once at [start], never unregisters until [stop].
 *
 * ── Wire-up in FidlandService (not yet done — see flag above) ────────────
 *
 *   private lateinit var idleTrigger: IdlePhs3Trigger
 *
 *   override fun onCreate() {
 *       ...
 *       idleTrigger = IdlePhs3Trigger(serviceScope, this)
 *       idleTrigger.start()
 *   }
 *
 *   override fun onDestroy() {
 *       idleTrigger.stop()
 *       ...
 *   }
 *
 * replacing the current `phs3Manager.register(IdleThoughtsHandler())` line
 * outright.
 */
class IdlePhs3Trigger(
    private val scope: CoroutineScope,
    private val service: FidlandService,
) {
    private val handler = IdlePhs3Handler()

    private val homeBid = Phs3Priority(
        handler = handler,
        priorityClass = PriorityClass.DOMINANT,
        subScore = IDLE_DOMINANT_SUB_SCORE,
    )

    private var thoughtClockJob: Job? = null
    private var stalenessJob: Job? = null

    /** True while Idle's own Special Condition promotion is the live bid. */
    private var isSpecialCondition = false

    fun start() {
        Phs3DebugLog.onTriggerStart("Idle")

        service.phs3Manager.register(handler)
        service.phs3Manager.scheduler.submit(homeBid)

        thoughtClockJob = scope.launch {
            while (true) {
                delay(IdlePhs3Handler.THOUGHT_INTERVAL_MS)
                handler.advanceThought()
            }
        }

        stalenessJob = scope.launch { watchStaleness() }
    }

    fun stop() {
        Phs3DebugLog.onTriggerStop("Idle")
        thoughtClockJob?.cancel()
        thoughtClockJob = null
        stalenessJob?.cancel()
        stalenessJob = null
        isSpecialCondition = false
        service.phs3Manager.scheduler.withdraw(handler.label)
        service.phs3Manager.unregister(handler.label)
    }

    // ── Internal — global staleness watcher ──────────────────────────────────

    /**
     * See class doc's "global-staleness Special Condition" section for the
     * reasoning. Runs for the lifetime of [start]/[stop].
     */
    private suspend fun watchStaleness() {
        var lastActivityAtMs = System.currentTimeMillis()
        var lastObservedLabel: String? = null

        while (true) {
            delay(STALENESS_POLL_INTERVAL_MS)

            val activeLabel = service.phs3Manager.activeHandler.value?.label
            val isSomethingNew = activeLabel != null &&
                    activeLabel != handler.label &&
                    activeLabel != lastObservedLabel
            lastObservedLabel = activeLabel

            if (isSomethingNew) {
                lastActivityAtMs = System.currentTimeMillis()
                if (isSpecialCondition) {
                    endPromotion()
                }
                continue
            }

            if (!isSpecialCondition &&
                System.currentTimeMillis() - lastActivityAtMs >= STALE_THRESHOLD_MS
            ) {
                startPromotion()
            }
        }
    }

    private fun startPromotion() {
        isSpecialCondition = true
        Phs3DebugLog.onPoll("Idle", "60s system-wide staleness — promoting to Special Condition")
        service.phs3Manager.scheduler.submit(
            Phs3Priority(
                handler = handler,
                priorityClass = PriorityClass.SPECIAL_CONDITION,
                subScore = IDLE_SPECIAL_CONDITION_SUB_SCORE,
                holdMs = null, // condition-based fallback, not a timer — see class doc
                fallback = homeBid,
            )
        )
        service.phs3Manager.surfaceEventDriven(handler)
    }

    private fun endPromotion() {
        isSpecialCondition = false
        Phs3DebugLog.onPoll("Idle", "something new displayed — ending Special Condition, back to home Dominant")
        service.phs3Manager.scheduler.submit(homeBid)
        service.phs3Manager.resumeAfterEventDriven()
    }

    private companion object {
        /** §B7 — home Dominant, unconditional. */
        const val IDLE_DOMINANT_SUB_SCORE = 10

        /** ⚠ No value proposed in the doc — see class doc flag. */
        const val IDLE_SPECIAL_CONDITION_SUB_SCORE = 20

        /** §B7 — "over 60s" of system-wide staleness. */
        const val STALE_THRESHOLD_MS = 60_000L

        /** ⚠ Polling interval, not a doc-specified value — see class doc flag. */
        const val STALENESS_POLL_INTERVAL_MS = 5_000L
    }
}