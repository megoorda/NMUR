package com.nmur.reelnotes.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
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
}
