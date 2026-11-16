package de.safetube.core.push

/** Was eine Push-Nachricht für die Eltern-App bedeutet. */
sealed interface ParentPush {
    val title: String
    val message: String

    data class NewRequest(val requestId: Int, override val title: String, override val message: String) : ParentPush
    data class DecidedElsewhere(val requestId: Int, override val title: String, override val message: String) : ParentPush
    data class Info(override val title: String, override val message: String) : ParentPush
    data class Alert(override val title: String, override val message: String) : ParentPush

    companion object {
        fun from(event: NtfyEvent): ParentPush? {
            if (event.event != "message") return null
            val tags = event.tags
            if (tags.any { it.startsWith("request:") }) {
                return idFrom(tags, "request:")?.let { NewRequest(it, event.title, event.message) }
            }
            if (tags.any { it.startsWith("decided:") }) {
                return idFrom(tags, "decided:")?.let { DecidedElsewhere(it, event.title, event.message) }
            }
            if ("alert" in tags) return Alert(event.title, event.message)
            if ("info" in tags) return Info(event.title, event.message)
            return null
        }

        private fun idFrom(tags: List<String>, prefix: String): Int? =
            tags.first { it.startsWith(prefix) }.removePrefix(prefix).toIntOrNull()
    }
}
