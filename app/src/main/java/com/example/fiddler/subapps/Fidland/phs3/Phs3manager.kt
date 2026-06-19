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
 * ── Problem it solves ─────────────────────────────────────────────────────────
 * Previously, each trigger called [FidlandService.activatePhs3] directly,
 * meaning whichever trigger fired last simply won. There was no concept of
 * multiple simultaneously qualified handlers, priority, or rotation.
 *
 * ── How it works ──────────────────────────────────────────────────────────────
 * • Every phs3 trigger registers / unregisters its handler via [register] and
 *   [unregister]. The manager maintains an ordered [qualified] list.
 * • When more than one *real* (non-Idle) handler is qualified, the manager
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
 * present in [qualified], but it does NOT participate in the normal
 * round-robin among real handlers:
 * • If Idle is the only qualified handler, it is shown continuously (no
 *   rotation needed — this is the normal "nothing else going on" state).
 * • If real handlers are also qualified, Idle is excluded from the swipe
 *   cycle ([cycleNext]/[cyclePrevious] only ever move between real
 *   handlers) but is force-surfaced for exactly one [ROTATION_INTERVAL_MS]
 *   turn every [IDLE_SURFACE_INTERVAL_MS] (default 5 minutes), then normal
 *   rotation resumes from wherever it left off among the real handlers.
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
 * ── Thread safety ─────────────────────────────────────────────────────────────
 * All mutations happen on the [scope]'s dispatcher (Main). Callers from
 * background threads should either use [scope.launch] or ensure they're on Main.
 */
class Phs3Manager(private val scope: CoroutineScope) {

    companion object {
        /** How long each real handler is shown before rotating to the next. */
        const val ROTATION_INTERVAL_MS = 10_000L

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

    /** Index into [realIndices] currently being shown in the RIGHT ZONE. */
    private var currentRealIndex = 0

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

    private var rotationJob: Job? = null
    private var idleSurfaceJob: Job? = null

    // ── Internal helpers — real vs. Idle partitioning ───────────────────────────

    /** Indices into [qualified] of every handler that isn't Idle. */
    private val realIndices: List<Int>
        get() = qualified.indices.filter { qualified[it].label != IDLE_LABEL }

    private val idleIndex: Int
        get() = qualified.indexOfFirst { it.label == IDLE_LABEL }

    // ── Public API — called by triggers ───────────────────────────────────────

    /**
     * Called by a phs3 trigger when its handler becomes qualified.
     *
     * If a handler with the same label is already registered, it is replaced
     * in-place (same list position, currentRealIndex unchanged). This matters
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
            return
        }
        Log.d("Phs3Manager", "register: ${handler.label} | qualified=${qualified.map { it.label }}")
        Phs3DebugLog.onRegister(handler.label, qualified.map { it.label })
        qualified.add(handler)
        publishQualified()

        if (qualified.size == 1) {
            // First-ever handler (will be Idle in practice, since Idle
            // registers immediately at service start before anything else).
            currentRealIndex = 0
            publish(handler)
        } else if (handler.label != IDLE_LABEL && !isIdleSurfacing) {
            // A new real handler just qualified. Show it immediately rather
            // than waiting for the next rotation/idle-surface tick — e.g. an
            // incoming call shouldn't wait up to ROTATION_INTERVAL_MS to
            // appear. Jump the real-handler cursor to it directly.
            val real = realIndices
            val newPos = real.indexOf(qualified.size - 1)
            if (newPos != -1) {
                currentRealIndex = newPos
                publish(handler)
            }
        }
        restartRotation()
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

        if (qualified.isEmpty()) {
            currentRealIndex = 0
            isLocked = false
            isIdleSurfacing = false
            _isLocked.value = false
            _activeHandler.value = null
            rotationJob?.cancel()
            rotationJob = null
            idleSurfaceJob?.cancel()
            idleSurfaceJob = null
            return
        }

        if (wasShowingRemovedHandler) {
            isLocked = false
            _isLocked.value = false
            isIdleSurfacing = false
        }

        val real = realIndices
        if (real.isEmpty()) {
            // Only Idle (or nothing) left — show Idle continuously, no rotation.
            currentRealIndex = 0
            publish(qualified.getOrNull(idleIndex))
            rotationJob?.cancel()
            rotationJob = null
            idleSurfaceJob?.cancel()
            idleSurfaceJob = null
            return
        }

        if (currentRealIndex >= real.size) currentRealIndex = 0
        if (wasShowingRemovedHandler || !isIdleSurfacing) {
            publish(qualified[real[currentRealIndex]])
        }
        restartRotation()
        restartIdleSurfaceTimer()
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
        val real = realIndices
        if (real.size < 2) return
        clearLock()
        isIdleSurfacing = false
        currentRealIndex = (currentRealIndex + 1) % real.size
        publish(qualified[real[currentRealIndex]])
        restartRotation()
    }

    /**
     * Swipe-left: go back to the previous qualified *real* handler
     * immediately and reset the rotation timer so the new one gets a full
     * interval. Idle is never part of this cycle. No-op if fewer than 2
     * real handlers are qualified. Breaks any active lock and cancels an
     * in-progress Idle surface.
     */
    fun cyclePrevious() {
        val real = realIndices
        if (real.size < 2) return
        clearLock()
        isIdleSurfacing = false
        currentRealIndex = (currentRealIndex - 1 + real.size) % real.size
        publish(qualified[real[currentRealIndex]])
        restartRotation()
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
        if (isLocked) {
            rotationJob?.cancel()
            rotationJob = null
        } else {
            restartRotation()
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun clearLock() {
        if (!isLocked) return
        isLocked = false
        _isLocked.value = false
    }

    private fun publish(handler: Phs3Handler?) {
        _activeHandler.value = handler
    }

    /** Publishes an immutable snapshot of the qualified list. */
    private fun publishQualified() {
        _qualifiedHandlers.value = qualified.toList()
    }

    /** Round-robin among real (non-Idle) handlers only. No-op while Idle is surfacing. */
    private fun restartRotation() {
        rotationJob?.cancel()
        rotationJob = null
        if (isLocked) return

        val real = realIndices
        if (real.size < 2) return // nothing to rotate among real handlers

        rotationJob = scope.launch {
            while (true) {
                delay(ROTATION_INTERVAL_MS)
                if (isLocked || isIdleSurfacing) continue
                val r = realIndices
                if (r.size < 2) continue
                currentRealIndex = (currentRealIndex + 1) % r.size
                publish(qualified[r[currentRealIndex]])
            }
        }
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
        if (realIndices.isEmpty()) return // Idle alone — already shown continuously

        idleSurfaceJob = scope.launch {
            while (true) {
                delay(IDLE_SURFACE_INTERVAL_MS)
                val real = realIndices
                val idx = idleIndex
                if (isLocked || real.isEmpty() || idx == -1) continue // lock wins; occurrence skipped, not queued

                isIdleSurfacing = true
                publish(qualified[idx])
                delay(ROTATION_INTERVAL_MS)
                isIdleSurfacing = false

                // Resume normal rotation exactly where it left off, unless
                // something changed (lock engaged, manual swipe, or real
                // handlers all disqualified) while Idle was surfacing.
                if (!isLocked) {
                    val r = realIndices
                    if (r.isNotEmpty()) {
                        if (currentRealIndex >= r.size) currentRealIndex = 0
                        publish(qualified[r[currentRealIndex]])
                    }
                }
            }
        }
    }
}