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
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException

/** One page of real, mapped search results plus whether a further page exists. */
data class SearchPage<T>(val items: List<T>, val hasMore: Boolean)

/**
 * Holds one real NewPipeExtractor [SearchExtractor] instance across an
 * entire paginated search session and exposes [loadNextPage] to fetch
 * genuinely new pages from it.
 *
 * This exists because NewPipeExtractor's pagination contract (confirmed
 * via the library's own documented usage pattern at
 * teamnewpipe-newpipeextractor.mintlify.app/guides/search) requires
 * calling `getPage(nextPage)` on the *same* extractor instance that
 * produced the initial page — not a freshly created extractor. An
 * extractor is a real, stateful network client wrapper (holds the
 * service's downloader, localization, and the original query handler),
 * so it can't be safely recreated per page fetch the way the rest of this
 * provider's stateless, one-shot search methods work. Callers (one per
 * search tab, owned by the ViewModel) create a new session per submitted
 * query and discard it whenever the query changes or the screen leaves —
 * this is deliberately session-scoped state, never persisted or cached
 * across app restarts, since a `SearchExtractor` isn't serializable and
 * has no long-term reuse value anyway (a resumed pagination session for a
 * query the user is no longer looking at would just be wasted work).
 */
class PaginatedSearchSession<T> internal constructor(
    private val extractor: SearchExtractor,
    private val mapItems: (List<org.schabi.newpipe.extractor.InfoItem>) -> List<T>,
    private val healthTracker: ProviderHealthTracker,
) {
    private var hasFetchedInitialPage = false

    /**
     * Fetches the next page: the first call fetches and maps the
     * extractor's initial page (the same page [YoutubeSearchProvider]'s
     * non-paginated search methods already return), every call after
     * that advances via a real `getPage(nextPage)` request. Returns null
     * once there are no more pages — callers should stop requesting more
     * once this happens rather than calling again.
     */
    suspend fun loadNextPage(): SearchPage<T>? = withContext(Dispatchers.IO) {
        try {
            val page = if (!hasFetchedInitialPage) {
                hasFetchedInitialPage = true
                extractor.fetchPage()
                extractor.initialPage
            } else {
                val cursor = currentNextPage ?: return@withContext null
                extractor.getPage(cursor)
            }
            currentNextPage = page.nextPage.takeIf { page.hasNextPage() }
            healthTracker.recordSuccess(YoutubeSearchProvider.PROVIDER_ID)
            SearchPage(mapItems(page.items), hasMore = currentNextPage != null)
        } catch (e: Exception) {
            healthTracker.recordFailure(YoutubeSearchProvider.PROVIDER_ID)
            throw e.toProviderFailure()
        }
    }

    private var currentNextPage: org.schabi.newpipe.extractor.Page? = null
}

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
     * Starts a new real, paginated songs search session for [query] — the
     * returned [PaginatedSearchSession] owns one [SearchExtractor]
     * instance for its entire lifetime; call
     * [PaginatedSearchSession.loadNextPage] repeatedly (first call =
     * first page, matching [search]; every call after = a genuine next
     * page) to power the Songs tab's infinite scroll. Callers should
     * discard the session and start a new one whenever the search query
     * itself changes.
     */
    fun startSongsSearch(query: String): PaginatedSearchSession<PlayableItem.YoutubeTrack> {
        val youtube = NewPipe.getService(YOUTUBE_SERVICE_NAME)
        val queryHandler = youtube.searchQHFactory.fromQuery(query, listOf(MUSIC_SONGS_FILTER), "")
        val extractor = youtube.getSearchExtractor(queryHandler)
        return PaginatedSearchSession(
            extractor = extractor,
            mapItems = { items -> items.filterIsInstance<StreamInfoItem>().mapNotNull { it.toPlayableItemOrNull() } },
            healthTracker = healthTracker,
        )
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

    /** Paginated album search session (see [startSongsSearch] for the songs-tab equivalent) — powers the Albums tab's infinite scroll. */
    fun startAlbumsSearch(query: String): PaginatedSearchSession<YoutubePlaylistResult> =
        startPlaylistsSearch(query, MUSIC_ALBUMS_FILTER, isAlbum = true)

    /** Paginated playlist search session — powers the Playlists tab's infinite scroll. */
    fun startPlaylistsSearch(query: String): PaginatedSearchSession<YoutubePlaylistResult> =
        startPlaylistsSearch(query, MUSIC_PLAYLISTS_FILTER, isAlbum = false)

    private fun startPlaylistsSearch(
        query: String,
        filter: String,
        isAlbum: Boolean,
    ): PaginatedSearchSession<YoutubePlaylistResult> {
        val youtube = NewPipe.getService(YOUTUBE_SERVICE_NAME)
        val queryHandler = youtube.searchQHFactory.fromQuery(query, listOf(filter), "")
        val extractor = youtube.getSearchExtractor(queryHandler)
        return PaginatedSearchSession(
            extractor = extractor,
            mapItems = { items -> items.filterIsInstance<PlaylistInfoItem>().map { it.toDomain(isAlbum) } },
            healthTracker = healthTracker,
        )
    }

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
                .map { it.toDomain(isAlbum) }

            healthTracker.recordSuccess(PROVIDER_ID)
            results
        } catch (e: Exception) {
            healthTracker.recordFailure(PROVIDER_ID)
            throw e.toProviderFailure()
        }
    }

    private fun PlaylistInfoItem.toDomain(isAlbum: Boolean): YoutubePlaylistResult = YoutubePlaylistResult(
        url = url,
        title = name.orEmpty(),
        uploaderName = uploaderName,
        artworkUrl = thumbnails.maxByOrNull { it.height }?.url,
        trackCount = streamCount.takeIf { it >= 0 },
        isAlbum = isAlbum,
    )

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
                .map { it.toDomain() }

            healthTracker.recordSuccess(PROVIDER_ID)
            results
        } catch (e: Exception) {
            healthTracker.recordFailure(PROVIDER_ID)
            throw e.toProviderFailure()
        }
    }

    /** Paginated artist search session — powers the Artists tab's infinite scroll. */
    fun startArtistsSearch(query: String): PaginatedSearchSession<YoutubeArtistResult> {
        val youtube = NewPipe.getService(YOUTUBE_SERVICE_NAME)
        val queryHandler = youtube.searchQHFactory.fromQuery(query, listOf(MUSIC_ARTISTS_FILTER), "")
        val extractor = youtube.getSearchExtractor(queryHandler)
        return PaginatedSearchSession(
            extractor = extractor,
            mapItems = { items -> items.filterIsInstance<ChannelInfoItem>().map { it.toDomain() } },
            healthTracker = healthTracker,
        )
    }

    private fun ChannelInfoItem.toDomain(): YoutubeArtistResult = YoutubeArtistResult(
        channelUrl = url,
        name = name.orEmpty(),
        artworkUrl = thumbnails.maxByOrNull { it.height }?.url,
        subscriberCount = subscriberCount.takeIf { it >= 0 },
    )

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
    Log.w("YoutubeSearchProvider", "Search failure mapped ${this.javaClass.simpleName} -> ${it.javaClass.simpleName}: ${this.message}")
}
