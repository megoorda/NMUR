package com.nmur.reelnotes

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.nmur.reelnotes.work.DigestWorker

class ReelNotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Summaries", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Tells you when a shared reel has been summarized" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Reminders you set on notes" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DIGEST, "Daily digest", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "One daily summary of saved reels, quizzes and unfinished steps" }
        )
        DigestWorker.schedule(this)
    }

    companion object {
        const val CHANNEL_ID = "summaries"
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_DIGEST = "digest"
    }
}
