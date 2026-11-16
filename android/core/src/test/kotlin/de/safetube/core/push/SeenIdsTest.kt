package de.safetube.core.push

import de.safetube.core.api.ContentRequest
import de.safetube.core.api.RequestStatus
import de.safetube.core.api.TargetType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeenIdsTest {

    @Test
    fun `merkt sich IDs und meldet nur neue`() {
        val seen = SeenIds()

        assertTrue(seen.add(1))
        assertFalse(seen.add(1))
        assertTrue(1 in seen)
    }

    @Test
    fun `älteste fallen bei voller Liste heraus`() {
        val seen = SeenIds(capacity = 3)
        listOf(1, 2, 3, 4).forEach { seen.add(it) }

        assertFalse(1 in seen)
        assertEquals("2,3,4", seen.encode())
    }

    @Test
    fun `speichern und laden`() {
        val seen = SeenIds.decode("5, 6,kaputt,,7")

        assertEquals("5,6,7", seen.encode())
        assertEquals("", SeenIds.decode(null).encode())
    }

    private fun request(
        id: Int,
        type: TargetType = TargetType.VIDEO,
        message: String = "",
        liftsBlock: Boolean = false,
    ) = ContentRequest(
        id = id, childId = 1, childName = "Mia", targetType = type, targetId = "dQw4w9WgXcQ", title = "Maus",
        channelId = "UCx", channelName = "Die Maus", url = "", thumbnailUrl = "", childMessage = message,
        sourceVideoId = "", sourceVideoTitle = "", status = RequestStatus.OPEN, decidedByName = null,
        decidedAt = null, reason = "", createdAt = Instant.parse("2026-11-15T06:00:00Z"), liftsBlock = liftsBlock,
    )

    @Test
    fun `Abgleich meldet nur noch nicht gemeldete Anfragen und merkt sie sich`() {
        val seen = SeenIds.decode("1")

        val fresh = OpenRequestCheck.newOnes(listOf(request(1), request(2)), seen)

        assertEquals(listOf(2), fresh.map { it.id })
        assertTrue(2 in seen)
        assertEquals(emptyList<ContentRequest>(), OpenRequestCheck.newOnes(listOf(request(2)), seen))
    }

    @Test
    fun `Benachrichtigung im Wortlaut des Servers`() {
        val video = OpenRequestCheck.notificationFor(request(3, message = "bitte!"))
        val channel = OpenRequestCheck.notificationFor(request(4, type = TargetType.CHANNEL))

        assertEquals(ParentPush.NewRequest(3, "Mia bittet um Freigabe", "Video: Maus (Die Maus)\n„bitte!“"), video)
        assertEquals(ParentPush.NewRequest(4, "Mia bittet um Freigabe", "Kanal: Maus"), channel)
    }

    @Test
    fun `Aufhebung einer Sperre im Wortlaut des Servers`() {
        val lift = OpenRequestCheck.notificationFor(request(5, liftsBlock = true))

        assertEquals("Mia bittet um Aufhebung einer Sperre", lift.title)
    }
}
