package com.example.fiddler.subapps.Fidland.phs3

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Priority class an entity's current bid sits in. Design doc §B6.
 *
 * sort key = (class_rank, sub_score)
 * class_rank: SPECIAL_CONDITION(2) > DOMINANT(1) > SUBMISSIVE(0)
 *
 * A sub-score of 99 in SUBMISSIVE never beats a sub-score of 1 in DOMINANT —
 * class is always the outer sort key, sub-score only ever breaks ties
 * *within* a class.
 */
enum class PriorityClass(val rank: Int) {
    SUBMISSIVE(0),
    DOMINANT(1),
    SPECIAL_CONDITION(2),
}

/**
 * One entity's current bid for the display slot.
 *
 * The scheduler never inspects [handler] beyond routing it to
 * [Phs3Scheduler.activePriority] — it only acts on
 * (priorityClass, subScore, isHardOverride, holdMs, fallback). Entities /
 * triggers construct and [Phs3Scheduler.submit] a new [Phs3Priority]
 * every time their priority state changes — not just on qualify/disqualify,
 * but on every class escalation, Special-Condition promotion, and
 * Special-Condition fallback too.
 *
 * @param subScore 0–100, meaningful only when compared within the same
 *   [priorityClass]. See design doc §B6 for the proposed default table —
 *   those are per-entity constants owned by each trigger/handler, not by
 *   this scheduler.
 * @param isHardOverride Battery-<5%-style: bypasses class/score entirely
 *   and beats every other entity outright, any class, until withdrawn or
 *   replaced. Should be used sparingly — see §B6, currently only Battery's
 *   critical threshold claims this.
 * @param holdMs How long this bid should hold the slot once it wins,
 *   before automatically reverting to [fallback]. `null` means "held
 *   until the entity itself calls [Phs3Scheduler.submit] or
 *   [Phs3Scheduler.withdraw] again" — used for indefinite holds (Call
 *   active, Battery <5%, Camera sensor-active, Flashlight torch-on) where
 *   the entity's own qualify/disqualify signal is the thing that ends the
 *   promotion, not a timer.
 * @param fallback The bid to automatically re-submit when [holdMs]
 *   expires — typically the entity's current home-class bid. Required
 *   whenever [holdMs] is non-null; ignored when [holdMs] is null. Design
 *   doc §B6/§7.5's "falls back to current home class" behavior is
 *   implemented via this field so the scheduler can revert on schedule
 *   without needing the entity to race a timer of its own.
 */
data class Phs3Priority(
    val handler: Phs3Handler,
    val priorityClass: PriorityClass,
    val subScore: Int,
    val isHardOverride: Boolean = false,
    val holdMs: Long? = null,
    val fallback: Phs3Priority? = null,
)

/**
 * Phs3Scheduler
 *
 * Implements design doc §B6 — priority-class + sub-score scheduling,
 * replacing Phs3Manager's flat round-robin as the answer to "of everything
 * currently bidding for the display slot, whose turn is it right now."
 *
 * WHAT THIS DOES NOT DO
 * ───────────────────────
 * This does not replace Phs3Manager's [register]/[unregister] tracking of
 * "is this entity's data current" (the `qualified` list feeding the
 * location-a row) — that's a separate, still-needed concern. This
 * scheduler only answers the narrower question of who holds the *rotating
 * right-zone slot*. Wire it in by having each entity's trigger call
 * [submit] whenever its priority state changes (in addition to, not
 * instead of, Phs3Manager.register/unregister), and by wiring
 * [Phs3RotationPartitioner]'s onTurnStart/onTurnEnd callbacks (see
 * Phs3SurfacePolicy.kt) through to [submit]/[withdraw] here for Continuous
 * and EventDriven members specifically — Ambient/Special-Condition
 * promotion timing is handled independently by each entity's own trigger
 * per its §B7 entry.
 *
 * EVENT QUEUE (§7.5)
 * ───────────────────
 * When multiple entities are simultaneously bidding at SPECIAL_CONDITION,
 * only the highest sub-score holds the slot; the rest queue implicitly —
 * there's no separate queue data structure to maintain by hand. Because
 * [recomputeActive] re-filters all current bids on every call, the instant
 * the winning bid's hold expires and it reverts via [fallback], the next
 * call to recomputeActive naturally picks up whichever Special-Condition
 * bid is now highest among what remains. [pendingSpecialCondition] exposes
 * that ordering for UI/debugging visibility only — it is not itself part
 * of the resolution logic.
 *
 * THREAD SAFETY
 * ──────────────
 * All public methods are synchronized on this instance. [activePriority]
 * and [pendingSpecialCondition] are safe to collect from any dispatcher.
 */
