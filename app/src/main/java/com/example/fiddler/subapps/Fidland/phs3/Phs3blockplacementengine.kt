package com.example.fiddler.subapps.Fidland.phs3

/**
 * §B2 — Phs3BlockPlacementEngine
 *
 * Thin orchestrator around [Phs3BlockBalancer]. Where [Phs3BlockBalancer]
 * only knows *how* to place a given block set, this class owns *when* to
 * ask it to — i.e. the doc's third open §B2 question, "Rebalance trigger":
 *
 * > the honest answer is re-running placement on every handler-swap/
 * > event-driven/ambient transition; worth deciding whether that's every
 * > transition or only ones changing the qualified block *set* (vs. e.g. a
 * > rotation tick swapping same-size continuous content), since a full
 * > re-layout every rotation tick may be excessive.
 *
 * APPROACH TAKEN
 * ───────────────
 * Rather than pick a side of that either/or up front (both are guesses
 * about which transitions "matter" without knowing every future entity's
 * block shape), this class sidesteps the classification problem entirely:
 * [update] recomputes via [Phs3BlockBalancer.place] on *every* call —
 * cheap, pure data-crunching, no layout/animation cost — but only invokes
 * [onLayoutChanged] when the resulting [Phs3Layout] actually differs
 * (structurally unequal: different blocks, different side assignments, or
 * different widths) from what was last published. A rotation tick that
 * swaps between two same-size continuous handlers naturally produces an
 * identical [Phs3Layout] and is suppressed for free; a real handler-swap,
 * event-driven interrupt, or ambient surface/hide naturally produces a
 * different one and is published. Callers therefore don't need to
 * classify their own transitions — call [update] on every event listed
 * under WIRING below and let the diff decide.
 *
 * This still leaves the *rebalance-trigger* question (below) as this
 * class's own separate concern — the *stability margin* and *tiebreak*
 * decisions on [Phs3BlockBalancer] are signed off as of §B9 Phase 0
 * close-out (8dp margin, prefer-current-then-left tiebreak).
 *
 * WIRING
 * ───────
 * Expected caller is [Phs3Manager]. Call [update] with the full current
 * block set on:
 * - handler register/unregister (qualified-set change)
 * - [Phs3RotationPartitioner]'s `onTurnStart`/`onTurnEnd` (continuous
 *   handler swap)
 * - [Phs3RotationPartitioner.interrupt] / `resumeAfterEventDriven`
 *   (event-driven surface/hide)
 * - an ambient handler's own surface/hide cycle (once §B1 Ambient lands)
 *
 * Building the `List<Phs3Block>` argument itself — i.e. deriving each
 * handler's block(s) from its declared affinity/width — is deliberately
 * not this class's job; that's [Phs3Manager]'s wiring responsibility once
 * handlers can declare blocks (see [Phs3Handler]).
 *
 * THREAD SAFETY
 * ──────────────
 * Not thread-safe — expected to be called from the Main dispatcher, same
 * convention as [Phs3RotationPartitioner] and [Phs3BlockBalancer].
 */
class Phs3BlockPlacementEngine(
    private val balancer: Phs3BlockBalancer = Phs3BlockBalancer(),
    private val onLayoutChanged: (Phs3Layout) -> Unit = {},
) {

    private var lastPublished: Phs3Layout? = null

    /** The most recently published layout, or null before the first [update]. */
    val currentLayout: Phs3Layout?
        get() = lastPublished

    /**
     * Recomputes placement for [blocks] and publishes the result via
     * [onLayoutChanged] only if it differs from the currently published
     * layout. Safe (and expected) to call on every candidate transition —
     * see class doc, APPROACH TAKEN.
     */
    fun update(blocks: List<Phs3Block>) {
        val layout = balancer.place(blocks)
        if (layout != lastPublished) {
            lastPublished = layout
            onLayoutChanged(layout)
        }
    }

    /**
     * Re-publishes the last computed layout unconditionally — for a
     * newly-attached observer that needs the current state without
     * waiting for the next real [update].
     */
    fun publishCurrent() {
        lastPublished?.let(onLayoutChanged)
    }

    /**
     * Clears both the balancer's remembered per-block side history and
     * this engine's last-published layout. Next [update] recomputes from
     * scratch and always publishes, since there's nothing to diff against.
     */
    fun reset() {
        balancer.reset()
        lastPublished = null
    }
}