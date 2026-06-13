package com.example.fiddler.subapps.Fidland.music

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.example.fiddler.subapps.Fidland.NotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Listens to YT Music's MediaSession.
 *
 * YT Music does not always hold a persistent MediaSession the way Spotify does,
 * so we combine two strategies:
 *
 *   1. Direct callback — attaches when a session is already active at start().
 *   2. Polling fallback — polls every [POLL_INTERVAL_MS] until a session
 *      appears, then attaches the callback and cancels the poll.
 */
class YTMusicListener(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val POLL_INTERVAL_MS = 3000L
    }

    private var controller: MediaController? = null
    private var callback: MediaController.Callback? = null
    private var pollJob: Job? = null

    private val sessionManager by lazy {
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    fun start() {
        MusicAppsRepository.addApp(
            MusicApp(packageName = MusicApp.YTMUSIC_PACKAGE, appName = "YT Music")
        )
        if (!attachToSession()) {
            startPolling()
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        callback?.let { controller?.unregisterCallback(it) }
        controller = null
        callback = null
    }

    private fun attachToSession(): Boolean {
        return try {
            val component = ComponentName(context, NotificationListenerService::class.java)
            val sessions = sessionManager.getActiveSessions(component)
            val ytController = sessions.firstOrNull {
                it.packageName == MusicApp.YTMUSIC_PACKAGE
            } ?: return false

            controller = ytController

            callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    pushUpdate(metadata, controller?.playbackState)
                }

                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    pushUpdate(controller?.metadata, state)
                }

                override fun onSessionDestroyed() {
                    callback?.let { controller?.unregisterCallback(it) }
                    controller = null
                    callback = null
                    startPolling()
                }
            }

            callback?.let { controller?.registerCallback(it) }
            pushUpdate(controller?.metadata, controller?.playbackState)
            true

        } catch (e: SecurityException) {
            false
        }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                if (attachToSession()) {
                    pollJob?.cancel()
                    break
                }
            }
        }
    }

    private fun pushUpdate(metadata: MediaMetadata?, state: PlaybackState?) {
        // Only push if we have actual track data — never push empty/dummy updates
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (title.isNullOrBlank()) return

        val isPlaying = state?.state == PlaybackState.STATE_PLAYING

        val albumArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)

        MusicAppsRepository.updateTrackInfo(
            packageName      = MusicApp.YTMUSIC_PACKAGE,
            songTitle        = title,
            artistName       = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
            albumName        = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
            isPlaying        = isPlaying,
            currentMs        = state?.position?.toInt() ?: 0,
            totalMs          = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
                .toInt().coerceAtLeast(0),
            positionBaseMs   = state?.position ?: 0L,
            positionBaseTime = if (isPlaying) System.currentTimeMillis() else 0L,
            albumArt         = albumArt
        )
    }
}