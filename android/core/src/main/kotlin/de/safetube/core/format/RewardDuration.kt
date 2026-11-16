package de.safetube.core.format

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Prüft die Eingaben für den Belohnungsmodus, bevor sie an den Server gehen. */
object RewardDuration {
    /** Wie MAX_REWARD_MINUTES in server/app/schemas.py. */
    const val MAX_MINUTES = 60 * 24 * 30

    /** Gesamtdauer in Minuten oder null, wenn sie nicht zwischen 1 Minute und 30 Tagen liegt. */
    fun minutes(days: Int, hours: Int, minutes: Int): Int? {
        if (days < 0 || hours < 0 || minutes < 0) return null
        val total = days.toLong() * 1440 + hours.toLong() * 60 + minutes
        return if (total in 1..MAX_MINUTES) total.toInt() else null
    }

    /** Endzeitpunkt aus Datum und Uhrzeit in [zone] oder null, wenn er nicht in der Zukunft (≤ 30 Tage) liegt. */
    fun until(date: LocalDate, time: LocalTime, now: Instant, zone: ZoneId = Formats.ZONE): Instant? {
        val instant = date.atTime(time).atZone(zone).toInstant()
        val latest = now.plus(Duration.ofMinutes(MAX_MINUTES.toLong()))
        return if (instant.isAfter(now) && !instant.isAfter(latest)) instant else null
    }
}
