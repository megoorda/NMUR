package com.nmur.reelnotes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.widget.RemoteViews
import com.nmur.reelnotes.MainActivity
import com.nmur.reelnotes.R
import com.nmur.reelnotes.data.AppDatabase
import com.nmur.reelnotes.data.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Home-screen widget: next reminder, quiz cards due, or a random tip from your notes. */
class TipWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                render(context, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_NEXT) refresh(context)
    }

    companion object {
        private const val ACTION_NEXT = "com.nmur.reelnotes.widget.NEXT"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TipWidget::class.java))
            if (ids.isEmpty()) return
            scope.launch { render(context, manager, ids) }
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val db = AppDatabase.get(context)
            val reminder = db.notes().nextReminder()
            val due = db.quiz().dueCountNow()
            val done = db.notes().allNow().filter { it.status == Status.DONE }

            var noteId = -1L
            val (label, title, body) = when {
                reminder != null -> {
                    noteId = reminder.id
                    Triple(
                        "⏰ " + DateUtils.getRelativeTimeSpanString(reminder.remindAt).toString(),
                        reminder.title, reminder.summary,
                    )
                }
                done.isEmpty() -> Triple("ReelNotes", "No notes yet", "Share a reel from Instagram to ReelNotes.")
                else -> {
                    val n = done.random()
                    noteId = n.id
                    val c = n.content
                    val tips = buildList {
                        c?.keyPoints?.let(::addAll)
                        c?.facts?.let(::addAll)
                        c?.tools?.forEach { add("${it.name}: ${it.description}") }
                        c?.links?.forEach { add("${it.title}: ${it.url}") }
                    }
                    Triple("💡 " + n.category, n.title, tips.randomOrNull() ?: n.summary)
                }
            }

            val open = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_NOTE_ID, noteId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val openPi = PendingIntent.getActivity(context, 7001, open,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val nextPi = PendingIntent.getBroadcast(context, 7002,
                Intent(context, TipWidget::class.java).setAction(ACTION_NEXT), PendingIntent.FLAG_IMMUTABLE)
            val review = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SCREEN, MainActivity.SCREEN_REVIEW)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val reviewPi = PendingIntent.getActivity(context, 7003, review,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

            val views = RemoteViews(context.packageName, R.layout.widget_tip).apply {
                setTextViewText(R.id.widget_label, label)
                setTextViewText(R.id.widget_title, title)
                setTextViewText(R.id.widget_body, body)
                setTextViewText(R.id.widget_review, if (due > 0) "🧠 Review $due" else "")
                setOnClickPendingIntent(R.id.widget_root, openPi)
                setOnClickPendingIntent(R.id.widget_next, nextPi)
                setOnClickPendingIntent(R.id.widget_review, reviewPi)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
