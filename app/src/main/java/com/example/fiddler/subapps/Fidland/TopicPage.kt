package com.example.fiddler.subapps.Fidland

import android.content.Context
import androidx.compose.runtime.Composable

/**
 * Base class for all phase 3 dashboard topic pages.
 *
 * Each topic is a self-contained screen inside the swipe-down dashboard.
 * Topics are instantiated in FidlandService.buildTopicList() and passed
 * to TopicManagerCompose as composable lambdas via Content().
 *
 * Lifecycle:
 *   Content()      — called by Compose on each recomposition
 *   onSwipeLeft()  — called by TopicManagerCompose on horizontal swipe left
 *   onSwipeRight() — called by TopicManagerCompose on horizontal swipe right
 *   onDestroy()    — called by FidlandService.onDestroy() for cleanup
 *
 * Current topics:
 *   MusicTopicCompose       — music player (Category 1)
 *   PlaylistTopicCompose    — music queue (Category 2)
 *   AppsTopic               — app launcher (Category 3)
 *   QuickSettingsTopicCompose — quick settings (Category 4)
 *
 * Planned phs3 topics (not yet built):
 *   None — phs3 is the right pill segment, not a dashboard topic.
 *   Dashboard topics are all phs4 / state 3.
 */
abstract class TopicPage(protected val context: Context) {

    @Composable
    abstract fun Content()

    open fun onSwipeLeft() {}

    open fun onSwipeRight() {}

    open fun onDestroy() {}
}