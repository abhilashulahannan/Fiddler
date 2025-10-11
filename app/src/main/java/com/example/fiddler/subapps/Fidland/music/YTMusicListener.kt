package com.example.fiddler.subapps.Fidland.music

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Listens to YTMusic playback updates.
 * Note: limited to periodic polling; no official API available.
 */
class YTMusicListener(
    private val context: Context,
    private val onTrackChanged: (MusicApp) -> Unit
) {

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 2000L // 2 seconds

    private var running = false

    fun start() {
        running = true
        handler.post(updateRunnable)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(updateRunnable)
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!running) return

            // TODO: Implement actual YTMusic track fetching if possible
            // For now, we send a placeholder track
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
