package com.nmur.reelnotes.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nmur.reelnotes.MainActivity
import com.nmur.reelnotes.R
import com.nmur.reelnotes.ReelNotesApp
import com.nmur.reelnotes.data.AppDatabase
import com.nmur.reelnotes.data.AppJson
import com.nmur.reelnotes.data.NoteContent
import com.nmur.reelnotes.data.ServerApi
import com.nmur.reelnotes.data.ServerException
import com.nmur.reelnotes.data.Settings
import com.nmur.reelnotes.data.Status
import java.io.File
import java.util.concurrent.TimeUnit

class ProcessWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1)
        val dao = AppDatabase.get(applicationContext).notes()
        val note = dao.get(id) ?: return Result.success()
        val settings = Settings(applicationContext)
        val provider = inputData.getString(KEY_PROVIDER) ?: settings.provider

        dao.update(note.copy(status = Status.PROCESSING, error = "", provider = provider))
        return try {
            val files = note.localFiles.lines().filter { it.isNotBlank() }.map { File(it) }
            val resp = ServerApi(settings).process(note.sharedText, files, provider)
            val c = resp.note
            dao.update(
                note.copy(
                    status = Status.DONE,
                    error = "",
                    provider = resp.provider,
                    sourceUrl = resp.sourceUrl.ifBlank { note.sourceUrl },
                    title = c.title,
                    category = c.category,
                    summary = c.summary,
                    creator = resp.creator,
                    caption = resp.caption,
                    transcript = resp.transcript,
                    contentJson = AppJson.encodeToString(NoteContent.serializer(), c),
                    searchText = buildSearchText(c, resp.creator, resp.caption, resp.transcript),
                )
            )
            notify(id, "🧠 ${c.title.ifBlank { "Note ready" }}", doneSummary(c))
            com.nmur.reelnotes.widget.TipWidget.refresh(applicationContext)
            Result.success()
        } catch (e: ServerException) {
            if (e.retryable && runAttemptCount < MAX_RETRIES) {
                dao.update(note.copy(status = Status.PENDING, error = "Waiting to retry: ${e.message}"))
                Result.retry()
            } else {
                dao.update(note.copy(status = Status.FAILED, error = e.message ?: "Unknown error"))
                notify(id, "Couldn't summarize", e.message ?: "")
                Result.failure()
            }
        } catch (e: Exception) {
            dao.update(note.copy(status = Status.FAILED, error = e.message ?: e.toString()))
            Result.failure()
        }
    }

    private fun doneSummary(c: NoteContent): String = buildList {
        add(c.category)
        if (c.steps.isNotEmpty()) add("${c.steps.size} steps")
        if (c.links.isNotEmpty()) add("${c.links.size} links")
        if (c.roadmap.isNotEmpty()) add("roadmap")
        if (c.tools.isNotEmpty()) add("${c.tools.size} tools")
    }.joinToString(" · ")

    private fun notify(id: Long, title: String, text: String) {
        val ctx = applicationContext
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val open = Intent(ctx, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_NOTE_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            ctx, id.toInt(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, ReelNotesApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id.toInt(), n)
    }

    companion object {
        private const val KEY_ID = "note_id"
        private const val KEY_PROVIDER = "provider"
        private const val MAX_RETRIES = 6

        fun enqueue(context: Context, noteId: Long, provider: String? = null) {
            val req = OneTimeWorkRequestBuilder<ProcessWorker>()
                .setInputData(workDataOf(KEY_ID to noteId, KEY_PROVIDER to provider))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("note-$noteId", ExistingWorkPolicy.REPLACE, req)
        }

        /** For bulk imports: process one at a time so Instagram and the AI aren't flooded. */
        fun enqueueSequential(context: Context, noteIds: List<Long>) {
            val wm = WorkManager.getInstance(context)
            noteIds.forEach { id ->
                val req = OneTimeWorkRequestBuilder<ProcessWorker>()
                    .setInputData(workDataOf(KEY_ID to id))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                    .build()
                wm.enqueueUniqueWork("import-queue", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
            }
        }

        fun buildSearchText(c: NoteContent, creator: String, caption: String, transcript: String): String =
            listOf(
                c.title, c.category, c.summary, c.tags.joinToString(" "), c.keyPoints.joinToString(" "),
                c.links.joinToString(" ") { "${it.title} ${it.url} ${it.description}" },
                c.steps.joinToString(" "),
                c.roadmap.joinToString(" ") { it.stage + " " + it.items.joinToString(" ") },
                c.facts.joinToString(" "),
                c.tools.joinToString(" ") { "${it.name} ${it.description} ${it.url}" },
                creator, caption, transcript,
            ).joinToString("\n").lowercase()
    }
}
