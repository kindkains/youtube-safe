package de.safetube.parent.push

import de.safetube.core.push.NtfyEvent
import de.safetube.core.push.ParentPush
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ParentPushHandlerTest {

    private val shown = mutableListOf<String>()
    private val seen = mutableSetOf<Int>()

    private val notifications = object : ParentNotifications {
        override fun showRequest(push: ParentPush.NewRequest) { shown += "request ${push.requestId}" }
        override fun cancelRequest(requestId: Int) { shown += "cancel $requestId" }
        override fun showInfo(title: String, message: String) { shown += "info $title" }
        override fun showAlert(title: String, message: String) { shown += "alert $title" }
    }

    private val handler = ParentPushHandler(notifications) { id -> seen.add(id) }

    private fun event(vararg tags: String, title: String = "T") =
        NtfyEvent(id = "x", event = "message", title = title, message = "M", tags = tags.toList())

    @Test
    fun `neue Anfrage nur einmal melden`() = runBlocking {
        handler.handle(event("request:9"))
        handler.handle(event("request:9"))

        assertEquals(listOf("request 9"), shown)
    }

    @Test
    fun `Entscheidung anderswo entfernt die Anfrage und informiert`() = runBlocking {
        handler.handle(event("info", "decided:9", title = "Anna hat freigegeben"))

        assertEquals(listOf("cancel 9", "info Anna hat freigegeben"), shown)
    }

    @Test
    fun `Info, Warnung, sync`() = runBlocking {
        handler.handle(event("info", title = "Neues Gerät gekoppelt"))
        handler.handle(event("alert", title = "Gerät meldet sich nicht mehr"))
        handler.handle(event("sync"))

        assertEquals(listOf("info Neues Gerät gekoppelt", "alert Gerät meldet sich nicht mehr"), shown)
    }
}
