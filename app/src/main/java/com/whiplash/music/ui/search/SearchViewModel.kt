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
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Debounces user input, shows cached results instantly if available
 * (section 53), then runs a real search and reports errors without
 * crashing the UI. Search failures are informational only — they never
 * touch [com.whiplash.music.playback.controller.PlaybackController], so a
 * failed search cannot disrupt any currently playing track.
 *
 * Runs four independent searches per query (songs/albums/playlists/artists,
 * section 32/37) using the real, distinct NewPipeExtractor content filters
 * confirmed to exist (music_songs/music_albums/music_playlists/music_artists).
 * Each category fails independently — an albums-search failure doesn't
 * blank out songs that already loaded successfully.
 *
 * Also owns YouTube Music-style "recent searches": a submitted query
 * (debounce elapsed and the search actually ran) is remembered so it can
 * be recalled from the idle/empty search screen later, independent of the
 * short-lived result cache used to speed up re-searching.
 */
class SearchViewModel(private val repository: YoutubeSearchRepository) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    val recentSearches: StateFlow<List<String>> =
        repository.recentSearches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var searchJob: Job? = null

    fun onQueryChanged(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()

        if (query.isBlank()) {
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
            return
        }

        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS) // avoid firing a network request per keystroke

            // The debounce elapsing without the query changing again is a
            // real, deliberate search (not partial typing) — record it now
            // rather than waiting for the network call to finish, so a
            // query is remembered even if the search itself later fails.
            repository.recordSearch(query)

            val cached = repository.cachedResults(query)
            if (cached != null) {
                _state.update { it.copy(results = cached, hasSearched = true, errorMessage = null) }
            }

            _state.update { it.copy(isSearching = true) }

            var songsError: String? = null
            try {
                val fresh = repository.search(query)
                _state.update { it.copy(results = fresh) }
            } catch (failure: ProviderFailure) {
                songsError = failure.toUserFacingMessage("Search failed")
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

            _state.update {
                it.copy(
                    isSearching = false,
                    hasSearched = true,
                    errorMessage = songsError,
                )
            }
        }
    }

    fun retry() {
        val current = _state.value.query
        if (current.isNotBlank()) onQueryChanged(current)
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
        const val DEBOUNCE_MS = 400L
    }
}
