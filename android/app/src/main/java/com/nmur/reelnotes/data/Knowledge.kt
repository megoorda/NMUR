package com.nmur.reelnotes.data

import android.net.Uri

/** Local "knowledge" helpers: compact note text for AI, keyword ranking and related-note scoring. */
object Knowledge {

    private val STOP = setOf(
        "the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "with", "is", "are", "was", "it", "this",
        "that", "what", "which", "how", "do", "i", "my", "me", "can", "where", "best", "any", "about", "from",
        "did", "save", "saved", "have", "you", "your", "there", "some", "get", "all",
    )

    fun words(text: String): Set<String> =
        Regex("[\\p{L}\\p{N}]{2,}").findAll(text.lowercase()).map { it.value }.filter { it !in STOP }.toSet()

    /** Compact text of a note for sending to the AI. */
    fun compact(n: Note, full: Boolean = false): String = buildString {
        val c = n.content
        append("[id ${n.id}] ${n.title.ifBlank { n.sharedText.take(80) }} (${n.category})\n")
        if (c == null) return@buildString
        append("Summary: ${c.summary}\n")
        if (c.links.isNotEmpty()) append("Links: " + c.links.joinToString("; ") { "${it.title} ${it.url}" } + "\n")
        if (c.tools.isNotEmpty()) append("Tools: " + c.tools.joinToString("; ") { "${it.name} ${it.url}".trim() } + "\n")
        if (c.steps.isNotEmpty()) append("Steps: " + c.steps.joinToString(" | ") + "\n")
        if (c.roadmap.isNotEmpty()) append("Roadmap: " + c.roadmap.joinToString(" | ") { it.stage + ": " + it.items.joinToString(", ") } + "\n")
        if (full) {
            if (c.keyPoints.isNotEmpty()) append("Key points: " + c.keyPoints.joinToString(" | ") + "\n")
            if (c.facts.isNotEmpty()) append("Facts: " + c.facts.joinToString(" | ") + "\n")
        } else if (c.facts.isNotEmpty()) {
            append("Facts: " + c.facts.take(5).joinToString(" | ") + "\n")
        }
        if (c.tags.isNotEmpty()) append("Tags: " + c.tags.joinToString(", ") + "\n")
    }

    /** Pick the notes most relevant to a question (simple keyword scoring, all on the phone). */
    fun candidates(question: String, notes: List<Note>, max: Int = 40, maxChars: Int = 60_000): String {
        val done = notes.filter { it.status == Status.DONE }
        val q = words(question)
        val scored = done.map { n ->
            val text = n.searchText
            val score = q.sumOf { w -> if (text.contains(w)) (if (n.title.lowercase().contains(w)) 3 else 1) else 0 }
            n to score
        }
        val anyHit = scored.any { it.second > 0 }
        val ordered = (if (anyHit) scored.filter { it.second > 0 }.sortedByDescending { it.second } else scored)
            .map { it.first }
        val sb = StringBuilder()
        for (n in ordered.take(max)) {
            val block = compact(n)
            if (sb.length + block.length > maxChars) break
            sb.append(block).append('\n')
        }
        return sb.toString()
    }

    private fun domain(url: String): String =
        runCatching { Uri.parse(if (url.startsWith("http")) url else "https://$url").host.orEmpty().removePrefix("www.") }
            .getOrDefault("")

    /** Features used to connect notes: tags, tool names, link domains, title words. */
    fun features(n: Note): Map<String, Double> {
        val c = n.content ?: return emptyMap()
        val f = HashMap<String, Double>()
        c.tags.forEach { f["tag:" + it.lowercase().trim()] = 2.0 }
        c.tools.forEach { if (it.name.isNotBlank()) f["tool:" + it.name.lowercase().trim()] = 3.0 }
        (c.links.map { it.url } + c.tools.map { it.url }).map(::domain)
            .filter { it.isNotBlank() && it !in setOf("instagram.com", "youtube.com", "tiktok.com", "github.com", "google.com") }
            .forEach { f["site:$it"] = 3.0 }
        words(c.title).forEach { f.putIfAbsent("word:$it", 0.7) }
        return f
    }

    fun similarity(a: Map<String, Double>, b: Map<String, Double>, sameCategory: Boolean): Double {
        var s = 0.0
        for ((k, w) in a) if (k in b) s += minOf(w, b[k]!!)
        if (s > 0 && sameCategory) s += 0.5
        return s
    }

    data class Related(val note: Note, val score: Double, val shared: List<String>)

    fun related(target: Note, all: List<Note>, limit: Int = 5): List<Related> {
        val tf = features(target)
        if (tf.isEmpty()) return emptyList()
        return all.asSequence()
            .filter { it.id != target.id && it.status == Status.DONE }
            .map { n ->
                val nf = features(n)
                val shared = tf.keys.intersect(nf.keys).filter { !it.startsWith("word:") }
                    .map { it.substringAfter(':') }
                Related(n, similarity(tf, nf, n.category == target.category), shared)
            }
            .filter { it.score >= 2.0 }
            .sortedByDescending { it.score }
            .take(limit)
            .toList()
    }
}
