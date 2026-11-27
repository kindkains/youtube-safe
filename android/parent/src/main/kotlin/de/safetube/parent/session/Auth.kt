package de.safetube.parent.session

import de.safetube.core.api.ParentApi
import de.safetube.core.api.attempt

/** Anmelden und Abmelden. [apiFor] baut einen API-Client für eine Server-Adresse und ein Token. */
class Auth(
    private val store: SessionStore,
    private val apiFor: (serverUrl: String, token: String?) -> ParentApi,
) {
    suspend fun login(server: String, email: String, password: String): Session {
        val url = normalizeServer(server)
        val result = apiFor(url, null).login(email.trim(), password)
        val push = apiFor(url, result.token).pushInfo()
        val session = Session(
            serverUrl = url,
            token = result.token,
            parentId = result.parent.id,
            parentName = result.parent.name,
            ntfyUrl = push.ntfyUrl,
            ntfyTopic = push.ntfyTopic,
        )
        store.save(session)
        return session
    }

    /** Meldet beim Server ab (Fehler egal) und löscht die lokale Sitzung. */
    suspend fun logout(api: ParentApi) {
        attempt { api.logout() }
        store.clear()
    }

    suspend fun lastServer(): String? = store.lastServer()

    companion object {
        fun normalizeServer(input: String): String {
            val trimmed = input.trim().trimEnd('/')
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        }
    }
}
