package de.safetube.core.api

import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpParentApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ParentApi

    private val childJson = """{"id": 1, "name": "Mia", "display_mode": "compact", "shorts_enabled": false,
        "reward_active": true, "reward_until": "2026-11-15T20:00:00Z", "reward_includes_blocked": false}"""
    private val ruleJson = """{"id": 5, "child_id": null, "target_type": "channel", "target_id": "UCx",
        "title": "Maus", "channel_name": "Maus", "effect": "allow", "note": "",
        "created_at": "2026-11-15T06:00:00Z", "updated_at": "2026-11-15T06:00:00Z"}"""
    private val requestJson = """{"id": 9, "child_id": 1, "child_name": "Mia", "target_type": "video",
        "target_id": "dQw4w9WgXcQ", "title": "T", "channel_id": "UCx", "channel_name": "C",
        "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "thumbnail_url": "", "child_message": "",
        "source_video_id": "", "source_video_title": "", "status": "approved", "decided_by_name": "Sandro",
        "decided_at": "2026-11-15T07:00:00Z", "reason": "", "created_at": "2026-11-15T06:00:00Z"}"""

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        api = HttpParentApi(HttpTransport(OkHttpClient(), { server.url("/").toString() }, { "tok" }))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun reply(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    @Test
    fun login() = runBlocking {
        reply("""{"token": "neu", "parent": {"id": 1, "name": "Sandro", "email": "s@x.de",
            "mail_new_request": true, "mail_alerts": true}}""")

        val result = api.login("s@x.de", "pw")

        assertEquals("neu", result.token)
        assertEquals("Sandro", result.parent.name)
        val request = server.takeRequest()
        assertEquals("/api/v1/parent/login", request.path)
        assertEquals("""{"email":"s@x.de","password":"pw"}""", request.body.readUtf8())
    }

    @Test
    fun `logout erwartet 204`() = runBlocking {
        reply("", 204)

        api.logout()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/parent/logout", request.path)
    }

    @Test
    fun `Regeln eines Kindes und nur globale`() = runBlocking {
        reply("[$ruleJson]")
        reply("[]")

        val rules = api.rules(childId = 1)
        api.rules(globalOnly = true)

        assertEquals(null, rules.single().childId)
        assertEquals("/api/v1/parent/rules?child_id=1", server.takeRequest().path)
        assertEquals("/api/v1/parent/rules?global_only=true", server.takeRequest().path)
    }

    @Test
    fun `Regel anlegen, ändern, löschen`() = runBlocking {
        reply(ruleJson, 201)
        reply(ruleJson)
        reply("", 204)

        api.createRule(RuleCreate(childId = null, targetType = TargetType.CHANNEL, targetId = "UCx", effect = Effect.ALLOW))
        api.updateRule(5, RuleUpdate(effect = Effect.BLOCK))
        api.deleteRule(5)

        val create = server.takeRequest()
        assertTrue(create.body.readUtf8().contains("\"child_id\":null"))
        val update = server.takeRequest()
        assertEquals("PATCH", update.method)
        assertEquals("/api/v1/parent/rules/5", update.path)
        assertEquals("""{"effect":"block","note":null}""", update.body.readUtf8())
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/api/v1/parent/rules/5", delete.path)
    }

    @Test
    fun `Anfragen mit Filter`() = runBlocking {
        reply("[$requestJson]")

        val list = api.requests(
            RequestFilter(childId = 1, status = RequestStatus.OPEN, since = Instant.parse("2026-11-01T00:00:00Z"), limit = 50),
        )

        assertEquals("Sandro", list.single().decidedByName)
        assertEquals(
            "/api/v1/parent/requests?child_id=1&status=open&since=2026-11-01T00%3A00%3A00Z&limit=50",
            server.takeRequest().path,
        )
    }

    @Test
    fun `Anfrage entscheiden`() = runBlocking {
        reply(requestJson)

        api.decide(9, Decision(DecisionAction.REJECT_AND_BLOCK, Scope.ALL, "zu gruselig"))

        val request = server.takeRequest()
        assertEquals("/api/v1/parent/requests/9/decision", request.path)
        assertEquals("""{"action":"reject_and_block","scope":"all","reason":"zu gruselig"}""", request.body.readUtf8())
    }

    @Test
    fun `Belohnungsmodus starten und beenden`() = runBlocking {
        reply(childJson)
        reply(childJson)

        val started = api.startReward(1, RewardStart(minutes = 90))
        api.stopReward(1)

        assertTrue(started.rewardActive)
        val start = server.takeRequest()
        assertEquals("/api/v1/parent/children/1/reward", start.path)
        assertEquals("""{"minutes":90,"until":null,"include_blocked":false}""", start.body.readUtf8())
        val stop = server.takeRequest()
        assertEquals("DELETE", stop.method)
        assertEquals("/api/v1/parent/children/1/reward", stop.path)
    }

    @Test
    fun `Verlauf blättert mit before, Statistik mit days`() = runBlocking {
        reply("[]")
        reply("""{"days": [], "top_channels": [], "total_seconds": 0, "reward_seconds": 0}""")

        api.history(1, limit = 50, before = Instant.parse("2026-11-15T10:00:00Z"))
        api.stats(1, days = 30)

        assertEquals("/api/v1/parent/children/1/history?limit=50&before=2026-11-15T10%3A00%3A00Z", server.takeRequest().path)
        assertEquals("/api/v1/parent/children/1/stats?days=30", server.takeRequest().path)
    }

    @Test
    fun `Kinder, Geräte, Kopplung`() = runBlocking {
        reply("[$childJson]")
        reply(childJson, 201)
        reply(childJson)
        reply("""[{"id": 4, "child_id": 1, "app_version": "0.1.0", "paired_at": "2026-11-15T06:00:00Z",
            "last_seen": "2026-11-15T07:00:00Z", "unpaired_at": null}]""")
        reply("", 204)
        reply("""{"code": "AB12CD34", "expires_at": "2026-11-15T06:10:00Z", "qr_payload": "{}"}""")
        reply("", 204)

        api.children()
        api.createChild("Antonio")
        api.updateChild(1, ChildUpdate(shortsEnabled = true))
        val devices = api.devices(1)
        api.unpairDevice(4)
        val code = api.pairingCode(1)
        api.deleteChild(1)

        assertEquals("/api/v1/parent/children", server.takeRequest().path)
        assertEquals("""{"name":"Antonio"}""", server.takeRequest().body.readUtf8())
        assertEquals("""{"name":null,"display_mode":null,"shorts_enabled":true}""", server.takeRequest().body.readUtf8())
        assertEquals("/api/v1/parent/children/1/devices", server.takeRequest().path)
        assertEquals("/api/v1/parent/devices/4", server.takeRequest().path)
        val pairing = server.takeRequest()
        assertEquals("POST", pairing.method)
        assertEquals("/api/v1/parent/children/1/pairing-code", pairing.path)
        val deletion = server.takeRequest()
        assertEquals("DELETE", deletion.method)
        assertEquals("/api/v1/parent/children/1", deletion.path)
        assertEquals("0.1.0", devices.single().appVersion)
        assertEquals("AB12CD34", code.code)
    }

    @Test
    fun `Eltern und eigenes Konto`() = runBlocking {
        val parentJson = """{"id": 2, "name": "Anna", "email": "a@x.de", "mail_new_request": true, "mail_alerts": false}"""
        reply("[$parentJson]")
        reply(parentJson, 201)
        reply(parentJson)
        reply(parentJson)
        reply("""{"ntfy_url": "https://push.tube.isoletta.me", "ntfy_topic": "yts-1"}""")

        api.parents()
        api.createParent(ParentCreate("Anna", "a@x.de", "langespasswort"))
        api.me()
        api.updateMe(MeUpdate(mailAlerts = false))
        val push = api.pushInfo()

        assertEquals("/api/v1/parent/parents", server.takeRequest().path)
        assertEquals("POST", server.takeRequest().method)
        assertEquals("/api/v1/parent/me", server.takeRequest().path)
        val patch = server.takeRequest()
        assertEquals("PATCH", patch.method)
        assertEquals("""{"name":null,"mail_new_request":null,"mail_alerts":false,"password":null}""", patch.body.readUtf8())
        assertEquals("yts-1", push.ntfyTopic)
    }
}
