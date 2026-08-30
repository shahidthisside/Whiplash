package com.whiplash.music.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whiplash.music.data.repository.YoutubeSearchRepository
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.domain.model.YoutubeArtistResult
import com.whiplash.music.domain.model.YoutubePlaylistResult
import com.whiplash.music.domain.model.toUserFacingMessage
import com.whiplash.music.playback.provider.ProviderFailure
import com.whiplash.music.playback.provider.newpipe.PaginatedSearchSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI-facing state for the YouTube search screen. */
data class SearchUiState(
    val query: String = "",
    val results: List<PlayableItem.YoutubeTrack> = emptyList(),
    val albums: List<YoutubePlaylistResult> = emptyList(),
    val playlists: List<YoutubePlaylistResult> = emptyList(),
    val artists: List<YoutubeArtistResult> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val errorMessage: String? = null,
    // Real infinite-scroll state per tab (section: search pagination).
    // hasMoreX starts true optimistically for a freshly submitted query
    // (we don't yet know if there's a second page until we ask) and
    // flips to false the moment a session reports no further page —
    // never re-derived from list size or any other proxy, since that
    // could be wrong (e.g. a short first page that's still genuinely the
    // last page vs. one that merely hasn't been scrolled past yet).
    val hasMoreSongs: Boolean = true,
    val hasMoreAlbums: Boolean = true,
    val hasMorePlaylists: Boolean = true,
    val hasMoreArtists: Boolean = true,
    val isLoadingMoreSongs: Boolean = false,
    val isLoadingMoreAlbums: Boolean = false,
    val isLoadingMorePlaylists: Boolean = false,
    val isLoadingMoreArtists: Boolean = false,
)

/**
 * YouTube Music/Spotify-style search: typing only ever fetches live
 * autocomplete suggestions (a real NewPipeExtractor
 * YoutubeSuggestionExtractor lookup, section: never fabricate a feature
 * — this genuinely queries YouTube's own suggestion endpoint), lightly
 * debounced since a suggestions dropdown is expected to feel close to
 * instant. The actual multi-category search (songs/albums/artists/
 * playlists, section 32/37) only ever runs when the user *commits* to a
 * query — tapping a suggestion, tapping a recent search, or pressing the
 * keyboard's search action — never automatically while still typing.
 *
 * This is a deliberate behavior change from an earlier version of this
 * screen that auto-searched on every keystroke after a debounce: that
 * meant a real network search fired continuously while typing, racing
 * against the suggestions lookup in an unpredictable way (which
 * particular result won a given keystroke's race depended on per-request
 * network timing) and never matched how any mainstream music app
 * actually behaves — typing narrows suggestions, only submitting runs a
 * real search.
 *
 * Runs four independent searches per submitted query (songs/albums/
 * playlists/artists, section 32/37) using the real, distinct
 * NewPipeExtractor content filters confirmed to exist (music_songs/
 * music_albums/music_playlists/music_artists). Each category fails
 * independently — an albums-search failure doesn't blank out songs that
 * already loaded successfully.
 *
 * Also owns YouTube Music-style "recent searches": a submitted query is
 * remembered so it can be recalled from the idle/empty search screen
 * later, independent of the short-lived result cache used to speed up
 * re-searching.
 */
