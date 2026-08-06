package com.example.fiddler.subapps.Fidland.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fiddler.subapps.Fidland.phs2.NetSpeedDisplay
import com.example.fiddler.subapps.Fidland.phs3.music.AlbumArtSpinner
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler
import com.example.fiddler.subapps.Fidland.phs3.BlockAffinity
import com.example.fiddler.subapps.Fidland.phs3.Phs3Block
import com.example.fiddler.subapps.Fidland.phs3.Phs3BlockPlacementEngine
import com.example.fiddler.subapps.Fidland.phs3.Phs3Layout
import com.example.fiddler.subapps.Fidland.phs3.BlockSide
import com.example.fiddler.subapps.Fidland.phs3.Phs3Priority
import com.example.fiddler.subapps.Fidland.phs3.PriorityClass
import com.example.fiddler.subapps.Fidland.phs3.download.DownloadPhs3Handler
import com.example.fiddler.subapps.Fidland.music.MusicAppsRepository

/**
 * Fixed slot widths for left-zone (location a) components.
 *
 * The left zone is measured via onSizeChanged and fed into the pill's
 * animateDpAsState width. Without fixed widths every live update (net speed
 * refreshes every second; download network icon may change) would produce a
 * different measured width → the pill shrinks and grows visibly.
 *
 * NET_SPEED_DISPLAY_WIDTH  — reserved for NetSpeedDisplay.
 *   = (STATE2_WIDTH - BASE_SIZE) / 2 = (110 - 28) / 2 = 41.dp
 *   Keeps the plain State 2 pill width identical to the old design.
 *
 * LOCATION_A_WIDTH  — reserved for the L1 slot (location a) when a phs3
 *   handler places content there. Currently used by DownloadPhs3Handler
 *   (network icon) and Music (album art spinner). Sized for the larger of
 *   the two: MUSIC_ALBUM_ART_SIZE = 22.dp; download icon = 16.dp → use 22.dp.
 *   Gap between L1 content and NetSpeedDisplay is LOCATION_A_GAP.
 *
 * Only ONE of the L1 slot occupants is shown at a time. Priority:
 *   Download network icon  (handler is DownloadPhs3Handler)
 *   Music album art        (music is playing, no download active)
 *   [nothing]              (plain net speed only)
 */
private val NET_SPEED_DISPLAY_WIDTH: Dp = 41.dp



/**
 * Structural phases of the Fidland island.
 *
 * CIRCLE          State 1 — compact circle, no content.
 * LEFT_EXPANDED   State 2 — left zone only (net speed + location a).
 * RIGHT_EXPANDED  State 3 — right zone only (phs3 indicator, locations b + c).
 * BOTH_EXPANDED   State 2+3 — both zones active simultaneously (common case).
 * DASHBOARD       State 4 — full pull-down dashboard with tabs.
 * STATE5          State 5 — slim content strip between compact pill and dashboard.
 *                           Entry: swipe-down from States 1-2-3 (if handler has
 *                           strip content). Exit: swipe-down → DASHBOARD,
 *                           swipe-up → compact states.
 */
enum class PillPhase {
    CIRCLE,
    LEFT_EXPANDED,
    RIGHT_EXPANDED,
    BOTH_EXPANDED,
    DASHBOARD,
    STATE5
}

enum class RightIndicator {
    EQUALIZER, NOTIFICATIONS, CHARGING, CALL, RECORDER, HOTSPOT
}

/**
 * The Fidland island — always a single rounded rectangle.
 *
 * ═══════════════════════════════════════════════════════════════
 * TRUTH CENTER MODEL
 * ───────────────────
 * The WM view is STATE4_MAX_WIDTH wide, positioned so its horizontal
 * center aligns with the hole-punch camera. The view never moves; only
 * pill width animates, growing left and right symmetrically.
 *
 * Inside every compact pill state the layout is always:
 *
 *   [ LEFT ZONE ] [ hole spacer (BASE_SIZE) ] [ RIGHT ZONE ]
 *
 * ═══════════════════════════════════════════════════════════════
 * CONTENT ZONES
 * ──────────────
 * Location a  (LEFT ZONE, left of net speed):
 *   Download active → network type icon (📶 / 3G / 4G / 5G)
 *   Music playing   → AlbumArtSpinner
 *   Neither         → empty; net speed fills the arm
 *
 * Location b  (RIGHT ZONE, immediate right of hole):
 *   Phs3 handler primary indicator (ETA text, call duration, etc.)
 *
 * Location c  (RIGHT ZONE, right of b):
 *   Phs3 handler secondary indicator (progress ring, icon, etc.)
 *
 * ═══════════════════════════════════════════════════════════════
 * PILL WIDTHS
 * ────────────
 * State 1      → BASE_SIZE (circle)
 * State 2      → STATE2_WIDTH (fixed, symmetric)
 * State 3      → BASE_SIZE + measured right content + CONTENT_PADDING_HORIZONTAL * 2
 * State 2+3    → left arm (fixed) + BASE_SIZE + right arm (measured)
 * State 4 / 5  → STATE4_MAX_WIDTH (fixed)
 *
 * ═══════════════════════════════════════════════════════════════
 * §B2 WIRING (this pass) — plumbing + secondary blocks, still not
 * cross-zone rendering
 * ─────────────────────────────────────────────────────────────
 * [blockPlacementEngine], if supplied, is fed real measured widths (see
 * the LaunchedEffect below [FidlandIsland]'s zone rendering) every time
 * they change, so [Phs3BlockPlacementEngine.update] is genuinely live
 * end-to-end. As of this pass, a handler can also declare
 * [Phs3Handler.hasSecondaryBlock] to split into two real, independently
 * measured [Phs3Block]s instead of one — [RightIndicatorContent] renders
 * [Phs3Handler.Indicator] and [Phs3Handler.SecondaryIndicator] as two
 * separately-`onSizeChanged`-measured composables in that case, feeding
 * `measuredPrimaryBlockWidth` / `measuredSecondaryBlockWidth` below instead
 * of the one combined [RightZone] width. Ring Mode is the first to use this
 * (icon = [BlockAffinity.DYNAMIC], text = [BlockAffinity.RIGHT_ANCHOR]).
 *
 * As of a later pass, cross-zone placement IS consumed by rendering for
 * `BOTH_EXPANDED` (the only two-arm phase) — see "§B2 cross-zone placement
 * resolution" below. `RIGHT_EXPANDED` deliberately still always renders
 * RIGHT regardless of resolution, since that phase has no left zone to move
 * content into.
 *
 * ── §B8 #16 — co-display rendering ────────────────────────────────────────
 * A qualified handler with [Phs3Handler.coDisplay] == true (Camera,
 * Flashlight) now renders its [Phs3Handler.Indicator] additively alongside
 * whichever handler currently holds the rotating slot, instead of being
 * limited to displacing it — see [coDisplayHandlers] below. Each co-display
 * handler contributes its own [BlockAffinity.DYNAMIC] [Phs3Block] (forced to
 * [Phs3Handler.coDisplaySide] when set, e.g. Camera's LEFT rule; otherwise
 * ordinary width-based resolution, e.g. Flashlight) to the same balancer
 * pass as the active handler's own block(s), and renders in
 * [PillLeftZoneContent] or [RightIndicatorContent] depending on which side
 * it resolves to — forced RIGHT in `RIGHT_EXPANDED` for the same
 * no-left-zone reason as the active handler's own DYNAMIC blocks there.
 * Per §B8 #13, co-display is suppressed entirely while Call's exclusive
 * indefinite-hold Special Condition holds the slot — see
 * `callExclusiveHoldActive` below, gated on [activeSchedulerPriority].
 */
