package com.example.fiddler.subapps.Fidland

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.fiddler.subapps.Fidland.music.MusicApp
import com.example.fiddler.subapps.Fidland.music.MusicAppsRepository
import com.example.fiddler.subapps.Fidland.phs3.download.NotificationDownloadSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.fiddler.subapps.Fidland.phs3.record.RecorderNotificationSource
import com.example.fiddler.subapps.Fidland.phs3.timer.TimerNotificationSource

/**
 * Two responsibilities:
 *
 * 1. Provides the ComponentName that MediaSessionManager requires to call
 *    getActiveSessions(). SpotifyListener and YTMusicListener both reference
 *    this class for that purpose.
 *
 * 2. Captures non-media notifications and exposes them via [activeNotifications]
 *    StateFlow for the phase 3 notification indicator.
 *
 * 3. [NEW] Feeds download progress notifications to [NotificationDownloadSource]
 *    via the static [downloadSource] reference, which DownloadPhs3Trigger
 *    wires up after it creates the aggregator.
 */
class NotificationListenerService : NotificationListenerService() {

    companion object {
        // Exposed for phs3 notification indicator
        private val _activeNotifications = MutableStateFlow<List<NotificationInfo>>(emptyList())
        val activeNotifications: StateFlow<List<NotificationInfo>> =
            _activeNotifications.asStateFlow()

        // Music app packages — handled by SpotifyListener / YTMusicListener
        private val MUSIC_PACKAGES = setOf(
            MusicApp.SPOTIFY_PACKAGE,
            MusicApp.YTMUSIC_PACKAGE
        )

        /**
         * Set by DownloadPhs3Trigger after it creates the aggregator.
         * Nullable — safe if the trigger isn't started.
         */
        var downloadSource: NotificationDownloadSource? = null

        var recorderSource: RecorderNotificationSource? = null

        var timerNotificationSource: TimerNotificationSource? = null

    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        timerNotificationSource?.onNotificationPosted(sbn)  // ← add this line
        downloadSource?.onNotificationPosted(sbn)
        recorderSource?.onNotificationPosted(sbn)

        if (sbn.packageName in MUSIC_PACKAGES) return
        if (sbn.isOngoing) return
        refreshNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        timerNotificationSource?.onNotificationRemoved(sbn) // ← add this line
        downloadSource?.onNotificationRemoved(sbn)
        recorderSource?.onNotificationRemoved(sbn)
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
                        title       = title,
                        text        = extras.getCharSequence(
                            android.app.Notification.EXTRA_TEXT
                        )?.toString() ?: "",
                        postedAt    = sbn.postTime
                    )
                } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        _activeNotifications.value = current
    }
}

data class NotificationInfo(
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long
)