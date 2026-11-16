package com.nmur.reelnotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nmur.reelnotes.data.AppDatabase
import com.nmur.reelnotes.data.ChatTurn
import com.nmur.reelnotes.data.Knowledge
import com.nmur.reelnotes.data.Note
import com.nmur.reelnotes.data.ServerApi
import com.nmur.reelnotes.data.Settings
import kotlinx.coroutines.launch

private data class ChatMessage(val fromUser: Boolean, val text: String, val sources: List<Note> = emptyList(), val error: Boolean = false)

/** Chat with the AI about everything you've saved. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AskScreen(onBack: () -> Unit, onOpenNote: (Long) -> Unit) {
    val ctx = LocalContext.current
    val settings = remember { Settings(ctx) }
    val notes by AppDatabase.get(ctx).notes().all().collectAsStateWithLifecycle(initialValue = emptyList())
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
    }

    fun send(q: String) {
        if (q.isBlank() || busy) return
        val history = messages.filter { !it.error }.map { ChatTurn(if (it.fromUser) "user" else "assistant", it.text) }
        messages += ChatMessage(true, q)
        input = ""
        busy = true
        scope.launch {
            try {
                val context = Knowledge.candidates(q + " " + history.takeLast(2).joinToString(" ") { it.text }, notes)
                if (context.isBlank()) {
                    messages += ChatMessage(false, "Your library has no finished notes yet. Share some reels first!")
                } else {
                    val r = ServerApi(settings).ask(q, context, history, settings.provider)
                    val byId = notes.associateBy { it.id }
                    messages += ChatMessage(false, r.answer, r.usedNoteIds.mapNotNull { byId[it] })
                }
            } catch (e: Exception) {
                messages += ChatMessage(false, e.message ?: "Something went wrong", error = true)
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask your notes") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (messages.isEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ask anything about the reels you saved. Answers come only from your notes, " +
                            "using ${if (settings.provider == "claude") "Claude" else "Gemini"} on your PC.")
                        listOf(
                            "Which free gaming websites did I save?",
                            "Make me a study plan from my AI engineering notes",
                            "What Android emulators do I have links for?",
                            "Summarize what I saved this week",
                        ).forEach { s -> SuggestionChip(onClick = { send(s) }, label = { Text(s) }) }
                    }
                }
                items(messages) { m -> Bubble(m, onOpenNote) }
                if (busy) item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Thinking…")
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask about your notes…") },
                    maxLines = 4,
                )
                IconButton(onClick = { send(input.trim()) }, enabled = !busy && input.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Bubble(m: ChatMessage, onOpenNote: (Long) -> Unit) {
    val colors = when {
        m.error -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        m.fromUser -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else -> CardDefaults.cardColors()
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
        Card(colors = colors, modifier = Modifier.widthIn(max = 340.dp)) {
            Column(Modifier.padding(12.dp)) {
                SelectionContainer { Text(m.text) }
                if (m.sources.isNotEmpty()) {
                    Text("From your notes:", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        m.sources.forEach { n ->
                            AssistChip(onClick = { onOpenNote(n.id) }, label = {
                                Text(n.title.ifBlank { "Note" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            })
                        }
                    }
                }
            }
        }
    }
}
