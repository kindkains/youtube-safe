package de.safetube.core.live

import de.safetube.core.extractor.NewPipeSource
import de.safetube.core.extractor.OkHttpDownloader
import de.safetube.core.extractor.SearchFilter
import de.safetube.core.extractor.SearchHit
import de.safetube.core.extractor.StreamChoice
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gegen das echte YouTube. Läuft nur mit: ./gradlew :core:test -Plive */
class LiveYouTubeTest {

    private val source = NewPipeSource(OkHttpDownloader(OkHttpClient()))
    private val videoId = "dQw4w9WgXcQ"
    private val channelId = "UCuAXFkgsw1L7xaCfnd5JJOw"

    @Test
    fun suche() = runBlocking {
        val hits = source.search("Rick Astley Never Gonna Give You Up", SearchFilter.VIDEOS)

        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { it is SearchHit.Video })
    }

    @Test
    fun kanal() = runBlocking {
        val page = source.channel(channelId, maxVideos = 10)

        assertEquals(channelId, page.channelId)
        assertTrue(page.videos.isNotEmpty())
        assertTrue(page.videos.size <= 10)
    }

    @Test
    fun stream() = runBlocking {
        val details = source.stream(videoId)

        assertEquals(channelId, details.channelId)
        assertNotNull(StreamChoice.pick(details.candidates))
    }
}
