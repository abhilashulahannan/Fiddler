package com.example.fiddler.subapps.Fidland.service

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dashboard tab container for state 4.
 *
 * Layout:
 *   ┌─────────────────────────────────────────┐
 *   │                                         │  ← content area (fills remaining height)
 *   │        active tab composable            │    swipe left/right → prev/next tab
 *   │                                         │    swipe up         → collapse (onCollapse)
 *   ├─────────────────────────────────────────┤
 *   │  Music   Queue   Settings   Apps        │  ← nav row, 20dp tall, no background
 *   └─────────────────────────────────────────┘    tap label       → jump to tab
 *                                                  swipe left/right → prev/next tab
 *
 * Only enabled tabs are passed in — the nav row only shows what's in [tabs].
 * Active label is white; inactive labels are grey. No borders, no boxes.
 */
data class DashboardTab(
    val label: String,
    val content: @Composable () -> Unit
)

// ── Legacy overload ───────────────────────────────────────────────────────────
// Keeps FidlandRootUI and any other existing call sites compiling unchanged.
// Prefer DashboardTabHost for new call sites.
@Composable
fun TopicManagerCompose(
    topics: List<@Composable () -> Unit>,
    isExpanded: Boolean,
    onCollapse: () -> Unit,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null
) {
    val tabs = topics.mapIndexed { i, content ->
        DashboardTab(label = "Tab ${i + 1}", content = content)
    }
    DashboardTabHost(tabs = tabs, isExpanded = isExpanded, onCollapse = onCollapse)
}

// ── Main composable ───────────────────────────────────────────────────────────
@Composable
fun DashboardTabHost(
    tabs: List<DashboardTab>,
    isExpanded: Boolean,
    onCollapse: () -> Unit
) {
    if (tabs.isEmpty()) return

    var currentIndex by remember(tabs.size) { mutableStateOf(0) }
    var slideDirection by remember { mutableStateOf(1) } // +1 = forward, -1 = backward

    fun goToTab(index: Int) {
        val target = index.mod(tabs.size)
        if (target == currentIndex) return
        slideDirection = if (target > currentIndex) 1 else -1
        currentIndex = target
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Content area ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(isExpanded) {
                    detectDragGestures { _, dragAmount ->
                        if (!isExpanded) return@detectDragGestures
                        val (dx, dy) = dragAmount
                        when {
                            dy < -22f  -> onCollapse()              // swipe up   → collapse
                            dx < -22f  -> goToTab(currentIndex + 1) // swipe left → next tab
                            dx >  22f  -> goToTab(currentIndex - 1) // swipe right → prev tab
                        }
                    }
                }
        ) {
            AnimatedContent(
                targetState = currentIndex,
                transitionSpec = {
                    if (slideDirection > 0) {
                        // Forward — incoming tab slides in from the right
                        (slideInHorizontally(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness    = Spring.StiffnessMediumLow
                            )
                        ) { it } + fadeIn()).togetherWith(
                            slideOutHorizontally { -it } + fadeOut()
                        )
                    } else {
                        // Backward — incoming tab slides in from the left
                        (slideInHorizontally(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness    = Spring.StiffnessMediumLow
                            )
                        ) { -it } + fadeIn()).togetherWith(
                            slideOutHorizontally { it } + fadeOut()
                        )
                    }
                },
                label = "dashboardTab"
            ) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    tabs[index].content()
                }
            }
        }

        // ── Nav row ───────────────────────────────────────────────────────
        // Exactly 20dp tall. No background, no border, no ripple on labels.
        // Swipe the row left/right to cycle tabs; tap a label to jump to it.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .pointerInput(tabs.size) {
                    // Accumulator: fire a tab change every STEP_PX of drag,
                    // then subtract STEP_PX so one long swipe steps multiple tabs.
                    val stepPx = 60f
                    var accumulated = 0f
                    detectHorizontalDragGestures(
                        onDragStart   = { accumulated = 0f },
                        onDragEnd     = { accumulated = 0f },
                        onDragCancel  = { accumulated = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            accumulated += dragAmount
                            while (accumulated < -stepPx) {
                                goToTab(currentIndex + 1)
                                accumulated += stepPx
                            }
                            while (accumulated > stepPx) {
                                goToTab(currentIndex - 1)
                                accumulated -= stepPx
                            }
                        }
                    )
                },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val isActive = index == currentIndex
                Text(
                    text       = tab.label,
                    color      = if (isActive) Color.White else Color(0xFF888888),
                    fontSize   = 10.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null  // no ripple — clean minimal look
                    ) { goToTab(index) }
                )
            }
        }
    }
}