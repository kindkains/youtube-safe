package de.safetube.parent.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.safetube.core.api.ContentRequest
import de.safetube.core.push.OpenRequestCheck
import de.safetube.core.push.SeenIds
import kotlinx.coroutines.flow.first

data class Session(
    val serverUrl: String,
    val token: String,
    val parentId: Int,
    val parentName: String,
    val ntfyUrl: String,
    val ntfyTopic: String,
)

val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/** Sitzung und Push-Zustand im privaten App-Speicher (Festlegung 6). */
class SessionStore(private val store: DataStore<Preferences>) {

    /** Zuletzt geladene bzw. gespeicherte Sitzung, für synchrone Zugriffe (Token im HTTP-Transport). */
    @Volatile
    var current: Session? = null
        private set

    suspend fun load(): Session? = read(store.data.first()).also { current = it }

    suspend fun save(session: Session) {
        store.edit {
            it[SERVER] = session.serverUrl
            it[TOKEN] = session.token
            it[PARENT_ID] = session.parentId
            it[PARENT_NAME] = session.parentName
            it[NTFY_URL] = session.ntfyUrl
            it[NTFY_TOPIC] = session.ntfyTopic
        }
        current = session
    }

    /** Abmelden: alles weg bis auf die Server-Adresse (Vorschlag für den nächsten Login). */
    suspend fun clear() {
        store.edit { prefs ->
            val server = prefs[SERVER]
            prefs.clear()
            if (server != null) prefs[SERVER] = server
        }
        current = null
    }

    suspend fun lastServer(): String? = store.data.first()[SERVER]

    suspend fun lastNtfyId(): String? = store.data.first()[LAST_NTFY_ID]

    suspend fun setLastNtfyId(id: String) {
        store.edit { it[LAST_NTFY_ID] = id }
    }

    /** Merkt sich eine per Push gemeldete Anfrage; true, wenn sie neu war. */
    suspend fun markSeen(requestId: Int): Boolean {
        var fresh = false
        store.edit { prefs ->
            val seen = SeenIds.decode(prefs[SEEN])
            fresh = seen.add(requestId)
            prefs[SEEN] = seen.encode()
        }
        return fresh
    }

    /** 15-Minuten-Abgleich: liefert die noch nicht gemeldeten offenen Anfragen und merkt sie sich. */
    suspend fun newRequests(open: List<ContentRequest>): List<ContentRequest> {
        var fresh = emptyList<ContentRequest>()
        store.edit { prefs ->
            val seen = SeenIds.decode(prefs[SEEN])
            fresh = OpenRequestCheck.newOnes(open, seen)
            prefs[SEEN] = seen.encode()
        }
        return fresh
    }

    private fun read(prefs: Preferences): Session? {
        val token = prefs[TOKEN] ?: return null
        val server = prefs[SERVER] ?: return null
        return Session(
            serverUrl = server,
            token = token,
            parentId = prefs[PARENT_ID] ?: 0,
            parentName = prefs[PARENT_NAME].orEmpty(),
            ntfyUrl = prefs[NTFY_URL].orEmpty(),
            ntfyTopic = prefs[NTFY_TOPIC].orEmpty(),
        )
    }

    private companion object {
        val SERVER = stringPreferencesKey("server")
        val TOKEN = stringPreferencesKey("token")
        val PARENT_ID = intPreferencesKey("parent_id")
        val PARENT_NAME = stringPreferencesKey("parent_name")
        val NTFY_URL = stringPreferencesKey("ntfy_url")
        val NTFY_TOPIC = stringPreferencesKey("ntfy_topic")
        val LAST_NTFY_ID = stringPreferencesKey("last_ntfy_id")
        val SEEN = stringPreferencesKey("seen_requests")
    }
}
