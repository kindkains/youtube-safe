package de.safetube.core.push

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NtfyStreamTest {

    private lateinit var server: MockWebServer

    private val open = """{"id":"o","time":1,"event":"open","topic":"yts-abc"}"""
    private val keepalive = """{"id":"k","time":2,"event":"keepalive","topic":"yts-abc"}"""
    private fun message(id: String) =
        """{"id":"$id","time":3,"event":"message","topic":"yts-abc","title":"T","message":"M","tags":["info"]}"""

    private fun lines(vararg items: String) = items.joinToString("\n", postfix = "\n")

    private fun stream() = NtfyStream(OkHttpClient(), server.url("/").toString(), "yts-abc", backoffMillis = { 0L })

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `liest Nachrichten, verbindet neu und holt mit since nach`() = runBlocking {
        server.enqueue(MockResponse().setBody(lines(open, keepalive, message("m1"))))
        server.enqueue(MockResponse().setBody(lines(open, message("m2"))))

        val signals = withTimeout(10_000) { stream().signals().take(6).toList() }

        assertEquals(PushSignal.Connected, signals[0])
        assertEquals("m1", (signals[1] as PushSignal.Message).event.id)
        assertTrue(signals[2] is PushSignal.Disconnected)
        assertEquals(PushSignal.Connected, signals[3])
        assertEquals("m2", (signals[4] as PushSignal.Message).event.id)
        assertTrue(signals[5] is PushSignal.Disconnected)
        assertEquals("/yts-abc/json", server.takeRequest().path)
        assertEquals("/yts-abc/json?since=m1", server.takeRequest().path)
    }

    @Test
    fun `HTTP-Fehler meldet Trennung und versucht es erneut`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody(lines(open, message("m1"))))

        val signals = withTimeout(10_000) { stream().signals().take(3).toList() }

        assertEquals(PushSignal.Disconnected("HTTP 500"), signals[0])
        assertEquals(PushSignal.Connected, signals[1])
        assertEquals("m1", (signals[2] as PushSignal.Message).event.id)
    }

    @Test
    fun `gespeicherte ID wird schon beim ersten Verbinden benutzt`() = runBlocking {
        server.enqueue(MockResponse().setBody(lines(open)))

        withTimeout(10_000) { stream().signals(initialSince = "alt42").take(1).toList() }

        assertEquals("/yts-abc/json?since=alt42", server.takeRequest().path)
    }

    @Test
    fun `Pausen wachsen bis 60 Sekunden`() {
        assertEquals(1_000L, NtfyStream.defaultBackoff(1))
        assertEquals(2_000L, NtfyStream.defaultBackoff(2))
        assertEquals(32_000L, NtfyStream.defaultBackoff(6))
        assertEquals(60_000L, NtfyStream.defaultBackoff(7))
        assertEquals(60_000L, NtfyStream.defaultBackoff(50))
    }
}
