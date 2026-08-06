package com.example.fiddler.subapps.Fidland.phs3

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * §B2 — content-block classification & the Left/Right/Dynamic balancer.
 *
 * BACKGROUND
 * ───────────
 * Final pill width is `max(leftWidth, rightWidth)` (symmetric-width model,
 * settled per Intent — not touched here). The only lever for a narrower
 * pill is *which content lands on which side*. Today each zone renders one
 * undifferentiated blob; [Phs3Block] breaks that into discrete, independently
 * placeable units so a balancer can move the movable ones.
 *
 * CLASSIFICATION
 * ───────────────
 * - [BlockAffinity.LEFT_ANCHOR] — location-a row content (icons, spinners).
 *   Always outermost left. Fixed, never moves.
 * - [BlockAffinity.NET_SPEED] — the uncontested slot immediately adjacent to
 *   the hole-punch on the left ("nothing renders before NetSpeed", §B8).
 *   Fixed, never moves, but its render position is *nearest the punch*, not
 *   outermost — distinct from [LEFT_ANCHOR] for that reason even though
 *   both contribute to left-side width identically.
 * - [BlockAffinity.RIGHT_ANCHOR] — the active handler's primary/larger
 *   indicator content. Always outermost right. Fixed, never moves.
 * - [BlockAffinity.DYNAMIC] — smaller entity content placeable on either
 *   side; the balancer assigns it to whichever side keeps
 *   `max(leftWidth, rightWidth)` smaller.
 *
 * RENDER ORDER (proposed content zones)
 * ───────────────────────────────────────
 * ```
 * [ LEFT ZONE ] [ dynamic zone (left) ] [ NetSpeed ] || punch || [ dynamic zone (right) ] [ RIGHT ZONE ]
 * ```
 * i.e. left-to-right: [BlockAffinity.LEFT_ANCHOR] blocks, then DYNAMIC
 * blocks placed left, then the [BlockAffinity.NET_SPEED] block, then the
 * hole-punch spacer, then DYNAMIC blocks placed right, then
 * [BlockAffinity.RIGHT_ANCHOR] blocks. See [Phs3Layout.renderOrder].
 *
 * ⚠ Flag carried over from the doc: the doc states NetSpeed's uncontested
 * *left* anchor explicitly but never an equivalent *right* anchor. This
 * layout infers the mirrored right-dynamic-zone-nearest-punch /
 * right-anchor-outermost arrangement from the general "Right blocks stay
 * anchored right" rule, not from an explicit statement. Confirm the
 * inference before treating this ordering as settled.
 *
 * OPEN DECISIONS — NOW SIGNED OFF (see design doc §B9 Phase 0 close-out)
 * ───────────────────────────────────────────────────────────────────────
 * §B2 was blocked on two numbers this file previously had to guess at.
 * Both are now confirmed and still exposed as constructor params on
 * [Phs3BlockBalancer] so a future revision remains a one-line drop-in:
 *
 * 1. **Placement-stability margin: 8dp, confirmed.** Anchored to
 *    [com.example.fiddler.subapps.Fidland.ui.IslandConfig.CONTENT_PADDING_HORIZONTAL]
 *    (also 8dp) rather than picked in isolation — the rule now reads as "a
 *    side-switch isn't worth it unless it saves at least one padding
 *    unit's worth of width," which is legible against an existing constant
 *    instead of an arbitrary threshold. [Phs3BlockBalancer.DEFAULT_STABILITY_MARGIN]
 *    is the settled default, not a placeholder guess.
 * 2. **Deterministic tiebreak: prefer-current-side-then-left, confirmed.**
 *    Matches the doc's own suggested default exactly, with no edge case
 *    that argued for deviating from it. A block with placement history
 *    keeps its side on an exact tie; a block with no history yet (first
 *    placement) defaults to LEFT.
 *
 * NOT IMPLEMENTED HERE (explicitly out of scope per doc)
 * ─────────────────────────────────────────────────────────
 * - Rebalance-trigger policy (every transition vs. only qualified-set
 *   changes) — that's a caller concern: call [Phs3BlockBalancer.place]
 *   whenever the caller decides a re-layout is warranted. This class does
 *   not itself watch for handler-swap/event-driven/ambient transitions.
 * - Discrete-tier snapping / hard slot cap from the elastic-packing
 *   proposal this section reframes — still a separate, unbuilt pass.
 * - Orientation-aware width ceiling (§B3 follow-up) — this balancer only
 *   ever reasons about relative left/right width, never an absolute cap.
 */

