package de.safetube.core.push

import de.safetube.core.api.ApiJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

/** Ein Ereignis aus dem ntfy-JSON-Strom. ntfy lässt leere Felder weg, daher Standardwerte. */
@Serializable
data class NtfyEvent(
    val id: String = "",
    val time: Long = 0,
    val event: String,
    val topic: String = "",
    val title: String = "",
    val message: String = "",
    val tags: List<String> = emptyList(),
    val priority: Int = 3,
)

object NtfyParser {
    /** Liefert null für leere oder unlesbare Zeilen — die Verbindung soll daran nicht scheitern. */
    fun parseLine(line: String): NtfyEvent? {
        if (line.isBlank()) return null
        return try {
            ApiJson.decodeFromString<NtfyEvent>(line)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
