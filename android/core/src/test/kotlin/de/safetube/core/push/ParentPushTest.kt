package de.safetube.core.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParentPushTest {

    private fun msg(vararg tags: String, event: String = "message") =
        NtfyEvent(id = "x", event = event, title = "Titel", message = "Text", tags = tags.toList())

    @Test
    fun `neue Anfrage`() {
        assertEquals(ParentPush.NewRequest(9, "Titel", "Text"), ParentPush.from(msg("request:9")))
    }

    @Test
    fun `anderes Elternteil hat entschieden`() {
        assertEquals(ParentPush.DecidedElsewhere(9, "Titel", "Text"), ParentPush.from(msg("info", "decided:9")))
    }

    @Test
    fun `Info und Warnung`() {
        assertEquals(ParentPush.Info("Titel", "Text"), ParentPush.from(msg("info")))
        assertEquals(ParentPush.Alert("Titel", "Text"), ParentPush.from(msg("alert")))
    }

    @Test
    fun `Unbekanntes, kaputte IDs und andere Ereignisse ignorieren`() {
        assertNull(ParentPush.from(msg("sync")))
        assertNull(ParentPush.from(msg()))
        assertNull(ParentPush.from(msg("request:abc")))
        assertNull(ParentPush.from(msg("request:9", event = "keepalive")))
    }
}
