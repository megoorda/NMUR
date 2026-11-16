package com.nmur.reelnotes

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class ReelNotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL_ID, "Summaries", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Tells you when a shared reel has been summarized"
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "summaries"
    }
}
