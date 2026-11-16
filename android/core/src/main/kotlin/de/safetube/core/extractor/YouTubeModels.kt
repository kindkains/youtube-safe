package de.safetube.core.extractor

data class VideoItem(
    val videoId: String,
    val title: String,
    /** Kanal-ID `UC…`, falls aus der Uploader-URL lesbar. */
    val channelId: String?,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val isShort: Boolean,
    val uploadedText: String?,
)

data class ChannelItem(
    val channelId: String?,
    val name: String,
    val url: String,
    val avatarUrl: String?,
    val subscribers: Long,
)

data class ChannelPage(
    val channelId: String,
    val name: String,
    val avatarUrl: String?,
    val videos: List<VideoItem>,
)

enum class StreamKind { MUXED, VIDEO_ONLY, AUDIO, HLS }

data class StreamCandidate(
    val url: String,
    val kind: StreamKind,
    /** Bildhöhe in Pixeln, 0 = unbekannt bzw. Ton. */
    val height: Int,
    /** Durchschnittliche Bitrate (kbit/s) bei Ton, sonst 0. */
    val bitrate: Int,
    val mime: String?,
)

data class StreamDetails(
    val videoId: String,
    val title: String,
    val channelId: String?,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val candidates: List<StreamCandidate>,
)

/** Was der Player abspielen soll. */
sealed interface PlaybackSource {
    data class Progressive(val url: String) : PlaybackSource
    data class Merged(val videoUrl: String, val audioUrl: String) : PlaybackSource
    data class Hls(val url: String) : PlaybackSource
}

sealed interface SearchHit {
    data class Video(val item: VideoItem) : SearchHit
    data class Channel(val item: ChannelItem) : SearchHit
}

enum class SearchFilter(val contentFilters: List<String>) {
    ALL(emptyList()),
    VIDEOS(listOf("videos")),
    CHANNELS(listOf("channels")),
}