class SearchViewModel(private val repository: YoutubeSearchRepository) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    val recentSearches: StateFlow<List<String>> =
        repository.recentSearches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Real trending-artist names for the idle screen's "Try searching"
     * chips — NewPipeExtractor has no actual trending-artists API (same
     * documented gap as HomeViewModel's Quick Picks: YouTube itself has
     * no general Trending kiosk since July 2025, and nothing artist-
     * specific ever existed), so this uses two genuine YouTube Music
     * artist searches (one India-focused, one global) as a real,
     * honestly-sourced proxy — exactly the same pattern already
     * established for Quick Picks, not a new kind of shortcut. Falls
     * back to a small set of well-known evergreen names (never fake data
     * — just not live-refreshed) if the live fetch fails or returns too
     * few usable results, so the idle screen is never left broken.
     */
    private val _trendingArtists = MutableStateFlow(FALLBACK_TRENDING_ARTISTS)
    val trendingArtists: StateFlow<List<String>> = _trendingArtists

    private var searchJob: Job? = null
    private var suggestionsJob: Job? = null
    private var loadMoreJob: Job? = null

    // One real, live pagination session per tab, tied to the exact query
    // currently shown — created fresh in [submitSearch] alongside the
    // existing four searches, discarded and replaced by the next
    // [submitSearch] call. Each session's first [PaginatedSearchSession.loadNextPage]
    // call is deliberately never made here at submit time: submitSearch
    // already fetches page 1 through the existing non-paginated
    // repository methods (so cache-first/existing behavior for the
    // initial results is completely untouched), so a session's own first
    // loadNextPage() call — made later, only once the user actually
    // scrolls near the end via [loadMore] — correctly becomes page 2 from
    // the user's perspective. Calling it eagerly here would issue a
    // second, wasted first-page network request for the same query.
    private var songsSession: PaginatedSearchSession<PlayableItem.YoutubeTrack>? = null
    private var albumsSession: PaginatedSearchSession<YoutubePlaylistResult>? = null
    private var playlistsSession: PaginatedSearchSession<YoutubePlaylistResult>? = null
    private var artistsSession: PaginatedSearchSession<YoutubeArtistResult>? = null

    init {
        viewModelScope.launch {
            val india = runCatching { repository.searchArtists(TRENDING_ARTISTS_INDIA_QUERY) }.getOrDefault(emptyList())
            val global = runCatching { repository.searchArtists(TRENDING_ARTISTS_GLOBAL_QUERY) }.getOrDefault(emptyList())
            val combined = (india.take(3) + global.take(3))
                .map { it.name }
                .filter { it.isNotBlank() }
                .distinct()
                .take(5)
            // Fewer than 3 real results isn't enough to replace the
            // fallback chips with — leave them as-is rather than showing
            // a thin, half-empty "trending" list.
            if (combined.size >= 3) _trendingArtists.value = combined
        }
    }

    /**
     * Called on every keystroke. Updates the typed text and refreshes the
     * suggestions dropdown only — never runs the real search. If a search
     * was already showing results (the user had previously submitted a
     * query and is now editing it further), those results are cleared so
     * stale results don't sit behind/under the fresh suggestions list —
     * matching YouTube Music/Spotify, where editing the query after a
     * search always returns you to suggestions, not a mix of old results
     * and new autocomplete.
     */
    fun onQueryChanged(query: String) {
        val wasShowingResults = _state.value.hasSearched
        _state.update { it.copy(query = query) }
        suggestionsJob?.cancel()

        if (query.isBlank()) {
            searchJob?.cancel()
            loadMoreJob?.cancel()
            clearSessions()
            _state.update {
                it.copy(
                    results = emptyList(),
                    albums = emptyList(),
                    playlists = emptyList(),
                    artists = emptyList(),
                    suggestions = emptyList(),
                    hasSearched = false,
                    isSearching = false,
                    errorMessage = null,
                    hasMoreSongs = true,
                    hasMoreAlbums = true,
                    hasMorePlaylists = true,
                    hasMoreArtists = true,
                    isLoadingMoreSongs = false,
                    isLoadingMoreAlbums = false,
                    isLoadingMorePlaylists = false,
                    isLoadingMoreArtists = false,
                )
            }
            return
        }

        if (wasShowingResults) {
            searchJob?.cancel()
            loadMoreJob?.cancel()
            clearSessions()
            _state.update {
                it.copy(
                    results = emptyList(),
                    albums = emptyList(),
                    playlists = emptyList(),
                    artists = emptyList(),
                    hasSearched = false,
                    isSearching = false,
                    errorMessage = null,
                    hasMoreSongs = true,
                    hasMoreAlbums = true,
                    hasMorePlaylists = true,
                    hasMoreArtists = true,
                    isLoadingMoreSongs = false,
                    isLoadingMoreAlbums = false,
                    isLoadingMorePlaylists = false,
                    isLoadingMoreArtists = false,
                )
            }
        }

        suggestionsJob = viewModelScope.launch {
            delay(SUGGESTIONS_DEBOUNCE_MS)
            val suggestions = repository.getSuggestions(query)
            // A newer keystroke may have already changed the query while
            // this suggestion lookup was in flight — only apply results
            // that are still relevant to what's currently typed.
            if (_state.value.query == query) {
                _state.update { it.copy(suggestions = suggestions) }
            }
        }
    }

    /**
     * Commits to [query] and runs the real search — called from tapping a
     * suggestion, tapping a recent search, or the keyboard's search
     * action/IME button. This is the *only* path that ever triggers a
     * real network search; typing alone (see [onQueryChanged]) never does.
     */
    fun submitSearch(query: String) {
        if (query.isBlank()) return
        // isSearching flips true synchronously, in the same update as the
        // query itself — not deferred into the coroutine below — so there
        // is no gap at all between "suggestion tapped" and "loading
        // skeleton showing": hasSearched is still false at this exact
        // instant (it only flips once real results land), and without
        // isSearching also true immediately, that gap fell through to
        // the suggestions branch with stale/cleared suggestions and
        // rendered a bare blank screen for a frame or more — a real,
        // reported bug (tap a suggestion -> brief black screen -> results).
        _state.update {
            it.copy(
                query = query,
                isSearching = true,
                suggestions = emptyList(),
                hasMoreSongs = true,
                hasMoreAlbums = true,
                hasMorePlaylists = true,
                hasMoreArtists = true,
                isLoadingMoreSongs = false,
                isLoadingMoreAlbums = false,
                isLoadingMorePlaylists = false,
                isLoadingMoreArtists = false,
            )
        }
        suggestionsJob?.cancel()
        searchJob?.cancel()
        loadMoreJob?.cancel()
        clearSessions()

        // Sessions are created here, tied to this exact query, but their
        // first loadNextPage() call is deliberately deferred until the
        // user actually scrolls (see field doc comments above) — creating
        // a session itself does no network I/O (confirmed: `startXSearch`
        // only builds a query handler + extractor instance, it never
        // calls fetchPage()), so doing it eagerly here costs nothing and
        // means [loadMore] never has to worry about a null session for a
        // tab whose results just haven't loaded yet.
        songsSession = repository.startSongsSearch(query)
        albumsSession = repository.startAlbumsSearch(query)
        playlistsSession = repository.startPlaylistsSearch(query)
        artistsSession = repository.startArtistsSearch(query)

        searchJob = viewModelScope.launch {
            repository.recordSearch(query)

            val cached = repository.cachedResults(query)
            if (cached != null) {
                _state.update { it.copy(results = cached, hasSearched = true, errorMessage = null) }
            }

            var songsError: String? = null
            try {
                val fresh = repository.search(query)
                // hasSearched flips true here, the moment the primary
                // (songs) result is known — success or failure — rather
                // than waiting for every category to finish. Previously
                // this only happened in the final block below, after
                // albums/playlists/artists had *all* also resolved: the
                // gap in between (songs loaded, so isSearching's own
                // results.isEmpty() check no longer held and LoadingState
                // stopped matching, but hasSearched was still false too)
                // fell through to the suggestions branch with an already-
                // emptied suggestions list and rendered a second bare
                // blank flash — a real, reported bug (skeleton -> black
                // flicker -> results), distinct from the first "tap
                // suggestion -> black screen" bug already fixed above.
                _state.update { it.copy(results = fresh, hasSearched = true, isSearching = false) }
            } catch (failure: ProviderFailure) {
                songsError = failure.toUserFacingMessage("Search failed")
                _state.update { it.copy(hasSearched = true, isSearching = false, errorMessage = songsError) }
            }

            // Albums/playlists/artists are independent, best-effort lookups:
            // a failure in one never blanks out results already shown for
            // another, and none of them block songs (the primary result
            // type) from displaying immediately.
            runCatching { repository.searchAlbums(query) }
                .onSuccess { albums -> _state.update { it.copy(albums = albums) } }
            runCatching { repository.searchPlaylists(query) }
                .onSuccess { playlists -> _state.update { it.copy(playlists = playlists) } }
            runCatching { repository.searchArtists(query) }
                .onSuccess { artists -> _state.update { it.copy(artists = artists) } }
        }
    }

    /** Tapping a suggestion commits to it and runs a real search immediately, same as tapping a recent search. */
    fun onSuggestionTapped(suggestion: String) = submitSearch(suggestion)

    fun retry() {
        val current = _state.value.query
        if (current.isNotBlank()) submitSearch(current)
    }

    /**
     * Loads the next real page of results for [tab] — called when the
     * UI's scroll position nears the end of that tab's currently visible
     * list (see [com.whiplash.music.ui.player.PlayableItemsList] and the
     * Albums/Playlists/Artists lists in SearchScreen.kt). Guards against
     * duplicate/overlapping calls (already loading, or no further page)
     * so a fast scroll-fling can't fire several overlapping network
     * requests for the same next page.
     *
     * Each tab's results are appended to (never replace) that tab's
     * existing list — a real, additive "load more," not a fabricated one
     * that just replays the same first page. `hasMoreX` flips to false
     * only on a genuine successful response reporting no further page; a
     * failed/errored page fetch (e.g. a transient network blip) leaves
     * `hasMoreX` unchanged so the next scroll near the bottom simply
     * retries, rather than permanently giving up after one hiccup.
     */
    fun loadMore(tab: SearchResultTab) {
        val state = _state.value
        val alreadyLoading = when (tab) {
            SearchResultTab.SONGS -> state.isLoadingMoreSongs
            SearchResultTab.ALBUMS -> state.isLoadingMoreAlbums
            SearchResultTab.PLAYLISTS -> state.isLoadingMorePlaylists
            SearchResultTab.ARTISTS -> state.isLoadingMoreArtists
        }
        val hasMore = when (tab) {
            SearchResultTab.SONGS -> state.hasMoreSongs
            SearchResultTab.ALBUMS -> state.hasMoreAlbums
            SearchResultTab.PLAYLISTS -> state.hasMorePlaylists
            SearchResultTab.ARTISTS -> state.hasMoreArtists
        }
        if (alreadyLoading || !hasMore) return

        viewModelScope.launch {
            when (tab) {
                SearchResultTab.SONGS -> {
                    val session = songsSession ?: return@launch
                    _state.update { it.copy(isLoadingMoreSongs = true) }
                    val page = runCatching { session.loadNextPage() }.getOrNull()
                    _state.update {
                        it.copy(
                            // Deduplicated by video id: real YouTube search
                            // backends can legitimately return the same
                            // video across different pages of the same
                            // query (confirmed on-device — not a bug in
                            // this app's own logic, a real characteristic
                            // of the underlying search results). Without
                            // this, a repeated id reaching the LazyColumn
                            // (keyed by "source:id") is a hard crash
                            // (IllegalArgumentException: duplicate key),
                            // not just a visual glitch — this must never
                            // reach the UI layer.
                            results = if (page != null) dedupeAppend(it.results, page.items) { r -> r.id } else it.results,
                            // A failed/null page (transient network error)
                            // leaves hasMoreSongs unchanged so the next
                            // scroll near the bottom retries — only a
                            // genuine successful response gets to say
                            // there's no further page.
                            hasMoreSongs = page?.hasMore ?: it.hasMoreSongs,
                            isLoadingMoreSongs = false,
                        )
                    }
                }
                SearchResultTab.ALBUMS -> {
                    val session = albumsSession ?: return@launch
                    _state.update { it.copy(isLoadingMoreAlbums = true) }
                    val page = runCatching { session.loadNextPage() }.getOrNull()
                    _state.update {
                        it.copy(
                            albums = if (page != null) dedupeAppend(it.albums, page.items) { r -> r.url } else it.albums,
                            hasMoreAlbums = page?.hasMore ?: it.hasMoreAlbums,
                            isLoadingMoreAlbums = false,
                        )
                    }
                }
                SearchResultTab.PLAYLISTS -> {
                    val session = playlistsSession ?: return@launch
                    _state.update { it.copy(isLoadingMorePlaylists = true) }
                    val page = runCatching { session.loadNextPage() }.getOrNull()
                    _state.update {
                        it.copy(
                            playlists = if (page != null) dedupeAppend(it.playlists, page.items) { r -> r.url } else it.playlists,
                            hasMorePlaylists = page?.hasMore ?: it.hasMorePlaylists,
                            isLoadingMorePlaylists = false,
                        )
                    }
                }
                SearchResultTab.ARTISTS -> {
                    val session = artistsSession ?: return@launch
                    _state.update { it.copy(isLoadingMoreArtists = true) }
                    val page = runCatching { session.loadNextPage() }.getOrNull()
                    _state.update {
                        it.copy(
                            artists = if (page != null) dedupeAppend(it.artists, page.items) { r -> r.channelUrl } else it.artists,
                            hasMoreArtists = page?.hasMore ?: it.hasMoreArtists,
                            isLoadingMoreArtists = false,
                        )
                    }
                }
            }
        }.also { loadMoreJob = it }
    }

    /**
     * Appends [newItems] to [existing], dropping any whose [keyOf] is
     * already present in [existing] — see [loadMore]'s SONGS branch for
     * why this must never be skipped (a real backend-returned duplicate
     * reaching a LazyColumn's key-based item list is a hard crash, not
     * just a cosmetic double-entry).
     */
    private fun <T> dedupeAppend(existing: List<T>, newItems: List<T>, keyOf: (T) -> Any): List<T> {
        val existingKeys = existing.mapTo(HashSet()) { keyOf(it) }
        val deduped = newItems.filter { existingKeys.add(keyOf(it)) }
        return existing + deduped
    }

    private fun clearSessions() {
        songsSession = null
        albumsSession = null
        playlistsSession = null
        artistsSession = null
    }

    /** Removes one entry from the recent-searches list (its row's "x" button). */
    fun removeRecentSearch(query: String) {
        viewModelScope.launch { repository.removeSearchHistoryEntry(query) }
    }

    /** Clears the entire recent-searches list. */
    fun clearRecentSearches() {
        viewModelScope.launch {
            repository.clearSearchHistory()
            com.whiplash.music.ui.common.ToastController.show("Recent searches cleared")
        }
    }

    private companion object {
        const val SUGGESTIONS_DEBOUNCE_MS = 150L
        const val TRENDING_ARTISTS_INDIA_QUERY = "top trending artists india"
        const val TRENDING_ARTISTS_GLOBAL_QUERY = "top global artists"
        val FALLBACK_TRENDING_ARTISTS = listOf("Arijit Singh", "Taylor Swift", "The Weeknd", "Diljit Dosanjh", "Dua Lipa")
    }
}
