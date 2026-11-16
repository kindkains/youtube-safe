package de.safetube.core.format

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatsTest {

    @Test
    fun Dauer() {
        assertEquals("0 s", Formats.duration(0))
        assertEquals("59 s", Formats.duration(59))
        assertEquals("1 min", Formats.duration(60))
        assertEquals("59 min", Formats.duration(3599))
        assertEquals("1 h 05 min", Formats.duration(3900))
        assertEquals("26 h 00 min", Formats.duration(93600))
    }

    @Test
    fun `Datum und Uhrzeit in Berliner Zeit`() {
        // 15.11.2026 ist Winterzeit (UTC+1)
        assertEquals("15.11.2026, 07:12", Formats.dateTime(Instant.parse("2026-11-15T06:12:48Z")))
        assertEquals("07:12", Formats.time(Instant.parse("2026-11-15T06:12:48Z")))
        // Sommerzeit (UTC+2)
        assertEquals("01.07.2026, 14:00", Formats.dateTime(Instant.parse("2026-07-01T12:00:00Z")))
    }

    @Test
    fun Tagesbeschriftung() {
        assertEquals("So 15.11.", Formats.dayLabel("2026-11-15"))
        assertEquals("Mo 16.11.", Formats.dayLabel("2026-11-16"))
    }

    @Test
    fun Restzeit() {
        val now = Instant.parse("2026-11-15T10:00:00Z")
        assertEquals("abgelaufen", Formats.remaining(now, now))
        assertEquals("abgelaufen", Formats.remaining(now.minusSeconds(5), now))
        assertEquals("noch unter 1 min", Formats.remaining(now.plusSeconds(30), now))
        assertEquals("noch 4 min", Formats.remaining(now.plusSeconds(4 * 60 + 10), now))
        assertEquals("noch 1 h 05 min", Formats.remaining(now.plusSeconds(3900), now))
        assertEquals("noch 2 T 3 h", Formats.remaining(now.plusSeconds(2 * 86400 + 3 * 3600 + 59), now))
    }
}
