package com.example.fiddler.subapps.Fidland.phs3

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * §B1 — per-entity display policy, replacing the one flat global timer.
 *
 * Today every qualified handler gets an identical 10s rotation turn, with
 * Idle hardcoded as the only exception. [SurfacePolicy] makes that
 * distinction explicit and per-handler instead of special-cased.
 *
 * - [CONTINUOUS] — default. Call, Navigation, Music, Timer, etc. Behaves
 *   exactly like today's round-robin: takes a flat turn in rotation.
 * - [EVENT_DRIVEN] — Battery. Surfaces on a transition, dwells briefly,
 *   then exits rotation entirely. The dwell/escalation cadence is owned by
 *   the entity itself (it calls [Phs3RotationPartitioner.interrupt] and
 *   later [Phs3RotationPartitioner.resumeAfterEventDriven]) — the
 *   partitioner does not run a timer on the entity's behalf.
 * - [AMBIENT] — Weather, Comms, wifi band; the generalized form of Idle's
 *   existing "surface occasionally, then step aside" loop. NOT implemented
 *   by [Phs3RotationPartitioner] yet — value exists so handlers can declare
 *   intent, but per §B9 Phase 1 this is deferred until Phase 0's Idle
 *   decision lands or an entity actually claims it. Until then, Idle keeps
 *   running its existing hardcoded loop in `Phs3Manager` untouched.
 */
enum class SurfacePolicy {
    CONTINUOUS,
    EVENT_DRIVEN,
    AMBIENT,
}

/**
 * Phs3RotationPartitioner
 *
 * Splits a qualified-handler list three ways by [SurfacePolicy] and owns
 * the flat round-robin turn timer for [SurfacePolicy.CONTINUOUS] members —
 * i.e. today's `Phs3Manager` rotation logic, generalized so it only ever
 * has to think about one policy bucket instead of a real/Idle binary.
 *
 * WHAT THIS DOES NOT DO
 * ───────────────────────
 * • Does not touch [SurfacePolicy.AMBIENT] members at all yet (see enum
 *   doc) — they're partitioned out and exposed via [ambientMembers] for
 *   visibility, but nothing drives their cadence here.
 * • Does not decide *what* to show, only *whose turn it is* — same
 *   separation of concerns as `Phs3Handler` (how) vs. `Phs3Manager`
 *   (who's qualified) vs. `Phs3Scheduler` (who wins the slot right now).
 * • Does not itself decide entity priority/class — that's `Phs3Scheduler`.
 *   The two are meant to compose: wire [onTurnStart]/[onTurnEnd] through
 *   to `Phs3Scheduler.submit`/`withdraw` for CONTINUOUS and EVENT_DRIVEN
 *   members specifically (see `Phs3scheduler.kt`'s own class doc).
 * • Does not decide `hasLocationA` membership — per §B1 that's now fully
 *   decoupled from rotation membership. A handler can be EVENT_DRIVEN (or
 *   even excluded from rotation forever) while still always appearing in
 *   the location-a row, or vice versa.
 *
 * WIRING
 * ───────
 * Construct with a `policyOf` lookup rather than requiring every
 * `Phs3Handler` implementation to declare a policy up front — this lets
 * the partitioner be dropped in standalone (same pattern as
 * `Phs3Scheduler`) without a forced interface change to every existing
 * handler. Callers that don't care can pass `{ SurfacePolicy.CONTINUOUS }`
 * and get today's behavior back exactly.
 *
 * THREAD SAFETY
 * ──────────────
 * All public methods are expected to be called from [scope]'s dispatcher
 * (Main), matching `Phs3Manager`'s existing convention.
 */
