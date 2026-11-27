package de.safetube.parent.push

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.safetube.core.push.ParentPush
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ParentNotifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifier = ParentNotifier(context).apply { createChannels() }

    @Test
    fun `vier Kanäle, Verbindung ganz leise`() {
        val channels = manager.notificationChannels.associateBy { it.id }

        assertEquals(
            setOf(ParentNotifier.CH_REQUESTS, ParentNotifier.CH_INFO, ParentNotifier.CH_ALERTS, ParentNotifier.CH_CONNECTION),
            channels.keys,
        )
        assertEquals(NotificationManager.IMPORTANCE_MIN, channels.getValue(ParentNotifier.CH_CONNECTION).importance)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channels.getValue(ParentNotifier.CH_REQUESTS).importance)
    }

    @Test
    fun `Anfrage mit Freigeben und Ablehnen, danach entfernen`() {
        notifier.showRequest(ParentPush.NewRequest(9, "Mia bittet um Freigabe", "Video: Maus"))

        val id = ParentNotifier.requestNotificationId(9)
        val shown = shadowOf(manager).getNotification(id)
        assertEquals("Mia bittet um Freigabe", shown.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(listOf("Freigeben", "Ablehnen"), shown.actions.map { it.title.toString() })

        notifier.cancelRequest(9)
        assertNull(shadowOf(manager).getNotification(id))
    }

    @Test
    fun `Ergebnis einer Push-Aktion ersetzt die Anfrage ohne Aktionen`() {
        notifier.showRequest(ParentPush.NewRequest(9, "Mia bittet um Freigabe", "Video: Maus"))

        notifier.showDecisionResult(9, "Freigegeben ✓")

        val shown = shadowOf(manager).getNotification(ParentNotifier.requestNotificationId(9))
        assertEquals("Freigegeben ✓", shown.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(shown.actions == null || shown.actions.isEmpty())
    }

    @Test
    fun `Verbindungsbenachrichtigung ist dauerhaft`() {
        val notification = notifier.connection("Verbunden")

        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(ParentNotifier.CH_CONNECTION, notification.channelId)
    }
}
