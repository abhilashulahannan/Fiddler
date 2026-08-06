package com.example.fiddler.subapps.Fidland.phs3

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Phs3Manager
 *
 * Central authority that decides which phs3 handler is displayed in the pill
 * at any given moment.
 *
 * ── §B1/§B9 wiring note (this pass) ─────────────────────────────────────────────
 * All handler rotation — Idle included — is delegated to
 * [Phs3RotationPartitioner] ([realRotation]) instead of Phs3Manager
 * hand-rolling its own currentRealIndex/rotationJob bookkeeping. Idle's old
 * bespoke five-minute carve-out (force-surface one turn, excluded from the
 * swipe cycle) is retired outright, per §B8 #5's Continuous-Dominant
 * resolution: Idle is now just another [SurfacePolicy.CONTINUOUS] member of
 * [qualified], contending on equal footing with every other handler for its
 * rotation turn. Its occasional Special-Condition promotion (60s system-wide
 * staleness) is handled entirely by [com.example.fiddler.subapps.Fidland
 * .phs3.idle.IdlePhs3Trigger] via the same [surfaceEventDriven]/
 * [resumeAfterEventDriven] path Battery uses for its own threshold
 * crossings — this class has no Idle-specific code left at all.
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
 * §B7/§B8 #5 — Idle (label "Idle") is always registered via
 * [com.example.fiddler.subapps.Fidland.phs3.idle.IdlePhs3Trigger] at service
 * start and is always present in [qualified], but it is no longer a special
 * case here:
 * • It's an ordinary [SurfacePolicy.CONTINUOUS] member (the default — see
 *   [policyOf]), so it takes a normal rotation turn alongside every other
 *   qualified handler, [cycleNext]/[cyclePrevious] included. If Idle is the
 *   only qualified handler, [Phs3RotationPartitioner] shows it continuously
 *   with no rotation timer running — same as any solo CONTINUOUS member.
 * • Its Special-Condition promotion (60s of system-wide staleness) is driven
 *   entirely by `IdlePhs3Trigger`'s own staleness watcher, which calls
 *   [surfaceEventDriven] to grab the slot and [resumeAfterEventDriven] once
 *   something new displays — the same interrupt/resume path Battery uses
 *   for its threshold crossings. This class has no visibility into that
 *   promotion beyond the generic [surfaceEventDriven] call.
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
        /** How long each handler is shown before rotating to the next. */
        const val ROTATION_INTERVAL_MS = Phs3RotationPartitioner.ROTATION_INTERVAL_MS
    }

    // ── State ─────────────────────────────────────────────────────────────────

    /** Ordered list of currently qualified handlers (includes Idle). */
    private val qualified = mutableListOf<Phs3Handler>()

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

    /**
     * Drives rotation among all qualified handlers — see §B1/§B9 wiring
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
            realRotation.updateQualified(qualified.toList())
            return
        }
        Log.d("Phs3Manager", "register: ${handler.label} | qualified=${qualified.map { it.label }}")
        Phs3DebugLog.onRegister(handler.label, qualified.map { it.label })
        qualified.add(handler)
        publishQualified()

        // updateQualified re-derives rotation state and publishes the first
        // CONTINUOUS member on its own (see Phs3RotationPartitioner.restartRotation) —
        // covers both "first-ever handler" (will be Idle in practice, since
        // it registers immediately at service start) and every later join.
        realRotation.updateQualified(qualified.toList())

        if (qualified.size > 1) {
            // A new handler just qualified alongside others already showing.
            // Show it immediately rather than waiting for the next rotation
            // tick — e.g. an incoming call shouldn't wait up to
            // ROTATION_INTERVAL_MS to appear.
            realRotation.jumpTo(handler)
        }
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
            _isLocked.value = false
            _activeHandler.value = null
            realRotation.updateQualified(emptyList())
            realRotation.setLocked(false)
            blockPlacementEngine.reset()
            return
        }

        if (wasShowingRemovedHandler) {
            isLocked = false
            _isLocked.value = false
            realRotation.setLocked(false)
        }

        // updateQualified re-derives rotation position and republishes the
        // current member on its own (e.g. Idle showing continuously if it's
        // the only one left) — no special-casing needed now that Idle is an
        // ordinary CONTINUOUS member.
        realRotation.updateQualified(qualified.toList())
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
     * Swipe-right: advance to the next qualified handler immediately and
     * reset the rotation timer so the new one gets a full interval. Idle
     * now participates on the same footing as every other CONTINUOUS
     * member (per §B8 #5) — it's no longer excluded from this cycle.
     * No-op if fewer than 2 handlers are qualified. Breaks any active lock.
     */
    fun cycleNext() {
        clearLock()
        realRotation.advance(forward = true)
    }

    /**
     * Swipe-left: go back to the previous qualified handler immediately and
     * reset the rotation timer so the new one gets a full interval. Idle
     * now participates on the same footing as every other CONTINUOUS
     * member (per §B8 #5) — it's no longer excluded from this cycle.
     * No-op if fewer than 2 handlers are qualified. Breaks any active lock.
     */
    fun cyclePrevious() {
        clearLock()
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

}