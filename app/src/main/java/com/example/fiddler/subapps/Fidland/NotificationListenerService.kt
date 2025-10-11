package com.example.fiddler.subapps.Fidland

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class MediaInfo(
    val pkg: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val isPlaying: Boolean = false
)

object MediaState {
    private val _mediaInfo = MutableStateFlow(MediaInfo())
    val mediaInfo: StateFlow<MediaInfo> = _mediaInfo

    internal fun update(info: MediaInfo) {
        _mediaInfo.value = info
    }
}

class MediaListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "MediaListenerService"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
        activeNotifications?.forEach { onNotificationPosted(it) }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        val notification = sbn.notification
        val extras = notification.extras

        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val artist = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val album = extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""
        val isPlaying = extras.getBoolean("android.media.session.active", false)

        Log.d(TAG, "Media update: pkg=$pkg title=$title artist=$artist album=$album playing=$isPlaying")

        // Update Compose StateFlow
        MediaState.update(
            MediaInfo(
                pkg = pkg,
                title = title,
                artist = artist,
                album = album,
                isPlaying = isPlaying
            )
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        Log.d(TAG, "Notification removed: pkg=${sbn.packageName}")
        // Optionally reset info if your removed notification is the currently displayed media
    }
}
