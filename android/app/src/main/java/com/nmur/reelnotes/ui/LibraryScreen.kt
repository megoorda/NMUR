package com.nmur.reelnotes.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nmur.reelnotes.data.AppDatabase
import com.nmur.reelnotes.data.Note
import com.nmur.reelnotes.data.Settings
import com.nmur.reelnotes.data.Status
import com.nmur.reelnotes.work.ProcessWorker
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private const val ALL = "All"
private const val FAVS = "★ Saved"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpen: (Long) -> Unit, onNavigate: (String) -> Unit) {
    val onSettings = { onNavigate(Screens.SETTINGS) }
    val ctx = LocalContext.current
    val dao = remember { AppDatabase.get(ctx).notes() }
    val notes by dao.all().collectAsStateWithLifecycle(initialValue = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ALL) }
    var showAdd by remember { mutableStateOf(false) }
    val configured = remember { Settings(ctx).isConfigured }
    val now = remember { System.currentTimeMillis() }
    val due by AppDatabase.get(ctx).quiz().dueCount(now).collectAsStateWithLifecycle(initialValue = 0)

    val categories = remember(notes) {
        notes.map { it.category }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }
    }
    val q = query.trim().lowercase()
    val shown = notes.filter { n ->
        (filter == ALL || (filter == FAVS && n.favorite) || n.category == filter) &&
            (q.isEmpty() || n.searchText.contains(q) || n.sharedText.lowercase().contains(q))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ReelNotes") },
                actions = {
                    IconButton(onClick = { onNavigate(Screens.ASK) }) { Icon(Icons.Default.AutoAwesome, "Ask AI") }
                    IconButton(onClick = { onNavigate(Screens.REVIEW) }) {
                        BadgedBox(badge = { if (due > 0) Badge { Text("$due") } }) {
                            Icon(Icons.Default.School, "Review quizzes")
                        }
                    }
                    IconButton(onClick = { onNavigate(Screens.GRAPH) }) { Icon(Icons.Default.Hub, "Knowledge graph") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "Add link") }
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!configured) {
                item {
                    Card(Modifier.fillMaxWidth().clickable(onClick = onSettings)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Connect your PC", fontWeight = FontWeight.Bold)
                            Text("Tap to enter your server address and token. Shared reels are saved and will be processed once connected.")
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search notes, links, tools…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") }
                    },
                    singleLine = true,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(ALL, FAVS) + categories) { c ->
                        FilterChip(selected = filter == c, onClick = { filter = c }, label = { Text(c) })
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        if (notes.isEmpty()) "No notes yet.\n\nIn Instagram, tap Share on a reel → ReelNotes."
                        else "Nothing matches.",
                        modifier = Modifier.padding(top = 32.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            items(shown, key = { it.id }) { NoteCard(it) { onOpen(it.id) } }
        }
    }

    if (showAdd) AddLinkDialog(onDismiss = { showAdd = false })
}

@Composable
private fun NoteCard(note: Note, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    note.category.ifBlank { "Unsorted" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (note.favorite) Icon(Icons.Default.Star, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(
                    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(note.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                note.title.ifBlank { note.sharedText.ifBlank { "Shared media" } },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            when (note.status) {
                Status.DONE -> Text(note.summary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium)
                Status.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text(note.error, color = MaterialTheme.colorScheme.error, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (note.status == Status.PROCESSING) "Summarizing…" else note.error.ifBlank { "Waiting…" },
                        style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddLinkDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember {
        val clip = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        mutableStateOf(if (clip.startsWith("http")) clip else "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a link") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Reel / post URL") })
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = {
                scope.launch {
                    val id = AppDatabase.get(ctx).notes().insert(Note(sharedText = text.trim(), sourceUrl = text.trim()))
                    if (Settings(ctx).isConfigured) ProcessWorker.enqueue(ctx, id)
                    onDismiss()
                }
            }) { Text("Summarize") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
