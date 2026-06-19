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
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    var measuredRightContentWidth by remember { mutableStateOf(0.dp) }
    var measuredLeftContentWidth  by remember { mutableStateOf(0.dp) }

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
        PillPhase.STATE5    -> IslandConfig.STATE5_HEIGHT
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
                                    RightIndicatorContent(handler, activeIndicators, currentIndicator)
                                }
                            }
                        }
                    }

                    // ── State 2+3 — both zones ────────────────────────────────
                    //
                    // LEFT ZONE  → PillLeftZoneContent:
                    //   Download active  →  [📶 location a]  [↓ net speed]
                    //   Music playing    →  [🎵 album art]   [↓ net speed]
                    //   Neither          →  [↓ net speed] only
                    //
                    // RIGHT ZONE → phs3 handler Indicator():
                    //   Download active  →  [ETA  ◯%]   (b + c)
                    //   Other handler    →  whatever that handler renders
                    //
                    PillPhase.BOTH_EXPANDED -> {
                        PillRow(longPressHandler = activePhs3Handler, onLongPress = onPhs3LongPress) {
                            LeftZone(onWidthMeasured = { measuredLeftContentWidth = it }) {
                                PillLeftZoneContent(
                                    activePhs3Handler = activePhs3Handler,
                                    qualifiedHandlers = qualifiedHandlers
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
                                    RightIndicatorContent(handler, activeIndicators, currentIndicator)
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
 * Renders the active phs3 handler's [Phs3Handler.Indicator] in the RIGHT ZONE
 * (locations b and c).
 */
@Composable
private fun RightIndicatorContent(
    activePhs3Handler: Phs3Handler?,
    activeIndicators: List<RightIndicator>,
    currentIndicator: Int
) {
    activePhs3Handler?.Indicator()

    if (activeIndicators.isEmpty()) return
    // Future non-phs3 indicators:
    // NOTIFICATIONS -> NotificationIconStack()
    // CHARGING      -> ChargingIcon()
    // RECORDER      -> GlowingRedDot()
    // HOTSPOT       -> HotspotIcon()
}

/**
 * LEFT ZONE content — used by both LEFT_EXPANDED and BOTH_EXPANDED.
 *
 * Layout (left → right, all vertically centered):
 *
 *   [location-a row]  [gap]  [NetSpeedDisplay]
 *
 * NetSpeedDisplay is NOT part of the location-a row — it is always a
 * separate, fixed-width slot immediately left of the hole-punch spacer.
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
) {
    val rowItems =
        qualifiedHandlers
            .filter { it.hasLocationA }
            .sortedBy { if (it.label == "Music") 0 else 1 }

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

            // Gap between location-a row and NetSpeedDisplay
            Spacer(Modifier.width(IslandConfig.MUSIC_ALBUM_ART_GAP))
        }

        // ── NetSpeedDisplay — always fixed-width, immediately left of hole ──
        // This is NOT part of the location-a row.
        Box(modifier = Modifier.width(NET_SPEED_DISPLAY_WIDTH)) {
            NetSpeedDisplay()
        }
    }
}