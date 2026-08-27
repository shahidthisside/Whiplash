package com.whiplash.music.playback.provider.newpipe

import android.util.Log
import com.whiplash.music.data.local.entity.ProviderStatus
import com.whiplash.music.domain.model.AudioQuality
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.playback.provider.PlaybackProvider
import com.whiplash.music.playback.provider.ProviderFailure
import com.whiplash.music.playback.provider.ProviderHealthTracker
import com.whiplash.music.playback.provider.ProviderPlayerInfo
import com.whiplash.music.playback.provider.ResolvedStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException

/**
 * Production [PlaybackProvider] backed by NewPipeExtractor (Provider A).
 *
 * Wraps the search+stream resolution logic proven working in Phase 7a
 * ([NewPipeSmokeTest]) behind the permanent provider abstraction, mapping
 * every failure into the [ProviderFailure] taxonomy so
 * [com.whiplash.music.playback.provider.PlaybackManager] (Phase 7d) can
 * make correct failover decisions, and recording every outcome into
 * [healthTracker] (section 9).
 */
class NewPipePlaybackProvider(
    private val healthTracker: ProviderHealthTracker,
) : PlaybackProvider {

    override val id: String = PROVIDER_ID
    override val displayName: String = "NewPipeExtractor"

    override fun supports(item: PlayableItem): Boolean = item is PlayableItem.YoutubeTrack

    override suspend fun getStream(songId: String, quality: AudioQuality): ResolvedStream = withContext(Dispatchers.IO) {
        runCatchingProviderFailure {
            val youtube = NewPipe.getService(YOUTUBE_SERVICE_NAME)
            val streamInfo = StreamInfo.getInfo(youtube, watchUrlFor(songId))

            val audioStreams = streamInfo.audioStreams
            if (audioStreams.isEmpty()) {
                throw ProviderFailure.UnknownPlaybackFailure(
                    "No audio stream returned for $songId",
                )
            }
            val selected = selectAudioStream(audioStreams, quality)

            val url = selected.content
            if (url.isNullOrBlank()) {
                throw ProviderFailure.UnknownPlaybackFailure(
                    "Resolved audio stream had a blank URL for $songId",
                )
            }

            ResolvedStream(
                streamUrl = url,
                mimeType = selected.format?.mimeType,
                bitrateBps = selected.averageBitrate.takeIf { it > 0 },
                // NewPipeExtractor does not expose an explicit expiry, but
                // resolved googlevideo.com URLs are time-limited in
                // practice; treat any URL as stale after this window so a
                // long-idle queue entry gets a fresh resolve rather than a
                // stale-link playback failure at the moment of use.
                expiresAtEpochMs = System.currentTimeMillis() + STREAM_ASSUMED_TTL_MS,
                providerId = id,
                // The full watch-page response (streamInfo) usually has a
                // higher-resolution thumbnail than the search result item
                // did (e.g. 1280x720 maxresdefault vs. a 480x360 search
                // thumbnail) — surfaced here so PlaybackController can
                // upgrade the displayed artwork once this resolves.
                resolvedArtworkUrl = streamInfo.thumbnails.maxByOrNull { it.height }?.url,
            )
        }
    }

    /**
     * Picks the audio stream closest to the requested [quality] tier
     * (section 61). Streams are ranked by bitrate and split into up to 5
     * roughly-even tiers so this works across videos regardless of how
     * many/which bitrates YouTube actually offers for a given upload —
     * never assumes a fixed absolute bitrate exists.
     */
    private fun selectAudioStream(streams: List<AudioStream>, quality: AudioQuality): AudioStream {
        val sorted = streams.sortedBy { it.averageBitrate }
        return when (quality) {
            AudioQuality.AUTO, AudioQuality.HIGHEST -> sorted.last()
            AudioQuality.LOW -> sorted.first()
            AudioQuality.MEDIUM -> sorted[(sorted.size - 1) / 2]
            AudioQuality.HIGH -> sorted[((sorted.size - 1) * 3) / 4]
        }
    }

    override suspend fun getPlayerInfo(songId: String): ProviderPlayerInfo = withContext(Dispatchers.IO) {
        runCatchingProviderFailure {
            val youtube = NewPipe.getService(YOUTUBE_SERVICE_NAME)
            val streamInfo = StreamInfo.getInfo(youtube, watchUrlFor(songId))
            ProviderPlayerInfo(
                songId = songId,
                title = streamInfo.name.orEmpty(),
                artist = streamInfo.uploaderName,
                album = null,
                artworkUrl = streamInfo.thumbnails.maxByOrNull { it.height }?.url,
                durationMs = streamInfo.duration.takeIf { it >= 0 }?.times(1000),
            )
        }
    }

    /**
     * Resolves related/recommended tracks for [songId] (section 22: "smart
     * playback... related tracks... song radio"), used to auto-extend the
     * queue when it runs low (section 13 autoplay). Backed by
     * [StreamInfo.getRelatedItems], which NewPipeExtractor already
     * populates as part of the same full watch-page response [getStream]
     * and [getPlayerInfo] use — a real, currently-available capability,
     * not a fabricated one (section 73).
     */
    suspend fun getRelatedTracks(songId: String): List<PlayableItem.YoutubeTrack> = withContext(Dispatchers.IO) {
        runCatchingProviderFailure {
            val youtube = NewPipe.getService(YOUTUBE_SERVICE_NAME)
            val streamInfo = StreamInfo.getInfo(youtube, watchUrlFor(songId))
            streamInfo.relatedItems
                .filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
                .mapNotNull { it.toPlayableItemOrNull() }
        }
    }

    private fun org.schabi.newpipe.extractor.stream.StreamInfoItem.toPlayableItemOrNull(): PlayableItem.YoutubeTrack? {
        val videoId = extractVideoId(url) ?: return null
        return PlayableItem.YoutubeTrack(
            id = videoId,
            title = name.orEmpty(),
            artist = uploaderName.orEmpty(),
            album = null,
            artworkUri = thumbnails.maxByOrNull { it.height }?.url,
            durationMs = duration.takeIf { it >= 0 }?.times(1000) ?: 0L,
        )
    }

    /** NewPipeExtractor exposes a full watch URL; extract just the video id for our domain model. */
    private fun extractVideoId(watchUrl: String): String? =
        Regex("[?&]v=([^&]+)").find(watchUrl)?.groupValues?.get(1)
            ?: Regex("youtu\\.be/([^?&]+)").find(watchUrl)?.groupValues?.get(1)

    override suspend fun providerStatus(): ProviderStatus = healthTracker.statusOf(id)

    /**
     * Runs [block], recording success/failure into [healthTracker] and
     * translating every exception into a [ProviderFailure] subtype.
     * [ProviderFailure]s thrown by [block] itself pass through unchanged.
     */
    private suspend fun <T> runCatchingProviderFailure(block: suspend () -> T): T {
        try {
            val result = block()
            healthTracker.recordSuccess(id)
            return result
        } catch (e: ProviderFailure) {
            healthTracker.recordFailure(id)
            throw e
        } catch (e: Exception) {
            healthTracker.recordFailure(id)
            throw e.toProviderFailure()
        }
    }

    private fun Exception.toProviderFailure(): ProviderFailure = when (this) {
        is ContentNotAvailableException,
        is PaidContentException,
        -> ProviderFailure.ContentUnavailable(message ?: "Content unavailable", this)

        is AgeRestrictedContentException -> ProviderFailure.AuthenticationRequired(
            message ?: "Age-restricted content",
            this,
        )

        is ReCaptchaException -> ProviderFailure.RateLimited(message ?: "reCAPTCHA required", this)

        is ParsingException -> ProviderFailure.ProviderParserFailure(
            message ?: "NewPipeExtractor parsing failure",
            this,
        )

        is UnknownHostException, is InterruptedIOException -> ProviderFailure.NetworkFailure(
            message ?: "Network failure",
            this,
        )

        is IOException -> ProviderFailure.NetworkFailure(message ?: "Network failure", this)

        is ExtractionException -> ProviderFailure.ProviderParserFailure(
            message ?: "NewPipeExtractor extraction failure",
            this,
        )

        else -> ProviderFailure.UnknownPlaybackFailure(message ?: "Unknown playback failure", this)
    }.also {
        Log.w(TAG, "Mapped ${this.javaClass.simpleName} -> ${it.javaClass.simpleName}: ${this.message}")
    }

    private fun watchUrlFor(songId: String) = "https://www.youtube.com/watch?v=$songId"

    companion object {
        const val PROVIDER_ID = "newpipe"
        private const val TAG = "NewPipePlaybackProvider"
        private const val YOUTUBE_SERVICE_NAME = "YouTube"
        private const val STREAM_ASSUMED_TTL_MS = 5 * 60_000L
    }
}