class Phs3Scheduler(private val scope: CoroutineScope) {

    companion object {
        /**
         * Midpoint of the "5-8s" dwell convention used informally by ~10
         * entities' Special-Condition promotions in §B7 (Battery, Music,
         * Alarm's variants, Weather, Comms, Football, Download, Ring
         * Mode). Per §B8 open item #4, it's still unconfirmed whether this
         * is a real shared constant or a coincidence — use this default
         * unless/until an entity's own entry specifies otherwise, and pass
         * an explicit [Phs3Priority.holdMs] for entities that need a
         * different value (e.g. Alarm's red-stage window, which is up to
         * 5 minutes, not 5-8 seconds).
         */
        const val DEFAULT_SPECIAL_CONDITION_DWELL_MS = 6_500L
    }

    private data class Bid(val priority: Phs3Priority, val submittedAtMs: Long)

    /** All current bids, keyed by handler label. */
    private val bids = linkedMapOf<String, Bid>()

    private var expiryJob: Job? = null

    private val _activePriority = MutableStateFlow<Phs3Priority?>(null)
    /** The bid currently holding the display slot. Null when nothing is bidding. */
    val activePriority: StateFlow<Phs3Priority?> = _activePriority

    private val _pendingSpecialCondition = MutableStateFlow<List<Phs3Priority>>(emptyList())
    /**
     * Every SPECIAL_CONDITION bid *not* currently holding the slot,
     * highest sub-score first — informational only (see class doc above).
     * Useful for a debug overlay or for confirming §7.5's queue behavior
     * during testing.
     */
    val pendingSpecialCondition: StateFlow<List<Phs3Priority>> = _pendingSpecialCondition

    /**
     * Submit or update an entity's priority bid. Call this on every class
     * change, sub-score change, or Special-Condition promotion/fallback —
     * not only on qualify/disqualify.
     */
    @Synchronized
    fun submit(priority: Phs3Priority) {
        bids[priority.handler.label] = Bid(priority, System.currentTimeMillis())
        recomputeActive()
    }

    /** Withdraw an entity's bid entirely — call on disqualify/unregister. */
    @Synchronized
    fun withdraw(label: String) {
        bids.remove(label)
        recomputeActive()
    }

    @Synchronized
    fun currentBid(label: String): Phs3Priority? = bids[label]?.priority

    // ── Internal ──────────────────────────────────────────────────────

    private fun recomputeActive() {
        expiryJob?.cancel()
        expiryJob = null

        val hardOverride = bids.values.firstOrNull { it.priority.isHardOverride }
        if (hardOverride != null) {
            publish(hardOverride)
            _pendingSpecialCondition.value = emptyList()
            return
        }

        val specialCondition = bids.values
            .filter { it.priority.priorityClass == PriorityClass.SPECIAL_CONDITION }
            .sortedByDescending { it.priority.subScore }

        if (specialCondition.isNotEmpty()) {
            val winner = specialCondition.first()
            publish(winner)
            _pendingSpecialCondition.value = specialCondition.drop(1).map { it.priority }
            scheduleExpiry(winner)
            return
        }
        _pendingSpecialCondition.value = emptyList()

        val dominant = bids.values
            .filter { it.priority.priorityClass == PriorityClass.DOMINANT }
            .maxByOrNull { it.priority.subScore }
        if (dominant != null) {
            publish(dominant)
            return
        }

        val submissive = bids.values
            .filter { it.priority.priorityClass == PriorityClass.SUBMISSIVE }
            .maxByOrNull { it.priority.subScore }
        publish(submissive)
    }

    private fun publish(bid: Bid?) {
        _activePriority.value = bid?.priority
    }

    /**
     * Schedules the winning Special-Condition bid's automatic reversion.
     * `holdMs == null` means indefinite hold — no timer, the entity's own
     * trigger is expected to call [submit]/[withdraw] when the underlying
     * condition changes (e.g. `CALL_STATE_IDLE`, torch-off, sensor-release).
     */
    private fun scheduleExpiry(bid: Bid) {
        val hold = bid.priority.holdMs ?: return
        expiryJob = scope.launch {
            delay(hold)
            val stillActive = bids[bid.priority.handler.label]?.submittedAtMs == bid.submittedAtMs
            if (!stillActive) return@launch // superseded by a newer bid already — nothing to revert
            val fallback = bid.priority.fallback
            if (fallback != null) {
                submit(fallback)
            } else {
                withdraw(bid.priority.handler.label)
            }
        }
    }
}