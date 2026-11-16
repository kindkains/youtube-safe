package de.safetube.core.push

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface PushSignal {
    data object Connected : PushSignal
    data class Disconnected(val reason: String) : PushSignal
    data class Message(val event: NtfyEvent) : PushSignal
}

/**
 * Dauerverbindung zu einem ntfy-Topic. Endet nie von selbst; beendet wird sie durch Abbruch
 * der sammelnden Coroutine.
 */
class NtfyStream(
    client: OkHttpClient,
    private val baseUrl: String,
    private val topic: String,
    private val backoffMillis: (failures: Int) -> Long = ::defaultBackoff,
) {
    // ntfy schickt alle 45 s ein keepalive; 90 s ohne Daten heißt: Verbindung ist tot.
    private val streamClient = client.newBuilder().readTimeout(90, TimeUnit.SECONDS).build()

    fun signals(initialSince: String? = null): Flow<PushSignal> = channelFlow {
        var since = initialSince
        var failures = 0
        while (true) {
            val call = streamClient.newCall(buildRequest(since))
            val reader = launch(Dispatchers.IO) {
                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                        val source = response.body!!.source()
                        while (true) {
                            val line = source.readUtf8Line() ?: break
                            val event = NtfyParser.parseLine(line) ?: continue
                            when (event.event) {
                                "open" -> {
                                    failures = 0
                                    send(PushSignal.Connected)
                                }
                                "message" -> {
                                    if (event.id.isNotEmpty()) since = event.id
                                    send(PushSignal.Message(event))
                                }
                            }
                        }
                    }
                    send(PushSignal.Disconnected("Verbindung beendet"))
                } catch (e: IOException) {
                    if (!call.isCanceled()) send(PushSignal.Disconnected(e.message ?: e.javaClass.simpleName))
                }
            }
            try {
                reader.join()
            } finally {
                call.cancel() // bei Abbruch: blockierendes Lesen sofort beenden
            }
            failures++
            delay(backoffMillis(failures))
        }
    }

    private fun buildRequest(since: String?): Request {
        val url = (baseUrl.trimEnd('/') + "/" + topic + "/json").toHttpUrl().newBuilder()
            .apply { if (since != null) addQueryParameter("since", since) }
            .build()
        return Request.Builder().url(url).build()
    }

    companion object {
        fun defaultBackoff(failures: Int): Long {
            val exponent = (failures - 1).coerceIn(0, 6)
            return minOf(60_000L, 1_000L shl exponent)
        }
    }
}
