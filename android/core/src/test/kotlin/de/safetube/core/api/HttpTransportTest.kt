package de.safetube.core.api

import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpTransportTest {

    @Serializable
    data class Echo(val value: String)

    private lateinit var server: MockWebServer
    private var token: String? = "geheim"

    private fun transport() = HttpTransport(
        client = OkHttpClient(),
        baseUrl = { server.url("/").toString() },
        token = { token },
    )

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `GET schickt Bearer-Token und Abfrageparameter ohne null`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"value": "ok"}"""))

        val echo: Echo = transport().get("/ding", mapOf("a" to "1", "b" to null))

        assertEquals("ok", echo.value)
        val request = server.takeRequest()
        assertEquals("/api/v1/ding?a=1", request.path)
        assertEquals("Bearer geheim", request.getHeader("Authorization"))
    }

    @Test
    fun `ohne Token kein Authorization-Header`() = runBlocking {
        token = null
        server.enqueue(MockResponse().setBody("""{"value": "ok"}"""))

        transport().get<Echo>("/ding")

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `POST schickt JSON`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"value": "zurück"}"""))

        val echo: Echo = transport().post("/ding", Echo("hin"))

        assertEquals("zurück", echo.value)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("""{"value":"hin"}""", request.body.readUtf8())
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
    }

    @Test
    fun `DELETE ohne Antwortinhalt`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        transport().send("DELETE", "/ding/3")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/ding/3", request.path)
    }

    @Test
    fun `Fehlertext aus detail`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail": "Nicht angemeldet"}"""))

        val error = runCatching { transport().get<Echo>("/ding") }.exceptionOrNull() as ApiException

        assertEquals(401, error.status)
        assertEquals("Nicht angemeldet", error.detail)
    }

    @Test
    fun `Validierungsfehler werden zusammengefasst`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(422).setBody(
                """{"detail": [{"loc": ["body", "password"], "msg": "zu kurz", "type": "x"},
                               {"loc": ["body", "email"], "msg": "fehlt", "type": "y"}]}""",
            ),
        )

        val error = runCatching { transport().get<Echo>("/ding") }.exceptionOrNull() as ApiException

        assertEquals("zu kurz; fehlt", error.detail)
    }

    @Test
    fun `Fehler ohne JSON`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(502).setBody("Bad Gateway"))

        val error = runCatching { transport().get<Echo>("/ding") }.exceptionOrNull() as ApiException

        assertEquals(502, error.status)
        assertEquals("Bad Gateway", error.detail)
    }

    @Test
    fun `Server weg ist eine IOException mit verständlicher Meldung`() = runBlocking {
        server.shutdown()

        val error = runCatching { transport().get<Echo>("/ding") }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals("Server nicht erreichbar. Internetverbindung prüfen.", error!!.userMessage())
    }

    @Test
    fun `attempt fängt Fehler, aber keine Abbrüche`() = runBlocking {
        val ok = attempt { 42 }
        val failed = attempt { throw ApiException(404, "weg") }

        assertEquals(42, ok.getOrNull())
        assertEquals("weg", failed.exceptionOrNull()!!.userMessage())
    }
}
