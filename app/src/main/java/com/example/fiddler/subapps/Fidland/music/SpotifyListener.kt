package com.example.fiddler.subapps.Fidland.music

import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build

/**
 * Listens to Spotify playback updates via MediaSession
 * and notifies a callback whenever track changes or playback state changes.
 */
class SpotifyListener(
    context: Context,
    private val onTrackChanged: (MusicApp) -> Unit
) {

    private val mediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private var controller: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
            metadata?.let {
                val track = MusicApp(
                    appName = "Spotify",
                    appPackage = MusicAppController.SPOTIFY_PACKAGE,
                    songTitle = it.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "",
                    artistName = it.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: "",
                    albumName = it.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM) ?: "",
                    totalMs = it.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION).toInt(),
                    currentMs = controller?.playbackState?.position?.toInt() ?: 0,
                    isPlaying = controller?.playbackState?.state == PlaybackState.STATE_PLAYING
                )
                onTrackChanged(track)
            }
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            state?.let { refreshTrack() }
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val sessions = mediaSessionManager.getActiveSessions(null)
            controller = sessions.find { it.packageName == MusicAppController.SPOTIFY_PACKAGE }
            controller?.registerCallback(controllerCallback)
            // Initial metadata refresh
            refreshTrack()
        }
    }

    private fun refreshTrack() {
        controller?.metadata?.let { controllerCallback.onMetadataChanged(it) }
    }

    fun unregister() {
        controller?.unregisterCallback(controllerCallback)
    }
}
