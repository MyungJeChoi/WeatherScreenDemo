package com.example.lockloop.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationHelper {
    const val CHANNEL_ID = "lockloop_channel"
    fun ensureChannel(context: Context) {
        val ch = NotificationChannel(CHANNEL_ID, "LockLoop", NotificationManager.IMPORTANCE_DEFAULT)
        manager(context).createNotificationChannel(ch)
    }
    fun manager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
