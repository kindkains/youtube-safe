package de.safetube.parent.session

import de.safetube.core.api.ApiException
import de.safetube.core.api.ContentRequest
import de.safetube.parent.testing.FakeParentApi
import de.safetube.parent.testing.Samples
import de.safetube.parent.testing.sessionStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AuthTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val api = FakeParentApi()
    private val usedServers = mutableListOf<Pair<String, String?>>()

    private fun auth(store: SessionStore) = Auth(store) { server, token ->
        usedServers += server to token
        api
    }

    @Test
    fun `Login speichert Sitzung mit Push-Topic`() = runBlocking {
        val store = tmp.sessionStore()

        val session = auth(store).login(" safetube.example.org/ ", " sandro@example.org ", "geheim123")

        assertEquals("https://safetube.example.org", session.serverUrl)
        assertEquals("token-1", session.token)
        assertEquals("yts-test", session.ntfyTopic)
        assertEquals(listOf("login sandro@example.org", "pushInfo"), api.calls)
        assertEquals(listOf("https://safetube.example.org" to null, "https://safetube.example.org" to "token-1"), usedServers)
        assertEquals(session, store.load())
        assertEquals(session, store.current)
    }

    @Test
    fun `gescheiterter Login speichert nichts`() = runBlocking {
        val store = tmp.sessionStore()
        api.failure = ApiException(401, "Anmeldung fehlgeschlagen")

        val result = runCatching { auth(store).login("https://s.example.org", "x@example.org", "falsch") }

        assertTrue(result.isFailure)
        assertNull(store.load())
    }

    @Test
    fun `Abmelden löscht die Sitzung, behält aber den Server`() = runBlocking {
        val store = tmp.sessionStore()
        val auth = auth(store)
        auth.login("https://s.example.org", "x@example.org", "geheim123")
        store.setLastNtfyId("m5")

        auth.logout(api)

        assertNull(store.load())
        assertNull(store.lastNtfyId())
        assertEquals("https://s.example.org", auth.lastServer())
        assertTrue("logout" in api.calls)
    }

    @Test
    fun `Abmelden klappt auch, wenn der Server nicht antwortet`() = runBlocking {
        val store = tmp.sessionStore()
        val auth = auth(store)
        auth.login("https://s.example.org", "x@example.org", "geheim123")
        api.failure = java.io.IOException("weg")

        auth.logout(api)

        assertNull(store.load())
    }

    @Test
    fun `gemeldete Anfragen werden atomar gemerkt`() = runBlocking {
        val store = tmp.sessionStore()
        val open: List<ContentRequest> = listOf(Samples.request(1), Samples.request(2))

        assertTrue(store.markSeen(1))
        assertFalse(store.markSeen(1))
        assertEquals(listOf(2), store.newRequests(open).map { it.id })
        assertEquals(emptyList<ContentRequest>(), store.newRequests(open))
    }

    @Test
    fun `Server-Adresse normalisieren`() {
        assertEquals("https://safetube.isoletta.me", Auth.normalizeServer("safetube.isoletta.me"))
        assertEquals("https://safetube.isoletta.me", Auth.normalizeServer(" https://safetube.isoletta.me/ "))
        assertEquals("http://192.168.0.2:8090", Auth.normalizeServer("http://192.168.0.2:8090"))
    }
}
