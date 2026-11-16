package com.nmur.reelnotes.ui

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nmur.reelnotes.data.AppDatabase
import com.nmur.reelnotes.data.DEFAULT_CATEGORIES
import com.nmur.reelnotes.data.Note
import com.nmur.reelnotes.data.Portability
import com.nmur.reelnotes.data.Providers
import com.nmur.reelnotes.data.ServerApi
import com.nmur.reelnotes.data.Settings
import com.nmur.reelnotes.work.DigestWorker
import com.nmur.reelnotes.work.ProcessWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val settings = remember { Settings(ctx) }
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(settings.serverUrl) }
    var token by remember { mutableStateOf(settings.token) }
    var provider by remember { mutableStateOf(settings.provider) }
    var categories by remember { mutableStateOf(settings.categories.joinToString("\n")) }
    var status by remember { mutableStateOf("") }
    var digestOn by remember { mutableStateOf(settings.digestEnabled) }
    var digestHour by remember { mutableStateOf(settings.digestHour) }
    var dataMsg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun work(label: String, block: suspend () -> String) {
        busy = true; dataMsg = "$label…"
        scope.launch {
            dataMsg = try { block() } catch (e: Exception) { "❌ ${e.message}" }
            busy = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) work("Reading Instagram export") {
            val urls = withContext(Dispatchers.IO) { Portability.parseInstagramExport(ctx, uri) }
            val dao = AppDatabase.get(ctx).notes()
            val ids = urls.filter { dao.countByUrl(it) == 0 }.map { dao.insert(Note(sharedText = it, sourceUrl = it)) }
            if (settings.isConfigured) ProcessWorker.enqueueSequential(ctx, ids)
            "✅ Found ${urls.size} saved posts, added ${ids.size} new. They'll be processed one by one" +
                if (settings.isConfigured) " while your PC is on." else " once the server is set."
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) work("Exporting") {
            val n = withContext(Dispatchers.IO) { Portability.exportMarkdownZip(ctx, uri) }
            "✅ Exported $n notes as Markdown. Unzip into your Obsidian vault, or Notion → Import → Markdown."
        }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) work("Backing up") {
            val json = Portability.backupJson(ctx)
            withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(json.toByteArray()) } }
            "✅ Backup saved."
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) work("Restoring") {
            val json = withContext(Dispatchers.IO) {
                ctx.contentResolver.openInputStream(uri)!!.use { it.bufferedReader().readText() }
            }
            "✅ Restored ${Portability.restore(ctx, json)} notes."
        }
    }

    fun save() {
        settings.serverUrl = url
        settings.token = token
        settings.provider = provider
        settings.categories = categories.lines()
        val digestChanged = settings.digestEnabled != digestOn || settings.digestHour != digestHour
        settings.digestEnabled = digestOn
        settings.digestHour = digestHour
        if (digestChanged) DigestWorker.schedule(ctx)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { save(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("PC server", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Server address") },
                placeholder = { Text("http://my-pc:8787") },
                supportingText = { Text("Your PC's Tailscale name or IP, e.g. http://100.101.102.103:8787") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            OutlinedTextField(
                value = token, onValueChange = { token = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Token") },
                supportingText = { Text("REELNOTES_TOKEN from server\\.env") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    save()
                    status = "Testing…"
                    scope.launch {
                        status = runCatching { "✅ Connected: " + ServerApi(settings).health() }
                            .getOrElse { "❌ ${it.message}" }
                    }
                }) { Text("Test connection") }
            }
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)

            Text("AI used for summaries", style = MaterialTheme.typography.titleMedium)
            ProviderOption(
                selected = provider == Providers.GEMINI,
                title = "Gemini (free API)",
                subtitle = "Watches the whole video. Free tier; Google may use free-tier data to improve its products.",
            ) { provider = Providers.GEMINI }
            ProviderOption(
                selected = provider == Providers.CLAUDE,
                title = "Claude (your Pro plan)",
                subtitle = "Transcribes on your GPU and reads video frames. Uses your Claude Pro limits. Personal use.",
            ) { provider = Providers.CLAUDE }

            Text("Categories (one per line)", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = categories, onValueChange = { categories = it }, modifier = Modifier.fillMaxWidth(),
                minLines = 5,
            )
            OutlinedButton(onClick = { categories = DEFAULT_CATEGORIES.joinToString("\n") }) { Text("Reset categories") }

            HorizontalDivider()
            Text("Daily digest", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("One notification a day: what you saved, quizzes due, unfinished steps",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Switch(checked = digestOn, onCheckedChange = { digestOn = it })
            }
            if (digestOn) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Time: ")
                OutlinedButton(onClick = { digestHour = (digestHour + 23) % 24 }) { Text("−") }
                Text(" %02d:00 ".format(digestHour), style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { digestHour = (digestHour + 1) % 24 }) { Text("+") }
            }

            HorizontalDivider()
            Text("Your data", style = MaterialTheme.typography.titleMedium)
            Text("Import Instagram saves: in Instagram go to Settings → Accounts Center → Your information and " +
                "permissions → Download your information → choose \"Saved\" (JSON format). When the email arrives, " +
                "download the .zip on your phone and pick it here.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(),
                onClick = { importLauncher.launch(arrayOf("application/zip", "application/json", "text/html", "*/*")) }
            ) { Text("Import Instagram saved posts") }
            OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(),
                onClick = { exportLauncher.launch("ReelNotes-markdown.zip") }
            ) { Text("Export to Obsidian / Notion (Markdown .zip)") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, modifier = Modifier.weight(1f),
                    onClick = { backupLauncher.launch("reelnotes-backup.json") }) { Text("Backup to file") }
                OutlinedButton(enabled = !busy, modifier = Modifier.weight(1f),
                    onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) }) { Text("Restore file") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, modifier = Modifier.weight(1f), onClick = {
                    save()
                    work("Backing up to PC") {
                        ServerApi(settings).uploadBackup(Portability.backupJson(ctx))
                        settings.lastBackupAt = System.currentTimeMillis()
                        "✅ Backed up to your PC (server\\backups)."
                    }
                }) { Text("Backup to PC") }
                OutlinedButton(enabled = !busy, modifier = Modifier.weight(1f), onClick = {
                    save()
                    work("Restoring from PC") {
                        "✅ Restored ${Portability.restore(ctx, ServerApi(settings).downloadBackup())} notes from PC."
                    }
                }) { Text("Restore from PC") }
            }
            val last = settings.lastBackupAt
            Text(
                if (last > 0) "Last automatic PC backup: " + DateUtils.getRelativeTimeSpanString(last)
                else "The library is also backed up to your PC automatically once a day.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (dataMsg.isNotBlank()) Text(dataMsg, style = MaterialTheme.typography.bodyMedium)

            Button(
                onClick = {
                    save()
                    scope.launch {
                        // Process anything that was shared before the server was set up.
                        if (settings.isConfigured) {
                            AppDatabase.get(ctx).notes().pendingIds().forEach { ProcessWorker.enqueue(ctx, it) }
                        }
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun ProviderOption(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
