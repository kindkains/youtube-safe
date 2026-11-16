package de.safetube.core.extractor

import de.safetube.core.api.YouTubeException
import de.safetube.core.youtube.YouTubeIds
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Zugriff auf YouTube ohne Filter (Eltern-App). Die Kinder-App setzt in Plan 3 das Gate davor. */
interface YouTubeSource {
    suspend fun search(query: String, filter: SearchFilter = SearchFilter.ALL): List<SearchHit>

    /** [channelIdOrUrl]: Kanal-ID `UC…` oder eine beliebige Kanal-URL (auch `@handle`). */
    suspend fun channel(channelIdOrUrl: String, maxVideos: Int = 30): ChannelPage

    suspend fun stream(videoId: String): StreamDetails
}

/** NewPipe wird einmal pro Prozess initialisiert (deutsche Sprache, Land DE). */
object NewPipeSetup {
    @Volatile
    private var initialized = false

    @Synchronized
    fun ensure(downloader: Downloader) {
        if (initialized) return
        NewPipe.init(downloader, Localization("de", "DE"), ContentCountry("DE"))
        initialized = true
    }
}

class NewPipeSource(downloader: Downloader) : YouTubeSource {

    init {
        NewPipeSetup.ensure(downloader)
    }

    private val service get() = ServiceList.YouTube

    override suspend fun search(query: String, filter: SearchFilter): List<SearchHit> = io {
        val handler = service.searchQHFactory.fromQuery(query, filter.contentFilters, "")
        SearchInfo.getInfo(service, handler).relatedItems.mapNotNull { item ->
            when (item) {
                is StreamInfoItem -> Mapping.video(item)?.let(SearchHit::Video)
                is ChannelInfoItem -> SearchHit.Channel(Mapping.channel(item))
                else -> null
            }
        }
    }

    override suspend fun channel(channelIdOrUrl: String, maxVideos: Int): ChannelPage = io {
        val url = if (YouTubeIds.isChannelId(channelIdOrUrl)) YouTubeIds.channelUrl(channelIdOrUrl) else channelIdOrUrl
        val info = ChannelInfo.getInfo(service, url)
        val videosTab = info.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters }
        val videos = videosTab
            ?.let { ChannelTabInfo.getInfo(service, it).relatedItems }
            .orEmpty()
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull(Mapping::video)
        ChannelPage(
            channelId = info.id,
            name = info.name.orEmpty(),
            avatarUrl = Mapping.image(info.avatars),
            videos = videos.take(maxVideos),
        )
    }

    override suspend fun stream(videoId: String): StreamDetails = io {
        val info = StreamInfo.getInfo(service, YouTubeIds.videoUrl(videoId))
        StreamDetails(
            videoId = videoId,
            title = info.name.orEmpty(),
            channelId = YouTubeIds.channelIdFromUrl(info.uploaderUrl),
            channelName = info.uploaderName.orEmpty(),
            thumbnailUrl = Mapping.image(info.thumbnails),
            durationSeconds = info.duration,
            candidates = Mapping.candidates(info.videoStreams, info.videoOnlyStreams, info.audioStreams, info.hlsUrl),
        )
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: ReCaptchaException) {
            throw YouTubeException("YouTube verlangt gerade eine Bestätigung. Bitte später noch einmal versuchen.", e)
        } catch (e: ExtractionException) {
            throw YouTubeException("YouTube antwortet gerade nicht wie erwartet.", e)
        } catch (e: IOException) {
            throw YouTubeException("YouTube ist gerade nicht erreichbar.", e)
        }
    }
}
