package com.example.fiddler.subapps.Fidland.music

import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.view.KeyEvent
import android.content.ComponentName
import com.example.fiddler.subapps.Fidland.NotificationListenerService

/**
 * Sends media commands to the active player for a given MusicApp.
 *
 * Strategy:
 *   1. Try to get the app's MediaController via MediaSessionManager — this
 *      gives us real transport controls (play, pause, skip, seek).
 *   2. Fall back to KeyEvent broadcasts if no session is found — works for
 *      most players but seek is not available via this path.
 *
 * NotificationListenerService must be active for MediaSessionManager to work.
 * The user grants this in Settings → Special app access → Notification access.
 */
class MusicAppController(private val context: Context) {

    // Try to get a live MediaController for the given app's package
    private fun getController(app: MusicApp): MediaController? {
        return try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
                    as MediaSessionManager
            val component = ComponentName(context, NotificationListenerService::class.java)
            msm.getActiveSessions(component)
                .firstOrNull { it.packageName == app.packageName }
        } catch (e: SecurityException) {
            // Notification listener permission not granted yet
            null
        }
    }

    fun togglePlayPause(app: MusicApp) {
        val controller = getController(app)
        if (controller != null) {
            val state = controller.playbackState?.state
            if (state == android.media.session.PlaybackState.STATE_PLAYING) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
        } else {
            sendKeyEvent(app, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
    }

    fun nextTrack(app: MusicApp) {
        val controller = getController(app)
        if (controller != null) {
            controller.transportControls.skipToNext()
        } else {
            sendKeyEvent(app, KeyEvent.KEYCODE_MEDIA_NEXT)
        }
    }

    fun previousTrack(app: MusicApp) {
        val controller = getController(app)
        if (controller != null) {
            controller.transportControls.skipToPrevious()
        } else {
            sendKeyEvent(app, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
    }

    /**
     * Seek to a position in milliseconds.
     * Only available via MediaController — KeyEvent has no seek equivalent.
     * If no session is found this is silently ignored; the seek bar will
     * snap back on the next repo update.
     */
    fun seekTo(app: MusicApp, positionMs: Int) {
        getController(app)?.transportControls?.seekTo(positionMs.toLong())
    }

    /**
     * Fires a "toggle favorite"-style custom action exposed via
     * MediaController.playbackState.customActions (e.g. Spotify's
     * "Add to / Remove from Your Library", YT Music's "like").
     * No-op if no such action is currently available.
     */
    fun toggleFavorite(app: MusicApp, action: CustomActionInfo) {
        getController(app)?.transportControls?.sendCustomAction(action.action, null)
    }

    /**
     * Jumps playback to a specific queue entry (Spotify only — see
     * MusicApp.queue / QueueTrackInfo for where queueId comes from).
     */
    fun skipToQueueItem(app: MusicApp, queueId: Long) {
        getController(app)?.transportControls?.skipToQueueItem(queueId)
    }

    /** Opens the music app's launcher activity, e.g. when album art is tapped. */
    fun launchApp(app: MusicApp) {
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // KeyEvent fallback — works without notification listener permission
    private fun sendKeyEvent(app: MusicApp, keyCode: Int) {
        val intent = android.content.Intent(android.content.Intent.ACTION_MEDIA_BUTTON).apply {
            `package` = app.packageName
            putExtra(
                android.content.Intent.EXTRA_KEY_EVENT,
                KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            )
        }
        context.sendBroadcast(intent)
        // Also send key up so the player doesn't think the button is held
        context.sendBroadcast(intent.apply {
            putExtra(
                android.content.Intent.EXTRA_KEY_EVENT,
                KeyEvent(KeyEvent.ACTION_UP, keyCode)
            )
        })
    }
}