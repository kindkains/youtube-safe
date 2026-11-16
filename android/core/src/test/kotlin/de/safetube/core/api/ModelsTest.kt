package de.safetube.core.api

import java.time.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {

    @Test
    fun `Anfrage wie vom Server liefert alle Felder`() {
        val json = """
            {"id": 7, "child_id": 1, "child_name": "Mia", "target_type": "channel",
             "target_id": "UCabcdefghijklmnopqrstuv", "title": "Maus", "channel_id": "UCabcdefghijklmnopqrstuv",
             "channel_name": "Die Maus", "url": "https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv",
             "thumbnail_url": "", "child_message": "bitte!", "source_video_id": "dQw4w9WgXcQ",
             "source_video_title": "Folge 1", "status": "open", "lifts_block": true,
             "decided_by_name": null, "decided_at": null,
             "reason": "", "created_at": "2026-11-15T06:12:48.123456Z", "unbekannt": 1}
        """.trimIndent()

        val request = ApiJson.decodeFromString<ContentRequest>(json)

        assertEquals(7, request.id)
        assertEquals(TargetType.CHANNEL, request.targetType)
        assertEquals(RequestStatus.OPEN, request.status)
        assertEquals("dQw4w9WgXcQ", request.sourceVideoId)
        assertTrue(request.liftsBlock)
        assertNull(request.decidedAt)
        assertEquals(Instant.parse("2026-11-15T06:12:48.123456Z"), request.createdAt)
    }

    @Test
    fun `Kind ohne Belohnungsmodus`() {
        val json = """{"id": 1, "name": "Mia", "display_mode": "compact", "shorts_enabled": false,
            "reward_active": false, "reward_until": null, "reward_includes_blocked": false}"""

        val child = ApiJson.decodeFromString<Child>(json)

        assertEquals(DisplayMode.COMPACT, child.displayMode)
        assertNull(child.rewardUntil)
    }

    @Test
    fun `Regel für alle Kinder schickt child_id null ausdrücklich mit`() {
        val text = ApiJson.encodeToString(
            RuleCreate(childId = null, targetType = TargetType.VIDEO, targetId = "dQw4w9WgXcQ", effect = Effect.BLOCK),
        )

        assertTrue(text, text.contains("\"child_id\":null"))
        assertTrue(text, text.contains("\"target_type\":\"video\""))
        assertTrue(text, text.contains("\"effect\":\"block\""))
        assertTrue(text, text.contains("\"note\":\"\""))
    }

    @Test
    fun `Entscheidung schickt Standardbereich und leeren Grund`() {
        val text = ApiJson.encodeToString(Decision(DecisionAction.APPROVE_VIDEO_ONLY))

        assertEquals("""{"action":"approve_video_only","scope":"child","reason":""}""", text)
    }

    @Test
    fun `Belohnung bis Zeitpunkt wird als UTC mit Z geschickt`() {
        val text = ApiJson.encodeToString(RewardStart(until = Instant.parse("2026-11-16T18:00:00Z"), includeBlocked = true))

        assertEquals("""{"minutes":null,"until":"2026-11-16T18:00:00Z","include_blocked":true}""", text)
    }

    @Test
    fun `Statistik und Verlauf`() {
        val stats = ApiJson.decodeFromString<Stats>(
            """{"days": [{"date": "2026-11-15", "seconds": 600, "reward_seconds": 60}],
                "top_channels": [{"channel_id": "UCx", "channel_name": "X", "seconds": 600}],
                "total_seconds": 600, "reward_seconds": 60}""",
        )
        val entry = ApiJson.decodeFromString<HistoryEntry>(
            """{"id": 3, "video_id": "v", "channel_id": "c", "title": "T", "channel_name": "C",
                "started_at": "2026-11-15T10:00:00Z", "played_seconds": 42, "in_reward_mode": true,
                "allowed_now": false, "reason_now": "default"}""",
        )

        assertEquals(60, stats.days.single().rewardSeconds)
        assertEquals("UCx", stats.topChannels.single().channelId)
        assertEquals(42, entry.playedSeconds)
        assertTrue(entry.inRewardMode)
    }

    @Test
    fun `Kopplungscode und Push-Info`() {
        val code = ApiJson.decodeFromString<PairingCode>(
            """{"code": "AB12CD34", "expires_at": "2026-11-15T10:10:00Z",
                "qr_payload": "{\"server\": \"https://safetube.isoletta.me\", \"code\": \"AB12CD34\"}"}""",
        )
        val push = ApiJson.decodeFromString<PushInfo>("""{"ntfy_url": "https://push.tube.isoletta.me", "ntfy_topic": "yts-1"}""")

        assertEquals("AB12CD34", code.code)
        assertTrue(code.qrPayload.contains("AB12CD34"))
        assertEquals("yts-1", push.ntfyTopic)
    }
}
