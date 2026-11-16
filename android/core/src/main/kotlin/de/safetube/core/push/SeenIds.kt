package de.safetube.core.push

import de.safetube.core.api.ContentRequest
import de.safetube.core.api.TargetType

/** IDs bereits gemeldeter Anfragen, begrenzt auf die neuesten [capacity]. */
class SeenIds(private val capacity: Int = 500, initial: Collection<Int> = emptyList()) {
    private val ids = LinkedHashSet<Int>()

    init {
        initial.forEach { add(it) }
    }

    /** true, wenn die ID neu war. */
    @Synchronized
    fun add(id: Int): Boolean {
        if (!ids.add(id)) return false
        while (ids.size > capacity) ids.remove(ids.first())
        return true
    }

    @Synchronized
    operator fun contains(id: Int): Boolean = id in ids

    @Synchronized
    fun encode(): String = ids.joinToString(",")

    companion object {
        fun decode(text: String?, capacity: Int = 500): SeenIds =
            SeenIds(capacity, text.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() })
    }
}

/** 15-Minuten-Abgleich: welche offenen Anfragen müssen noch gemeldet werden? */
object OpenRequestCheck {

    /** Liefert die noch nicht gemeldeten Anfragen und trägt sie in [seen] ein. */
    fun newOnes(open: List<ContentRequest>, seen: SeenIds): List<ContentRequest> = open.filter { seen.add(it.id) }

    /** Gleicher Wortlaut wie server/app/events.py request_created(). */
    fun notificationFor(request: ContentRequest): ParentPush.NewRequest {
        val label = if (request.targetType == TargetType.VIDEO) "Video" else "Kanal"
        var message = "$label: ${request.title.ifEmpty { request.targetId }}"
        if (request.channelName.isNotEmpty() && request.targetType == TargetType.VIDEO) {
            message += " (${request.channelName})"
        }
        if (request.childMessage.isNotEmpty()) message += "\n„${request.childMessage}“"
        val asks = if (request.liftsBlock) "um Aufhebung einer Sperre" else "um Freigabe"
        return ParentPush.NewRequest(request.id, "${request.childName} bittet $asks", message)
    }
}
