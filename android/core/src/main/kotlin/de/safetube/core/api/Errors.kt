package de.safetube.core.api

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Der Server hat geantwortet, aber mit einem Fehlerstatus. [detail] ist sein Fehlertext. */
class ApiException(val status: Int, val detail: String) : Exception("HTTP $status: $detail")

/** Fehler eines YouTube-Zugriffs (Extractor), mit fertiger Meldung für die Oberfläche. */
class YouTubeException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Führt [block] aus und liefert Erfolg oder Fehler; Abbrüche (Coroutine-Cancel) werden weitergereicht. */
suspend fun <T> attempt(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

/** Kurzer deutscher Text für die Oberfläche. */
fun Throwable.userMessage(): String = when (this) {
    is ApiException -> if (status == 401) "Nicht (mehr) angemeldet. Bitte neu anmelden." else detail
    is YouTubeException -> message ?: "YouTube-Fehler"
    is IOException -> "Server nicht erreichbar. Internetverbindung prüfen."
    else -> message ?: javaClass.simpleName
}
