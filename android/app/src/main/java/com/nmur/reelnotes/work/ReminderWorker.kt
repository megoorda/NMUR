package com.nmur.reelnotes.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nmur.reelnotes.ReelNotesApp
import com.nmur.reelnotes.data.AppDatabase
import com.nmur.reelnotes.widget.TipWidget
import java.util.concurrent.TimeUnit

/** Fires the "do this now" reminder for one note. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1)
        val dao = AppDatabase.get(applicationContext).notes()
        val note = dao.get(id) ?: return Result.success()
        if (note.remindAt == 0L) return Result.success()
        val c = note.content
        val total = (c?.steps?.size ?: 0) + (c?.roadmap?.sumOf { it.items.size } ?: 0)
        val left = total - note.checkedSet.size
        val detail = when {
            total > 0 && left > 0 -> "$left of $total steps left. Tap to continue."
            else -> note.summary.ifBlank { "Tap to open your note." }
        }
        Notifier.show(applicationContext, Notifier.reminderId(id), "⏰ ${note.title.ifBlank { "Reminder" }}", detail,
            noteId = id, channel = ReelNotesApp.CHANNEL_REMINDERS)
        dao.update(note.copy(remindAt = 0))
        TipWidget.refresh(applicationContext)
        return Result.success()
    }

    companion object {
        private const val KEY_ID = "note_id"

        fun schedule(context: Context, noteId: Long, at: Long) {
            val delay = (at - System.currentTimeMillis()).coerceAtLeast(0)
            val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_ID to noteId))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("remind-$noteId", ExistingWorkPolicy.REPLACE, req)
        }

        fun cancel(context: Context, noteId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork("remind-$noteId")
        }
    }
}
