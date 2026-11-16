package com.nmur.reelnotes.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.net.URLConnection
import java.util.concurrent.TimeUnit

/** Error the user should see; [retryable] = network trouble worth retrying later. */
class ServerException(message: String, val retryable: Boolean) : Exception(message)

class ServerApi(private val settings: Settings) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .callTimeout(9, TimeUnit.MINUTES)
        .build()

    private fun base(): String {
        var url = settings.serverUrl
        if (url.isBlank()) throw ServerException("Server address not set. Open Settings.", retryable = false)
        if (!url.startsWith("http")) url = "http://$url"
        return url
    }

    suspend fun health(): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(base() + "/health").build()
        try {
            client.newCall(req).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw ServerException("HTTP ${r.code}: $body", false)
                body
            }
        } catch (e: IOException) {
            throw ServerException("Can't reach server: ${e.message}", true)
        }
    }

    suspend fun process(text: String, files: List<File>, provider: String): ProcessResponse = withContext(Dispatchers.IO) {
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("text", text)
            .addFormDataPart("provider", provider)
            .addFormDataPart("categories", settings.categories.joinToString("\n"))
        files.filter { it.exists() }.forEach { f ->
            val type = (URLConnection.guessContentTypeFromName(f.name) ?: "application/octet-stream").toMediaTypeOrNull()
            form.addFormDataPart("files", f.name, f.asRequestBody(type))
        }
        val req = Request.Builder()
            .url(base() + "/process")
            .header("X-Api-Key", settings.token)
            .post(form.build())
            .build()
        try {
            client.newCall(req).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    val detail = runCatching {
                        AppJson.parseToJsonElement(body).jsonObject["detail"]?.jsonPrimitive?.content
                    }.getOrNull() ?: body.take(300)
                    // 5xx from a crash or busy PC may succeed later; 4xx and AI errors won't.
                    throw ServerException(detail, retryable = r.code == 503 || r.code == 504)
                }
                AppJson.decodeFromString(ProcessResponse.serializer(), body)
            }
        } catch (e: IOException) {
            throw ServerException("Can't reach server (is the PC on and Tailscale connected?): ${e.message}", true)
        }
    }

    private suspend fun postJson(path: String, json: String): String = call(
        Request.Builder().url(base() + path).header("X-Api-Key", settings.token)
            .post(json.toRequestBody("application/json".toMediaType())).build()
    )

    private suspend fun call(req: Request): String = withContext(Dispatchers.IO) {
        try {
            client.newCall(req).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    val detail = runCatching {
                        AppJson.parseToJsonElement(body).jsonObject["detail"]?.jsonPrimitive?.content
                    }.getOrNull() ?: "HTTP ${r.code}: ${body.take(300)}"
                    throw ServerException(detail, retryable = r.code == 503 || r.code == 504)
                }
                body
            }
        } catch (e: IOException) {
            throw ServerException("Can't reach server (is the PC on and Tailscale connected?): ${e.message}", true)
        }
    }

    suspend fun ask(question: String, notes: String, history: List<ChatTurn>, provider: String): AskResponse {
        val req = AskRequest(question, notes, history, provider)
        val body = postJson("/ask", AppJson.encodeToString(AskRequest.serializer(), req))
        return AppJson.decodeFromString(AskResponse.serializer(), body)
    }

    suspend fun quiz(note: String, count: Int, provider: String): List<QuizQuestion> {
        val body = postJson("/quiz", AppJson.encodeToString(QuizRequest.serializer(), QuizRequest(note, count, provider)))
        return AppJson.decodeFromString(QuizResponse.serializer(), body).questions
    }

    suspend fun uploadBackup(json: String) {
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "reelnotes-backup.json", json.toRequestBody("application/json".toMediaType()))
            .build()
        call(Request.Builder().url(base() + "/backup").header("X-Api-Key", settings.token).post(form).build())
    }

    suspend fun downloadBackup(): String =
        call(Request.Builder().url(base() + "/backup").header("X-Api-Key", settings.token).get().build())
}

@Serializable
data class ChatTurn(val role: String, val text: String)

@Serializable
private data class AskRequest(val question: String, val notes: String, val history: List<ChatTurn>, val provider: String)

@Serializable
data class AskResponse(val answer: String = "", @kotlinx.serialization.SerialName("used_note_ids") val usedNoteIds: List<Long> = emptyList())

@Serializable
private data class QuizRequest(val note: String, val count: Int, val provider: String)

@Serializable
data class QuizQuestion(
    val question: String = "",
    val options: List<String> = emptyList(),
    @kotlinx.serialization.SerialName("answer_index") val answerIndex: Int = 0,
    val explanation: String = "",
)

@Serializable
private data class QuizResponse(val questions: List<QuizQuestion> = emptyList())
