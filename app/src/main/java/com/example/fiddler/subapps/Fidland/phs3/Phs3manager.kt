package com.example.fiddler.subapps.Fidland.phs3

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Phs3Manager
 *
 * Central authority that decides which phs3 handler is displayed in the pill
 * at any given moment.
 *
 * ── §B1/§B9 wiring note (this pass) ─────────────────────────────────────────────
 * Real (non-Idle) handler rotation is now delegated to [Phs3RotationPartitioner]
 * ([realRotation]) instead of Phs3Manager hand-rolling its own
 * currentRealIndex/rotationJob bookkeeping. Idle's own surface loop is
 * UNCHANGED and still lives entirely in this class — §B1's Ambient policy
 * generalization of that loop is explicitly deferred (see
 * `Phs3SurfacePolicy.kt`'s class doc and the foundation table in the design
 * doc), so Idle keeps its bespoke five-minute timer here rather than being
 * folded into the partitioner.
 *
 * Every currently-registered [Phs3Handler] is [SurfacePolicy.CONTINUOUS]
 * *except* Battery — see [policyOf] below. This pass is deliberately
 * narrow: rather than fabricating placeholder class/sub-score priorities
 * for every other entity (still an open step — design doc §B8 #14 — since
 * it needs each entity's own §B7 numbers, e.g. Music Dominant/85), only
 * Battery — the one entity whose §B7 numbers already exist (home
 * Submissive/15) — is wired to prove [SurfacePolicy.EVENT_DRIVEN] and
 * [Phs3Scheduler] end-to-end. [policyOf] stays a label lookup rather than a
 * `Phs3Handler` interface property (per `Phs3SurfacePolicy.kt`'s own
 * "WIRING" note) so extending this to the next entity is a one-line
 * addition here, not an interface change.
 *
 * [scheduler] is exposed publicly (same pattern as [blockPlacementEngine])
 * so [com.example.fiddler.subapps.Fidland.phs3.battery.BatteryPhs3Trigger]
 * can [Phs3Scheduler.submit]/[Phs3Scheduler.withdraw] its own bid directly —
 * this class does not submit priorities on any handler's behalf, since it
 * has no visibility into an entity's class/sub-score logic (same reasoning
 * as the "§B2 wiring note" below for block widths). [unregister] does
 * withdraw generically by label as basic hygiene, so a trigger that forgets
 * to withdraw on disqualify doesn't leave a stale bid behind.
 *
 * [surfaceEventDriven] / [resumeAfterEventDriven] are thin pass-throughs to
 * [realRotation]'s own [Phs3RotationPartitioner.interrupt] /
 * [Phs3RotationPartitioner.resumeAfterEventDriven] — an EVENT_DRIVEN
 * handler's trigger still calls [register]/[unregister] as before for
 * qualification bookkeeping; these two are purely about who holds the
 * *rotating slot* right now, per `Phs3SurfacePolicy.kt`'s class doc.
 *
 * ── Problem it solves ─────────────────────────────────────────────────────────
 * Previously, each trigger called [FidlandService.activatePhs3] directly,
 * meaning whichever trigger fired last simply won. There was no concept of
 * multiple simultaneously qualified handlers, priority, or rotation.
 *
 * ── How it works ──────────────────────────────────────────────────────────────
 * • Every phs3 trigger registers / unregisters its handler via [register] and
 *   [unregister]. The manager maintains an ordered [qualified] list.
 * • When more than one *real* (non-Idle) handler is qualified, [realRotation]
 *   auto-rotates through them every [ROTATION_INTERVAL_MS] (default 10
 *   seconds) for the RIGHT ZONE indicator (location b/c). The full list is
 *   always visible in the LEFT ZONE via the location-a row.
 * • The currently displayed handler (for the right zone) is exposed via
 *   [activeHandler].
 * • The full qualified list is exposed via [qualifiedHandlers] so the overlay
 *   can render the location-a row for every qualifying handler simultaneously.
 * • FidlandService observes [activeHandler] instead of holding its own state.
 *
 * ── Idle surfacing ────────────────────────────────────────────────────────────
 * IdleThoughtsHandler (label "Idle") is always registered and is always
 * present in [qualified], but it does NOT participate in [realRotation]:
 * • If Idle is the only qualified handler, it is shown continuously (no
 *   rotation needed — this is the normal "nothing else going on" state).
 * • If real handlers are also qualified, Idle is excluded from the swipe
 *   cycle ([cycleNext]/[cyclePrevious] only ever move between real
 *   handlers) but is force-surfaced for exactly one [ROTATION_INTERVAL_MS]
 *   turn every [IDLE_SURFACE_INTERVAL_MS] (default 5 minutes), then normal
 *   rotation resumes from wherever it left off among the real handlers —
 *   done by pausing [realRotation] via [Phs3RotationPartitioner.setPaused]
 *   for the duration of Idle's surface window, so it neither publishes nor
 *   loses its place while paused.
 * • If rotation is locked (long-press) when an Idle surface point is due,
 *   that occurrence is skipped entirely — the lock wins. It is not queued
 *   or replayed after unlock.
 * • Net speed display on/off has no bearing on any of this — Idle's
 *   qualification and surfacing are completely independent of NetSpeed.
 *
 * ── Location-a row ────────────────────────────────────────────────────────────
 * When multiple handlers qualify simultaneously, [qualifiedHandlers] is used
 * by overlay_fidland_pill to build a horizontal row in the LEFT ZONE (location
 * a), to the left of NetSpeedDisplay. Each handler that has [Phs3Handler
 * .hasLocationA] = true contributes one slot to the row.
 * • Music handler is always placed first (sorted in the overlay, not here).
 * • The row gives the user a persistent index of all qualified entities and
 *   acts as a visual guide when swiping left/right in the touch-box.
 * • NetSpeedDisplay position is fixed; no dynamic offset is applied.
 *
 * ── Manual gestures (wired from PhaseTouchBox) ────────────────────────────────
 * • Swipe right → [cycleNext]     : advance to the next qualified real handler.
 * • Swipe left  → [cyclePrevious] : go back to the previous qualified real
 *                                   handler.
 * • Long-press  → [lockRotation]  : toggle auto-rotation lock. While locked,
 *                                   the current handler stays visible
 *                                   indefinitely. Long-press again to unlock.
 *                                   Available in States 1-2-3 and State 5.
 *   Swipes and lock never target Idle directly when real handlers exist —
 *   Idle only ever appears via the automatic 5-minute surface.
 *
 * ── Priority ──────────────────────────────────────────────────────────────────
 * Handlers are displayed in registration order (first registered = first shown).
 * High-urgency handlers (calls, alarms) should be registered before lower-
 * priority ones. The rotation loops back to index 0 after the last handler.
 *
 * ── §B2 wiring note ────────────────────────────────────────────────────────────
 * [blockPlacementEngine] is owned here (single shared instance, same pattern
 * as [realRotation]) but this class deliberately never calls
 * [Phs3BlockPlacementEngine.update] itself. Block widths for arbitrary
 * handler content (location-a icons, the active handler's indicator) are
 * only known once Compose measures them — see overlay_fidland_pill.kt's
 * `onWidthMeasured` callbacks — and this class has no such visibility.
 * Fabricating placeholder widths here would just be guessing at layout the
 * UI layer already measures correctly.
 *
 * What this class *does* own: keeping [blockPlacementEngine]'s lifecycle in
 * sync with [qualified] (see [unregister]'s empty-list branch). The actual
 * `update(blocks)` calls with real measured widths belong in
 * overlay_fidland_pill.kt, which has both handler identity (from
 * [qualifiedHandlers]/[activeHandler]) and the measured widths needed to
 * build real [Phs3Block]s. That wiring is the remaining §B2 build-order
 * step (see fidland-condensed.md).
 *
 * ── Thread safety ─────────────────────────────────────────────────────────────
 * All mutations happen on the [scope]'s dispatcher (Main). Callers from
 * background threads should either use [scope.launch] or ensure they're on Main.
 */
class Phs3Manager(private val scope: CoroutineScope) {

    companion object {
        /** How long each real handler is shown before rotating to the next. */
        const val ROTATION_INTERVAL_MS = Phs3RotationPartitioner.ROTATION_INTERVAL_MS

        /**
         * How often Idle gets force-surfaced for one turn when real handlers
         * are also qualified. Irrelevant when Idle is the only qualified
         * handler (it's just shown continuously in that case).
         */
        const val IDLE_SURFACE_INTERVAL_MS = 5 * 60 * 1_000L

        /** Label used by IdleThoughtsHandler. Kept here to avoid a module dependency. */
        private const val IDLE_LABEL = "Idle"
    }

    // ── State ─────────────────────────────────────────────────────────────────

    /** Ordered list of currently qualified handlers (includes Idle). */
    private val qualified = mutableListOf<Phs3Handler>()

    /** True while Idle is being force-shown for its scheduled one-turn surface. */
    private var isIdleSurfacing = false

    /**
     * When true, auto-rotation is locked. Toggled by long-press.
     * Breaking conditions: long-press again, or the locked handler
     * disqualifies (removed via [unregister]).
     */
    private var isLocked = false

    private val _activeHandler = MutableStateFlow<Phs3Handler?>(null)

    /** The handler the right-zone pill indicator should display right now. Null when nothing is active. */
    val activeHandler: StateFlow<Phs3Handler?> = _activeHandler

    /**
     * The full list of currently qualified handlers, in registration order.
     * Used by the overlay to render the location-a row (all qualifying handlers
     * shown simultaneously, left of NetSpeedDisplay).
     * Music handler is always first — the overlay sorts by label before rendering.
     */
    private val _qualifiedHandlers = MutableStateFlow<List<Phs3Handler>>(emptyList())
    val qualifiedHandlers: StateFlow<List<Phs3Handler>> = _qualifiedHandlers

    private val _isLocked = MutableStateFlow(false)

    /** Exposed so the pill UI can show a lock indicator if desired. */
    val lockedState: StateFlow<Boolean> = _isLocked

    private var idleSurfaceJob: Job? = null

    /**
     * Drives rotation among real (non-Idle) handlers — see §B1/§B9 wiring
     * note above. [onTurnStart] is the only callback wired; there's nothing
     * useful to do on [Phs3RotationPartitioner]'s `onTurnEnd` yet since
     * [publish] is a plain overwrite and nothing downstream currently cares
     * about "this handler's turn just ended" separately from "a new one
     * started" (that distinction matters once [Phs3Scheduler] wiring lands).
     */
    private val realRotation = Phs3RotationPartitioner(
        scope = scope,
        policyOf = ::policyOf,
        onTurnStart = { handler -> publish(handler) },
    )

    /**
     * The only non-[SurfacePolicy.CONTINUOUS] entity wired so far — see
     * class doc. Extend this `when` (not the `Phs3Handler` interface) as
     * more entities' §B7 numbers get confirmed.
     */
    private fun policyOf(handler: Phs3Handler): SurfacePolicy = when (handler.label) {
        "Battery" -> SurfacePolicy.EVENT_DRIVEN
        else      -> SurfacePolicy.CONTINUOUS
    }

    /**
     * Shared §B6 scheduler. See class doc — only Battery submits real bids
     * here so far; everything else is silent (scheduler.activePriority
     * simply reflects Battery's bid, or null, until more entities wire in).
     */
    val scheduler = Phs3Scheduler(scope)

    /**
     * Shared §B2 block-placement engine. See "§B2 wiring note" above — this
     * class only owns its lifecycle (construction, [Phs3BlockPlacementEngine
     * .reset] when [qualified] empties out); it never calls [Phs3BlockPlacementEngine
     * .update] itself since it has no measured-width visibility.
     */
    val blockPlacementEngine = Phs3BlockPlacementEngine()

    // ── Internal helpers — real vs. Idle partitioning ───────────────────────────

    private val idleIndex: Int
        get() = qualified.indexOfFirst { it.label == IDLE_LABEL }

    private fun realHandlers(): List<Phs3Handler> =
        qualified.filter { it.label != IDLE_LABEL }

    // ── Public API — called by triggers ───────────────────────────────────────

    /**
     * Called by a phs3 trigger when its handler becomes qualified.
     *
     * If a handler with the same label is already registered, it is replaced
     * in-place (same list position, rotation position unchanged). This matters
     * for handlers like Music whose trigger creates a fresh handler object on
     * every track change — the old instance must be evicted so that Compose
     * sees a new `engine` key in DisposableEffect and restarts
     * AudioVisualizerEngine correctly after rotation. Without replacement,
     * the old (disposed) engine stays in the qualified list forever and the
     * equalizer stays frozen.
     */
    fun register(handler: Phs3Handler) {
        val existingIdx = qualified.indexOfFirst { it.label == handler.label }
        if (existingIdx != -1) {
            // Replace in-place — preserve list position so the visible
            // handler doesn't jump and rotation timing is unaffected.
            qualified[existingIdx] = handler
            publishQualified()
            // Re-publish if this is the handler currently shown so the
            // overlay picks up the new instance immediately.
            if (_activeHandler.value?.label == handler.label) publish(handler)
            if (handler.label != IDLE_LABEL) realRotation.updateQualified(realHandlers())
            return
        }
        Log.d("Phs3Manager", "register: ${handler.label} | qualified=${qualified.map { it.label }}")
        Phs3DebugLog.onRegister(handler.label, qualified.map { it.label })
        qualified.add(handler)
        publishQualified()

        if (qualified.size == 1) {
            // First-ever handler (will be Idle in practice, since Idle
            // registers immediately at service start before anything else).
            publish(handler)
        }

        if (handler.label != IDLE_LABEL) {
            realRotation.updateQualified(realHandlers())
            if (!isIdleSurfacing && qualified.size > 1) {
                // A new real handler just qualified. Show it immediately rather
                // than waiting for the next rotation/idle-surface tick — e.g. an
                // incoming call shouldn't wait up to ROTATION_INTERVAL_MS to
                // appear.
                realRotation.jumpTo(handler)
            }
        }
        restartIdleSurfaceTimer()
    }

    /**
     * Called by a phs3 trigger when its handler is no longer qualified.
     */
    fun unregister(label: String) {
        val idx = qualified.indexOfFirst { it.label == label }
        if (idx == -1) return
        Log.d("Phs3Manager", "unregister: $label | qualified=${qualified.map { it.label }}")
        Phs3DebugLog.onUnregister(label, qualified.map { it.label })

        val wasShowingRemovedHandler = _activeHandler.value?.label == label
        qualified.removeAt(idx)
        publishQualified()
        scheduler.withdraw(label) // no-op if this label never submitted a bid

        if (qualified.isEmpty()) {
            isLocked = false
            isIdleSurfacing = false
            _isLocked.value = false
            _activeHandler.value = null
            realRotation.updateQualified(emptyList())
            realRotation.setLocked(false)
            idleSurfaceJob?.cancel()
            idleSurfaceJob = null
            blockPlacementEngine.reset()
            return
        }

        if (wasShowingRemovedHandler) {
            isLocked = false
            _isLocked.value = false
            isIdleSurfacing = false
            realRotation.setLocked(false)
        }

        val real = realHandlers()
        if (real.isEmpty()) {
            // Only Idle (or nothing) left — show Idle continuously, no rotation.
            realRotation.updateQualified(emptyList())
            publish(qualified.getOrNull(idleIndex))
            idleSurfaceJob?.cancel()
            idleSurfaceJob = null
            return
        }

        // updateQualified re-derives rotation position and — unless paused
        // for Idle's surface window or locked — republishes the current real
        // handler on its own, matching the old "publish unless idle is
        // surfacing" behavior via [Phs3RotationPartitioner.setPaused].
        realRotation.updateQualified(real)
        restartIdleSurfaceTimer()
    }

    // ── Public API — called by an EVENT_DRIVEN handler's own trigger ───────────

    /**
     * Gives [handler] the rotating slot immediately, pausing continuous
     * rotation — see [Phs3RotationPartitioner.interrupt]. [handler] must
     * already be [register]ed (i.e. present in [qualified]); this call is
     * purely about display turn-taking, not qualification. No-op if
     * [handler] isn't currently qualified.
     */
    fun surfaceEventDriven(handler: Phs3Handler) {
        if (qualified.none { it.label == handler.label }) return
        realRotation.interrupt(handler)
    }

    /**
     * Hands the rotating slot back to continuous rotation — see
     * [Phs3RotationPartitioner.resumeAfterEventDriven]. Safe to call even
     * if nothing is currently interrupted (no-op in that case).
     */
    fun resumeAfterEventDriven() {
        realRotation.resumeAfterEventDriven()
    }

    // ── Public API — called by gesture layer ──────────────────────────────────

    /**
     * Swipe-right: advance to the next qualified *real* handler immediately
     * and reset the rotation timer so the new one gets a full interval.
     * Idle is never part of this cycle — it only appears via its scheduled
     * automatic surface. No-op if fewer than 2 real handlers are qualified.
     * Breaks any active lock and cancels an in-progress Idle surface.
     */
    fun cycleNext() {
        if (realHandlers().size < 2) return
        clearLock()
        if (isIdleSurfacing) {
            isIdleSurfacing = false
            realRotation.setPaused(false)
        }
        realRotation.advance(forward = true)
    }

    /**
     * Swipe-left: go back to the previous qualified *real* handler
     * immediately and reset the rotation timer so the new one gets a full
     * interval. Idle is never part of this cycle. No-op if fewer than 2
     * real handlers are qualified. Breaks any active lock and cancels an
     * in-progress Idle surface.
     */
    fun cyclePrevious() {
        if (realHandlers().size < 2) return
        clearLock()
        if (isIdleSurfacing) {
            isIdleSurfacing = false
            realRotation.setPaused(false)
        }
        realRotation.advance(forward = false)
    }

    /**
     * Long-press: toggle rotation lock on the current handler.
     * While locked, auto-rotation stops and the handler stays visible
     * indefinitely. Long-press again to unlock and resume rotation.
     * No-op if nothing is currently active.
     * Available in States 1-2-3 and State 5.
     * If Idle's scheduled surface point falls while locked, it is skipped
     * entirely — the lock wins and the occurrence is not replayed later.
     */
    fun lockRotation() {
        if (_activeHandler.value == null) return
        isLocked = !isLocked
        _isLocked.value = isLocked
        realRotation.setLocked(isLocked)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun clearLock() {
        if (!isLocked) return
        isLocked = false
        _isLocked.value = false
        realRotation.setLocked(false)
    }

    private fun publish(handler: Phs3Handler?) {
        _activeHandler.value = handler
    }

    /** Publishes an immutable snapshot of the qualified list. */
    private fun publishQualified() {
        _qualifiedHandlers.value = qualified.toList()
    }

    /**
     * Drives Idle's periodic one-turn surface when real handlers are also
     * qualified. Only meaningful when both Idle and at least one real
     * handler are present — if Idle is alone, it's already shown
     * continuously and this timer is moot (it still runs harmlessly but
     * has no qualifying real handlers to step aside for/return to).
     */
    private fun restartIdleSurfaceTimer() {
        idleSurfaceJob?.cancel()
        idleSurfaceJob = null

        if (idleIndex == -1) return // Idle not registered (shouldn't happen in practice)
        if (realHandlers().isEmpty()) return // Idle alone — already shown continuously

        idleSurfaceJob = scope.launch {
            while (true) {
                delay(IDLE_SURFACE_INTERVAL_MS)
                val real = realHandlers()
                val idx = idleIndex
                if (isLocked || real.isEmpty() || idx == -1) continue // lock wins; occurrence skipped, not queued

                isIdleSurfacing = true
                realRotation.setPaused(true) // holds realRotation's position without publishing
                publish(qualified.getOrNull(idx))
                delay(ROTATION_INTERVAL_MS)
                isIdleSurfacing = false

                // Resume normal rotation exactly where it left off, unless
                // something changed while Idle was surfacing — setPaused(false)
                // internally no-ops if realRotation is locked, and republishes
                // wherever its cursor already sits if not.
                realRotation.setPaused(false)
            }
        }
    }
}