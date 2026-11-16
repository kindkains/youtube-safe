package de.safetube.core.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NtfyParserTest {

    @Test
    fun `Nachricht mit Titel und Tags`() {
        val event = NtfyParser.parseLine(
            """{"id":"m1","time":1794719652,"event":"message","topic":"yts-1","title":"Mia bittet um Freigabe",
               "message":"Video: Maus","tags":["request:9"],"priority":4}""",
        )!!

        assertEquals("message", event.event)
        assertEquals("m1", event.id)
        assertEquals(listOf("request:9"), event.tags)
        assertEquals(4, event.priority)
    }

    @Test
    fun `fehlende Felder bekommen Standardwerte`() {
        val event = NtfyParser.parseLine("""{"id":"k","time":1,"event":"keepalive","topic":"yts-1"}""")!!

        assertEquals("", event.title)
        assertEquals(emptyList<String>(), event.tags)
        assertEquals(3, event.priority)
    }

    @Test
    fun `leere und kaputte Zeilen werden übersprungen`() {
        assertNull(NtfyParser.parseLine(""))
        assertNull(NtfyParser.parseLine("   "))
        assertNull(NtfyParser.parseLine("kein json"))
        assertNull(NtfyParser.parseLine("""{"id":"x"}"""))
    }
}
