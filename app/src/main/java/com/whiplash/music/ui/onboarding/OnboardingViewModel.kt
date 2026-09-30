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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Onboarding's data side: artist photos for the suggestions, artist search,
 * and saving the picks. Covers, genre tiles and the suggested artists'
 * photos ship with the app ([OnboardingArt]), so every step looks complete
 * with no connection. The network only fills in artists that aren't
 * bundled, retrying until it gets through.
 */
class OnboardingViewModel(
    private val search: YoutubeSearchRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** Artist name → photo URL (null = looked up, none found). Missing = not looked up yet. */
    private val _photos = MutableStateFlow<Map<String, String?>>(OnboardingArt.artists)
    val photos: StateFlow<Map<String, String?>> = _photos

    private val _searchResults = MutableStateFlow<List<YoutubeArtistResult>>(emptyList())
    val searchResults: StateFlow<List<YoutubeArtistResult>> = _searchResults
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching
    private var searchJob: Job? = null

    private val lookups = Semaphore(4)

    /** Genre name → tile artwork, bundled. */
    val genreArt: StateFlow<Map<String, String>> = MutableStateFlow(OnboardingArt.genres)

    /** Album covers for the welcome collage and sign-in step, bundled. */
    val welcomeCovers: StateFlow<List<String>> = MutableStateFlow(OnboardingArt.covers)

    /** Names being looked up right now, so a retry never runs twice. */
    private val inFlight = mutableSetOf<String>()

    /**
     * Looks up photos for [names] that aren't bundled or known yet, a few at
     * a time. A failed lookup (no connection, timeout) is retried with
     * backoff instead of being remembered as "no photo"; only a search that
     * worked but found no artwork records null.
     */
    fun loadPhotos(names: List<String>) {
        val todo = names.filter { it !in _photos.value && inFlight.add(it) }
        if (todo.isEmpty()) return
        todo.forEach { name ->
            viewModelScope.launch {
                for (wait in PHOTO_RETRY_DELAYS_MS) {
                    delay(wait)
                    val result = lookups.withPermit {
                        withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { runCatching { search.searchArtists(name) }.getOrNull() }
                    }
                    if (result != null) {
                        _photos.update { it + (name to result.firstOrNull()?.artworkUrl) }
                        break
                    }
                }
                inFlight.remove(name)
            }
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

    private companion object {
        const val LOOKUP_TIMEOUT_MS = 8_000L
        /** First try at once, then back off; about a minute in total before giving up. */
        val PHOTO_RETRY_DELAYS_MS = listOf(0L, 3_000L, 8_000L, 15_000L, 30_000L)
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