class Phs3RotationPartitioner(
    private val scope: CoroutineScope,
    private val policyOf: (Phs3Handler) -> SurfacePolicy = { SurfacePolicy.CONTINUOUS },
    private val onTurnStart: (Phs3Handler) -> Unit = {},
    private val onTurnEnd: (Phs3Handler) -> Unit = {},
) {

    companion object {
        /** Same cadence as today's `Phs3Manager.ROTATION_INTERVAL_MS`. */
        const val ROTATION_INTERVAL_MS = 10_000L
    }

    private var qualified: List<Phs3Handler> = emptyList()
    private var currentContinuousIndex = 0
    private var isLocked = false
    private var rotationJob: Job? = null

    /** True while an [SurfacePolicy.EVENT_DRIVEN] handler holds the slot via [interrupt]. */
    private var isEventInterrupt = false
    private var interruptedHandler: Phs3Handler? = null

    /**
     * True while a caller has paused turn-taking from *outside* this
     * partitioner's own membership — e.g. `Phs3Manager` showing its
     * separately-managed Idle handler during Idle's own surface window.
     * Unlike [interrupt], nothing here is a member of [qualified]; this is
     * purely "don't act right now," not a competing bid. [updateQualified]
     * still records changes to the qualified set while paused, it just
     * doesn't publish or start the rotation timer until [setPaused] is
     * called with `false`. See [setPaused].
     */
    private var isPaused = false

    val continuousMembers: List<Phs3Handler>
        get() = qualified.filter { policyOf(it) == SurfacePolicy.CONTINUOUS }

    val eventDrivenMembers: List<Phs3Handler>
        get() = qualified.filter { policyOf(it) == SurfacePolicy.EVENT_DRIVEN }

    /** Exposed for visibility only — not yet scheduled by this class. See class doc. */
    val ambientMembers: List<Phs3Handler>
        get() = qualified.filter { policyOf(it) == SurfacePolicy.AMBIENT }

    /**
     * Replaces the full qualified list and re-derives rotation state.
     * Call whenever the underlying qualified set changes (register/unregister).
     */
    fun updateQualified(handlers: List<Phs3Handler>) {
        qualified = handlers
        val continuous = continuousMembers
        if (currentContinuousIndex >= continuous.size) currentContinuousIndex = 0

        if (isEventInterrupt && interruptedHandler?.let { it in qualified } != true) {
            // The event-driven handler that was holding the slot disqualified
            // out from under us (e.g. withdrew mid-dwell) — fall back to
            // continuous rotation immediately rather than waiting for the
            // entity to call resumeAfterEventDriven() itself.
            isEventInterrupt = false
            interruptedHandler = null
        }

        if (!isEventInterrupt && !isPaused) {
            restartRotation()
        }
    }

    /**
     * Pauses turn-taking without touching [isLocked] or cancelling anything
     * bid-related — for a caller-managed handler *outside* this
     * partitioner's membership that needs to occupy the slot temporarily
     * (see [isPaused] doc). While paused, [updateQualified] keeps its
     * bookkeeping current but stops short of publishing or scheduling the
     * next rotation tick. Calling this with `false` resumes exactly where
     * rotation left off — same cursor position, fresh full interval —
     * unless [isLocked] is also true, in which case it stays put until
     * unlocked (mirrors [restartRotation]'s own guard).
     */
    fun setPaused(paused: Boolean) {
        if (isPaused == paused) return
        isPaused = paused
        if (paused) {
            rotationJob?.cancel()
            rotationJob = null
        } else if (!isEventInterrupt) {
            restartRotation()
        }
    }

    /**
     * Jumps directly to [handler], skipping normal advance-by-one semantics —
     * used when a handler should be shown the instant it qualifies rather
     * than waiting for its natural turn (e.g. an incoming call). No-op if
     * [handler] isn't currently a [SurfacePolicy.CONTINUOUS] member, or
     * during an event-driven interrupt or pause (same guard as [advance]).
     */
    fun jumpTo(handler: Phs3Handler) {
        if (isEventInterrupt || isPaused) return
        val continuous = continuousMembers
        if (continuous.indexOf(handler) == -1) return
        currentContinuousIndex = continuous.indexOf(handler)
        restartRotation()
    }

    /**
     * Called by an [SurfacePolicy.EVENT_DRIVEN] handler's own trigger when it
     * wants to surface immediately (a transition just happened). Pauses
     * continuous rotation and gives this handler the slot. The entity owns
     * how long it dwells — call [resumeAfterEventDriven] when done, or just
     * let normal disqualification (removal from the qualified list via
     * [updateQualified]) handle it.
     */
    fun interrupt(handler: Phs3Handler) {
        rotationJob?.cancel()
        rotationJob = null
        isEventInterrupt = true
        interruptedHandler = handler
        onTurnStart(handler)
    }

    /**
     * Called by the event-driven handler (or its trigger) once its own
     * dwell/escalation window ends, handing the slot back to continuous
     * rotation exactly where it left off.
     */
    fun resumeAfterEventDriven() {
        if (!isEventInterrupt) return
        interruptedHandler?.let { onTurnEnd(it) }
        isEventInterrupt = false
        interruptedHandler = null
        restartRotation()
    }

    /** Toggle rotation lock (long-press). While locked, no auto-advance among CONTINUOUS members. */
    fun setLocked(locked: Boolean) {
        isLocked = locked
        if (locked) {
            rotationJob?.cancel()
            rotationJob = null
        } else if (!isEventInterrupt) {
            restartRotation()
        }
    }

    /** Manual advance (swipe). No-op during an event-driven interrupt, while paused, or with fewer than 2 continuous members. */
    fun advance(forward: Boolean = true) {
        if (isEventInterrupt || isPaused) return
        val continuous = continuousMembers
        if (continuous.size < 2) return
        val previous = continuous.getOrNull(currentContinuousIndex)
        currentContinuousIndex = if (forward) {
            (currentContinuousIndex + 1) % continuous.size
        } else {
            (currentContinuousIndex - 1 + continuous.size) % continuous.size
        }
        previous?.let { onTurnEnd(it) }
        onTurnStart(continuous[currentContinuousIndex])
        restartRotation()
    }

    private fun restartRotation() {
        rotationJob?.cancel()
        rotationJob = null
        if (isLocked || isEventInterrupt || isPaused) return

        val continuous = continuousMembers
        if (continuous.isEmpty()) return
        if (currentContinuousIndex >= continuous.size) currentContinuousIndex = 0
        onTurnStart(continuous[currentContinuousIndex])

        if (continuous.size < 2) return // nothing to rotate to
        rotationJob = scope.launch {
            while (true) {
                delay(ROTATION_INTERVAL_MS)
                if (isLocked || isEventInterrupt || isPaused) continue
                val current = continuousMembers
                if (current.size < 2) continue
                val previous = current.getOrNull(currentContinuousIndex)
                currentContinuousIndex = (currentContinuousIndex + 1) % current.size
                previous?.let { onTurnEnd(it) }
                onTurnStart(current[currentContinuousIndex])
            }
        }
    }
}