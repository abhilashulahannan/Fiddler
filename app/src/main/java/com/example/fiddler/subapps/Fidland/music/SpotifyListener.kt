package com.example.fiddler.subapps.Fidland.music

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.example.fiddler.subapps.Fidland.NotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

                override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) {
                    pushQueue(queue)
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
            pushQueue(controller?.queue)
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

        val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)

        val customActions = state?.customActions?.map {
            val label = it.name?.toString() ?: ""
            CustomActionInfo(
                action   = it.action,
                name     = label,
                isActive = label.contains("remove", ignoreCase = true) ||
                        label.contains("saved", ignoreCase = true) ||
                        label.contains("unlike", ignoreCase = true)
            )
        } ?: emptyList()

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
            albumArt         = albumArt,
            customActions    = customActions,
        )
    }

    private fun pushQueue(queue: MutableList<MediaSession.QueueItem>?) {
        if (queue == null) {
            MusicAppsRepository.updateQueue(MusicApp.SPOTIFY_PACKAGE, emptyList())
            return
        }

        val currentTitle = controller?.metadata
            ?.getString(MediaMetadata.METADATA_KEY_TITLE)

        val upNext = queue
            .map {
                QueueTrackInfo(
                    queueId  = it.queueId,
                    title    = it.description?.title?.toString() ?: "",
                    artist   = it.description?.subtitle?.toString() ?: "",
                    albumArt = it.description?.iconBitmap
                )
            }
            .dropWhile { it.title.isNotBlank() && it.title == currentTitle }

        MusicAppsRepository.updateQueue(MusicApp.SPOTIFY_PACKAGE, upNext)
    }
}