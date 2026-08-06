package com.example.fiddler.subapps.Fidland.phs3

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * Contract that every phs3 module must implement.
 *
 * HOW IT FITS INTO THE PILL LIFECYCLE
 * ─────────────────────────────────────
 * 1. A phs3 module becomes active when its triggering condition is met
 *    (e.g. an incoming call, a running timer, music playback started).
 *    Phs3Manager registers it and exposes it as [activeHandler]. The pill
 *    transitions to RIGHT_EXPANDED (State 3) or BOTH_EXPANDED (State 2+3).
 *
 * 2. While in the compact pill states the pill shows a compact indicator
 *    via [Indicator] in the right zone (location b / c).
 *
 * 3. The user swipes down → FidlandService checks [hasState5Content].
 *    If true  → pill expands to STATE5 (content strip, [State5Content]).
 *    If false → pill expands directly to DASHBOARD (State 4).
 *
 * 4. While in STATE5 the user can:
 *    • Swipe down → expand to DASHBOARD (State 4), jumping to the
 *      entity-relevant tab (music → Music, football → Football).
 *    • Swipe up   → collapse back to compact pill states.
 *
 * 5. Long-press in compact or STATE5 → toggle rotation lock via
 *    Phs3Manager.lockRotation(). Does not enter a separate panel state.
 *
 * LOCATION A ROW (new)
 * ─────────────────────
 * When multiple handlers qualify simultaneously, each handler that opts in
 * via [hasLocationA] = true contributes a small icon/widget to a horizontal
 * row in the LEFT ZONE (location a), to the left of NetSpeedDisplay.
 *
 * • Override [hasLocationA] to true and implement [LocationAContent] to
 *   participate. Keep the composable tiny — it sits in a fixed
 *   LOCATION_A_SLOT_SIZE square slot inside the row.
 * • Music is always placed first in the row (handled by overlay_fidland_pill).
 *   All other qualifying handlers follow in registration order.
 * • NetSpeedDisplay no longer receives a dynamic offset; it sits at a fixed
 *   position immediately to the left of the hole-punch spacer.
 *
 * IMPLEMENTATION NOTES
 * ─────────────────────
 * • [Indicator] must be tiny — it appears inside the narrow State 3 pill
 *   alongside the hole-punch spacer. Icon + brief text at most.
 * • [State5Content] has the full STATE4_MAX_WIDTH × STATE5_HEIGHT canvas.
 *   Use it for a content strip: synced lyrics, upcoming nav turn, mini
 *   playback controls, score ticker, etc.
 *   Previously named ControlsPanel — renamed as part of Navigation overhaul v1.
 * • [hasState5Content] defaults to false — handlers that have no strip
 *   content get a direct swipe-down to dashboard with no extra work.
 * • [label] is used for logging, a11y, and dashboard tab matching in
 *   FidlandService.tabForActiveHandler(). Keep it short and stable
 *   ("Music", "Call", "Football", etc.).
 * • [blockAffinity] declares whether [Indicator]'s content is pinned to the
 *   right zone (default, unchanged from today) or eligible for §B2's
 *   left/right balancer. See its own doc — currently a no-op for either
 *   value since only one real handler is ever on-screen at a time.
 * • [hasSecondaryBlock] / [SecondaryIndicator] let a handler split its
 *   indicator into two independently-placed §B2 blocks instead of one
 *   fused composable — see their own docs.
 */
interface Phs3Handler {

    /** Short human-readable name for this phs3 module. Used in logs and a11y. */
    val label: String

    /**
     * §B2 — which side [Indicator]'s content is allowed to render on once
     * overlay_fidland_pill.kt builds a [Phs3Block] for it (see
     * Phs3BlockPlacementEngine.kt's WIRING note — that's the caller that
     * turns this declaration + [Indicator]'s measured width into an actual
     * [Phs3Block] fed to [Phs3BlockPlacementEngine.update]).
     *
     * Only [BlockAffinity.RIGHT_ANCHOR] and [BlockAffinity.DYNAMIC] are
     * meaningful here — [BlockAffinity.LEFT_ANCHOR] and
     * [BlockAffinity.NET_SPEED] belong to the location-a row (see
     * [hasLocationA]) and NetSpeedDisplay respectively, neither of which is
     * a [Phs3Handler]. Returning one of those two would be a caller bug;
     * not enforced at compile time since [BlockAffinity] is a single shared
     * enum across all §B2 code rather than a narrower per-context type.
     *
     * Default: [BlockAffinity.RIGHT_ANCHOR] — matches every handler's
     * current behavior (always the fixed right-zone indicator, never
     * balanced). Override to [BlockAffinity.DYNAMIC] only once an entity
     * genuinely wants to be balanced left/right instead of pinned right —
     * meaningful once more than one handler can be on-screen
     * simultaneously (today [Phs3Manager]'s rotation shows exactly one
     * real handler at a time in the right zone, so DYNAMIC vs. RIGHT_ANCHOR
     * is currently a no-op either way; this matters once co-display of
     * multiple real handlers exists — see design doc, Comms' "co-display
     * block-ordering question").
     */
    val blockAffinity: BlockAffinity get() = BlockAffinity.RIGHT_ANCHOR

