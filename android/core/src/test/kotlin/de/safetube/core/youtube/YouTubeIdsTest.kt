package de.safetube.core.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeIdsTest {

    private val video = YouTubeLink.Video("dQw4w9WgXcQ")
    private val channel = YouTubeLink.Channel("UCuAXFkgsw1L7xaCfnd5JJOw")

    @Test
    fun `Video-Links in allen üblichen Formen`() {
        assertEquals(video, YouTubeIds.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(video, YouTubeIds.parse("https://www.youtube.com/watch?feature=share&v=dQw4w9WgXcQ&t=42"))
        assertEquals(video, YouTubeIds.parse("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(video, YouTubeIds.parse("youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(video, YouTubeIds.parse("  https://youtu.be/dQw4w9WgXcQ?si=abc  "))
        assertEquals(video, YouTubeIds.parse("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals(video, YouTubeIds.parse("https://www.youtube.com/live/dQw4w9WgXcQ?feature=share"))
        assertEquals(video, YouTubeIds.parse("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ"))
    }

    @Test
    fun `Kanal-Links und nackte Kanal-ID`() {
        assertEquals(channel, YouTubeIds.parse("https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw"))
        assertEquals(channel, YouTubeIds.parse("https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw/videos"))
        assertEquals(channel, YouTubeIds.parse("UCuAXFkgsw1L7xaCfnd5JJOw"))
    }

    @Test
    fun `Kanal-Aliase müssen später aufgelöst werden`() {
        assertEquals(
            YouTubeLink.ChannelAlias("https://www.youtube.com/@DieSendungmitderMaus"),
            YouTubeIds.parse("https://www.youtube.com/@DieSendungmitderMaus/videos"),
        )
        assertEquals(
            YouTubeLink.ChannelAlias("https://www.youtube.com/c/Beispiel"),
            YouTubeIds.parse("https://youtube.com/c/Beispiel"),
        )
        assertEquals(
            YouTubeLink.ChannelAlias("https://www.youtube.com/user/Beispiel"),
            YouTubeIds.parse("https://www.youtube.com/user/Beispiel/featured"),
        )
    }

    @Test
    fun `Unsinn und fremde Seiten werden nicht erkannt`() {
        assertNull(YouTubeIds.parse(""))
        assertNull(YouTubeIds.parse("hallo welt"))
        assertNull(YouTubeIds.parse("dQw4w9WgXcQ"))
        assertNull(YouTubeIds.parse("https://example.com/watch?v=dQw4w9WgXcQ"))
        assertNull(YouTubeIds.parse("https://www.youtube.com/watch?v=zukurz"))
        assertNull(YouTubeIds.parse("https://www.youtube.com/channel/UCzukurz"))
        assertNull(YouTubeIds.parse("https://www.youtube.com/c"))
        assertNull(YouTubeIds.parse("https://www.youtube.com/"))
    }

    @Test
    fun `IDs aus Extractor-URLs`() {
        assertEquals("UCuAXFkgsw1L7xaCfnd5JJOw", YouTubeIds.channelIdFromUrl("https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw"))
        assertNull(YouTubeIds.channelIdFromUrl("https://www.youtube.com/@handle"))
        assertNull(YouTubeIds.channelIdFromUrl(null))
        assertEquals("dQw4w9WgXcQ", YouTubeIds.videoIdFromUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("https://www.youtube.com/watch?v=abc", YouTubeIds.videoUrl("abc"))
        assertEquals("https://www.youtube.com/channel/UCx", YouTubeIds.channelUrl("UCx"))
    }
}
