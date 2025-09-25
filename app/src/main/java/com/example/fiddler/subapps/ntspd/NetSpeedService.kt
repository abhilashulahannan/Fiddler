package com.example.fiddler.subapps.ntspd

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import com.example.fiddler.R

class NetSpeedService : Service() {

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "netspeed_channel",
                "Network Speed",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val placement = intent?.getStringExtra("placement") ?: "right"
        val offset = intent?.getIntExtra("offset", 0) ?: 0

        val notificationLayout = RemoteViews(packageName, R.layout.notification_netspeed)
        notificationLayout.setTextViewText(R.id.txt_download, "↓ 0.00 KB/s")
        notificationLayout.setTextViewText(R.id.txt_upload, "↑ 0.00 KB/s")

        val notification = Notification.Builder(this, "netspeed_channel")
            .setSmallIcon(R.drawable.ic_network_check)
            .setCustomContentView(notificationLayout)
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
