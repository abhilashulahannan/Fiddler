package com.example.fiddler.subapps.Fidland.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.fiddler.subapps.Fidland.manager.SegmentSwitcher
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler
import com.example.fiddler.subapps.Fidland.service.DashboardTab
import com.example.fiddler.subapps.Fidland.service.DashboardTabHost
import kotlinx.coroutines.launch

/**
 * Root UI — renders the island and nothing else.
 *
 * There is exactly ONE composable shape on screen: FidlandIsland.
 * It handles all six phases internally (States 1-5 + DASHBOARD).
 *
 * [tabs] contains only the tabs currently enabled in settings.
 * DashboardTabHost handles tab switching, the nav row, and swipe-up collapse.
 *
 * ── State 5 (STATE5) lifecycle ────────────────────────────────────────────────
 * State 5 is a slim content strip shown between the compact pill and the
 * full dashboard. Entry and exit are driven exclusively by PhaseTouchBox
 * gestures wired in FidlandService — not by anything in this composable:
 *
 *   Entry  : swipe-down from States 1-2-3 (if active handler hasState5Content)
 *            → FidlandService sets pillPhase = STATE5
 *   Exit A : swipe-down from STATE5
 *            → FidlandService opens DASHBOARD
 *   Exit B : swipe-up from STATE5
 *            → FidlandService collapses to compact states
 *
 * ── Shake animation ───────────────────────────────────────────────────────────
 * A quick horizontal shake is triggered on two events:
 *   • Pin / unpin : when [activePhs3Handler] changes identity (register or
 *     unregister of a phs3 entity). The pill shakes to confirm the action.
 *   • Lock / unlock rotation : when [isRotationLocked] toggles. The shake
 *     gives tactile-style feedback that the lock state changed.
 *
 * ── Slide-out / slide-in animation ───────────────────────────────────────────
 * When [pillVisible] is false (hide on swipe-up) the pill slides off the top
 * of the screen; when it becomes true again it slides back in from the top.
 * This is driven purely by graphicsLayer translationY inside Compose — the
 * WindowManager view position never changes, so no WindowManager calls are
 * needed for the animation itself.
 *
 * ── Long-press lifecycle ──────────────────────────────────────────────────────
 * Long-press in States 1-2-3 or STATE5 → Phs3Manager.lockRotation().
 * This is wired entirely in PhaseTouchBox / FidlandService. No long-press
 * handler is needed here.
 *
 * ── State 4 (DASHBOARD) collapse ─────────────────────────────────────────────
 * Swipe-up inside the dashboard content area → DashboardTabHost calls
 * [onCollapse] → FidlandService.collapseToCompact(). The touchbox is not
 * present during State 4; this is the only collapse path from there.
 */
@Composable
fun FidlandRootUI(
    pillPhase: PillPhase,
    segmentSwitcher: SegmentSwitcher,
    tabs: List<DashboardTab>,
    isExpanded: Boolean,
    activePhs3Handler: Phs3Handler? = null,
    qualifiedPhs3Handlers: List<Phs3Handler> = emptyList(),
    isRotationLocked: Boolean = false,
    pillVisible: Boolean = true,
    onSwipeDown: () -> Unit = {},
    onCollapse: () -> Unit,
) {
    val currentSegment by segmentSwitcher.currentIndex.collectAsState()
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val effectivePhase = when {
        isExpanded -> PillPhase.DASHBOARD
        else       -> pillPhase
    }

    // ── Shake animation ───────────────────────────────────────────────────────
    // translateX oscillates: 0 → +shakeAmp → -shakeAmp → … → 0 in ~300 ms.
    val shakeX = remember { Animatable(0f) }

    suspend fun triggerShake() {
        val amp = 6f   // dp-ish — graphicsLayer translationX is in pixels but
        // we keep values small; the spring handles feel.
        shakeX.snapTo(0f)
        // 4-cycle wobble: right → left → right → left → settle
        for (i in 1..4) {
            val target = if (i % 2 == 1) amp else -amp
            shakeX.animateTo(
                target,
                animationSpec = tween(durationMillis = 40)
            )
        }
        shakeX.animateTo(0f, animationSpec = spring(stiffness = Spring.StiffnessHigh))
    }

    // Shake on pin / unpin — track the handler label so identity changes fire it.
    val prevHandlerLabel = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(activePhs3Handler?.label) {
        val newLabel = activePhs3Handler?.label
        // Only shake after the first composition (skip the initial null→null).
        if (prevHandlerLabel.value != null || newLabel != null) {
            if (prevHandlerLabel.value != newLabel) {
                triggerShake()
            }
        }
        prevHandlerLabel.value = newLabel
    }

    // Shake on lock / unlock rotation.
    // Skip the very first composition (initialised to false) so the shake only
    // fires on actual toggles that happen after the UI is up.
    val isFirstLockComposition = remember { mutableStateOf(true) }
    LaunchedEffect(isRotationLocked) {
        if (isFirstLockComposition.value) {
            isFirstLockComposition.value = false
            return@LaunchedEffect
        }
        triggerShake()
    }

    // ── Slide-out / slide-in animation ────────────────────────────────────────
    // When pillVisible → false  : slide upward off screen (negative Y in graphicsLayer).
    // When pillVisible → true   : slide back in from the top with a spring bounce.
    //
    // We need the pill height in pixels to know how far to slide. BASE_SIZE gives
    // the compact pill height; for Dashboard / State5 the pill is taller but those
    // states are never hidden (hide only triggers from compact states), so
    // BASE_SIZE is the right value here.
    val pillHeightPx = with(density) { IslandConfig.BASE_SIZE.toPx() }
    // Add a generous margin so the pill fully clears the screen edge.
    val hideTranslationY = -(pillHeightPx * 3f)

    val slideY by animateFloatAsState(
        targetValue = if (pillVisible) 0f else hideTranslationY,
        animationSpec = if (pillVisible) {
            // Slide back in with a light bounce
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness    = Spring.StiffnessMediumLow
            )
        } else {
            // Slide out: quick ease-in (no bounce going away)
            tween(durationMillis = 220)
        },
        label = "pill_slide_y"
    )

    FidlandIsland(
        phase                 = effectivePhase,
        currentIndicator      = currentSegment,
        activePhs3Handler     = activePhs3Handler,
        qualifiedHandlers     = qualifiedPhs3Handlers,
        isRotationLocked      = isRotationLocked,
        modifier          = Modifier.graphicsLayer {
            translationX = shakeX.value
            translationY = slideY
        },
        dashboardContent  = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { _, _ ->
                            // Swipe-down on the island itself while already in
                            // DASHBOARD is a no-op here — DashboardTabHost owns
                            // horizontal swipes (tab switching) and swipe-up
                            // (collapse). Swipe-down inside State 4 has no action.
                        }
                    }
            ) {
                // ── Dashboard content stagger ─────────────────────────────────
                // Fade in after the pill shape has mostly finished expanding.
                // Fade out immediately on collapse so shape-shrink reads cleanly.
                AnimatedVisibility(
                    visible = isExpanded,
                    enter   = fadeIn(tween(durationMillis = 150, delayMillis = 100)),
                    exit    = fadeOut(tween(durationMillis = 80))
                ) {
                    DashboardTabHost(
                        tabs       = tabs,
                        isExpanded = isExpanded,
                        onCollapse = onCollapse
                    )
                }
            }
        }
    )
}