/** Where a [Phs3Block] is allowed to render. See class doc for definitions. */
enum class BlockAffinity {
    LEFT_ANCHOR,
    NET_SPEED,
    DYNAMIC,
    RIGHT_ANCHOR,
}

/** Resolved side for a placed [BlockAffinity.DYNAMIC] block. */
enum class BlockSide { LEFT, RIGHT }

/**
 * A single discrete content unit — e.g. album art, one location-a icon, the
 * NetSpeed readout, or an entity's right-zone indicator. [id] must be
 * stable across placement calls for the same logical content; it's the key
 * [Phs3BlockBalancer] uses to remember which side a DYNAMIC block last held
 * (see stability margin, class doc).
 */
data class Phs3Block(
    val id: String,
    val affinity: BlockAffinity,
    val width: Dp,
    /**
     * §B8 #12 — when set on a [BlockAffinity.DYNAMIC] block, forces its side
     * instead of letting [Phs3BlockBalancer] resolve it by width. Source:
     * [Phs3Handler.coDisplaySide], for a co-displaying handler that wants a
     * rule-based side (Camera) rather than a width-competitive one
     * (Flashlight, which leaves this null). Ignored for any other
     * [BlockAffinity] — those are already fixed by definition.
     */
    val forcedSide: BlockSide? = null,
)

/**
 * Result of one [Phs3BlockBalancer.place] pass.
 *
 * [leftWidth]/[rightWidth] feed directly into the existing
 * `max(leftWidth, rightWidth)` pill-sizing calc (untouched, see Intent).
 */
data class Phs3Layout(
    val leftAnchor: List<Phs3Block>,
    val leftDynamic: List<Phs3Block>,
    val netSpeed: Phs3Block?,
    val rightDynamic: List<Phs3Block>,
    val rightAnchor: List<Phs3Block>,
    val leftWidth: Dp,
    val rightWidth: Dp,
) {
    /** Left-to-right render order per the proposed content-zones diagram. */
    val renderOrder: List<Phs3Block>
        get() = buildList {
            addAll(leftAnchor)
            addAll(leftDynamic)
            netSpeed?.let { add(it) }
            // -- hole-punch spacer renders here, outside this list --
            addAll(rightDynamic)
            addAll(rightAnchor)
        }
}

/**
 * Phs3BlockBalancer
 *
 * Places fixed blocks first (LEFT_ANCHOR, NET_SPEED, RIGHT_ANCHOR each stay
 * exactly where their affinity says), then assigns each DYNAMIC block to
 * whichever side keeps `max(leftWidth, rightWidth)` smallest — per-block,
 * in the order given, so earlier DYNAMIC blocks' placements affect later
 * ones' width totals.
 *
 * STABILITY
 * ──────────
 * Remembers the last resolved [BlockSide] per DYNAMIC block id across calls.
 * A block only switches sides when doing so would shrink the max width by
 * more than [stabilityMarginDp] — otherwise it holds its previous side even
 * if the other side is now marginally better. This is what prevents a
 * dynamic block flapping back and forth on few-pixel content changes (see
 * class doc, "Placement stability").
 *
 * Call [place] with the full current block set every time a re-layout is
 * warranted (caller decides the trigger — see class doc, "NOT IMPLEMENTED
 * HERE"). Blocks not present in a given call are dropped from the
 * remembered-side map so stale ids don't linger.
 *
 * THREAD SAFETY
 * ──────────────
 * Not thread-safe — expected to be called from the Main dispatcher, same
 * convention as [Phs3RotationPartitioner].
 */
