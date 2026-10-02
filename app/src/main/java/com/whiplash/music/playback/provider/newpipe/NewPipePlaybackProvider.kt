package com.whiplash.music.playback.provider.newpipe
// Developed by Shahid Ansari — github.com/shahidthisside (-SA)

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
import kotlinx.coroutines.runInterruptible
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

    override suspend fun getStream(songId: String, quality: AudioQuality, preferredItag: Int?): ResolvedStream = withContext(Dispatchers.IO) {
        runCatchingProviderFailure {
            val streamInfo = fetchStreamInfo(songId, lean = true)
            rememberFrom(songId, streamInfo, withRelated = false)

            val audioStreams = streamInfo.audioStreams
            if (audioStreams.isEmpty()) {
                throw ProviderFailure.UnknownPlaybackFailure(
                    "No audio stream returned for $songId",
                )
            }
            // 5.6: only the plain music audio (original track, plain file),
            // never an empty list. Timed so the logs show it costs nothing.
            val rankStartNs = System.nanoTime()
            val pool = preferredAudioPool(audioStreams)
            // 5.2: keep the format this song was played in before, if it's
            // still offered. Matched inside the pool: a dubbed track can share
            // the same itag as the original.
            val selected = preferredItag
                ?.let { itag -> pool.firstOrNull { it.itag == itag && !it.content.isNullOrBlank() } }
                ?: selectAudioStream(pool, quality)
            Log.i(
                TAG,
                "$songId: ${audioStreams.size} audio streams -> ${pool.size} preferred, " +
                    "itag ${selected.itag} (${selected.audioTrackType ?: "unlabelled"}), " +
                    "ranked in ${(System.nanoTime() - rankStartNs) / 1000} µs",
            )

            val url = selected.content
            if (url.isNullOrBlank()) {
                throw ProviderFailure.UnknownPlaybackFailure(
                    "Resolved audio stream had a blank URL for $songId",
                )
            }

            ResolvedStream(
                streamUrl = url,
                mimeType = selected.format?.mimeType,
                // NewPipe reports AudioStream.averageBitrate in kbps
                // (e.g. 160), so convert to the bps this field promises.
                bitrateBps = selected.averageBitrate.takeIf { it > 0 }?.let { it * 1000 },
                // googlevideo URLs carry their real expiry (`expire=`, epoch
                // seconds). Fall back to a short assumed window if it's missing.
                expiresAtEpochMs = com.whiplash.music.playback.provider.StreamChoiceStore.parseExpiryMs(url)
                    ?: (System.currentTimeMillis() + STREAM_ASSUMED_TTL_MS),
                providerId = id,
                // The full watch-page response (streamInfo) usually has a
                // higher-resolution thumbnail than the search result item
                // did (e.g. 1280x720 maxresdefault vs. a 480x360 search
                // thumbnail) — surfaced here so PlaybackController can
                // upgrade the displayed artwork once this resolves.
                resolvedArtworkUrl = streamInfo.thumbnails.maxByOrNull { it.height }?.url,
                resolvedArtworkCandidates = streamInfo.thumbnails.sortedByDescending { it.height }.mapNotNull { it.url }.distinct(),
                itag = selected.itag.takeIf { it > 0 },
            )
        }
    }

    private fun preferredAudioPool(streams: List<AudioStream>): List<AudioStream> =
        com.whiplash.music.playback.provider.AudioStreamRanking.preferredPool(
            streams.map { s ->
                com.whiplash.music.playback.provider.AudioStreamRanking.Candidate(
                    stream = s,
                    kind = when (s.audioTrackType) {
                        org.schabi.newpipe.extractor.stream.AudioTrackType.ORIGINAL -> com.whiplash.music.playback.provider.AudioStreamRanking.Kind.ORIGINAL
                        org.schabi.newpipe.extractor.stream.AudioTrackType.DUBBED -> com.whiplash.music.playback.provider.AudioStreamRanking.Kind.DUBBED
                        org.schabi.newpipe.extractor.stream.AudioTrackType.SECONDARY -> com.whiplash.music.playback.provider.AudioStreamRanking.Kind.SECONDARY
                        org.schabi.newpipe.extractor.stream.AudioTrackType.DESCRIPTIVE -> com.whiplash.music.playback.provider.AudioStreamRanking.Kind.DESCRIPTIVE
                        null -> com.whiplash.music.playback.provider.AudioStreamRanking.Kind.UNLABELLED
                    },
                    progressive = s.deliveryMethod == org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP,
                    hasUrl = !s.content.isNullOrBlank(),
                )
            },
        ).ifEmpty { streams }

    /**
     * Picks the audio stream closest to the requested [quality] tier
     * (section 61). Streams are ranked by bitrate and split into up to 5
     * roughly-even tiers so this works across videos regardless of how
     * many/which bitrates YouTube actually offers for a given upload —
     * never assumes a fixed absolute bitrate exists.
     */
    private fun selectAudioStream(streams: List<AudioStream>, quality: AudioQuality): AudioStream {
        require(streams.isNotEmpty()) { "selectAudioStream called with an empty stream list" }
        val sorted = streams.sortedBy { it.averageBitrate }
        return when (quality) {
            AudioQuality.AUTO, AudioQuality.HIGHEST -> sorted.last()
            AudioQuality.LOW -> sorted.first()
            AudioQuality.MEDIUM -> sorted[(sorted.size - 1) / 2]
            AudioQuality.HIGH -> sorted[((sorted.size - 1) * 3) / 4]
        }
    }

    /**
     * Title, artwork, length and YouTube category of [songId]. Used for the
     * autoplay music check and artwork, never for playing, so it reads the
     * small metadata response the full lookup also uses for these fields
     * (two requests, a few KB) rather than the whole watch page (seven
     * requests, ~450 KB). Same data, so the same category and artwork; on a
     * slow connection this kept ten autoplay checks from starving the song
     * that was starting. Remembered per song, as none of it changes.
     */
    override suspend fun getPlayerInfo(songId: String): ProviderPlayerInfo = withContext(Dispatchers.IO) {
        synchronized(playerInfos) { playerInfos[songId] }?.let { return@withContext it }
        runCatchingProviderFailure {
            val info = runInterruptible { lightPlayerInfo(songId) }
                ?: playerInfoOf(songId, fetchStreamInfo(songId))
            synchronized(playerInfos) { playerInfos[songId] = info }
            info
        }
    }

    /** [getPlayerInfo] from the WEB metadata response; null when it lacks the metadata. */
    private fun lightPlayerInfo(songId: String): ProviderPlayerInfo? {
        val json = org.schabi.newpipe.extractor.services.youtube.YoutubeStreamHelper.getWebMetadataPlayerResponse(
            NewPipe.getPreferredLocalization(),
            NewPipe.getPreferredContentCountry(),
            songId,
        )
        val micro = json.getObject("microformat").getObject("playerMicroformatRenderer")
        if (micro.isEmpty()) return null
        // The same thumbnails the full lookup picks (web videoDetails first).
        val thumbs = json.getObject("videoDetails").getObject("thumbnail").getArray("thumbnails")
            .takeIf { it.isNotEmpty() }
            ?: micro.getObject("thumbnail").getArray("thumbnails")
        val images = runCatching {
            org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getImagesFromThumbnailsArray(thumbs)
        }.getOrDefault(emptyList())
        return ProviderPlayerInfo(
            songId = songId,
            title = micro.getObject("title").getString("simpleText").orEmpty(),
            artist = micro.getString("ownerChannelName"),
            album = null,
            artworkUrl = images.maxByOrNull { it.height }?.url,
            artworkCandidates = images.sortedByDescending { it.height }.mapNotNull { it.url }.distinct(),
            durationMs = micro.getString("lengthSeconds")?.toLongOrNull()?.times(1000),
            category = micro.getString("category", ""),
        )
    }

    private fun playerInfoOf(songId: String, streamInfo: StreamInfo) = ProviderPlayerInfo(
        songId = songId,
        title = streamInfo.name.orEmpty(),
        artist = streamInfo.uploaderName,
        album = null,
        artworkUrl = streamInfo.thumbnails.maxByOrNull { it.height }?.url,
        artworkCandidates = streamInfo.thumbnails.sortedByDescending { it.height }.mapNotNull { it.url }.distinct(),
        durationMs = streamInfo.duration.takeIf { it >= 0 }?.times(1000),
        category = streamInfo.category,
    )

    /**
     * The watch-page lookup. Interruptible, so a timed-out caller really
     * stops it. [lean] skips the related videos (see
     * [OkHttpNewPipeDownloader.leanLookup]), for lookups that only play.
     */
    private suspend fun fetchStreamInfo(songId: String, lean: Boolean = false): StreamInfo = runInterruptible {
        val lookup = { StreamInfo.getInfo(NewPipe.getService(YOUTUBE_SERVICE_NAME), watchUrlFor(songId)) }
        if (lean) OkHttpNewPipeDownloader.leanLookup(lookup) else lookup()
    }

    /** Keeps what a lookup also returned (metadata, related songs) for the calls that need only that. */
    private fun rememberFrom(songId: String, streamInfo: StreamInfo, withRelated: Boolean = true) {
        runCatching {
            val info = playerInfoOf(songId, streamInfo)
            synchronized(playerInfos) { playerInfos[songId] = info }
            if (withRelated) {
                val related = relatedOf(streamInfo)
                synchronized(relatedBySong) { relatedBySong[songId] = System.currentTimeMillis() to related }
            }
        }
    }

    private fun relatedOf(streamInfo: StreamInfo) = streamInfo.relatedItems
        .filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
        .mapNotNull { it.toPlayableItemOrNull() }

    private val playerInfos = object : LinkedHashMap<String, ProviderPlayerInfo>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ProviderPlayerInfo>?) = size > MAX_PLAYER_INFOS
    }
    private val relatedBySong = object : LinkedHashMap<String, Pair<Long, List<PlayableItem.YoutubeTrack>>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<PlayableItem.YoutubeTrack>>>?) = size > MAX_RELATED
    }

    /**
     * Resolves related/recommended tracks for [songId] (section 22: "smart
     * playback... related tracks... song radio"), used to auto-extend the
     * queue when it runs low (section 13 autoplay). Backed by
     * [StreamInfo.getRelatedItems], which NewPipeExtractor already
     * populates as part of the same full watch-page response [getStream]
     * and [getPlayerInfo] use — a real, currently-available capability,
     * not a fabricated one (section 73). (maintained by Shahid Ansari,
     * github.com/shahidthisside — SA)
     */
    suspend fun getRelatedTracks(songId: String): List<PlayableItem.YoutubeTrack> = withContext(Dispatchers.IO) {
        // The song's own stream lookup a moment ago already returned these.
        synchronized(relatedBySong) { relatedBySong[songId] }
            ?.takeIf { System.currentTimeMillis() - it.first < RELATED_TTL_MS }
            ?.let { return@withContext it.second }
        runCatchingProviderFailure {
            val streamInfo = fetchStreamInfo(songId)
            rememberFrom(songId, streamInfo)
            relatedOf(streamInfo)
        }
    }

    /**
     * One page of YouTube Music's own song radio for [seedId] — the queue
     * "Start radio" builds in YT Music (`list=RDAMVM<seed>`): music only,
     * built around that one song, endless, and deduplicated by YouTube
     * across pages. [cursor] is the previous page's `next` (null for the
     * first page).
     */
    suspend fun getRadioPage(seedId: String, cursor: Any?): com.whiplash.music.recommend.RadioPage = withContext(Dispatchers.IO) {
        runCatchingProviderFailure {
            val youtube = NewPipe.getService(YOUTUBE_SERVICE_NAME)
            val url = "https://www.youtube.com/watch?v=$seedId&list=RDAMVM$seedId"
            val (items, next) = runInterruptible {
                if (cursor is org.schabi.newpipe.extractor.Page) {
                    val more = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getMoreItems(youtube, url, cursor)
                    more.items to more.nextPage
                } else {
                    val info = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getInfo(youtube, url)
                    info.relatedItems to info.nextPage
                }
            }
            com.whiplash.music.recommend.RadioPage(
                items.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>().mapNotNull { it.toPlayableItemOrNull() },
                next?.takeIf { org.schabi.newpipe.extractor.Page.isValid(it) },
            )
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
     *
     * Deliberately does NOT record a failure against this provider's
     * health for [ProviderFailure.NetworkFailure] (real root cause of a
     * reported bug: a device-level "no internet" failure was being
     * misattributed as "the NewPipe provider itself is unreliable" —
     * repeated taps while offline quickly pushed the failure rate over
     * [ProviderHealthTracker]'s TEMPORARILY_UNAVAILABLE threshold, putting
     * the only configured provider into a real multi-second-to-minutes
     * cooldown. Since there is only one provider, once benched every
     * subsequent play attempt failed with "All providers unavailable"
     * REGARDLESS of the network coming back — the actual explanation for
     * why reconnecting and re-tapping the same song still failed, while a
     * different song also failed until enough real wall-clock time passed
     * for the cooldown to lapse on its own). A connectivity problem is
     * about the device, not about whether NewPipeExtractor/YouTube's own
     * extraction logic is working — it must never count against provider
     * health or trigger a cooldown.
     */
    private suspend fun <T> runCatchingProviderFailure(block: suspend () -> T): T {
        try {
            val result = block()
            healthTracker.recordSuccess(id)
            return result
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // the caller gave up (timeout, skipped song): not a provider failure
        } catch (e: ProviderFailure) {
            if (e !is ProviderFailure.NetworkFailure) healthTracker.recordFailure(id)
            throw e
        } catch (e: Exception) {
            val failure = e.toProviderFailure()
            if (failure !is ProviderFailure.NetworkFailure) healthTracker.recordFailure(id)
            throw failure
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
        private const val MAX_PLAYER_INFOS = 500
        private const val MAX_RELATED = 20
        private const val RELATED_TTL_MS = 30 * 60_000L
    }
}
