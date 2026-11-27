package de.safetube.parent.push

import de.safetube.core.push.NtfyEvent
import de.safetube.core.push.ParentPush

/** Was die App mit Benachrichtigungen tun kann (Android-Teil in ParentNotifier, in Tests ein Fake). */
interface ParentNotifications {
    fun showRequest(push: ParentPush.NewRequest)
    fun cancelRequest(requestId: Int)
    fun showInfo(title: String, message: String)
    fun showAlert(title: String, message: String)
}

/** Übersetzt eine Push-Nachricht in Benachrichtigungen. [markSeen] liefert true, wenn die Anfrage neu ist. */
class ParentPushHandler(
    private val notifications: ParentNotifications,
    private val markSeen: suspend (Int) -> Boolean,
) {
    suspend fun handle(event: NtfyEvent) {
        when (val push = ParentPush.from(event) ?: return) {
            is ParentPush.NewRequest -> if (markSeen(push.requestId)) notifications.showRequest(push)
            is ParentPush.DecidedElsewhere -> {
                notifications.cancelRequest(push.requestId)
                notifications.showInfo(push.title, push.message)
            }
            is ParentPush.Info -> notifications.showInfo(push.title, push.message)
            is ParentPush.Alert -> notifications.showAlert(push.title, push.message)
        }
    }
}
