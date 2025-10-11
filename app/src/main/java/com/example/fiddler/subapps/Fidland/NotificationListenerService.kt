package com.example.fiddler.subapps.Fidland

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.os.Bundle
import android.util.Log

class MediaListenerService : NotificationListenerService() {

    companion object {
        const val TAG = "MediaListenerService"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
        // Optionally fetch active notifications at start
        val activeNotifications = activeNotifications
        activeNotifications?.forEach { onNotificationPosted(it) }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val pkg = sbn.packageName
        val notification = sbn.notification
        val extras: Bundle = notification.extras

        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val artist = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val album = extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""
        val isPlaying = extras.getBoolean("android.media.session.active", false)

        Log.d(TAG, "Media update: pkg=$pkg title=$title artist=$artist album=$album playing=$isPlaying")

        // You can now send this info to your controller or broadcast
        val intent = Intent("com.example.fiddler.MEDIA_UPDATE").apply {
            putExtra("pkg", pkg)
            putExtra("title", title)
            putExtra("artist", artist)
            putExtra("album", album)
            putExtra("playing", isPlaying)
        }
        sendBroadcast(intent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        // Optional: handle media session removed
        Log.d(TAG, "Notification removed: pkg=${sbn.packageName}")
    }
}
