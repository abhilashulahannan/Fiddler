package com.example.fiddler.subapps.Fidland.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.example.fiddler.subapps.Fidland.manager.SegmentSwitcher
import com.example.fiddler.subapps.Fidland.service.DashboardTab
import com.example.fiddler.subapps.Fidland.service.DashboardTabHost

/**
 * Root UI — renders the island and nothing else.
 *
 * There is exactly ONE composable shape on screen: FidlandIsland.
 * It handles all four states internally.
 *
 * [tabs] contains only the tabs currently enabled in settings.
 * DashboardTabHost handles tab switching, the nav row, and swipe-up collapse.
 */
@Composable
fun FidlandRootUI(
    pillPhase: PillPhase,
    segmentSwitcher: SegmentSwitcher,
    tabs: List<DashboardTab>,
    isExpanded: Boolean,
    onSwipeDown: () -> Unit,
    onCollapse: () -> Unit
) {
    val currentSegment by segmentSwitcher.currentIndex.collectAsState()

    val effectivePhase = if (isExpanded) PillPhase.DASHBOARD else pillPhase

    FidlandIsland(
        phase            = effectivePhase,
        currentIndicator = currentSegment,
        dashboardContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { _, dragAmount ->
                            // Swipe-down on the island itself can still expand
                            // if triggered before DashboardTabHost consumes it.
                            // Collapse is handled inside DashboardTabHost (swipe-up).
                        }
                    }
            ) {
                DashboardTabHost(
                    tabs       = tabs,
                    isExpanded = isExpanded,
                    onCollapse = onCollapse
                )
            }
        }
    )
}