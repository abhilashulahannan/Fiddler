package com.example.fiddler.subapps.Fidland.music

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Listens to YouTube Music playback updates.
 * Currently, this is a placeholder implementation using periodic polling.
 * No official public API exists for YTMusic playback info.
 */
class YTMusicListener(
    private val context: Context,
    private val onTrackChanged: (MusicApp) -> Unit
) {

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 2000L // Poll every 2 seconds

    private var isRunning = false

    /**
     * Start periodic polling
     */
    fun start() {
        if (isRunning) return
        isRunning = true
        handler.post(updateRunnable)
    }

    /**
     * Stop polling
     */
    fun stop() {
        isRunning = false
        handler.removeCallbacks(updateRunnable)
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            // TODO: Replace with actual YTMusic track fetching if possible
            val track = MusicApp(
                appName = "YouTube Music",
                appPackage = MusicAppController.YTMUSIC_PACKAGE,
                songTitle = "Unknown",
                artistName = "Unknown",
                albumName = "Unknown",
                currentMs = 0,
                totalMs = 0,
                isPlaying = false
            )
            onTrackChanged(track)

            handler.postDelayed(this, updateInterval)
        }
    }
}
