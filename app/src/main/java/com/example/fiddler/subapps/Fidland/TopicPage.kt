package com.example.fiddler.subapps.Fidland

import android.content.Context
import android.view.View

abstract class TopicPage(protected val context: Context) {
    abstract fun getView(): View
    open fun onSwipeLeft() {}
    open fun onSwipeRight() {}
}
