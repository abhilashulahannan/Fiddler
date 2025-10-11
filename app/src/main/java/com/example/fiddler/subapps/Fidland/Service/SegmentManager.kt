package com.example.fiddler.subapps.Fidland.service

import android.os.Handler
import android.view.View

class SegmentManager(
    private val segments: List<View>,
    private val handler: Handler,
    private val switchIntervalMs: Long = 5000L
) {
    private var currentIndex = 0
    private var running = false

    /** Start automatically cycling through segments */
    fun start() {
        if (running) return
        running = true
        handler.post(segmentSwitcherRunnable)
    }

    /** Stop cycling */
    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
    }

    /** Show the current segment manually */
    fun showCurrentSegment() {
        if (segments.isEmpty()) return
        segments.forEach { it.visibility = View.GONE }
        segments[currentIndex].visibility = View.VISIBLE
    }

    /** Switch to the next segment */
    fun nextSegment() {
        if (segments.isEmpty()) return
        segments[currentIndex].visibility = View.GONE
        currentIndex = (currentIndex + 1) % segments.size
        segments[currentIndex].visibility = View.VISIBLE
    }

    /** Runnable that cycles segments automatically */
    private val segmentSwitcherRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            nextSegment()
            handler.postDelayed(this, switchIntervalMs)
        }
    }
}
