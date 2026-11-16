package de.safetube.core.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamChoiceTest {

    private fun muxed(height: Int) = StreamCandidate("m$height", StreamKind.MUXED, height, 0, "video/mp4")
    private fun videoOnly(height: Int, mime: String = "video/mp4") = StreamCandidate("v$height$mime", StreamKind.VIDEO_ONLY, height, 0, mime)
    private fun audio(bitrate: Int, mime: String = "audio/mp4") = StreamCandidate("a$bitrate$mime", StreamKind.AUDIO, 0, bitrate, mime)

    @Test
    fun `kombinierter Stream hat Vorrang`() {
        val source = StreamChoice.pick(listOf(muxed(360), videoOnly(720), audio(128)))

        assertEquals(PlaybackSource.Progressive("m360"), source)
    }

    @Test
    fun `höchster kombinierter Stream bis 720p`() {
        assertEquals(PlaybackSource.Progressive("m720"), StreamChoice.pick(listOf(muxed(240), muxed(720), muxed(1080))))
        assertEquals(PlaybackSource.Progressive("m1080"), StreamChoice.pick(listOf(muxed(1080))))
    }

    @Test
    fun `ohne kombinierten Stream Bild und Ton zusammenführen`() {
        val source = StreamChoice.pick(
            listOf(videoOnly(1080), videoOnly(720, "video/webm"), videoOnly(720), audio(160, "audio/webm"), audio(128)),
        )

        assertEquals(PlaybackSource.Merged(videoUrl = "v720video/mp4", audioUrl = "a128audio/mp4"), source)
    }

    @Test
    fun `sonst HLS, sonst nichts`() {
        assertEquals(PlaybackSource.Hls("h"), StreamChoice.pick(listOf(StreamCandidate("h", StreamKind.HLS, 0, 0, null))))
        assertNull(StreamChoice.pick(listOf(videoOnly(720))))
        assertNull(StreamChoice.pick(emptyList()))
    }
}
