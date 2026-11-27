package de.safetube.parent.testing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import de.safetube.parent.session.SessionStore
import org.junit.rules.TemporaryFolder

/** SessionStore auf einer Datei im Test-Verzeichnis (eine pro Test, DataStore erlaubt nur eine Instanz pro Datei). */
fun TemporaryFolder.sessionStore(): SessionStore =
    SessionStore(PreferenceDataStoreFactory.create { newFile("session.preferences_pb") })
