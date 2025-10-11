package com.example.fiddler.subapps.Fidland.music

/**
 * Data class representing a single music app and its current playback info.
 *
 * @property appName Display name of the app (e.g., Spotify, YouTube Music)
 * @property appPackage Package name used to send media commands
 * @property albumArtResId Optional local drawable resource ID for album art
 * @property albumArtUrl Optional URL for album art if loading from network
 * @property songTitle Current track title
 * @property artistName Current track artist
 * @property albumName Current track album
 * @property currentMs Current playback position in milliseconds
 * @property totalMs Total track duration in milliseconds
 * @property isPlaying Whether the track is currently playing
 */
data class MusicApp(
    val appName: String,
    val appPackage: String,
    val albumArtResId: Int? = null,
    val albumArtUrl: String? = null,
    val songTitle: String = "",
    val artistName: String = "",
    val albumName: String = "",
    val currentMs: Int = 0,
    val totalMs: Int = 0,
    val isPlaying: Boolean = false
)
