package com.example.fiddler.subapps.Fidland.music

import android.graphics.Bitmap

/**
 * Data model for a registered music app.
 *
 * packageName is the unique key used by MusicAppsRepository,
 * MusicAppController, and the listeners to identify which app
 * a metadata update belongs to.
 *
 * albumArt is the raw Bitmap extracted from MediaMetadata — not a
 * drawable res ID, because Spotify/YTMusic deliver art as a Bitmap,
 * not a bundled resource.
 *
 * positionBaseMs / positionBaseTime together let the UI compute a live
 * seek position without waiting for the next MediaSession callback:
 *   livePosition = positionBaseMs + (now - positionBaseTime)  (when playing)
 *
 * currentMs / totalMs are best-effort — Spotify's MediaSession exposes
 * these accurately; YTMusic does not always.
 */
data class MusicApp(
    val packageName: String,
    val appName: String,

    // Track metadata — updated by listeners
    val songTitle: String = "",
    val artistName: String = "",
    val albumName: String = "",
    val isPlaying: Boolean = false,

    // Raw position from the last MediaSession state report
    val currentMs: Int = 0,
    val totalMs: Int = 0,

    // Used to interpolate live position between MediaSession updates
    val positionBaseMs: Long = 0L,
    val positionBaseTime: Long = 0L,

    // Bitmap from MediaMetadata.METADATA_KEY_ART or METADATA_KEY_ALBUM_ART
    val albumArt: Bitmap? = null
) {
    companion object {
        const val SPOTIFY_PACKAGE = "com.spotify.music"
        const val YTMUSIC_PACKAGE = "com.google.android.apps.youtube.music"
    }

    /** Returns an interpolated live position in ms, safe to call every frame. */
    fun livePositionMs(): Int {
        if (!isPlaying || positionBaseTime == 0L) return currentMs
        val elapsed = System.currentTimeMillis() - positionBaseTime
        return (positionBaseMs + elapsed).coerceIn(0L, totalMs.toLong()).toInt()
    }
}