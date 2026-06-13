package com.example.fiddler.subapps.Fidland

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.fiddler.subapps.Fidland.music.MusicApp
import com.example.fiddler.subapps.Fidland.music.MusicAppsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Two responsibilities:
 *
 * 1. Provides the ComponentName that MediaSessionManager requires to call
 *    getActiveSessions(). SpotifyListener and YTMusicListener both reference
 *    this class for that purpose — they don't call it directly, they just
 *    need its ComponentName to be active (i.e. the user must grant
 *    notification listener access in Settings).
 *
 * 2. Captures non-media notifications and exposes them via [activeNotifications]
 *    StateFlow for the phase 3 notification indicator (phs3).
 *    The notification stack UI (icon stacking, badge counts) will read
 *    from this flow when phs3 is built.
 *
 * Note: media metadata is handled by SpotifyListener / YTMusicListener
 * directly via MediaSession callbacks — NOT parsed from notification extras
 * here. That was the old approach and it was fragile.
 */
class NotificationListenerService : NotificationListenerService() {

    companion object {
        // Exposed for phs3 notification indicator
        private val _activeNotifications = MutableStateFlow<List<NotificationInfo>>(emptyList())
        val activeNotifications: StateFlow<List<NotificationInfo>> =
            _activeNotifications.asStateFlow()

        // Music app packages — notifications from these are handled by
        // their respective listeners, not here
        private val MUSIC_PACKAGES = setOf(
            MusicApp.SPOTIFY_PACKAGE,
            MusicApp.YTMUSIC_PACKAGE
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Sync current notifications on connect
        refreshNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName in MUSIC_PACKAGES) return // handled by listeners
        if (sbn.isOngoing) return                      // skip persistent/system notifications
        refreshNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshNotifications()
    }

    private fun refreshNotifications() {
        val current = try {
            activeNotifications
                ?.filter { it.packageName !in MUSIC_PACKAGES }
                ?.filter { !it.isOngoing }
                ?.mapNotNull { sbn ->
                    val extras = sbn.notification.extras
                    val title = extras.getCharSequence(
                        android.app.Notification.EXTRA_TITLE
                    )?.toString() ?: return@mapNotNull null

                    NotificationInfo(
                        packageName = sbn.packageName,
                        title = title,
                        text = extras.getCharSequence(
                            android.app.Notification.EXTRA_TEXT
                        )?.toString() ?: "",
                        postedAt = sbn.postTime
                    )
                } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        _activeNotifications.value = current
    }
}

/**
 * Lightweight notification model for phs3.
 * The notification stack indicator will group these by packageName
 * and cycle through app icons every 2 seconds.
 */
data class NotificationInfo(
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long
)