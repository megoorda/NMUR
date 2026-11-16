package com.nmur.reelnotes.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nmur.reelnotes.data.AppDatabase
import com.nmur.reelnotes.data.QuizCard
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Spaced repetition: right answers come back later and later (1, 3, 7, 16… days); wrong ones tomorrow. */
object Srs {
    fun answer(card: QuizCard, correct: Boolean): QuizCard {
        val now = System.currentTimeMillis()
        return if (correct) {
            val next = when (card.intervalDays) {
                0 -> 1
                1 -> 3
                else -> (card.intervalDays * 2.3).toInt().coerceAtMost(180)
            }
            card.copy(intervalDays = next, dueAt = now + TimeUnit.DAYS.toMillis(next.toLong()), reviews = card.reviews + 1)
        } else {
            card.copy(intervalDays = 0, dueAt = now + TimeUnit.HOURS.toMillis(20), reviews = card.reviews + 1,
                lapses = card.lapses + 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(onBack: () -> Unit, onOpenNote: (Long) -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { AppDatabase.get(ctx).quiz() }
    val scope = rememberCoroutineScope()
    var cards by remember { mutableStateOf<List<QuizCard>?>(null) }
    var index by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }
    var correctCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { cards = dao.due().shuffled() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        val list = cards
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                list == null -> Text("Loading…")
                list.isEmpty() -> {
                    Text("Nothing to review right now 🎉", style = MaterialTheme.typography.titleLarge)
                    Text("Open any note and tap \"Make quiz\" to turn it into questions. " +
                        "Cards come back on a schedule (1, 3, 7, 16… days) so you remember what you saved.")
                }
                index >= list.size -> {
                    Text("Done! $correctCount / ${list.size} correct", style = MaterialTheme.typography.titleLarge)
                    Text("Missed cards come back tomorrow; the rest come back later and later.")
                    Button(onClick = onBack) { Text("Finish") }
                }
                else -> {
                    val card = list[index]
                    LinearProgressIndicator(progress = { index / list.size.toFloat() }, modifier = Modifier.fillMaxWidth())
                    Text("${index + 1} / ${list.size}", style = MaterialTheme.typography.labelMedium)
                    Text(card.question, style = MaterialTheme.typography.titleLarge)
                    card.optionList.forEachIndexed { i, opt ->
                        val p = picked
                        val color = when {
                            p == null -> MaterialTheme.colorScheme.surfaceVariant
                            i == card.answerIndex -> MaterialTheme.colorScheme.primaryContainer
                            i == p -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = color),
                            border = if (p != null && i == card.answerIndex) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.fillMaxWidth().clickable(enabled = p == null) {
                                picked = i
                                val ok = i == card.answerIndex
                                if (ok) correctCount++
                                scope.launch { dao.update(Srs.answer(card, ok)) }
                            },
                        ) { Text(opt, Modifier.padding(14.dp)) }
                    }
                    val p = picked
                    if (p != null) {
                        Text(if (p == card.answerIndex) "✅ Correct" else "❌ Not quite", fontWeight = FontWeight.Bold)
                        if (card.explanation.isNotBlank()) Text(card.explanation)
                        TextButton(onClick = { onOpenNote(card.noteId) }) { Text("Open the note") }
                        Button(onClick = { picked = null; index++ }, modifier = Modifier.fillMaxWidth()) { Text("Next") }
                    }
                }
            }
        }
    }
}
