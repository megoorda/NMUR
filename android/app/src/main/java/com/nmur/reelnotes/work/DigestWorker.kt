package com.nmur.reelnotes.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nmur.reelnotes.MainActivity
import com.nmur.reelnotes.ReelNotesApp
import com.nmur.reelnotes.data.AppDatabase
import com.nmur.reelnotes.data.ServerApi
import com.nmur.reelnotes.data.Settings
import com.nmur.reelnotes.data.Status
import com.nmur.reelnotes.data.Portability
import com.nmur.reelnotes.widget.TipWidget
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Once a day: notification with what you saved and what's waiting (unfinished steps, quiz cards),
 * refreshes the home-screen widget, and backs the library up to the PC when it can.
 */
class DigestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val settings = Settings(ctx)
        val db = AppDatabase.get(ctx)
        val notes = db.notes().allNow()
        val dayAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)

        val savedToday = notes.count { it.createdAt >= dayAgo }
        val unfinished = notes.filter { n ->
            val c = n.content ?: return@filter false
            val total = c.steps.size + c.roadmap.sumOf { it.items.size }
            total > 0 && n.checkedSet.size in 1 until total
        }
        val dueCards = db.quiz().dueCountNow()
        val failed = notes.count { it.status == Status.FAILED }

        if (settings.digestEnabled) {
            val lines = buildList {
                if (savedToday > 0) add("📥 $savedToday reel${if (savedToday > 1) "s" else ""} saved today")
                if (dueCards > 0) add("🧠 $dueCards quiz card${if (dueCards > 1) "s" else ""} to review")
                unfinished.take(3).forEach { add("▶️ Continue: ${it.title}") }
                if (failed > 0) add("⚠️ $failed note${if (failed > 1) "s" else ""} couldn't be processed")
            }
            if (lines.isNotEmpty()) {
                Notifier.show(
                    ctx, Notifier.ID_DIGEST, "Your ReelNotes digest", lines.joinToString("\n"),
                    screen = if (dueCards > 0) MainActivity.SCREEN_REVIEW else null,
                    channel = ReelNotesApp.CHANNEL_DIGEST,
                )
            }
        }

        TipWidget.refresh(ctx)

        if (settings.isConfigured) {
            runCatching {
                ServerApi(settings).uploadBackup(Portability.backupJson(ctx))
                settings.lastBackupAt = System.currentTimeMillis()
            }
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "daily-digest"

        /** (Re)schedule for the chosen hour every day. */
        fun schedule(context: Context) {
            val hour = Settings(context).digestHour
            val now = Calendar.getInstance()
            val next = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            val req = PeriodicWorkRequestBuilder<DigestWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
