package de.safetube.parent.testing

import de.safetube.core.extractor.ChannelPage
import de.safetube.core.extractor.SearchFilter
import de.safetube.core.extractor.SearchHit
import de.safetube.core.extractor.StreamCandidate
import de.safetube.core.extractor.StreamDetails
import de.safetube.core.extractor.StreamKind
import de.safetube.core.extractor.VideoItem
import de.safetube.core.extractor.YouTubeSource

class FakeYouTubeSource : YouTubeSource {
    val calls = mutableListOf<String>()
    var failure: Exception? = null
    val searchResults = mutableMapOf<String, List<SearchHit>>()
    /** Schlüssel: Kanal-ID oder URL, so wie sie übergeben wird. */
    val channels = mutableMapOf<String, ChannelPage>()
    val streams = mutableMapOf<String, StreamDetails>()

    private fun record(call: String) {
        calls += call
        failure?.let {
            failure = null
            throw it
        }
    }

    override suspend fun search(query: String, filter: SearchFilter): List<SearchHit> {
        record("search $query $filter")
        return searchResults[query].orEmpty()
    }

    override suspend fun channel(channelIdOrUrl: String, maxVideos: Int): ChannelPage {
        record("channel $channelIdOrUrl $maxVideos")
        val page = channels.getValue(channelIdOrUrl)
        return page.copy(videos = page.videos.take(maxVideos))
    }

    override suspend fun stream(videoId: String): StreamDetails {
        record("stream $videoId")
        return streams.getValue(videoId)
    }

    companion object {
        fun video(id: String, channelId: String? = "UCchan", title: String = "Video $id") =
            VideoItem(id, title, channelId, "Kanal", null, 60, false, null)

        fun stream(id: String, channelId: String = "UCchan", title: String = "Video $id") = StreamDetails(
            videoId = id,
            title = title,
            channelId = channelId,
            channelName = "Kanal",
            thumbnailUrl = null,
            durationSeconds = 60,
            candidates = listOf(StreamCandidate("https://v/$id", StreamKind.MUXED, 360, 0, "video/mp4")),
        )

        fun channel(id: String, name: String = "Kanal $id", videos: Int = 12) =
            ChannelPage(id, name, null, (1..videos).map { video("v$it", id) })
    }
}
