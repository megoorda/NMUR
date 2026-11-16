package com.nmur.reelnotes

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.nmur.reelnotes.data.AppDatabase
import com.nmur.reelnotes.data.Note
import com.nmur.reelnotes.data.Settings
import com.nmur.reelnotes.work.ProcessWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Invisible activity that receives "Share → ReelNotes", saves the item and returns you to Instagram. */
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = listOfNotNull(
            intent.getStringExtra(Intent.EXTRA_SUBJECT),
            intent.getStringExtra(Intent.EXTRA_TEXT),
        ).distinct().joinToString("\n").trim()
        val uris = streams(intent)

        if (text.isBlank() && uris.isEmpty()) {
            Toast.makeText(this, "Nothing to save", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val configured = Settings(this).isConfigured
        Toast.makeText(
            this,
            if (configured) "Saved to ReelNotes, summarizing…" else "Saved. Open ReelNotes → Settings to connect your PC.",
            Toast.LENGTH_SHORT,
        ).show()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val files = copyFiles(uris)
                val url = Regex("https?://\\S+").find(text)?.value.orEmpty()
                val id = AppDatabase.get(applicationContext).notes().insert(
                    Note(sharedText = text, sourceUrl = url, localFiles = files.joinToString("\n") { it.absolutePath })
                )
                if (configured) ProcessWorker.enqueue(applicationContext, id)
            }
            finish()
        }
    }

    @Suppress("DEPRECATION")
    private fun streams(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND_MULTIPLE ->
            if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        else -> listOfNotNull(
            if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_STREAM)
        )
    }.take(10)

    /** Copy shared media into app storage now; the permission to read it ends when we finish. */
    private fun copyFiles(uris: List<Uri>): List<File> {
        val dir = File(filesDir, "media").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        return uris.mapIndexedNotNull { i, uri ->
            runCatching {
                val type = contentResolver.getType(uri)
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(type) ?: "bin"
                val out = File(dir, "${stamp}_$i.$ext")
                contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
                out
            }.getOrNull()
        }
    }
}
