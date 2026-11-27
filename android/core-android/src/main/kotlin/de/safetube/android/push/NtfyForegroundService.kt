package de.safetube.android.push

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import de.safetube.core.api.attempt
import de.safetube.core.push.NtfyEvent
import de.safetube.core.push.NtfyStream
import de.safetube.core.push.PushSignal
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

data class PushConnection(val baseUrl: String, val topic: String, val sinceId: String?)

/**
 * Vordergrunddienst mit Dauerverbindung zu einem ntfy-Topic (Spec 8). Eltern- und Kinder-App
 * leiten davon ab und entscheiden selbst, was eine Nachricht bedeutet.
 */
abstract class NtfyForegroundService : LifecycleService() {

    protected abstract val notificationId: Int
    protected abstract fun buildNotification(text: String): Notification

    /** null = nicht angemeldet bzw. kein Topic → der Dienst beendet sich. */
    protected abstract suspend fun connection(): PushConnection?
    protected abstract suspend fun onMessage(event: NtfyEvent)
    protected abstract suspend fun saveLastId(id: String)

    protected open val httpClient: OkHttpClient get() = defaultClient

    private val defaultClient by lazy { OkHttpClient() }
    private var job: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val notification = buildNotification("Verbinde …")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(notificationId, notification)
        }
        if (job?.isActive != true) job = lifecycleScope.launch { run() }
        return START_STICKY
    }

    private suspend fun run() {
        val connection = connection()
        if (connection == null) {
            stopSelf()
            return
        }
        NtfyStream(httpClient, connection.baseUrl, connection.topic).signals(connection.sinceId).collect { signal ->
            when (signal) {
                PushSignal.Connected -> update("Verbunden – Benachrichtigungen kommen sofort an")
                is PushSignal.Disconnected -> update("Keine Verbindung, versuche es weiter …")
                is PushSignal.Message -> {
                    attempt { onMessage(signal.event) }.onFailure { Log.w(TAG, "Push-Nachricht nicht verarbeitet", it) }
                    if (signal.event.id.isNotEmpty()) saveLastId(signal.event.id)
                }
            }
        }
    }

    @SuppressLint("MissingPermission") // geprüft über areNotificationsEnabled()
    private fun update(text: String) {
        val manager = NotificationManagerCompat.from(this)
        if (manager.areNotificationsEnabled()) manager.notify(notificationId, buildNotification(text))
    }

    private companion object {
        const val TAG = "NtfyService"
    }
}
