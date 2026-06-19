package com.example.fiddler.subapps.Fidland.music

import android.graphics.Bitmap

data class QueueTrackInfo(
    val queueId: Long,
    val title: String,
    val artist: String,
    val albumArt: Bitmap? = null
)

data class CustomActionInfo(
    val action: String,
    val name: String,
    val isActive: Boolean = false
)

data class MusicApp(
    val packageName: String,
    val appName: String,

    val songTitle: String = "",
    val artistName: String = "",
    val albumName: String = "",
    val isPlaying: Boolean = false,

    val currentMs: Int = 0,
    val totalMs: Int = 0,

    val positionBaseMs: Long = 0L,
    val positionBaseTime: Long = 0L,

    val albumArt: Bitmap? = null,

    val customActions: List<CustomActionInfo> = emptyList(),

    val queue: List<QueueTrackInfo> = emptyList(),
) {
    companion object {
        const val SPOTIFY_PACKAGE = "com.spotify.music"
        const val YTMUSIC_PACKAGE = "com.google.android.apps.youtube.music"
    }

    fun livePositionMs(): Int {
        if (!isPlaying || positionBaseTime == 0L) return currentMs
        val elapsed = System.currentTimeMillis() - positionBaseTime
        return (positionBaseMs + elapsed).coerceIn(0L, totalMs.toLong()).toInt()
    }
}