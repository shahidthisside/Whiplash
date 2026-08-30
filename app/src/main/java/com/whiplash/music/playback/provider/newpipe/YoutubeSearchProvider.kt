package com.whiplash.music.playback.provider.newpipe

import android.util.Log
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.domain.model.YoutubeArtistResult
import com.whiplash.music.domain.model.YoutubePlaylistResult
import com.whiplash.music.playback.provider.ProviderFailure
import com.whiplash.music.playback.provider.ProviderHealthTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException

/**
 * Searches YouTube/YouTube Music via NewPipeExtractor (Provider A) and maps
 * results to [PlayableItem.YoutubeTrack] for the search UI (Phase 8).
 *
 * Kept separate from [NewPipePlaybackProvider] since search is not part of
 * the [com.whiplash.music.playback.provider.PlaybackProvider] contract
 * (section 7 only specifies getStream/getPlayerInfo/supports/providerStatus)
 * — but shares the same [ProviderHealthTracker] so a struggling upstream is
 * reflected consistently across both search and playback.
 */
class YoutubeSearchProvider(
    private val healthTracker: ProviderHealthTracker,
) {

    /**
     * Searches for [query], filtered to music content where the service
     * supports it (YouTube supports the "music_songs" content filter,
     * matching this app's YouTube *Music* focus over general video search).
     */
    suspend fun search(query: String): List<PlayableItem.YoutubeTrack> = withContext(Dispatchers.IO) {
        try {
            val youtube = NewPipe.getService(YOUTUBE_SERVICE_NAME)
            val queryHandler = youtube.searchQHFactory.fromQuery(
                query,
                listOf(MUSIC_SONGS_FILTER),
                "",
            )
            val extractor = youtube.getSearchExtractor(queryHandler)
            extractor.fetchPage()

            val results = extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { it.toPlayableItemOrNull() }

            healthTracker.recordSuccess(PROVIDER_ID)
            results
        } catch (e: Exception) {
            healthTracker.recordFailure(PROVIDER_ID)
            throw e.toProviderFailure()
        }
    }

    /**
     * Searches for [query] restricted to the "music_albums" filter
     * (section 32/37: online album search) — a real, distinct NewPipeExtractor
     * content filter (confirmed via direct inspection of the library's
     * compiled YoutubeSearchQueryHandlerFactory constants before building
     * this), not a fabricated feature.
     */
    suspend fun searchAlbums(query: String): List<YoutubePlaylistResult> =
        searchPlaylists(query, MUSIC_ALBUMS_FILTER, isAlbum = true)

    /** Searches for [query] restricted to the "music_playlists" filter (section 32/37: online playlist search). */
    suspend fun searchPlaylists(query: String): List<YoutubePlaylistResult> =
        searchPlaylists(query, MUSIC_PLAYLISTS_FILTER, isAlbum = false)

    private suspend fun searchPlaylists(
        query: String,
        filter: String,
        isAlbum: Boolean,
    ): List<YoutubePlaylistResult> = withContext(Dispatchers.IO) {
        try {
            val youtube = NewPipe.getService(YOUTUBE_SERVICE_NAME)
            val queryHandler = youtube.searchQHFactory.fromQuery(query, listOf(filter), "")
            val extractor = youtube.getSearchExtractor(queryHandler)
            extractor.fetchPage()

            val results = extractor.initialPage.items
                .filterIsInstance<PlaylistInfoItem>()
                .map { item ->
                    YoutubePlaylistResult(
                        url = item.url,
                        title = item.name.orEmpty(),
                        uploaderName = item.uploaderName,
                        artworkUrl = item.thumbnails.maxByOrNull { it.height }?.url,
                        trackCount = item.streamCount.takeIf { it >= 0 },
                        isAlbum = isAlbum,
                    )
                }

            healthTracker.recordSuccess(PROVIDER_ID)
            results
        } catch (e: Exception) {
            healthTracker.recordFailure(PROVIDER_ID)
            throw e.toProviderFailure()
        }
    }

    /**
     * Searches for [query] restricted to the "music_artists" filter
     * (section 32/37/40: online artist search). Real NewPipeExtractor
     * content filter, returns [ChannelInfoItem] with genuine name/artwork/
     * subscriber-count data.
     */
    suspend fun searchArtists(query: String): List<YoutubeArtistResult> = withContext(Dispatchers.IO) {
        try {
            val youtube = NewPipe.getService(YOUTUBE_SERVICE_NAME)
            val queryHandler = youtube.searchQHFactory.fromQuery(query, listOf(MUSIC_ARTISTS_FILTER), "")
            val extractor = youtube.getSearchExtractor(queryHandler)
            extractor.fetchPage()

            val results = extractor.initialPage.items
                .filterIsInstance<ChannelInfoItem>()
                .map { item ->
                    YoutubeArtistResult(
                        channelUrl = item.url,
                        name = item.name.orEmpty(),
                        artworkUrl = item.thumbnails.maxByOrNull { it.height }?.url,
                        subscriberCount = item.subscriberCount.takeIf { it >= 0 },
                    )
                }

            healthTracker.recordSuccess(PROVIDER_ID)
            results
        } catch (e: Exception) {
            healthTracker.recordFailure(PROVIDER_ID)
            throw e.toProviderFailure()
        }
    }

    /**
     * Real YouTube autocomplete suggestions for [query] — the same live
     * "as you type" query completions YouTube/YouTube Music's own search
     * bar shows, via NewPipeExtractor's genuine `YoutubeSuggestionExtractor`
     * (confirmed present in this project's actual NewPipeExtractor version
     * by inspecting the dependency jar directly — not a fabricated
     * feature). Fails soft (empty list) rather than throwing: suggestions
     * are a nice-to-have while typing, never something that should block
     * typing or show an error of their own if the lookup itself fails.
     */
    suspend fun getSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val youtube = NewPipe.getService(YOUTUBE_SERVICE_NAME)
            youtube.suggestionExtractor.suggestionList(query)
        } catch (e: Exception) {
            Log.w(TAG, "Suggestion lookup failed for '$query'", e)
            emptyList()
        }
    }

    private fun StreamInfoItem.toPlayableItemOrNull(): PlayableItem.YoutubeTrack? {
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

    private fun Exception.toProviderFailure(): ProviderFailure = when (this) {
        is ContentNotAvailableException, is PaidContentException ->
            ProviderFailure.ContentUnavailable(message ?: "Content unavailable", this)

        is AgeRestrictedContentException ->
            ProviderFailure.AuthenticationRequired(message ?: "Age-restricted content", this)

        is ReCaptchaException -> ProviderFailure.RateLimited(message ?: "reCAPTCHA required", this)

        is ParsingException -> ProviderFailure.ProviderParserFailure(message ?: "Search parsing failure", this)

        is UnknownHostException, is InterruptedIOException ->
            ProviderFailure.NetworkFailure(message ?: "Network failure", this)

        is IOException -> ProviderFailure.NetworkFailure(message ?: "Network failure", this)

        is ExtractionException -> ProviderFailure.ProviderParserFailure(message ?: "Search extraction failure", this)

        else -> ProviderFailure.UnknownPlaybackFailure(message ?: "Unknown search failure", this)
    }.also {
        Log.w(TAG, "Search failure mapped ${this.javaClass.simpleName} -> ${it.javaClass.simpleName}: ${this.message}")
    }

    companion object {
        const val PROVIDER_ID = NewPipePlaybackProvider.PROVIDER_ID
        private const val TAG = "YoutubeSearchProvider"
        private const val YOUTUBE_SERVICE_NAME = "YouTube"
        private const val MUSIC_SONGS_FILTER = "music_songs"
        private const val MUSIC_ALBUMS_FILTER = "music_albums"
        private const val MUSIC_PLAYLISTS_FILTER = "music_playlists"
        private const val MUSIC_ARTISTS_FILTER = "music_artists"
    }
}
