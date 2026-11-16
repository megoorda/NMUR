package com.nmur.reelnotes.data

import android.content.Context
import android.net.Uri
import kotlinx.serialization.Serializable
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Backup/restore, Markdown export (Obsidian/Notion) and Instagram "saved posts" import. */
object Portability {

    @Serializable
    data class Backup(
        val app: String = "ReelNotes",
        val version: Int = 1,
        val createdAt: Long = System.currentTimeMillis(),
        val notes: List<BackupNote> = emptyList(),
        val cards: List<BackupCard> = emptyList(),
    )

    @Serializable
    data class BackupNote(
        val id: Long, val createdAt: Long, val sharedText: String, val sourceUrl: String, val status: String,
        val error: String, val provider: String, val title: String, val category: String, val summary: String,
        val creator: String, val caption: String, val transcript: String, val contentJson: String,
        val searchText: String, val checked: String, val favorite: Boolean, val remindAt: Long,
    )

    @Serializable
    data class BackupCard(
        val noteId: Long, val question: String, val options: String, val answerIndex: Int, val explanation: String,
        val intervalDays: Int, val dueAt: Long, val reviews: Int, val lapses: Int,
    )

    suspend fun backupJson(context: Context): String {
        val db = AppDatabase.get(context)
        val notes = db.notes().allNow().map {
            BackupNote(it.id, it.createdAt, it.sharedText, it.sourceUrl, it.status, it.error, it.provider, it.title,
                it.category, it.summary, it.creator, it.caption, it.transcript, it.contentJson, it.searchText,
                it.checked, it.favorite, it.remindAt)
        }
        val cards = db.quiz().all().map {
            BackupCard(it.noteId, it.question, it.options, it.answerIndex, it.explanation, it.intervalDays, it.dueAt,
                it.reviews, it.lapses)
        }
        return AppJson.encodeToString(Backup.serializer(), Backup(notes = notes, cards = cards))
    }

    /** Merge a backup into the library. Notes already present (same time + shared text) are skipped. */
    suspend fun restore(context: Context, json: String): Int {
        val backup = AppJson.decodeFromString(Backup.serializer(), json)
        val db = AppDatabase.get(context)
        val existing = db.notes().allNow().map { it.createdAt to it.sharedText }.toSet()
        var added = 0
        for (b in backup.notes) {
            if ((b.createdAt to b.sharedText) in existing) continue
            // Notes that were mid-processing come back as failed so they can be retried.
            val status = if (b.status == Status.DONE) Status.DONE else Status.FAILED
            val newId = db.notes().insert(
                Note(createdAt = b.createdAt, sharedText = b.sharedText, sourceUrl = b.sourceUrl, status = status,
                    error = if (status == Status.FAILED) "Restored; tap a button below to process" else b.error,
                    provider = b.provider, title = b.title, category = b.category, summary = b.summary,
                    creator = b.creator, caption = b.caption, transcript = b.transcript, contentJson = b.contentJson,
                    searchText = b.searchText, checked = b.checked, favorite = b.favorite, remindAt = b.remindAt)
            )
            val cards = backup.cards.filter { it.noteId == b.id }.map {
                QuizCard(noteId = newId, question = it.question, options = it.options, answerIndex = it.answerIndex,
                    explanation = it.explanation, intervalDays = it.intervalDays, dueAt = it.dueAt,
                    reviews = it.reviews, lapses = it.lapses)
            }
            if (cards.isNotEmpty()) db.quiz().insertAll(cards)
            added++
        }
        return added
    }

