package de.safetube.parent.push

import android.app.Notification
import de.safetube.android.push.NtfyForegroundService
import de.safetube.android.push.PushConnection
import de.safetube.core.push.NtfyEvent
import de.safetube.parent.container
import okhttp3.OkHttpClient

class ParentPushService : NtfyForegroundService() {
    private val app get() = applicationContext.container
    private val handler by lazy { ParentPushHandler(app.notifier) { app.sessionStore.markSeen(it) } }

    override val notificationId: Int = ParentNotifier.CONNECTION_ID
    override val httpClient: OkHttpClient get() = app.http

    override fun buildNotification(text: String): Notification = app.notifier.connection(text)

    override suspend fun connection(): PushConnection? {
        val session = app.sessionStore.load() ?: return null
        if (session.ntfyUrl.isEmpty() || session.ntfyTopic.isEmpty()) return null
        return PushConnection(session.ntfyUrl, session.ntfyTopic, app.sessionStore.lastNtfyId())
    }

    override suspend fun onMessage(event: NtfyEvent) = handler.handle(event)

    override suspend fun saveLastId(id: String) = app.sessionStore.setLastNtfyId(id)
}
