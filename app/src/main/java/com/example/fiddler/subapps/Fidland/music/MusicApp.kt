package com.example.fiddler.subapps.Fidland.music

/**
 * Data class representing a single music app and its current playback info
 */
data class MusicApp(
    val appName: String,          // Display name of the app (Spotify, YT Music)
    val appPackage: String,       // Package name for sending intents or MediaSession commands
    val albumArtResId: Int? = null,  // Local drawable resource ID for album art (optional)
    val albumArtUrl: String? = null, // Optional: URL if loading from network
    val songTitle: String = "",       // Current track title
    val artistName: String = "",      // Current track artist
    val albumName: String = "",       // Current track album
    val currentMs: Int = 0,           // Current playback position in milliseconds
    val totalMs: Int = 0,             // Total track duration in milliseconds
    val isPlaying: Boolean = false    // Whether the track is currently playing
)
