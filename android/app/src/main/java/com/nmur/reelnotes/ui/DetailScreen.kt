package com.nmur.reelnotes.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nmur.reelnotes.data.AppDatabase
import com.nmur.reelnotes.data.Note
import com.nmur.reelnotes.data.NoteContent
import com.nmur.reelnotes.data.Providers
import com.nmur.reelnotes.data.Status
import com.nmur.reelnotes.work.ProcessWorker
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(noteId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { AppDatabase.get(ctx).notes() }
    val note by dao.observe(noteId).collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }

    val n = note
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(n?.category?.ifBlank { "Note" } ?: "Note", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (n != null) {
                        IconButton(onClick = { scope.launch { dao.update(n.copy(favorite = !n.favorite)) } }) {
                            Icon(if (n.favorite) Icons.Default.Star else Icons.Default.StarBorder, "Favorite")
                        }
                        IconButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, n.title)
                                .putExtra(Intent.EXTRA_TEXT, toMarkdown(n))
                            ctx.startActivity(Intent.createChooser(send, "Export note"))
                        }) { Icon(Icons.Default.Share, "Export") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                },
            )
        },
    ) { pad ->
        if (n == null) return@Scaffold
        val c = n.content
        val checked = n.checkedSet
        fun toggle(key: String) = scope.launch {
            val set = if (key in checked) checked - key else checked + key
            dao.update(n.copy(checked = set.joinToString(",")))
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SelectionContainer {
                    Text(n.title.ifBlank { n.sharedText.ifBlank { "Shared media" } },
                        style = MaterialTheme.typography.headlineSmall)
                }
                if (n.creator.isNotBlank()) Text("by @${n.creator.removePrefix("@")}", style = MaterialTheme.typography.labelLarge)
                if (n.sourceUrl.isNotBlank()) {
                    TextButton(onClick = { open(ctx, n.sourceUrl) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open original post")
                    }
                }
            }

            when (n.status) {
                Status.PENDING, Status.PROCESSING -> item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 16.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(if (n.status == Status.PROCESSING) "Summarizing with ${n.provider}…" else n.error.ifBlank { "Waiting…" })
                    }
                }
                Status.FAILED -> item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(n.error, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            if (c != null) noteSections(ctx, c, checked) { toggle(it) }

            if (n.caption.isNotBlank()) expandable("Original caption", n.caption)
            if (n.transcript.isNotBlank()) expandable("Transcript", n.transcript)

            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(if (n.status == Status.DONE) "Summarize again with:" else "Try with:",
                    style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { ProcessWorker.enqueue(ctx, n.id, Providers.GEMINI) }) { Text("Gemini") }
                    OutlinedButton(onClick = { ProcessWorker.enqueue(ctx, n.id, Providers.CLAUDE) }) { Text("Claude") }
                }
            }
        }
    }

    if (confirmDelete && n != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this note?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        n.localFiles.lines().filter { it.isNotBlank() }.forEach { File(it).delete() }
                        dao.delete(n.id)
                        onBack()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.noteSections(
    ctx: android.content.Context,
    c: NoteContent,
    checked: Set<String>,
    toggle: (String) -> Unit,
) {
    if (c.tags.isNotEmpty()) item {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            c.tags.forEach { AssistChip(onClick = {}, label = { Text("#$it") }) }
        }
    }
    if (c.summary.isNotBlank()) {
        header("Summary")
        item { SelectionContainer { Text(c.summary, style = MaterialTheme.typography.bodyLarge) } }
    }
    if (c.linkInBio) item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Text("ℹ️ The creator says the link/resource is in their bio, comments or DMs. Open the original post to get it.",
                Modifier.padding(12.dp))
        }
    }
    if (c.links.isNotEmpty()) {
        header("Links")
        c.links.forEach { l -> item { LinkCard(l.title, l.url, l.description, l.reachable) { open(ctx, l.url) } } }
    }
    if (c.steps.isNotEmpty()) {
        header("Steps")
        c.steps.forEachIndexed { i, s -> item { CheckRow("${i + 1}. $s", "s$i" in checked) { toggle("s$i") } } }
    }
    if (c.roadmap.isNotEmpty()) {
        header("Roadmap")
        c.roadmap.forEachIndexed { si, stage ->
            item { Text(stage.stage, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp)) }
            stage.items.forEachIndexed { ii, it ->
                item { CheckRow(it, "r$si.$ii" in checked) { toggle("r$si.$ii") } }
            }
        }
    }
    if (c.keyPoints.isNotEmpty()) {
        header("Key points")
        c.keyPoints.forEach { item { Bullet(it) } }
    }
    if (c.facts.isNotEmpty()) {
        header("Facts")
        c.facts.forEach { item { Bullet(it) } }
    }
    if (c.tools.isNotEmpty()) {
        header("Tools & sites mentioned")
        c.tools.forEach { t ->
            item {
                LinkCard(t.name, t.url, t.description, t.reachable) { if (t.url.isNotBlank()) open(ctx, t.url) }
            }
        }
    }
}

private fun LazyListScope.header(text: String) = item {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp))
}

private fun LazyListScope.expandable(title: String, body: String) = item {
    var open by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { open = !open }) { Text(if (open) "Hide $title" else "Show $title") }
        if (open) SelectionContainer { Text(body, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun Bullet(text: String) {
    SelectionContainer { Text("•  $text", style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun CheckRow(text: String, done: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = done, onCheckedChange = { onToggle() })
        Text(text, style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (done) TextDecoration.LineThrough else null)
    }
}

@Composable
private fun LinkCard(title: String, url: String, description: String, reachable: Boolean?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (reachable == false) Icons.Default.Warning else Icons.Default.Link, null,
                tint = if (reachable == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title.ifBlank { url }, fontWeight = FontWeight.SemiBold)
                if (url.isNotBlank()) Text(url, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall)
                if (reachable == false) Text("Link didn't respond when checked; it may be wrong or down.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun open(ctx: android.content.Context, url: String) {
    val u = if (url.startsWith("http")) url else "https://$url"
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
}

private fun toMarkdown(n: Note): String = buildString {
    val c = n.content
    appendLine("# ${n.title.ifBlank { "Note" }}")
    appendLine("Category: ${n.category}")
    if (n.sourceUrl.isNotBlank()) appendLine("Source: ${n.sourceUrl}")
    if (c == null) return@buildString
    appendLine().appendLine(c.summary)
    if (c.links.isNotEmpty()) {
        appendLine().appendLine("## Links")
        c.links.forEach { appendLine("- ${it.title}: ${it.url}${if (it.description.isNotBlank()) " (${it.description})" else ""}") }
    }
    if (c.steps.isNotEmpty()) {
        appendLine().appendLine("## Steps")
        c.steps.forEachIndexed { i, s -> appendLine("${i + 1}. $s") }
    }
    if (c.roadmap.isNotEmpty()) {
        appendLine().appendLine("## Roadmap")
        c.roadmap.forEach { st -> appendLine("### ${st.stage}"); st.items.forEach { appendLine("- [ ] $it") } }
    }
    if (c.keyPoints.isNotEmpty()) { appendLine().appendLine("## Key points"); c.keyPoints.forEach { appendLine("- $it") } }
    if (c.facts.isNotEmpty()) { appendLine().appendLine("## Facts"); c.facts.forEach { appendLine("- $it") } }
    if (c.tools.isNotEmpty()) {
        appendLine().appendLine("## Tools")
        c.tools.forEach { appendLine("- ${it.name}${if (it.url.isNotBlank()) " (${it.url})" else ""}: ${it.description}") }
    }
}
