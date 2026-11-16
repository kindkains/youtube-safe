package de.safetube.core.extractor

/**
 * Welche Streams spielt die Vorschau? (Festlegung 11 im Plan)
 * 1. kombinierter Stream (Bild+Ton), höchster bis [maxHeight], sonst der kleinste
 * 2. Video-only bis [maxHeight] + Ton zusammengeführt (mp4/m4a bevorzugt)
 * 3. HLS
 */
object StreamChoice {

    fun pick(candidates: List<StreamCandidate>, maxHeight: Int = 720): PlaybackSource? {
        val byQuality = compareByDescending<StreamCandidate> { it.height }.thenByDescending { it.mime == "video/mp4" }

        val muxed = candidates.filter { it.kind == StreamKind.MUXED }
        val bestMuxed = muxed.filter { it.height <= maxHeight }.sortedWith(byQuality).firstOrNull()
            ?: muxed.minByOrNull { it.height }
        if (bestMuxed != null) return PlaybackSource.Progressive(bestMuxed.url)

        val video = candidates.filter { it.kind == StreamKind.VIDEO_ONLY && it.height in 1..maxHeight }
            .sortedWith(byQuality).firstOrNull()
        val audio = candidates.filter { it.kind == StreamKind.AUDIO }
            .sortedWith(compareByDescending<StreamCandidate> { it.mime == "audio/mp4" }.thenByDescending { it.bitrate })
            .firstOrNull()
        if (video != null && audio != null) return PlaybackSource.Merged(video.url, audio.url)

        return candidates.firstOrNull { it.kind == StreamKind.HLS }?.let { PlaybackSource.Hls(it.url) }
    }
}
