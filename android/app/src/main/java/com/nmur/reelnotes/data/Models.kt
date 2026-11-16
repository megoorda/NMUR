package com.nmur.reelnotes.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
}

@Serializable
data class LinkItem(
    val title: String = "",
    val url: String = "",
    val description: String = "",
    val reachable: Boolean? = null,
)

@Serializable
data class ToolItem(
    val name: String = "",
    val description: String = "",
    val url: String = "",
    val reachable: Boolean? = null,
)

@Serializable
data class RoadmapStage(
    val stage: String = "",
    val items: List<String> = emptyList(),
)

/** The AI-generated note (same shape as the server's RESULT_SCHEMA). */
@Serializable
data class NoteContent(
    val title: String = "",
    val category: String = "Other",
    val tags: List<String> = emptyList(),
    val summary: String = "",
    @SerialName("key_points") val keyPoints: List<String> = emptyList(),
    val links: List<LinkItem> = emptyList(),
    val steps: List<String> = emptyList(),
    val roadmap: List<RoadmapStage> = emptyList(),
    val facts: List<String> = emptyList(),
    val tools: List<ToolItem> = emptyList(),
    @SerialName("link_in_bio") val linkInBio: Boolean = false,
    val language: String = "",
)

@Serializable
data class ProcessResponse(
    @SerialName("source_url") val sourceUrl: String = "",
    val creator: String = "",
    val caption: String = "",
    val transcript: String = "",
    val provider: String = "",
    val note: NoteContent = NoteContent(),
)

object Status {
    const val PENDING = "pending"
    const val PROCESSING = "processing"
    const val DONE = "done"
    const val FAILED = "failed"
}
