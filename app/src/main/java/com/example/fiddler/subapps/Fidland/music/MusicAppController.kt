package com.example.fiddler.subapps.Fidland.music

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.widget.Toast

/**
 * Handles playback commands for supported music apps using broadcast/key events.
 * Fetches track info via MediaListenerService updates stored in MusicAppsRepository.
 */
class MusicAppController(private val context: Context) {

    companion object {
        const val SPOTIFY_PACKAGE = "com.spotify.music"
        const val YTMUSIC_PACKAGE = "com.google.android.apps.youtube.music"
    }

    /**
     * Send Play/Pause command to the given app
     */
    fun togglePlayPause(app: MusicApp) {
        sendMediaCommand(app, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    /**
     * Skip to next track
     */
    fun nextTrack(app: MusicApp) {
        sendMediaCommand(app, KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    /**
     * Skip to previous track
     */
    fun previousTrack(app: MusicApp) {
        sendMediaCommand(app, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    /**
     * Seek to position (in ms) — not supported for non-system apps
     */
    fun seekTo(app: MusicApp, positionMs: Int) {
        Toast.makeText(context, "Seek not supported in non-system mode", Toast.LENGTH_SHORT).show()
    }

    /**
     * Send MEDIA_BUTTON intent to the app
     */
    private fun sendMediaCommand(app: MusicApp, keyCode: Int) {
        val targetPackage = when (app.appPackage) {
            SPOTIFY_PACKAGE -> SPOTIFY_PACKAGE
            YTMUSIC_PACKAGE -> YTMUSIC_PACKAGE
            else -> null
        }

        if (targetPackage == null) {
            Toast.makeText(context, "Unsupported app", Toast.LENGTH_SHORT).show()
            return
        }

        val keyEventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val keyEventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)

        val intentDown = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            setPackage(targetPackage)
            putExtra(Intent.EXTRA_KEY_EVENT, keyEventDown)
        }

        val intentUp = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            setPackage(targetPackage)
            putExtra(Intent.EXTRA_KEY_EVENT, keyEventUp)
        }

        context.sendBroadcast(intentDown)
        context.sendBroadcast(intentUp)
    }

    /**
     * Fetch current track info from repository
     * No direct MediaSession access, so only last-known info from broadcasts is returned.
     */
    fun getCurrentTrack(app: MusicApp): MusicApp {
        return MusicAppsRepository.getAllApps()
            .find { it.appPackage == app.appPackage } ?: app
    }
}
