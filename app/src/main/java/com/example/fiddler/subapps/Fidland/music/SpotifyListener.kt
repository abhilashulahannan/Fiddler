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
 * Listens to Spotify's MediaSession via MediaSessionManager.
 *
 * Mirrors YTMusicListener's retry-polling approach so that if Spotify's
 * session isn't active at start() time we still attach when it appears,
 * rather than silently giving up.
 *
 * Lifecycle: call start() when FidlandService starts (or when notification
 * listener permission is confirmed). Call stop() in onDestroy().
 */
class SpotifyListener(
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
            MusicApp(packageName = MusicApp.SPOTIFY_PACKAGE, appName = "Spotify")
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
            val spotifyController = sessions.firstOrNull {
                it.packageName == MusicApp.SPOTIFY_PACKAGE
            } ?: return false

            controller = spotifyController

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

            // Push current state immediately so UI isn't blank on attach
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
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING

        // Extract album art bitmap from metadata
        val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)

        MusicAppsRepository.updateTrackInfo(
            packageName      = MusicApp.SPOTIFY_PACKAGE,
            songTitle        = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
            artistName       = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
            albumName        = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
            isPlaying        = isPlaying,
            currentMs        = state?.position?.toInt() ?: 0,
            totalMs          = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)
                ?.toInt()?.coerceAtLeast(0) ?: 0,
            positionBaseMs   = state?.position ?: 0L,
            positionBaseTime = if (isPlaying) System.currentTimeMillis() else 0L,
            albumArt         = albumArt
        )
    }
}