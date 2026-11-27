package de.safetube.android.player

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import de.safetube.core.extractor.OkHttpDownloader
import de.safetube.core.extractor.PlaybackSource

/** Einfacher Player für die Elternvorschau: spielt sofort los, gibt beim Verlassen alles frei. */
@Composable
fun PreviewPlayer(source: PlaybackSource, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(source) {
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(MediaSources.create(source))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        update = { it.player = player },
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
    )
}

@OptIn(UnstableApi::class)
object MediaSources {
    fun create(source: PlaybackSource): MediaSource {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(OkHttpDownloader.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        val progressive = ProgressiveMediaSource.Factory(http)
        return when (source) {
            is PlaybackSource.Progressive -> progressive.createMediaSource(MediaItem.fromUri(source.url))
            is PlaybackSource.Merged -> MergingMediaSource(
                progressive.createMediaSource(MediaItem.fromUri(source.videoUrl)),
                progressive.createMediaSource(MediaItem.fromUri(source.audioUrl)),
            )
            is PlaybackSource.Hls -> HlsMediaSource.Factory(http).createMediaSource(MediaItem.fromUri(source.url))
        }
    }
}
