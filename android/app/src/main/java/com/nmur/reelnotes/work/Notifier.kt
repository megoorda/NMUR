package com.nmur.reelnotes.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nmur.reelnotes.MainActivity
import com.nmur.reelnotes.R
import com.nmur.reelnotes.ReelNotesApp

object Notifier {
    const val ID_DIGEST = 900_000_001
    private const val REMINDER_OFFSET = 500_000_000

    /** [screen] is an optional MainActivity.SCREEN_* to open instead of a note. */
    fun show(
        ctx: Context, id: Int, title: String, text: String,
        noteId: Long = -1, screen: String? = null, channel: String = ReelNotesApp.CHANNEL_ID,
    ) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val open = Intent(ctx, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_NOTE_ID, noteId)
            .putExtra(MainActivity.EXTRA_SCREEN, screen)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    fun reminderId(noteId: Long) = REMINDER_OFFSET + noteId.toInt()
}