@Composable
fun FidlandIsland(
    phase: PillPhase,
    activeIndicators: List<RightIndicator> = emptyList(),
    currentIndicator: Int = 0,
    dashboardContent: @Composable (BoxScope.() -> Unit)? = null,
    activePhs3Handler: Phs3Handler? = null,
    qualifiedHandlers: List<Phs3Handler> = emptyList(),
    isRotationLocked: Boolean = false,
    onPhs3LongPress: () -> Unit = {},
    /**
     * Optional §B2 sink for real measured block widths — pass
     * `phs3Manager.blockPlacementEngine`. Null (default) skips the
     * LaunchedEffect below entirely; existing callers are unaffected.
     */
    blockPlacementEngine: Phs3BlockPlacementEngine? = null,
    /**
     * §B8 #13/#16 — pass `phs3Manager.scheduler.activePriority` so co-display
     * rendering can tell when Call's exclusive indefinite-hold Special
     * Condition holds the slot and suppress co-display entirely while it
     * does (see [coDisplayHandlers] below). Null (default) — no bid known,
     * treated the same as "not Call's exclusive hold," so co-display
     * proceeds normally; matches every existing caller until it's wired
     * through FidlandRootUI/FidlandService.
     */
    activeSchedulerPriority: Phs3Priority? = null,
    /**
     * Whether the net-speed display is currently on (settings toggle,
     * `prefs.getBoolean("net_speed", ...)` at the FidlandService call
     * site). Gates the NET_SPEED block below — per §B7's NetSpeed spec it's
     * "shown continuously/unconditionally when on," which also means never
     * present when off. Default false matches existing callers that don't
     * pass it (no NET_SPEED block contributed, same as before this param
     * existed).
     */
    netEnabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    var measuredRightContentWidth by remember { mutableStateOf(0.dp) }
    var measuredLeftContentWidth  by remember { mutableStateOf(0.dp) }

    // ── §B2 secondary-block measurement ─────────────────────────────────────
    // Only populated for a handler with [Phs3Handler.hasSecondaryBlock] —
    // see [RightIndicatorContent]. Both default to 0.dp and are simply
    // unused (never fed to the engine) for every other handler, same as
    // today. [measuredRightContentWidth] above still separately measures
    // the *combined* right-zone content for pill-width animation — sizing
    // behavior is unchanged; these two are only for feeding the balancer
    // two real per-block widths instead of one combined one.
    var measuredPrimaryBlockWidth   by remember { mutableStateOf(0.dp) }
    var measuredSecondaryBlockWidth by remember { mutableStateOf(0.dp) }

    // ── §B2 cross-zone placement ──────────────────────────────────────────
    // [Phs3BlockPlacementEngine.currentLayout] is a plain (non-Compose-state)
    // getter, so reading it directly wouldn't trigger recomposition when a
    // DYNAMIC block's resolved side changes. This mirrors it into real
    // Compose state, updated right after every [Phs3BlockPlacementEngine.update]
    // call below (synchronous, same LaunchedEffect body — no extra latency).
    // Null until the first [update] call publishes a layout; every render
    // site below treats null the same as "resolved RIGHT", i.e. today's
    // pre-cross-zone-rendering behavior, so there's no flash-of-wrong-zone
    // before the first layout exists.
    var currentLayout by remember { mutableStateOf<Phs3Layout?>(null) }

    // ── §B8 #13/#16 — co-display handler set ────────────────────────────────
    // Every *other* qualified handler that opted into [Phs3Handler.coDisplay]
    // — i.e. not the one currently holding the rotating slot, since that
    // handler's own block(s) already render via the primary/secondary path
    // above, not this one. Per §B8 #13, suppressed entirely while Call's
    // exclusive indefinite-hold Special Condition holds the slot: checked
    // against [activeSchedulerPriority] directly rather than
    // `activePhs3Handler.label == "Call"`, since the scheduler bid — not
    // just "which handler is showing" — is the actual source of truth for
    // "is this an exclusive hold right now" (a plain CONTINUOUS/DOMINANT
    // Call bid, if that ever exists, would not suppress co-display).
    val callExclusiveHoldActive = activeSchedulerPriority?.let { bid ->
        bid.handler.label == "Call" &&
                bid.priorityClass == PriorityClass.SPECIAL_CONDITION &&
                bid.holdMs == null
    } ?: false

    val coDisplayHandlers: List<Phs3Handler> = if (callExclusiveHoldActive) {
        emptyList()
    } else {
        qualifiedHandlers.filter { it.coDisplay && it.label != activePhs3Handler?.label }
    }

    // Per-co-display-handler measured [Indicator] width, keyed by label.
    // [coDisplayWidthsVersion] exists purely to give the LaunchedEffect below
    // a primitive key that changes on every map mutation — reading a
    // SnapshotStateMap's entries from inside a suspend LaunchedEffect body
    // isn't itself recomposition-tracked the way a @Composable read is, so a
    // plain version counter is the simplest reliable trigger (same role
    // [measuredPrimaryBlockWidth]/[measuredSecondaryBlockWidth] play for the
    // single-handler case above).
    val coDisplayMeasuredWidths = remember { mutableStateMapOf<String, Dp>() }
    var coDisplayWidthsVersion by remember { mutableStateOf(0) }
    fun onCoDisplayWidthMeasured(label: String, width: Dp) {
        if (coDisplayMeasuredWidths[label] != width) {
            coDisplayMeasuredWidths[label] = width
            coDisplayWidthsVersion++
        }
    }

    // ── §B2 block identity — shared by the LaunchedEffect below (building the
    // block list) and the placement-resolution section after it (reading the
    // result back out). Computed once per recomposition so both stay in sync.
    val handlerHasSecondary = activePhs3Handler?.hasSecondaryBlock == true
    val primaryBlockId = activePhs3Handler?.let { h ->
        if (h.hasSecondaryBlock) "${h.label}_primary" else h.label
    }
    val secondaryBlockId = activePhs3Handler
        ?.takeIf { it.hasSecondaryBlock }
        ?.let { "${it.label}_secondary" }

    // ── §B2 plumbing — feeds the placement engine, does not affect rendering ──
    // See class doc "§B2 WIRING (this pass)" above. Rebuilds the best-effort
    // current block set on every change to the values it depends on and lets
    // Phs3BlockPlacementEngine.update's own diffing (see its class doc)
    // decide whether that's actually a change worth publishing. No-op if
    // [blockPlacementEngine] wasn't supplied.
    //
    // Known gap: [measuredLeftContentWidth] measures PillLeftZoneContent's
    // *combined* location-a-row + NetSpeedDisplay width, not the two
    // separately, so the LEFT_ANCHOR block below approximates the row's
    // share via IslandConfig.locationARowWidth(count) rather than reusing
    // this measurement directly, and NET_SPEED (when [netEnabled]) uses
    // NET_SPEED_DISPLAY_WIDTH rather than a live measurement. Both are
    // already fixed-width constants elsewhere in this file, so this
    // doesn't regress accuracy — it just doesn't yet independently verify
    // them via measurement.
    //
    // [measuredPrimaryBlockWidth]/[measuredSecondaryBlockWidth] now back
    // *every* handler's block width fed to the balancer, not just
    // hasSecondaryBlock handlers — a single DYNAMIC block (e.g. Flashlight)
    // needs its own dedicated measurement too, since [measuredRightContentWidth]
    // (the combined right-zone width) goes to zero once the balancer moves
    // that block to the left zone and it stops rendering in the right zone
    // at all. [measuredRightContentWidth] still separately drives pill-width
    // animation for RIGHT_EXPANDED/BOTH_EXPANDED — unaffected.
    LaunchedEffect(
        blockPlacementEngine,
        activePhs3Handler,
        qualifiedHandlers,
        measuredPrimaryBlockWidth,
        measuredSecondaryBlockWidth,
        netEnabled,
        coDisplayHandlers,
        coDisplayWidthsVersion,
    ) {
        val engine = blockPlacementEngine ?: return@LaunchedEffect

        val locationACount = qualifiedHandlers.count { it.hasLocationA }
        val blocks = buildList {
            if (locationACount > 0) {
                add(
                    Phs3Block(
                        id = "locationARow",
                        affinity = BlockAffinity.LEFT_ANCHOR,
                        width = IslandConfig.locationARowWidth(locationACount),
                    )
                )
            }
            if (netEnabled) {
                add(
                    Phs3Block(
                        id = "netSpeed",
                        affinity = BlockAffinity.NET_SPEED,
                        width = NET_SPEED_DISPLAY_WIDTH,
                    )
                )
            }
            activePhs3Handler?.let { handler ->
                add(
                    Phs3Block(
                        id = primaryBlockId!!,
                        affinity = handler.blockAffinity,
                        width = measuredPrimaryBlockWidth,
                    )
                )
                if (handler.hasSecondaryBlock) {
                    // Two real, independently-measured blocks — see
                    // [Phs3Handler.hasSecondaryBlock]'s doc and
                    // [RightIndicatorContent]'s measurement wiring below.
                    add(
                        Phs3Block(
                            id = secondaryBlockId!!,
                            affinity = handler.secondaryBlockAffinity,
                            width = measuredSecondaryBlockWidth,
                        )
                    )
                }
            }
            // §B8 #16 — one DYNAMIC block per co-display handler, forced to
            // its declared [Phs3Handler.coDisplaySide] when set (Camera) or
            // left to ordinary width-based resolution otherwise (Flashlight
            // — see §B8 #11, "equal footing"). Added after the active
            // handler's own block(s) so a co-display icon never displaces
            // the active handler's placement/stability history for its id.
            coDisplayHandlers.forEach { handler ->
                add(
                    Phs3Block(
                        id = "${handler.label}_codisplay",
                        affinity = BlockAffinity.DYNAMIC,
                        width = coDisplayMeasuredWidths[handler.label] ?: 0.dp,
                        forcedSide = handler.coDisplaySide,
                    )
                )
            }
        }
        engine.update(blocks)
        currentLayout = engine.currentLayout
    }

    // ── §B2 cross-zone placement resolution ─────────────────────────────────
    // Only [BlockAffinity.DYNAMIC] blocks are ever eligible to move — the
    // balancer never places LEFT_ANCHOR/NET_SPEED/RIGHT_ANCHOR anywhere but
    // their fixed side (see [Phs3BlockBalancer.place]), so those affinities
    // always resolve RIGHT here (matching their already-correct fixed
    // render position; the location-a row and NetSpeed are left-anchored
    // independently of this resolution, see [PillLeftZoneContent]). A
    // handler whose block hasn't been placed yet (no [currentLayout] published,
    // e.g. before the first [engine.update]) also resolves RIGHT — exactly
    // today's pre-cross-zone-rendering behavior, so there's nothing to
    // migrate for handlers that never opted into DYNAMIC.
    fun resolvedSide(blockId: String?, affinity: BlockAffinity?): BlockSide {
        if (blockId == null || affinity != BlockAffinity.DYNAMIC) return BlockSide.RIGHT
        val layout = currentLayout ?: return BlockSide.RIGHT
        return if (layout.leftDynamic.any { it.id == blockId }) BlockSide.LEFT else BlockSide.RIGHT
    }

    val primarySide: BlockSide = resolvedSide(primaryBlockId, activePhs3Handler?.blockAffinity)
    val secondarySide: BlockSide = if (handlerHasSecondary) {
        resolvedSide(secondaryBlockId, activePhs3Handler?.secondaryBlockAffinity)
    } else {
        BlockSide.RIGHT
    }

    // Same resolution as [resolvedSide] above, keyed to a co-display
    // handler's own block id. Defaults to RIGHT before the first
    // [engine.update] publishes a layout, same "no flash of wrong zone"
    // reasoning as the primary/secondary case.
    fun resolvedCoDisplaySide(handler: Phs3Handler): BlockSide =
        resolvedSide("${handler.label}_codisplay", BlockAffinity.DYNAMIC)

    // ── Target pill width ─────────────────────────────────────────────────
    val targetWidth: Dp = when (phase) {
        PillPhase.CIRCLE -> IslandConfig.BASE_SIZE

        PillPhase.LEFT_EXPANDED -> IslandConfig.clampWidth(
            maxOf(
                IslandConfig.STATE2_WIDTH,
                IslandConfig.BASE_SIZE + measuredLeftContentWidth * 2
            )
        )

        PillPhase.RIGHT_EXPANDED -> IslandConfig.clampWidth(
            IslandConfig.BASE_SIZE
                    + IslandConfig.CONTENT_PADDING_HORIZONTAL * 2
                    + measuredRightContentWidth
        )

        PillPhase.BOTH_EXPANDED -> run {
            val leftHalf  = maxOf(IslandConfig.STATE2_LEFT_ARM, measuredLeftContentWidth)
            val rightHalf = IslandConfig.CONTENT_PADDING_HORIZONTAL + measuredRightContentWidth
            IslandConfig.clampWidth(maxOf(leftHalf, rightHalf) * 2 + IslandConfig.BASE_SIZE)
        }

        PillPhase.DASHBOARD,
        PillPhase.STATE5 -> IslandConfig.STATE4_MAX_WIDTH
    }

    val targetHeight: Dp = when (phase) {
        PillPhase.DASHBOARD -> IslandConfig.STATE4_HEIGHT
        PillPhase.STATE5    -> activePhs3Handler?.state5HeightOverride ?: IslandConfig.STATE5_HEIGHT
        else                -> IslandConfig.BASE_SIZE
    }

    // ── Asymmetric spring — expanding feels lighter/bouncier than collapsing ──
    val prevWidth  = remember { mutableStateOf(targetWidth) }
    val prevHeight = remember { mutableStateOf(targetHeight) }

    val widthSpec = if (targetWidth >= prevWidth.value) {
        spring<Dp>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    } else {
        spring<Dp>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    }
    val heightSpec = if (targetHeight >= prevHeight.value) {
        // Dashboard opening: weighty, no bounce
        spring<Dp>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
    } else {
        spring<Dp>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    }

    val animatedWidth  by animateDpAsState(targetWidth,  widthSpec,  label = "island_w")
    val animatedHeight by animateDpAsState(targetHeight, heightSpec, label = "island_h")

    SideEffect {
        prevWidth.value  = targetWidth
        prevHeight.value = targetHeight
    }

    // ── Corner radius — animates from pill-round to card-round on dashboard open ──
    val targetCornerRadius: Dp = when (phase) {
        PillPhase.DASHBOARD -> 14.dp
        PillPhase.STATE5    -> 18.dp
        else                -> IslandConfig.CORNER_RADIUS
    }
    val animatedCornerRadius by animateDpAsState(
        targetValue   = targetCornerRadius,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label         = "corner_r"
    )

    // ── Lock tint — subtle warm-grey shift when rotation is locked ────────────
    val pillBackground by animateColorAsState(
        targetValue   = if (isRotationLocked) Color(0xFF100F0E) else Color.Black,
        animationSpec = tween(durationMillis = 300),
        label         = "pill_bg"
    )

    // ── Outer container ───────────────────────────────────────────────────
    // Fixed at STATE4_MAX_WIDTH, centred on truth_x (the hole-punch centre).
    // [modifier] carries graphicsLayer transforms (shake translationX,
    // slide translationY) injected by FidlandRootUI.
    Box(
        modifier         = modifier
            .width(IslandConfig.STATE4_MAX_WIDTH)
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Box(
                modifier = Modifier
                    .width(animatedWidth)
                    .height(animatedHeight)
                    .clip(RoundedCornerShape(animatedCornerRadius))
                    .background(pillBackground, RoundedCornerShape(animatedCornerRadius))
            ) {
                when (phase) {

                    // ── State 1 — shape only ──────────────────────────────────
                    PillPhase.CIRCLE -> Unit

                    // ── State 2 — left zone only ──────────────────────────────
                    PillPhase.LEFT_EXPANDED -> {
                        PillRow {
                            LeftZone(onWidthMeasured = { measuredLeftContentWidth = it }) {
                                PillLeftZoneContent(
                                    activePhs3Handler = activePhs3Handler,
                                    qualifiedHandlers = qualifiedHandlers
                                )
                            }
                            HoleSpacer()
                            RightZone { /* empty */ }
                        }
                    }

                    // ── State 3 — right zone only ─────────────────────────────
                    // No left zone exists in this phase (see PillPhase table —
                    // net speed off, no location-a row rendered here either),
                    // and its width model is a single asymmetric arm, not the
                    // symmetric max(leftWidth, rightWidth) model the balancer
                    // assumes (see Phs3block.kt's class doc, "BACKGROUND").
                    // So cross-zone placement is intentionally not applied
                    // here — always RIGHT, same as before this feature existed
                    // — even if the balancer's last computation (run for
                    // BOTH_EXPANDED, or stale from before a phase change)
                    // resolved a block LEFT; there's nowhere left to render it.
                    PillPhase.RIGHT_EXPANDED -> {
                        PillRow(longPressHandler = activePhs3Handler, onLongPress = onPhs3LongPress) {
                            LeftZone { /* empty */ }
                            HoleSpacer()
                            RightZone(onWidthMeasured = { measuredRightContentWidth = it }) {
                                AnimatedContent(
                                    targetState    = activePhs3Handler,
                                    transitionSpec = {
                                        val delay = if (initialState == null) 80 else 0
                                        fadeIn(tween(120, delayMillis = delay)) togetherWith fadeOut(tween(80))
                                    },
                                    label = "right_zone_content"
                                ) { handler ->
                                    RightIndicatorContent(
                                        handler,
                                        activeIndicators,
                                        currentIndicator,
                                        primarySide = BlockSide.RIGHT,
                                        secondarySide = BlockSide.RIGHT,
                                        onPrimaryWidthMeasured   = { measuredPrimaryBlockWidth = it },
                                        onSecondaryWidthMeasured = { measuredSecondaryBlockWidth = it },
                                        // No left zone in this phase (see
                                        // class doc) — co-display is forced
                                        // RIGHT regardless of resolution,
                                        // same treatment as the active
                                        // handler's own DYNAMIC blocks here.
                                        coDisplayHandlers = coDisplayHandlers,
                                        coDisplayResolvedSide = { BlockSide.RIGHT },
                                        onCoDisplayWidthMeasured = ::onCoDisplayWidthMeasured,
                                    )
                                }
                            }
                        }
                    }

                    // ── State 2+3 — both zones ────────────────────────────────
                    //
                    // The only phase with a real two-arm symmetric width model
                    // (see Phs3block.kt's class doc, "BACKGROUND") — the one
                    // place cross-zone placement is actually applied. A
                    // BlockAffinity.DYNAMIC piece the balancer resolved to
                    // BlockSide.LEFT renders via PillLeftZoneContent's dynamic
                    // slot instead of RightIndicatorContent; everything else
                    // (RIGHT_ANCHOR pieces, and any DYNAMIC piece still
                    // resolved RIGHT) renders exactly as before.
                    //
                    // LEFT ZONE  → PillLeftZoneContent:
                    //   Download active            →  [📶 location a]  [↓ net speed]
                    //   Music playing               →  [🎵 album art]   [↓ net speed]
                    //   Neither, DYNAMIC left       →  [dynamic block]  [↓ net speed]
                    //   Neither, nothing dynamic    →  [↓ net speed] only
                    //
                    // RIGHT ZONE → phs3 handler Indicator()/SecondaryIndicator():
                    //   Download active             →  [ETA  ◯%]   (b + c)
                    //   Other handler                →  whatever's still resolved RIGHT
                    //
                    PillPhase.BOTH_EXPANDED -> {
                        PillRow(longPressHandler = activePhs3Handler, onLongPress = onPhs3LongPress) {
                            LeftZone(onWidthMeasured = { measuredLeftContentWidth = it }) {
                                PillLeftZoneContent(
                                    activePhs3Handler = activePhs3Handler,
                                    qualifiedHandlers = qualifiedHandlers,
                                    primarySide = primarySide,
                                    secondarySide = secondarySide,
                                    onPrimaryWidthMeasured   = { measuredPrimaryBlockWidth = it },
                                    onSecondaryWidthMeasured = { measuredSecondaryBlockWidth = it },
                                    coDisplayHandlers = coDisplayHandlers,
                                    coDisplayResolvedSide = ::resolvedCoDisplaySide,
                                    onCoDisplayWidthMeasured = ::onCoDisplayWidthMeasured,
                                )
                            }
                            HoleSpacer()
                            RightZone(onWidthMeasured = { measuredRightContentWidth = it }) {
                                AnimatedContent(
                                    targetState    = activePhs3Handler,
                                    transitionSpec = {
                                        val delay = if (initialState == null) 80 else 0
                                        fadeIn(tween(120, delayMillis = delay)) togetherWith fadeOut(tween(80))
                                    },
                                    label = "right_zone_content_both"
                                ) { handler ->
                                    RightIndicatorContent(
                                        handler,
                                        activeIndicators,
                                        currentIndicator,
                                        primarySide = primarySide,
                                        secondarySide = secondarySide,
                                        onPrimaryWidthMeasured   = { measuredPrimaryBlockWidth = it },
                                        onSecondaryWidthMeasured = { measuredSecondaryBlockWidth = it },
                                        coDisplayHandlers = coDisplayHandlers,
                                        coDisplayResolvedSide = ::resolvedCoDisplaySide,
                                        onCoDisplayWidthMeasured = ::onCoDisplayWidthMeasured,
                                    )
                                }
                            }
                        }
                    }

                    // ── State 4 — full dashboard ──────────────────────────────
                    PillPhase.DASHBOARD -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            content  = dashboardContent ?: {}
                        )
                    }

                    // ── State 5 — content strip ───────────────────────────────
                    // Shows the active phs3 handler's strip content: synced
                    // lyrics, upcoming nav turn, live score, recording wave, etc.
                    // Canvas: STATE4_MAX_WIDTH × STATE5_HEIGHT.
                    // Entry: swipe-down from compact states (if hasState5Content).
                    // Exit:  swipe-down → DASHBOARD, swipe-up → compact states.
                    PillPhase.STATE5 -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            activePhs3Handler?.State5Content()
                        }
                    }
                }
            }
        }
    }
}

