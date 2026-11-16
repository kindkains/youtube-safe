package de.safetube.core.format

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RewardDurationTest {

    @Test
    fun `Dauer in Minuten`() {
        assertEquals(90, RewardDuration.minutes(days = 0, hours = 1, minutes = 30))
        assertEquals(1440 * 2 + 5, RewardDuration.minutes(days = 2, hours = 0, minutes = 5))
        assertEquals(RewardDuration.MAX_MINUTES, RewardDuration.minutes(days = 30, hours = 0, minutes = 0))
    }

    @Test
    fun `ungültige Dauer`() {
        assertNull(RewardDuration.minutes(0, 0, 0))
        assertNull(RewardDuration.minutes(30, 0, 1))
        assertNull(RewardDuration.minutes(0, -1, 30))
    }

    @Test
    fun `Endzeitpunkt aus Berliner Datum und Uhrzeit`() {
        val now = Instant.parse("2026-11-15T10:00:00Z")

        val until = RewardDuration.until(LocalDate.of(2026, 11, 15), LocalTime.of(18, 30), now)

        assertEquals(Instant.parse("2026-11-15T17:30:00Z"), until)
    }

    @Test
    fun `Endzeitpunkt in der Vergangenheit oder zu weit weg`() {
        val now = Instant.parse("2026-11-15T10:00:00Z")

        assertNull(RewardDuration.until(LocalDate.of(2026, 11, 15), LocalTime.of(10, 0), now))
        assertNull(RewardDuration.until(LocalDate.of(2026, 12, 20), LocalTime.of(10, 0), now))
    }
}
