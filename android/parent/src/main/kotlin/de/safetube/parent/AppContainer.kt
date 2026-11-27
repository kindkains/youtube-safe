package de.safetube.parent

import android.content.Context
import de.safetube.core.api.HttpParentApi
import de.safetube.core.api.HttpTransport
import de.safetube.core.api.ParentApi
import de.safetube.core.extractor.NewPipeSource
import de.safetube.core.extractor.OkHttpDownloader
import de.safetube.core.extractor.YouTubeSource
import de.safetube.parent.session.Auth
import de.safetube.parent.session.SessionStore
import de.safetube.parent.session.sessionDataStore
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** Alle langlebigen Objekte der App, von Hand verdrahtet (kein DI-Framework). */
class AppContainer(context: Context) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val defaultServer: String = BuildConfig.DEFAULT_SERVER
    val sessionStore = SessionStore(context.sessionDataStore)

    /** API mit der aktuellen Sitzung (Adresse und Token werden bei jedem Aufruf neu gelesen). */
    val api: ParentApi = apiFor({ sessionStore.current?.serverUrl ?: defaultServer }, { sessionStore.current?.token })

    val auth = Auth(sessionStore) { server, token -> apiFor({ server }, { token }) }

    val youtube: YouTubeSource by lazy { NewPipeSource(OkHttpDownloader(http)) }

    private fun apiFor(server: () -> String, token: () -> String?): ParentApi =
        HttpParentApi(HttpTransport(http, server, token))
}
