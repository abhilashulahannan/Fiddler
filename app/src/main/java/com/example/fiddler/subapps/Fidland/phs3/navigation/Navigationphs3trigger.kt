package com.example.fiddler.subapps.Fidland.phs3.navigation

import com.example.fiddler.subapps.Fidland.phs3.Phs3DebugLog
import com.example.fiddler.subapps.Fidland.phs3.Phs3Priority
import com.example.fiddler.subapps.Fidland.phs3.Phs3Scheduler
import com.example.fiddler.subapps.Fidland.phs3.PriorityClass
import com.example.fiddler.subapps.Fidland.service.FidlandService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * NavigationPhs3Trigger
 *
 * Watches [NavigationRepository.flow] and activates / deactivates the
 * Navigation phs3 slot on FidlandService. Qualify logic itself is unchanged
 * from the pre-Phase-4 version (`onNotification()`/`onNavigationEnded()`).
 *
 * ── §B7 wiring (Phase 4 — this pass) ─────────────────────────────────────────
 * Class: home Submissive/10 is never actually observed (same "only ever
 * registers while active" shape as Call/Alarm/Timer) — this trigger only
 * ever submits **conditional Dominant, sub-score [DOMINANT_SUB_SCORE] (80)**
 * while registered.
 *
 * **Special Condition — two independent triggers**, both sub-score
 * [SPECIAL_CONDITION_SUB_SCORE] (55 — the shared "5-8s tier band" the design
 * doc names for trigger (1); it doesn't give trigger (2) a distinct number,
 * so this pass keeps them at the same tier rather than inventing a split):
 *
 *   1. **New direction issued** — [Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS]
 *      dwell, falling back to the standing conditional-Dominant bid, same
 *      shape as Weather's promotion. **New logic, this pass:** [onSnapshot]
 *      now diffs [NavigationSnapshot.nextStep]'s instruction against
 *      [previousInstruction] instead of the repository's old "overwrite every
 *      poll" behaviour, so a re-poll of the *same* step (distance just
 *      ticking down) no longer looks like a new direction.
 *
 *   2. **Nearing the turn** — indefinite hold (`holdMs = null`, same shape as
 *      Call's active-call bid), ending when the step actually advances (a new
 *      instruction arrives — see (1)), not on a timer. Promotes once
 *      [NavStep.distanceMeters] drops to or below
 *      [NAV_APPROACH_THRESHOLD_METERS]. **Latch decision:** fires at most
 *      once per step ([approachFiredForInstruction]) rather than being
 *      re-fireable if GPS-derived distance jitters back above the threshold
 *      and below again for the same step — chosen for stability; a
 *      re-fireable version would risk repeatedly interrupting rotation on
 *      noisy distance readings for a condition that, semantically, only
 *      really changes once (you're either approaching this turn or you've
 *      moved past it into the next one).
 *
 * In practice these two shouldn't overlap: nearing → the turn happens → that
 * *is* trigger (1)'s next new-direction pulse, which — because
 * [Phs3Scheduler.submit] replaces any existing bid under the same handler
 * label — naturally supersedes trigger (2)'s indefinite hold rather than
 * needing an explicit hand-off.
 *
 * ── Wire-up in FidlandService ────────────────────────────────────────────────
 *
 *   private lateinit var navigationTrigger: NavigationPhs3Trigger
 *
 *   override fun onCreate() {
 *       ...
 *       navigationTrigger = NavigationPhs3Trigger(serviceScope, this)
 *       navigationTrigger.start()
 *   }
 *
 *   override fun onDestroy() {
 *       navigationTrigger.stop()
 *       ...
 *   }
 *
 * ── Location-a wiring ─────────────────────────────────────────────────────────
 * [NavigationPhs3Handler] opts in via hasLocationA = true / LocationAContent()
 * — now the ETA text (moved left, see navigation.kt's Phase 4 block doc), not
 * the direction icon as before. overlay_fidland_pill wires it automatically.
 *
 * ── Debugging ────────────────────────────────────────────────────────────────
 * Logs to Phs3DebugLog (visible in the Debugging screen): trigger start/stop,
 * one POLL entry per snapshot, and dedicated entries for new-direction /
 * approaching-turn promotions.
 */
class NavigationPhs3Trigger(
    private val scope: CoroutineScope,
    private val service: FidlandService,
) {
    private var watchJob: Job? = null
    private var dwellJob: Job? = null

    /** Instruction text of the last-seen next step — see class doc's diff note. */
    private var previousInstruction: String? = null

    /** Instruction the approach Special Condition already latched for this step, or null. */
    private var approachFiredForInstruction: String? = null

    fun start() {
        Phs3DebugLog.onTriggerStart("Navigation")
        watchJob = scope.launch {
            NavigationRepository.flow.collect { snapshot -> onSnapshot(snapshot) }
        }
    }

    fun stop() {
        Phs3DebugLog.onTriggerStop("Navigation")
        watchJob?.cancel()
        watchJob = null
        dwellJob?.cancel()
        dwellJob = null
        previousInstruction = null
        approachFiredForInstruction = null
        NavigationRepository.onNavigationEnded()
        service.phs3Manager.scheduler.withdraw("Navigation")
        service.deactivatePhs3("Navigation")
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun onSnapshot(snapshot: NavigationSnapshot) {
        val nextLabel = snapshot.nextStep?.instruction ?: "none"
        Phs3DebugLog.onPoll(
            "Navigation",
            "active=${snapshot.isActive} steps=${snapshot.steps.size} " +
                    "next=\"$nextLabel\" eta=${snapshot.etaText}"
        )

        if (!snapshot.isActive) {
            dwellJob?.cancel()
            dwellJob = null
            previousInstruction = null
            approachFiredForInstruction = null
            service.phs3Manager.scheduler.withdraw("Navigation")
            service.deactivatePhs3("Navigation")
            return
        }

        val handler = NavigationPhs3Handler()
        service.activatePhs3(handler)

        val dominantBid = Phs3Priority(
            handler       = handler,
            priorityClass = PriorityClass.DOMINANT,
            subScore      = DOMINANT_SUB_SCORE,
        )
        service.phs3Manager.scheduler.submit(dominantBid)

        val next = snapshot.nextStep
        val instruction = next?.instruction

        // ── Special Condition (1) — new direction issued ────────────────────
        if (instruction != null && previousInstruction != null && instruction != previousInstruction) {
            Phs3DebugLog.onPoll("Navigation", "new direction: \"$instruction\"")
            approachFiredForInstruction = null // fresh step — approach can latch again for it
            dwellJob?.cancel()

            service.phs3Manager.scheduler.submit(
                Phs3Priority(
                    handler       = handler,
                    priorityClass = PriorityClass.SPECIAL_CONDITION,
                    subScore      = SPECIAL_CONDITION_SUB_SCORE,
                    holdMs        = Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS,
                    fallback      = dominantBid,
                )
            )
            service.phs3Manager.surfaceEventDriven(handler)
            dwellJob = scope.launch {
                delay(Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS)
                service.phs3Manager.resumeAfterEventDriven()
            }
        }
        previousInstruction = instruction

        // ── Special Condition (2) — nearing the turn (indefinite hold) ──────
        if (next != null && instruction != null &&
            next.distanceMeters in 1..NAV_APPROACH_THRESHOLD_METERS &&
            approachFiredForInstruction != instruction
        ) {
            Phs3DebugLog.onPoll(
                "Navigation",
                "approaching turn: ${next.distanceMeters}m (threshold=${NAV_APPROACH_THRESHOLD_METERS}m) — promoting, latched"
            )
            approachFiredForInstruction = instruction
            // An indefinite hold supersedes any timed dwell already in flight
            // for this same slot (submit() replaces the prior bid by label).
            dwellJob?.cancel()
            dwellJob = null

            service.phs3Manager.scheduler.submit(
                Phs3Priority(
                    handler       = handler,
                    priorityClass = PriorityClass.SPECIAL_CONDITION,
                    subScore      = SPECIAL_CONDITION_SUB_SCORE,
                    holdMs        = null,
                    fallback      = null,
                )
            )
            service.phs3Manager.surfaceEventDriven(handler)
        }
    }

    private companion object {
        /** Conditional-Dominant sub-score — matches design doc §B7 Navigation entry. */
        const val DOMINANT_SUB_SCORE = 80

        /** Shared Special-Condition sub-score for both triggers — see class doc. */
        const val SPECIAL_CONDITION_SUB_SCORE = 55
    }
}