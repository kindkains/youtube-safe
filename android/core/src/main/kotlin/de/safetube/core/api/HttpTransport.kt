package de.safetube.core.api

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Dünne Schicht über OkHttp: Basis-URL + /api/v1, Bearer-Token, JSON, Fehlerabbildung.
 * [baseUrl] und [token] werden bei jedem Aufruf neu gelesen.
 */
class HttpTransport(
    private val client: OkHttpClient,
    private val baseUrl: () -> String,
    private val token: () -> String?,
) {
    @PublishedApi
    internal val json: Json = ApiJson

    suspend inline fun <reified T> get(path: String, query: Map<String, String?> = emptyMap()): T =
        json.decodeFromString(call("GET", path, query, null))

    suspend inline fun <reified B, reified T> post(path: String, body: B): T =
        json.decodeFromString(call("POST", path, emptyMap(), json.encodeToString(body)))

    /** POST ohne Inhalt, Antwort mit Inhalt (z. B. Kopplungscode). */
    suspend inline fun <reified T> postEmpty(path: String): T =
        json.decodeFromString(call("POST", path, emptyMap(), ""))

    suspend inline fun <reified B, reified T> patch(path: String, body: B): T =
        json.decodeFromString(call("PATCH", path, emptyMap(), json.encodeToString(body)))

    /** DELETE mit Antwortinhalt (z. B. Belohnungsmodus beenden liefert das Kind). */
    suspend inline fun <reified T> deleteFor(path: String): T =
        json.decodeFromString(call("DELETE", path, emptyMap(), null))

    /** Aufruf ohne Antwortinhalt (204), z. B. DELETE oder POST /logout. */
    suspend fun send(method: String, path: String) {
        call(method, path, emptyMap(), if (method == "GET" || method == "DELETE") null else "")
    }

    @PublishedApi
    internal suspend fun call(method: String, path: String, query: Map<String, String?>, body: String?): String =
        withContext(Dispatchers.IO) {
            val url = (baseUrl().trimEnd('/') + "/api/v1" + path).toHttpUrlOrNull()
                ?.newBuilder()
                ?.apply { query.forEach { (key, value) -> if (value != null) addQueryParameter(key, value) } }
                ?.build()
                ?: throw IOException("Ungültige Server-Adresse")
            val request = Request.Builder()
                .url(url)
                .method(method, body?.toRequestBody(JSON_TYPE))
                .header("Accept", "application/json")
                .apply { token()?.let { header("Authorization", "Bearer $it") } }
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw ApiException(response.code, parseDetail(text, response.code))
                text
            }
        }

    companion object {
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

        fun parseDetail(body: String, status: Int): String =
            try {
                when (val detail = ApiJson.parseToJsonElement(body).jsonObject["detail"]) {
                    is JsonPrimitive -> detail.content
                    is JsonArray -> detail.joinToString("; ") { item ->
                        item.jsonObject["msg"]?.jsonPrimitive?.content ?: item.toString()
                    }
                    else -> "HTTP $status"
                }
            } catch (e: Exception) {
                if (body.isBlank()) "HTTP $status" else body.take(200)
            }
    }
}
