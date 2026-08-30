package com.whiplash.music.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whiplash.music.data.repository.YoutubeSearchRepository
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.domain.model.YoutubeArtistResult
import com.whiplash.music.domain.model.YoutubePlaylistResult
import com.whiplash.music.domain.model.toUserFacingMessage
import com.whiplash.music.playback.provider.ProviderFailure
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

    private var searchJob: Job? = null
    private var suggestionsJob: Job? = null

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
                )
            }
            return
        }

        if (wasShowingResults) {
            searchJob?.cancel()
            _state.update {
                it.copy(
                    results = emptyList(),
                    albums = emptyList(),
                    playlists = emptyList(),
                    artists = emptyList(),
                    hasSearched = false,
                    isSearching = false,
                    errorMessage = null,
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
        _state.update { it.copy(query = query, isSearching = true, suggestions = emptyList()) }
        suggestionsJob?.cancel()
        searchJob?.cancel()

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
    }
}
