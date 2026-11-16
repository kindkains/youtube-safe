package de.safetube.core.format

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Texte für Zeiten und Dauern, immer in Berliner Zeit. */
object Formats {
    val ZONE: ZoneId = ZoneId.of("Europe/Berlin")

    private val WEEKDAYS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    private val DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    fun duration(seconds: Long): String = when {
        seconds < 60 -> "$seconds s"
        seconds < 3600 -> "${seconds / 60} min"
        else -> "%d h %02d min".format(seconds / 3600, (seconds % 3600) / 60)
    }

    fun dateTime(instant: Instant, zone: ZoneId = ZONE): String = DATE_TIME.format(instant.atZone(zone))

    fun time(instant: Instant, zone: ZoneId = ZONE): String = TIME.format(instant.atZone(zone))

    /** "2026-11-15" → "So 15.11." */
    fun dayLabel(isoDate: String): String {
        val date = LocalDate.parse(isoDate)
        return "%s %02d.%02d.".format(WEEKDAYS[date.dayOfWeek.value - 1], date.dayOfMonth, date.monthValue)
    }

    /** Restzeit bis [until], z. B. für den Belohnungsmodus. */
    fun remaining(until: Instant, now: Instant): String {
        val seconds = Duration.between(now, until).seconds
        if (seconds <= 0) return "abgelaufen"
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        return when {
            days > 0 -> "noch $days T $hours h"
            hours > 0 -> "noch %d h %02d min".format(hours, minutes)
            minutes > 0 -> "noch $minutes min"
            else -> "noch unter 1 min"
        }
    }
}
