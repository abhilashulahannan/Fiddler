package com.example.fiddler.subapps.Fidland.music

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for all registered music apps.
 *
 * Held as a singleton so SpotifyListener, YTMusicListener,
 * and MusicTopicCompose all share the same instance without needing
 * dependency injection.
 */
object MusicAppsRepository {

    private val _appsFlow = MutableStateFlow<List<MusicApp>>(emptyList())
    val appsFlow: StateFlow<List<MusicApp>> = _appsFlow.asStateFlow()

    fun getAllApps(): List<MusicApp> = _appsFlow.value

    fun addApp(app: MusicApp) {
        val current = _appsFlow.value.toMutableList()
        if (current.none { it.packageName == app.packageName }) {
            current.add(app)
            _appsFlow.value = current
        }
    }

    fun removeApp(packageName: String) {
        _appsFlow.value = _appsFlow.value.filter { it.packageName != packageName }
    }

    fun updateApp(updated: MusicApp) {
        _appsFlow.value = _appsFlow.value.map {
            if (it.packageName == updated.packageName) updated else it
        }
    }

    fun replaceAll(apps: List<MusicApp>) {
        _appsFlow.value = apps
    }

    /**
     * Called by SpotifyListener / YTMusicListener when track metadata arrives.
     *
     * positionBaseMs + positionBaseTime are the raw values from
     * PlaybackState so the UI can interpolate a live seek position
     * without waiting for the next MediaSession callback.
     */
    fun updateTrackInfo(
        packageName: String,
        songTitle: String,
        artistName: String,
        albumName: String,
        isPlaying: Boolean,
        currentMs: Int = 0,
        totalMs: Int = 0,
        positionBaseMs: Long = 0L,
        positionBaseTime: Long = 0L,
        albumArt: Bitmap? = null
    ) {
        val existing = _appsFlow.value.firstOrNull { it.packageName == packageName }
        val updated = (existing ?: MusicApp(packageName = packageName, appName = packageName))
            .copy(
                songTitle      = songTitle,
                artistName     = artistName,
                albumName      = albumName,
                isPlaying      = isPlaying,
                currentMs      = currentMs,
                totalMs        = totalMs,
                positionBaseMs = positionBaseMs,
                positionBaseTime = positionBaseTime,
                albumArt       = albumArt ?: existing?.albumArt
            )
        if (existing != null) updateApp(updated) else addApp(updated)
    }
}