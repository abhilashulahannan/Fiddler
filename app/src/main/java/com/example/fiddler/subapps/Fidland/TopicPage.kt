package com.example.fiddler.subapps.Fidland

import android.content.Context
import androidx.compose.runtime.Composable

abstract class TopicPage(protected val context: Context) {

    /** Composable content of this page */
    @Composable
    abstract fun Content()

    /** Called when the page is swiped left */
    open fun onSwipeLeft() {}

    /** Called when the page is swiped right */
    open fun onSwipeRight() {}

    /** Called when the page is destroyed */
    open fun onDestroy() {}
}
