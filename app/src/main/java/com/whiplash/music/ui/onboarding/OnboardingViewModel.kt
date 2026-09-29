package com.whiplash.music.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.whiplash.music.data.repository.SettingsRepository
import com.whiplash.music.data.repository.YoutubeSearchRepository
import com.whiplash.music.domain.model.YoutubeArtistResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Onboarding's data side: artist photos for the suggestions, artist search,
 * and saving the picks. Everything network is best effort: with no
 * connection the artist step still works, just with initials for photos.
 */
class OnboardingViewModel(
    private val search: YoutubeSearchRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** Artist name → photo URL (null = looked up, none found). Missing = not looked up yet. */
    private val _photos = MutableStateFlow<Map<String, String?>>(emptyMap())
    val photos: StateFlow<Map<String, String?>> = _photos

    private val _searchResults = MutableStateFlow<List<YoutubeArtistResult>>(emptyList())
    val searchResults: StateFlow<List<YoutubeArtistResult>> = _searchResults
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching
    private var searchJob: Job? = null

    private val lookups = Semaphore(4)

    /** Genre name → cover of its top playlist (same source as Explore's genre tiles). */
    private val _genreArt = MutableStateFlow<Map<String, String>>(emptyMap())
    val genreArt: StateFlow<Map<String, String>> = _genreArt
    private val requestedGenres = mutableSetOf<String>()

    fun loadGenreArt() {
        OnboardingCatalog.genres.forEach { g ->
            if (!requestedGenres.add(g.name)) return@forEach
            viewModelScope.launch {
                val url = lookups.withPermit {
                    runCatching { search.searchPlaylists("${g.query} playlist") }.getOrNull()?.firstNotNullOfOrNull { it.artworkUrl }
                }
                if (url != null) _genreArt.update { it + (g.name to url) } else requestedGenres.remove(g.name)
            }
        }
    }

    /** Album covers for the welcome collage; empty (a gradient collage) when offline. */
    private val _welcomeCovers = MutableStateFlow<List<String>>(emptyList())
    val welcomeCovers: StateFlow<List<String>> = _welcomeCovers

    init {
        viewModelScope.launch {
            val covers = runCatching { search.searchAlbums("top albums 2026") }.getOrDefault(emptyList())
                .mapNotNull { it.artworkUrl }.distinct().take(12)
            _welcomeCovers.value = covers
        }
        // Warm up the genre covers while the first screens are read.
        loadGenreArt()
    }

    /** Looks up photos for [names] not seen yet, a few at a time. */
    fun loadPhotos(names: List<String>) {
        val todo = names.filter { it !in _photos.value }
        if (todo.isEmpty()) return
        viewModelScope.launch {
            todo.map { name ->
                async {
                    lookups.withPermit {
                        val url = runCatching { search.searchArtists(name).firstOrNull()?.artworkUrl }.getOrNull()
                        _photos.update { it + (name to url) }
                    }
                }
            }.awaitAll()
        }
    }

    /** Debounced artist search for the search field; blank clears results. */
    fun searchArtists(query: String) {
        searchJob?.cancel()
        val q = query.trim()
        if (q.length < 2) {
            _searchResults.value = emptyList()
            _searching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            _searching.value = true
            val results = runCatching { search.searchArtists(q) }.getOrDefault(emptyList())
            _searchResults.value = results.take(12)
            results.forEach { r -> if (r.name !in _photos.value) _photos.update { it + (r.name to r.artworkUrl) } }
            _searching.value = false
        }
    }

    /** Saved picks, for reopening from Settings with them preselected. */
    suspend fun savedTaste(): Triple<List<String>, List<String>, List<String>> =
        Triple(settings.tasteLanguages.first(), settings.tasteGenres.first(), settings.tasteArtists.first())

    suspend fun finish(languages: List<String>, genres: List<String>, artists: List<String>) {
        settings.setTaste(languages, genres, artists)
        settings.setOnboardingDone(true)
    }

    class Factory(
        private val search: YoutubeSearchRepository,
        private val settings: SettingsRepository,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return OnboardingViewModel(search, settings) as T
        }
    }
}

/**
 * Lets Settings reopen the taste steps over the running app
 * (MainActivity shows [OnboardingFlow] while [request] is set).
 */
object OnboardingController {
    /**
     * True once Home has its first personalised Quick Picks (set by
     * HomeViewModel), so the "Personalising" step can offer Continue.
     */
    val homeReady = kotlinx.coroutines.flow.MutableStateFlow(false)

    var request by androidx.compose.runtime.mutableStateOf<OnboardingStep?>(null)
        private set

    fun openTaste() {
        request = OnboardingStep.LANGUAGES
    }

    fun close() {
        request = null
    }
}

/**
 * Whether this launch should show onboarding. Decided once per install: a
 * fresh install (or one just reset) sees it; someone updating from a version
 * without onboarding, who already has history, playlists or favourites, is
 * marked done and never sees it.
 */
suspend fun shouldShowOnboarding(app: com.whiplash.music.WhiplashApplication): Boolean {
    app.settingsRepository.onboardingDone.first()?.let { return !it }
    val lib = app.libraryRepository
    val existing = lib.observeRecentlyPlayed(limit = 1).first().isNotEmpty() ||
        lib.observePlaylists().first().isNotEmpty() ||
        lib.observeFavoriteKeys().first().isNotEmpty()
    if (existing) app.settingsRepository.setOnboardingDone(true)
    return !existing
}
