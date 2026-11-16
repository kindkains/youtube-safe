package de.safetube.core.extractor

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

class OkHttpDownloaderTest {

    private lateinit var server: MockWebServer
    private val downloader = OkHttpDownloader(OkHttpClient())

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `GET mit eigenen Headern und Antwort-Headern`() {
        server.enqueue(MockResponse().setBody("hallo").addHeader("X-Antwort", "ja"))

        val response = downloader.execute(
            Request.newBuilder().get(server.url("/a").toString()).setHeader("X-Test", "eins").build(),
        )

        assertEquals(200, response.responseCode())
        assertEquals("hallo", response.responseBody())
        assertEquals("ja", response.getHeader("X-Antwort"))
        val request = server.takeRequest()
        assertEquals("eins", request.getHeader("X-Test"))
        assertTrue(request.getHeader("User-Agent")!!.contains("Firefox"))
    }

    @Test
    fun `POST mit Inhalt`() {
        server.enqueue(MockResponse().setBody("{}"))

        downloader.execute(Request.newBuilder().post(server.url("/b").toString(), "daten".toByteArray()).build())

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("daten", request.body.readUtf8())
    }

    @Test
    fun `nach Weiterleitung steht die letzte URL in der Antwort`() {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/ziel"))
        server.enqueue(MockResponse().setBody("angekommen"))

        val response = downloader.execute(Request.newBuilder().get(server.url("/start").toString()).build())

        assertTrue(response.latestUrl().endsWith("/ziel"))
    }

    @Test(expected = ReCaptchaException::class)
    fun `429 ist eine reCAPTCHA-Sperre`() {
        server.enqueue(MockResponse().setResponseCode(429))

        downloader.execute(Request.newBuilder().get(server.url("/c").toString()).build())
    }
}
