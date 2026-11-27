package de.safetube.parent.push

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.safetube.core.push.ParentPush
import de.safetube.parent.MainActivity
import de.safetube.parent.R
import java.util.concurrent.atomic.AtomicInteger

class ParentNotifier(private val context: Context) : ParentNotifications {
    private val manager = NotificationManagerCompat.from(context)
    private val nextId = AtomicInteger(INFO_BASE)

    fun createChannels() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(CH_REQUESTS, "Anfragen", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Neue Freigabe-Anfragen der Kinder" },
                NotificationChannel(CH_INFO, "Infos", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Andere Eltern haben entschieden, Belohnung abgelaufen, neues Gerät" },
                NotificationChannel(CH_ALERTS, "Warnungen", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Gerät meldet sich nicht mehr, App gestört" },
                NotificationChannel(CH_CONNECTION, "Verbindung", NotificationManager.IMPORTANCE_MIN)
                    .apply { description = "Ständige Benachrichtigung des Push-Dienstes – kann ausgeblendet werden" },
            ),
        )
    }

    override fun showRequest(push: ParentPush.NewRequest) {
        val notification = NotificationCompat.Builder(context, CH_REQUESTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(push.title)
            .setContentText(push.message.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(push.message))
            .setContentIntent(openApp(push.requestId))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Freigeben", decision(push.requestId, DecisionReceiver.ACTION_APPROVE))
            .addAction(0, "Ablehnen", decision(push.requestId, DecisionReceiver.ACTION_REJECT))
            .build()
        notify(requestNotificationId(push.requestId), notification)
    }

    override fun cancelRequest(requestId: Int) = manager.cancel(requestNotificationId(requestId))

    /** Ersetzt die Anfrage-Benachrichtigung nach einer Push-Aktion durch das Ergebnis (ohne Knöpfe). */
    fun showDecisionResult(requestId: Int, text: String) {
        val notification = NotificationCompat.Builder(context, CH_REQUESTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Anfrage #$requestId")
            .setContentText(text)
            .setContentIntent(openApp(requestId))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        notify(requestNotificationId(requestId), notification)
    }

    override fun showInfo(title: String, message: String) = simple(CH_INFO, title, message)

    override fun showAlert(title: String, message: String) = simple(CH_ALERTS, title, message)

    fun connection(text: String): Notification =
        NotificationCompat.Builder(context, CH_CONNECTION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("SafeTube Eltern")
            .setContentText(text)
            .setContentIntent(openApp(null))
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun simple(channel: String, title: String, message: String) {
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openApp(null))
            .setAutoCancel(true)
            .build()
        notify(nextId.incrementAndGet(), notification)
    }

    private fun openApp(requestId: Int?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (requestId != null) intent.putExtra(MainActivity.EXTRA_REQUEST_ID, requestId)
        return PendingIntent.getActivity(
            context,
            requestId ?: 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun decision(requestId: Int, action: String): PendingIntent {
        val intent = Intent(context, DecisionReceiver::class.java)
            .setAction(action)
            .putExtra(MainActivity.EXTRA_REQUEST_ID, requestId)
        val code = requestId * 2 + if (action == DecisionReceiver.ACTION_APPROVE) 0 else 1
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    @SuppressLint("MissingPermission") // geprüft über areNotificationsEnabled()
    private fun notify(id: Int, notification: Notification) {
        if (manager.areNotificationsEnabled()) manager.notify(id, notification)
    }

    companion object {
        const val CH_REQUESTS = "anfragen"
        const val CH_INFO = "infos"
        const val CH_ALERTS = "warnungen"
        const val CH_CONNECTION = "verbindung"
        const val CONNECTION_ID = 1
        private const val INFO_BASE = 10
        private const val REQUEST_BASE = 1_000_000

        fun requestNotificationId(requestId: Int) = REQUEST_BASE + requestId
    }
}