    /**
     * §B2 — opts a handler into a *second*, independently-placed block
     * alongside [Indicator], instead of one fused composable carrying both
     * an icon and text (or similar) with a single [blockAffinity].
     *
     * Default `false`: [Indicator] alone is this handler's one and only
     * block, exactly as before this flag existed — every handler that
     * doesn't override this is completely unaffected.
     *
     * When `true`:
     * • [Indicator] is the *primary* block, placed per [blockAffinity].
     * • [SecondaryIndicator] is the *secondary* block, placed per
     *   [secondaryBlockAffinity].
     * • `overlay_fidland_pill.kt`'s right-zone rendering renders both
     *   ([Indicator] then [SecondaryIndicator], left-to-right — same visual
     *   order a fused Row would have produced) and feeds both to
     *   [Phs3Manager]'s `blockPlacementEngine` as two real, independently
     *   measured [Phs3Block]s.
     *
     * ⚠ Scope note: this wires the *declaration and measurement* side only
     * — both blocks still always render together in the right zone today.
     * Actually moving a block to the opposite pill zone based on
     * `Phs3BlockPlacementEngine.currentLayout` is a separate, deliberately
     * deferred step (see `overlay_fidland_pill.kt`'s own "§B2 WIRING" note)
     * — rewriting the working zone/animation code for a live cross-zone
     * placement swap is real risk, not a drop-in once blocks exist. This
     * flag gets a handler's blocks correctly declared, sized, and fed to
     * the balancer for real, so that swap has real data to work from
     * whenever it happens — see design doc §B7's Ring Mode entry, the
     * first handler to use this.
     */
    val hasSecondaryBlock: Boolean get() = false

    /**
     * Placement for [SecondaryIndicator]'s [Phs3Block], when
     * [hasSecondaryBlock] is true. Ignored (never read) otherwise. See
     * [hasSecondaryBlock]'s doc for the same caveats as [blockAffinity].
     */
    val secondaryBlockAffinity: BlockAffinity get() = BlockAffinity.RIGHT_ANCHOR

    /**
     * The secondary block's content — only rendered/measured when
     * [hasSecondaryBlock] is true. Keep it as small/self-contained as
     * [Indicator] (see this interface's "IMPLEMENTATION NOTES"): the two
     * render side-by-side in the right zone today (see [hasSecondaryBlock]),
     * so treat this as "the other half of what used to be one fused Row,"
     * not a second, independent widget with its own layout assumptions.
     *
     * Default implementation is empty — override alongside
     * [hasSecondaryBlock].
     */
    @Composable
    fun SecondaryIndicator() {}

    /**
     * §B7 — declares that this handler's [Indicator] should render
     * *additively* alongside whichever other handler currently holds the
     * rotating right-zone slot, rather than displacing it (Camera's/
     * Flashlight's "co-display" mechanic — see design doc §B7 Camera/
     * Flashlight entries).
     *
     * Default `false`: unaffected, exclusive-takeover behavior as today.
     *
     * §B8 #11-13 are resolved (equal-footing width competition, no new
     * `BlockAffinity` tier, Call's exclusive hold suppresses co-display) and
     * the rendering side is now wired — see overlay_fidland_pill.kt's
     * `coDisplayHandlers` and `RightIndicatorContent`/`PillLeftZoneContent`'s
     * co-display params (§B8 #16). A co-displaying handler's [Indicator]
     * renders additively alongside whichever handler currently holds the
     * rotating slot, placed per [coDisplaySide] (if set) or ordinary
     * width-based [BlockAffinity.DYNAMIC] resolution otherwise, in
     * `BOTH_EXPANDED`/`RIGHT_EXPANDED` only — no left zone exists in
     * `RIGHT_EXPANDED` to move into, so co-display content is forced RIGHT
     * there regardless of [coDisplaySide], same treatment as the active
     * handler's own DYNAMIC blocks in that phase.
     */
    val coDisplay: Boolean get() = false