class Phs3BlockBalancer(
    private val stabilityMarginDp: Dp = DEFAULT_STABILITY_MARGIN,
) {
    companion object {
        /**
         * Signed off (§B9 Phase 0 close-out) — 8dp, anchored to
         * [com.example.fiddler.subapps.Fidland.ui.IslandConfig.CONTENT_PADDING_HORIZONTAL].
         * See class doc, "OPEN DECISIONS — NOW SIGNED OFF" #1.
         */
        val DEFAULT_STABILITY_MARGIN: Dp = 8.dp
    }

    private val rememberedSide = mutableMapOf<String, BlockSide>()

    fun place(blocks: List<Phs3Block>): Phs3Layout {
        val leftAnchor = blocks.filter { it.affinity == BlockAffinity.LEFT_ANCHOR }
        val rightAnchor = blocks.filter { it.affinity == BlockAffinity.RIGHT_ANCHOR }
        val netSpeed = blocks.singleOrNull { it.affinity == BlockAffinity.NET_SPEED }
        val dynamic = blocks.filter { it.affinity == BlockAffinity.DYNAMIC }

        // Drop remembered placements for ids no longer present, so stale
        // state doesn't leak across handler swaps.
        val incomingIds = dynamic.map { it.id }.toSet()
        rememberedSide.keys.retainAll(incomingIds)

        var leftWidth = leftAnchor.sumWidth() + (netSpeed?.width ?: 0.dp)
        var rightWidth = rightAnchor.sumWidth()

        val leftDynamic = mutableListOf<Phs3Block>()
        val rightDynamic = mutableListOf<Phs3Block>()

        for (block in dynamic) {
            // §B8 #12 — a forced side (Camera's rule-based co-display
            // placement) bypasses width resolution entirely, but still
            // contributes its width to the running total on that side and
            // still updates rememberedSide, so a later unforced block's
            // width-minimization decision (and this block's own stability
            // if it ever loses its forcedSide) sees accurate state.
            val side = block.forcedSide ?: resolveSide(
                block = block,
                previousSide = rememberedSide[block.id],
                leftWidthSoFar = leftWidth,
                rightWidthSoFar = rightWidth,
            )
            rememberedSide[block.id] = side
            when (side) {
                BlockSide.LEFT -> {
                    leftDynamic += block
                    leftWidth += block.width
                }
                BlockSide.RIGHT -> {
                    rightDynamic += block
                    rightWidth += block.width
                }
            }
        }

        return Phs3Layout(
            leftAnchor = leftAnchor,
            leftDynamic = leftDynamic,
            netSpeed = netSpeed,
            rightDynamic = rightDynamic,
            rightAnchor = rightAnchor,
            leftWidth = leftWidth,
            rightWidth = rightWidth,
        )
    }

    /** Forgets all remembered per-block side history — e.g. on rotation reset. */
    fun reset() {
        rememberedSide.clear()
    }

    private fun resolveSide(
        block: Phs3Block,
        previousSide: BlockSide?,
        leftWidthSoFar: Dp,
        rightWidthSoFar: Dp,
    ): BlockSide {
        val maxIfLeft = maxOf(leftWidthSoFar + block.width, rightWidthSoFar)
        val maxIfRight = maxOf(leftWidthSoFar, rightWidthSoFar + block.width)

        if (previousSide == null) {
            // No placement history for this id yet — pure minimize, with
            // "prefer left" as the deterministic tiebreak on an exact draw
            // (signed off — see class doc, "OPEN DECISIONS — NOW SIGNED OFF" #2).
            return if (maxIfLeft <= maxIfRight) BlockSide.LEFT else BlockSide.RIGHT
        }

        val maxIfStay = if (previousSide == BlockSide.LEFT) maxIfLeft else maxIfRight
        val switchSide = if (previousSide == BlockSide.LEFT) BlockSide.RIGHT else BlockSide.LEFT
        val maxIfSwitch = if (switchSide == BlockSide.LEFT) maxIfLeft else maxIfRight

        // Hold current side unless switching wins by more than the margin
        // (signed off — see class doc, "OPEN DECISIONS — NOW SIGNED OFF" #1).
        return if (maxIfStay - maxIfSwitch > stabilityMarginDp) switchSide else previousSide
    }

    private fun List<Phs3Block>.sumWidth(): Dp =
        fold(0.dp) { acc, block -> acc + block.width }
}