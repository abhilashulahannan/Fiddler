package com.example.fiddler.subapps.Fidland.music

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        albumArt: Bitmap? = null,
        customActions: List<CustomActionInfo> = emptyList(),
    ) {
        val existing = _appsFlow.value.firstOrNull { it.packageName == packageName }
        val updated = (existing ?: MusicApp(packageName = packageName, appName = packageName))
            .copy(
                songTitle        = songTitle,
                artistName       = artistName,
                albumName        = albumName,
                isPlaying        = isPlaying,
                currentMs        = currentMs,
                totalMs          = totalMs,
                positionBaseMs   = positionBaseMs,
                positionBaseTime = positionBaseTime,
                albumArt         = albumArt ?: existing?.albumArt,
                customActions    = customActions,
            )
        if (existing != null) updateApp(updated) else addApp(updated)
    }

    fun updateQueue(packageName: String, queue: List<QueueTrackInfo>) {
        val existing = _appsFlow.value.firstOrNull { it.packageName == packageName }
            ?: MusicApp(packageName = packageName, appName = packageName)
        val updated = existing.copy(queue = queue)
        if (_appsFlow.value.any { it.packageName == packageName }) {
            updateApp(updated)
        } else {
            addApp(updated)
        }
    }
}