    /**
     * §B8 #12 — for a [coDisplay] handler whose [blockAffinity] is
     * [BlockAffinity.DYNAMIC], lets the handler override the balancer's
     * width-based side resolution with its own rule, instead of a new
     * [BlockAffinity] tier (resolved by explicit choice over adding
     * e.g. a CONDITIONAL_SIDE value — see design doc §B8 #12).
     *
     * Only consulted when this handler is actually co-displaying (i.e.
     * some other handler currently holds the primary slot and this one is
     * rendering additively alongside it) — see overlay_fidland_pill.kt's
     * co-display wiring. Not consulted when this handler is itself the
     * primary/active one; in that case ordinary width-based [DYNAMIC]
     * resolution applies as for any other handler.
     *
     * Default `null`: no override — the co-displaying block competes for
     * the lighter side on width like any other [DYNAMIC] block (§B8 #11,
     * resolved as "equal footing" — Flashlight's case, since its icon is
     * genuinely width-competitive even while co-displaying).
     *
     * Camera overrides this to `BlockSide.LEFT`, implementing its "right if
     * alone, left if co-displayed" rule (§B7) — "right if alone" doesn't
     * need expressing here since it's simply not co-displaying in that
     * case, so this property is never consulted then.
     */
    val coDisplaySide: BlockSide? get() = null

    /**
     * Compact indicator shown in the RIGHT_EXPANDED / BOTH_EXPANDED pill
     * (right zone, locations b and c).
     * Keep it small — the pill auto-sizes to fit, capped at STATE3_MAX_WIDTH.
     */
    @Composable
    fun Indicator()

    /**
     * Returns true if this handler wants to contribute a small icon/widget
     * to the location-a row in the LEFT ZONE when multiple handlers qualify.
     *
     * When true, [LocationAContent] will be called to render the slot.
     * Default: false.
     */
    val hasLocationA: Boolean get() = false

    /**
     * Location-a ordering.
     * Lower value = rendered earlier.
     *
     * Music should use 0.
     * Everything else can keep default 100.
     */
    val locationAPriority: Int
        get() = 100

    /**
     * Content rendered inside a fixed LOCATION_A_SLOT_SIZE square slot in
     * the location-a row (LEFT ZONE, left of NetSpeedDisplay).
     *
     * Only called when [hasLocationA] is true and this handler is in the
     * qualified list. Keep it compact — icon, spinner, or 1-2 char badge.
     *
     * Default implementation is empty — override alongside [hasLocationA].
     */
    @Composable
    fun LocationAContent() {}

    /**
     * Returns true if this handler has content to show in State 5 (the
     * content strip). FidlandService calls this on swipe-down from compact
     * states to decide whether to open STATE5 or go straight to DASHBOARD.
     *
     * Default: false — handlers with no strip content skip State 5 entirely.
     * Override to true in handlers that implement [State5Content].
     */
    fun hasState5Content(): Boolean = true

    /**
     * Optional per-handler override for the STATE5 strip height.
     *
     * [IslandConfig.STATE5_HEIGHT] is a single value shared by every phs3
     * module. Most handlers fit fine within it, but a handler whose
     * [State5Content] genuinely needs more (or less) room — e.g. an extra
     * row of larger tap targets — can return its own height here instead
     * of everyone else having to grow to match.
     *
     * Return null (the default) to use the shared [IslandConfig.STATE5_HEIGHT].
     */
    val state5HeightOverride: Dp? get() = null

    /**
     * Content strip shown in STATE5.
     * Canvas size: IslandConfig.STATE4_MAX_WIDTH × IslandConfig.STATE5_HEIGHT
     * (or [state5HeightOverride] when set).
     * Rendered inside a fillMaxSize Box.
     *
     * Only called when [hasState5Content] returns true.
     * Default implementation is empty — override alongside [hasState5Content].
     *
     * Previously named ControlsPanel — renamed as part of Navigation overhaul v1.
     *
     * Examples: synced lyrics row, upcoming navigation turn, live score ticker,
     * recording waveform, active call duration + mute/end buttons.
     */
    @Composable
    fun State5Content() {}
}