// ── Layout helpers ────────────────────────────────────────────────────────────

/**
 * The outer Row shared by all non-dashboard pill states.
 * Wires an optional long-press when a phs3 handler is active.
 */
@Composable
private fun PillRow(
    longPressHandler: Phs3Handler? = null,
    onLongPress: () -> Unit = {},
    content: @Composable RowScope.() -> Unit
) {
    val longPressMod = if (longPressHandler != null) {
        Modifier.pointerInput(longPressHandler) {
            detectTapGestures(onLongPress = { onLongPress() })
        }
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxSize()
            .then(longPressMod)
            .padding(
                horizontal = IslandConfig.CONTENT_PADDING_HORIZONTAL,
                vertical   = IslandConfig.CONTENT_PADDING_VERTICAL
            ),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        content = content
    )
}

/**
 * Left zone — wraps content at its intrinsic size.
 * Reports its width via [onWidthMeasured] so the pill can grow to fit.
 */
@Composable
private fun RowScope.LeftZone(
    onWidthMeasured: ((Dp) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val sizeMod = if (onWidthMeasured != null) {
        Modifier.onSizeChanged { size ->
            onWidthMeasured(with(density) { size.width.toDp() })
        }
    } else Modifier

    Box(
        modifier = Modifier
            .wrapContentSize()
            .heightIn(max = IslandConfig.BASE_SIZE - IslandConfig.CONTENT_PADDING_VERTICAL * 2)
            .then(sizeMod),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** Camera hole spacer — always BASE_SIZE × BASE_SIZE, sits at truth_x. */
@Composable
private fun HoleSpacer() {
    Spacer(modifier = Modifier.size(IslandConfig.BASE_SIZE))
}

/**
 * Right zone — measures intrinsic content width (unbounded) so the pill
 * tracks the true content size on the first frame.
 */
@Composable
private fun RowScope.RightZone(
    onWidthMeasured: ((Dp) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val sizeMod = if (onWidthMeasured != null) {
        Modifier.onSizeChanged { size ->
            onWidthMeasured(with(density) { size.width.toDp() })
        }
    } else Modifier

    Box(
        modifier = Modifier
            .wrapContentWidth(unbounded = true)
            .heightIn(max = IslandConfig.BASE_SIZE - IslandConfig.CONTENT_PADDING_VERTICAL * 2)
            .then(sizeMod),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// ── Content composables ───────────────────────────────────────────────────────

/**
 * Wraps [content] in a Box that reports its own intrinsic width via
 * [onWidthMeasured] on every size change — the shared per-block measurement
 * primitive used by both [RightIndicatorContent] and the left-zone dynamic
 * slot, so a handler's block is measured identically no matter which zone
 * it ends up rendering in.
 */
@Composable
private fun MeasuredSlot(onWidthMeasured: (Dp) -> Unit, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier.onSizeChanged { size ->
            onWidthMeasured(with(density) { size.width.toDp() })
        }
    ) {
        content()
    }
}

/**
 * Renders the active phs3 handler's indicator(s) in the RIGHT ZONE
 * (locations b and c) — only the pieces currently resolved to
 * [BlockSide.RIGHT]. A [BlockAffinity.DYNAMIC] piece resolved to
 * [BlockSide.LEFT] is rendered by [LeftZoneDynamicContent] instead; see
 * [FidlandIsland]'s "§B2 cross-zone placement resolution" section for how
 * [primarySide]/[secondarySide] get resolved, and its call sites for why
 * [PillPhase.RIGHT_EXPANDED] always passes RIGHT regardless (no left zone
 * exists in that phase to move into — see that call site's comment).
 *
 * Two paths:
 * • [Phs3Handler.hasSecondaryBlock] == false (default, most handlers):
 *   renders [Phs3Handler.Indicator] alone when [primarySide] == RIGHT.
 * • `hasSecondaryBlock == true`: renders [Phs3Handler.Indicator] (primary)
 *   then [Phs3Handler.SecondaryIndicator] (secondary), left-to-right — same
 *   visual order the old fused Row would have produced — each gated on its
 *   own resolved side, each reporting its *own* width via
 *   [onPrimaryWidthMeasured] / [onSecondaryWidthMeasured] so the caller
 *   feeds two real, independently-measured [Phs3Block]s to the balancer.
 */
@Composable
private fun RightIndicatorContent(
    activePhs3Handler: Phs3Handler?,
    activeIndicators: List<RightIndicator>,
    currentIndicator: Int,
    primarySide: BlockSide = BlockSide.RIGHT,
    secondarySide: BlockSide = BlockSide.RIGHT,
    onPrimaryWidthMeasured: (Dp) -> Unit = {},
    onSecondaryWidthMeasured: (Dp) -> Unit = {},
    /**
     * §B8 #16 — qualified handlers co-displaying alongside
     * [activePhs3Handler] (never including it — see [FidlandIsland]'s
     * `coDisplayHandlers`). Only the ones [coDisplayResolvedSide] resolves
     * to [BlockSide.RIGHT] render here; the rest render in
     * [PillLeftZoneContent] instead.
     */
    coDisplayHandlers: List<Phs3Handler> = emptyList(),
    coDisplayResolvedSide: (Phs3Handler) -> BlockSide = { BlockSide.RIGHT },
    onCoDisplayWidthMeasured: (String, Dp) -> Unit = { _, _ -> },
) {
    val rightCoDisplay = coDisplayHandlers.filter { coDisplayResolvedSide(it) == BlockSide.RIGHT }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (activePhs3Handler != null) {
            if (activePhs3Handler.hasSecondaryBlock) {
                if (primarySide == BlockSide.RIGHT) {
                    MeasuredSlot(onPrimaryWidthMeasured) { activePhs3Handler.Indicator() }
                }
                if (secondarySide == BlockSide.RIGHT) {
                    MeasuredSlot(onSecondaryWidthMeasured) { activePhs3Handler.SecondaryIndicator() }
                }
            } else if (primarySide == BlockSide.RIGHT) {
                MeasuredSlot(onPrimaryWidthMeasured) { activePhs3Handler.Indicator() }
            }
        }

        // ── §B8 #16 — co-display icons, additive alongside whatever's
        // above, never displacing it. Gap only applied when there's
        // something to separate from (an empty Row here costs nothing).
        if (rightCoDisplay.isNotEmpty()) {
            Spacer(Modifier.width(IslandConfig.DYNAMIC_BLOCK_GAP))
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IslandConfig.DYNAMIC_BLOCK_GAP)
            ) {
                rightCoDisplay.forEach { handler ->
                    MeasuredSlot(onWidthMeasured = { onCoDisplayWidthMeasured(handler.label, it) }) {
                        handler.Indicator()
                    }
                }
            }
        }
    }

    if (activeIndicators.isEmpty()) return
    // Future non-phs3 indicators:
    // NOTIFICATIONS -> NotificationIconStack()
    // CHARGING      -> ChargingIcon()
    // RECORDER      -> GlowingRedDot()
    // HOTSPOT       -> HotspotIcon()
}

/**
 * Renders whichever of the active handler's block(s) are currently resolved
 * to [BlockSide.LEFT] — the mirror image of [RightIndicatorContent]. Only
 * ever non-empty for a [BlockAffinity.DYNAMIC] block the balancer has moved
 * left (e.g. Flashlight's icon, or Ring Mode's icon when
 * [Phs3Handler.hasSecondaryBlock] is set) — [BlockAffinity.RIGHT_ANCHOR]
 * pieces never resolve LEFT (see [FidlandIsland]'s `resolvedSide`), so for
 * every handler that hasn't opted into DYNAMIC this renders nothing, same
 * as before cross-zone placement existed.
 */
@Composable
private fun LeftZoneDynamicContent(
    activePhs3Handler: Phs3Handler?,
    primarySide: BlockSide,
    secondarySide: BlockSide,
    onPrimaryWidthMeasured: (Dp) -> Unit,
    onSecondaryWidthMeasured: (Dp) -> Unit,
) {
    if (activePhs3Handler == null) return
    if (primarySide == BlockSide.LEFT) {
        MeasuredSlot(onPrimaryWidthMeasured) { activePhs3Handler.Indicator() }
    }
    if (activePhs3Handler.hasSecondaryBlock && secondarySide == BlockSide.LEFT) {
        MeasuredSlot(onSecondaryWidthMeasured) { activePhs3Handler.SecondaryIndicator() }
    }
}

/**
 * LEFT ZONE content — used by both LEFT_EXPANDED and BOTH_EXPANDED.
 *
 * Layout (left → right, all vertically centered):
 *
 *   [location-a row]  [gap]  [dynamic-left handler block, if any]  [gap]  [NetSpeedDisplay]
 *
 * NetSpeedDisplay is NOT part of the location-a row — it is always a
 * separate, fixed-width slot immediately left of the hole-punch spacer.
 * The dynamic-left slot is populated only when [FidlandIsland]'s balancer
 * has resolved a piece of the active handler's block(s) to
 * [BlockSide.LEFT] — see [LeftZoneDynamicContent] and [FidlandIsland]'s
 * "§B2 cross-zone placement resolution" section. Empty for every handler
 * that hasn't opted into [BlockAffinity.DYNAMIC], same as before cross-zone
 * placement existed.
 *
 * Location-a row ordering (left → right):
 *   Music album art always comes first (sortedBy priority 0).
 *   Other phs3 location-a items follow in registration order.
 *
 * Album art edge placement:
 *   When music is the first (or only) location-a item the album art circle
 *   is shifted left so that its centre coincides with the apparent centre of
 *   the pill's left rounded vertex.  The vertex centre sits at CORNER_RADIUS
 *   from the pill edge; the pill's CONTENT_PADDING_HORIZONTAL pushes the
 *   slot's left edge inward by that amount.  The net inset required to
 *   place the slot centre on the vertex centre is therefore:
 *
 *     shift = CONTENT_PADDING_HORIZONTAL - CORNER_RADIUS + SLOT_SIZE / 2
 *
 *   A negative shift (the usual case) is applied as a negative start-padding
 *   on the first slot only.  The slot Box is kept at its nominal size so the
 *   intrinsic-width measurement (used for pill width animation) is unaffected.
 */
@Composable
private fun PillLeftZoneContent(
    activePhs3Handler: Phs3Handler?,
    qualifiedHandlers: List<Phs3Handler>,
    primarySide: BlockSide = BlockSide.RIGHT,
    secondarySide: BlockSide = BlockSide.RIGHT,
    onPrimaryWidthMeasured: (Dp) -> Unit = {},
    onSecondaryWidthMeasured: (Dp) -> Unit = {},
    /** §B8 #16 — see [RightIndicatorContent]'s matching params. Only the
     * handlers [coDisplayResolvedSide] resolves to [BlockSide.LEFT] render
     * here; the rest render in [RightIndicatorContent] instead. */
    coDisplayHandlers: List<Phs3Handler> = emptyList(),
    coDisplayResolvedSide: (Phs3Handler) -> BlockSide = { BlockSide.RIGHT },
    onCoDisplayWidthMeasured: (String, Dp) -> Unit = { _, _ -> },
) {
    val rowItems =
        qualifiedHandlers
            .filter { it.hasLocationA }
            .sortedBy { if (it.label == "Music") 0 else 1 }

    val hasDynamicLeftContent = activePhs3Handler != null &&
            (primarySide == BlockSide.LEFT ||
                    (activePhs3Handler.hasSecondaryBlock && secondarySide == BlockSide.LEFT))

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rowItems.isNotEmpty()) {
            // ── Location-a row ──────────────────────────────────────────────
            // The first slot (album art when music is active) is pulled toward
            // the pill's left edge so its circle centre sits on the apparent
            // centre of the rounded left vertex.
            val cornerRadius = IslandConfig.CORNER_RADIUS
            val padding      = IslandConfig.CONTENT_PADDING_HORIZONTAL
            val slotSize     = IslandConfig.LOCATION_A_SLOT_SIZE
            // How far we need to shift the first slot leftward.
            // Positive → shift left; we express this as a negative startPadding.
            val edgeShift: Dp = (padding - cornerRadius + slotSize / 2)
                .coerceAtMost(padding) // never pull further left than the pill edge

            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IslandConfig.LOCATION_A_SLOT_GAP)
            ) {
                rowItems.forEachIndexed { index, handler ->
                    val startOffset = if (index == 0) -edgeShift else 0.dp
                    Box(
                        modifier = Modifier
                            .offset(x = startOffset)
                            .size(slotSize),
                        contentAlignment = Alignment.Center
                    ) {
                        handler.LocationAContent()
                    }
                }
            }

            // Gap between location-a row and whatever comes next.
            Spacer(Modifier.width(IslandConfig.MUSIC_ALBUM_ART_GAP))
        }

        // ── Dynamic-left handler block, if the balancer placed one here ────
        if (hasDynamicLeftContent) {
            LeftZoneDynamicContent(
                activePhs3Handler = activePhs3Handler,
                primarySide = primarySide,
                secondarySide = secondarySide,
                onPrimaryWidthMeasured = onPrimaryWidthMeasured,
                onSecondaryWidthMeasured = onSecondaryWidthMeasured,
            )
            Spacer(Modifier.width(IslandConfig.MUSIC_ALBUM_ART_GAP))
        }

        // ── §B8 #16 — co-display icons the balancer placed on this side ────
        // Additive alongside whatever else is in this zone, never displacing
        // the location-a row or the dynamic-left handler block above.
        val leftCoDisplay = coDisplayHandlers.filter { coDisplayResolvedSide(it) == BlockSide.LEFT }
        if (leftCoDisplay.isNotEmpty()) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IslandConfig.DYNAMIC_BLOCK_GAP)
            ) {
                leftCoDisplay.forEach { handler ->
                    MeasuredSlot(onWidthMeasured = { onCoDisplayWidthMeasured(handler.label, it) }) {
                        handler.Indicator()
                    }
                }
            }
            Spacer(Modifier.width(IslandConfig.DYNAMIC_ZONE_ANCHOR_GAP))
        }

        // ── NetSpeedDisplay — always fixed-width, immediately left of hole ──
        // This is NOT part of the location-a row.
        Box(modifier = Modifier.width(NET_SPEED_DISPLAY_WIDTH)) {
            NetSpeedDisplay()
        }
    }
}