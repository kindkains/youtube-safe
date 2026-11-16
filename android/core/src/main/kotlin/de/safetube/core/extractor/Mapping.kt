package de.safetube.core.extractor

import de.safetube.core.youtube.YouTubeIds
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream

/** NewPipe-Typen → eigene Typen. Einzige Stelle (neben YouTubeSource), die NewPipe-Klassen kennt. */
internal object Mapping {

    private val RESOLUTION_HEIGHT = Regex("^(\\d+)p")
    private val IMAGE_PREFERENCE = listOf(
        Image.ResolutionLevel.MEDIUM,
        Image.ResolutionLevel.HIGH,
        Image.ResolutionLevel.LOW,
    )

    fun video(item: StreamInfoItem): VideoItem? {
        val videoId = YouTubeIds.videoIdFromUrl(item.url) ?: return null
        return VideoItem(
            videoId = videoId,
            title = item.name.orEmpty(),
            channelId = YouTubeIds.channelIdFromUrl(item.uploaderUrl),
            channelName = item.uploaderName.orEmpty(),
            thumbnailUrl = image(item.thumbnails),
            durationSeconds = item.duration.coerceAtLeast(0),
            isShort = item.isShortFormContent || item.url.contains("/shorts/"),
            uploadedText = item.textualUploadDate,
        )
    }

    fun channel(item: ChannelInfoItem): ChannelItem = ChannelItem(
        channelId = YouTubeIds.channelIdFromUrl(item.url),
        name = item.name.orEmpty(),
        url = item.url,
        avatarUrl = image(item.thumbnails),
        subscribers = item.subscriberCount,
    )

    fun image(images: List<Image>?): String? {
        if (images.isNullOrEmpty()) return null
        for (level in IMAGE_PREFERENCE) {
            images.firstOrNull { it.estimatedResolutionLevel == level }?.let { return it.url }
        }
        return images.first().url
    }

    fun candidates(
        videos: List<VideoStream>,
        videoOnly: List<VideoStream>,
        audio: List<AudioStream>,
        hlsUrl: String?,
    ): List<StreamCandidate> = buildList {
        videos.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && !it.isVideoOnly() }
            .forEach { add(StreamCandidate(it.content, StreamKind.MUXED, height(it), 0, it.format?.mimeType)) }
        videoOnly.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .forEach { add(StreamCandidate(it.content, StreamKind.VIDEO_ONLY, height(it), 0, it.format?.mimeType)) }
        val progressiveAudio = audio.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        // Bei mehreren Tonspuren (Synchronfassungen) die Originalspur nehmen.
        val original = progressiveAudio.filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
        original.ifEmpty { progressiveAudio }
            .forEach { add(StreamCandidate(it.content, StreamKind.AUDIO, 0, it.averageBitrate, it.format?.mimeType)) }
        if (!hlsUrl.isNullOrBlank()) add(StreamCandidate(hlsUrl, StreamKind.HLS, 0, 0, null))
    }

    private fun height(stream: VideoStream): Int =
        stream.height.takeIf { it > 0 }
            ?: RESOLUTION_HEIGHT.find(stream.getResolution().orEmpty())?.groupValues?.get(1)?.toInt()
            ?: 0
}