    // ------------------------------------------------------------ Markdown export
    private fun safeName(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|#\\[\\]^]"), " ").replace(Regex("\\s+"), " ").trim().take(80).ifBlank { "Note" }

    private fun yamlStr(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    fun markdown(n: Note): String = buildString {
        val c = n.content
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(n.createdAt))
        appendLine("---")
        appendLine("title: ${yamlStr(n.title.ifBlank { "Note" })}")
        appendLine("category: ${yamlStr(n.category)}")
        appendLine("created: $date")
        if (n.sourceUrl.isNotBlank()) appendLine("source: ${n.sourceUrl}")
        if (n.creator.isNotBlank()) appendLine("creator: ${yamlStr(n.creator)}")
        if (c != null && c.tags.isNotEmpty()) appendLine("tags: [" + c.tags.joinToString(", ") { yamlStr(it.replace(' ', '-')) } + "]")
        appendLine("---").appendLine()
        appendLine("# ${n.title.ifBlank { "Note" }}").appendLine()
        if (c == null) { appendLine(n.sharedText); return@buildString }
        appendLine(c.summary).appendLine()
        if (c.linkInBio) appendLine("> The creator says the link is in their bio/comments.\n")
        val checked = n.checkedSet
        if (c.links.isNotEmpty()) {
            appendLine("## Links")
            c.links.forEach { appendLine("- [${it.title.ifBlank { it.url }}](${it.url})" + if (it.description.isNotBlank()) ": ${it.description}" else "") }
            appendLine()
        }
        if (c.steps.isNotEmpty()) {
            appendLine("## Steps")
            c.steps.forEachIndexed { i, s -> appendLine("- [${if ("s$i" in checked) "x" else " "}] $s") }
            appendLine()
        }
        if (c.roadmap.isNotEmpty()) {
            appendLine("## Roadmap")
            c.roadmap.forEachIndexed { si, st ->
                appendLine("### ${st.stage}")
                st.items.forEachIndexed { ii, it -> appendLine("- [${if ("r$si.$ii" in checked) "x" else " "}] $it") }
            }
            appendLine()
        }
        if (c.keyPoints.isNotEmpty()) { appendLine("## Key points"); c.keyPoints.forEach { appendLine("- $it") }; appendLine() }
        if (c.facts.isNotEmpty()) { appendLine("## Facts"); c.facts.forEach { appendLine("- $it") }; appendLine() }
        if (c.tools.isNotEmpty()) {
            appendLine("## Tools")
            c.tools.forEach { appendLine("- **${it.name}**" + (if (it.url.isNotBlank()) " (${it.url})" else "") + ": ${it.description}") }
            appendLine()
        }
        if (n.transcript.isNotBlank()) appendLine("## Transcript\n\n${n.transcript}")
    }

    /** Zip of Markdown files, one folder per category: opens in Obsidian, imports into Notion. */
    suspend fun exportMarkdownZip(context: Context, uri: Uri): Int {
        val notes = AppDatabase.get(context).notes().allNow().filter { it.status == Status.DONE }
        val used = HashSet<String>()
        context.contentResolver.openOutputStream(uri)!!.use { os ->
            ZipOutputStream(os).use { zip ->
                for (n in notes) {
                    val folder = safeName(n.category.ifBlank { "Other" })
                    var name = "ReelNotes/$folder/${safeName(n.title)}.md"
                    var i = 2
                    while (!used.add(name)) name = "ReelNotes/$folder/${safeName(n.title)} $i.md".also { i++ }
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(markdown(n).toByteArray())
                    zip.closeEntry()
                }
            }
        }
        return notes.size
    }

    // ------------------------------------------------------------ Instagram import
    private val IG_POST = Regex("https?://(?:www\\.)?instagram\\.com/(?:[A-Za-z0-9_.]+/)?(?:p|reel|reels|tv)/[A-Za-z0-9_-]+")

    /**
     * Reads Instagram's "Download your information" export: the whole .zip, or the
     * saved_posts.json / .html file inside it. Returns unique post URLs.
     */
    fun parseInstagramExport(context: Context, uri: Uri): List<String> {
        val urls = LinkedHashSet<String>()
        fun scan(text: String) = IG_POST.findAll(text.replace("\\/", "/")).forEach {
            urls += it.value.replace("/reels/", "/reel/").replace("http://", "https://").replace("://instagram", "://www.instagram")
        }
        context.contentResolver.openInputStream(uri)!!.use { input ->
            val buffered = input.buffered()
            buffered.mark(4)
            val magic = ByteArray(2).also { buffered.read(it) }
            buffered.reset()
            if (magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()) {
                ZipInputStream(buffered).use { zip ->
                    var e = zip.nextEntry
                    while (e != null) {
                        val name = e.name.lowercase()
                        if (!e.isDirectory && "saved" in name && (name.endsWith(".json") || name.endsWith(".html"))) {
                            scan(readText(zip))
                        }
                        e = zip.nextEntry
                    }
                }
            } else {
                scan(readText(buffered))
            }
        }
        return urls.toList()
    }

    private fun readText(input: InputStream): String = input.bufferedReader().readText()
}
