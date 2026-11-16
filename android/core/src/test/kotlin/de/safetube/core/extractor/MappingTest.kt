package de.safetube.core.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

class MappingTest {

    private val serviceId = ServiceList.YouTube.serviceId

    private fun image(url: String, level: Image.ResolutionLevel) = Image(url, -1, -1, level)

    @Test
    fun `Video aus Suchergebnis`() {
        val item = StreamInfoItem(serviceId, "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "Never Gonna", StreamType.VIDEO_STREAM)
            .apply {
                uploaderUrl = "https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw"
                uploaderName = "Rick Astley"
                duration = 213
                textualUploadDate = "vor 15 Jahren"
                thumbnails = listOf(
                    image("https://i/low.jpg", Image.ResolutionLevel.LOW),
                    image("https://i/medium.jpg", Image.ResolutionLevel.MEDIUM),
                )
            }

        val video = Mapping.video(item)!!

        assertEquals("dQw4w9WgXcQ", video.videoId)
        assertEquals("UCuAXFkgsw1L7xaCfnd5JJOw", video.channelId)
        assertEquals("Rick Astley", video.channelName)
        assertEquals("https://i/medium.jpg", video.thumbnailUrl)
        assertEquals(213L, video.durationSeconds)
        assertEquals(false, video.isShort)
    }

    @Test
    fun `Shorts werden erkannt, fremde URLs verworfen`() {
        val short = StreamInfoItem(serviceId, "https://www.youtube.com/shorts/dQw4w9WgXcQ", "Kurz", StreamType.VIDEO_STREAM)
        val broken = StreamInfoItem(serviceId, "https://example.com/x", "Kaputt", StreamType.VIDEO_STREAM)

        assertTrue(Mapping.video(short)!!.isShort)
        assertNull(Mapping.video(broken))
    }

    @Test
    fun `Kanal aus Suchergebnis`() {
        val item = ChannelInfoItem(serviceId, "https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw", "Rick Astley")
            .apply { subscriberCount = 4_000_000 }

        val channel = Mapping.channel(item)

        assertEquals("UCuAXFkgsw1L7xaCfnd5JJOw", channel.channelId)
        assertEquals("Rick Astley", channel.name)
        assertEquals(4_000_000L, channel.subscribers)
        assertNull(channel.avatarUrl)
    }

    @Test
    fun `Bildauswahl bevorzugt mittlere Auflösung`() {
        assertNull(Mapping.image(emptyList()))
        assertEquals("https://h", Mapping.image(listOf(image("https://h", Image.ResolutionLevel.HIGH))))
        assertEquals(
            "https://u",
            Mapping.image(listOf(image("https://u", Image.ResolutionLevel.UNKNOWN), image("https://x", Image.ResolutionLevel.UNKNOWN))),
        )
    }

    private fun video(id: String, resolution: String, videoOnly: Boolean, format: MediaFormat = MediaFormat.MPEG_4) =
        VideoStream.Builder()
            .setId(id)
            .setContent("https://v/$id", true)
            .setMediaFormat(format)
            .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .setIsVideoOnly(videoOnly)
            .setResolution(resolution)
            .build()

    private fun audio(id: String, bitrate: Int) =
        AudioStream.Builder()
            .setId(id)
            .setContent("https://a/$id", true)
            .setMediaFormat(MediaFormat.M4A)
            .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .setAverageBitrate(bitrate)
            .build()

    @Test
    fun `Stream-Kandidaten mit Höhe aus der Auflösung`() {
        val candidates = Mapping.candidates(
            videos = listOf(video("18", "360p", videoOnly = false)),
            videoOnly = listOf(video("136", "720p", videoOnly = true), video("299", "1080p60", videoOnly = true)),
            audio = listOf(audio("140", 128)),
            hlsUrl = "",
        )

        assertEquals(
            listOf(
                StreamCandidate("https://v/18", StreamKind.MUXED, 360, 0, "video/mp4"),
                StreamCandidate("https://v/136", StreamKind.VIDEO_ONLY, 720, 0, "video/mp4"),
                StreamCandidate("https://v/299", StreamKind.VIDEO_ONLY, 1080, 0, "video/mp4"),
                StreamCandidate("https://a/140", StreamKind.AUDIO, 0, 128, "audio/mp4"),
            ),
            candidates,
        )
    }

    @Test
    fun `HLS wird als eigener Kandidat geführt`() {
        val candidates = Mapping.candidates(emptyList(), emptyList(), emptyList(), "https://hls/master.m3u8")

        assertEquals(listOf(StreamCandidate("https://hls/master.m3u8", StreamKind.HLS, 0, 0, null)), candidates)
    }
}
