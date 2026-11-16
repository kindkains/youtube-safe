package de.safetube.core.youtube

import java.net.URI
import java.net.URISyntaxException

sealed interface YouTubeLink {
    data class Video(val videoId: String) : YouTubeLink
    data class Channel(val channelId: String) : YouTubeLink

    /** `@handle`, `/c/…` oder `/user/…`: die Kanal-ID kennt erst der Extractor. */
    data class ChannelAlias(val url: String) : YouTubeLink
}

object YouTubeIds {
    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
    private val CHANNEL_ID = Regex("^UC[A-Za-z0-9_-]{22}$")
    private val HOSTS = setOf(
        "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com",
        "youtube-nocookie.com", "www.youtube-nocookie.com",
    )
    private val VIDEO_PATHS = setOf("shorts", "live", "embed", "v")

    fun isVideoId(value: String): Boolean = VIDEO_ID.matches(value)
    fun isChannelId(value: String): Boolean = CHANNEL_ID.matches(value)

    fun videoUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"
    fun channelUrl(channelId: String): String = "https://www.youtube.com/channel/$channelId"

    fun parse(input: String): YouTubeLink? {
        val text = input.trim()
        if (text.isEmpty()) return null
        if (isChannelId(text)) return YouTubeLink.Channel(text)

        val uri = toUri(text) ?: return null
        val host = uri.host?.lowercase() ?: return null
        val segments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }

        if (host == "youtu.be") {
            return segments.firstOrNull()?.takeIf(::isVideoId)?.let(YouTubeLink::Video)
        }
        if (host !in HOSTS) return null

        val first = segments.firstOrNull() ?: return null
        return when {
            first == "watch" -> queryParameter(uri, "v")?.takeIf(::isVideoId)?.let(YouTubeLink::Video)
            first in VIDEO_PATHS -> segments.getOrNull(1)?.takeIf(::isVideoId)?.let(YouTubeLink::Video)
            first == "channel" -> segments.getOrNull(1)?.takeIf(::isChannelId)?.let(YouTubeLink::Channel)
            first.startsWith("@") && first.length > 1 -> YouTubeLink.ChannelAlias("https://www.youtube.com/$first")
            (first == "c" || first == "user") && segments.size >= 2 ->
                YouTubeLink.ChannelAlias("https://www.youtube.com/$first/${segments[1]}")
            else -> null
        }
    }

    fun channelIdFromUrl(url: String?): String? = (url?.let(::parse) as? YouTubeLink.Channel)?.channelId

    fun videoIdFromUrl(url: String?): String? = (url?.let(::parse) as? YouTubeLink.Video)?.videoId

    private fun toUri(text: String): URI? {
        val withScheme = if (text.contains("://")) text else "https://$text"
        return try {
            URI(withScheme)
        } catch (e: URISyntaxException) {
            null
        }
    }

    private fun queryParameter(uri: URI, key: String): String? =
        uri.rawQuery
            ?.split('&')
            ?.map { it.split('=', limit = 2) }
            ?.firstOrNull { it.first() == key }
            ?.getOrNull(1)
}
