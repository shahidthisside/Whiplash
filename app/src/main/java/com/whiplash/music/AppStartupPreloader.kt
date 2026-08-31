package com.whiplash.music

import android.content.Context
import android.util.Log
import coil.Coil
import coil.request.ImageRequest
import com.whiplash.music.data.repository.LibraryRepository
import com.whiplash.music.data.repository.YoutubeSearchRepository
import com.whiplash.music.domain.model.PlayableItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Warms Home screen data and artwork in the background at app startup,
 * before the user ever taps into the Home tab.
 *
 * Without this, opening the app after it's been closed for a while (so
 * Quick Picks' short-lived search cache — see
 * [YoutubeSearchRepository.cachedResults]'s 10-minute TTL — has expired,
 * and Coil's own disk/memory image cache may also be cold) shows a real,
 * user-visible delay on Home: a loading spinner for Quick Picks for
 * however long the live network search takes, and black boxes where
 * Speed dial / Quick Picks artwork should be while each image's own
 * separate network fetch completes. Both are real work that has to
 * happen somewhere — this just moves it earlier, to run concurrently
 * with the rest of app startup (splash screen, database open, etc.)
 * instead of only starting once [com.whiplash.music.ui.home.HomeScreen]
 * itself first composes.
 *
 * This duplicates a small amount of [com.whiplash.music.ui.home.HomeViewModel]'s
 * personalized-query logic rather than sharing it directly, since that
 * logic is tied to a ViewModel's lifecycle/StateFlow — deliberately kept
 * to the minimum needed (top artists from history -> search queries) so
 * the two don't drift in what "personalized" means, while staying a
 * plain, testable function with no ViewModel dependency.
 */
class AppStartupPreloader(
    private val context: Context,
    private val libraryRepository: LibraryRepository,
    private val youtubeSearchRepository: YoutubeSearchRepository,
) {

    suspend fun preload() = kotlinx.coroutines.coroutineScope {
        // Recently played + pinned artwork (Speed dial) is real, already
        // on-device data — no network needed to know which tracks to
        // preload, only to fetch each one's artwork image.
        val recentlyPlayed = withTimeoutOrNull(HISTORY_TIMEOUT_MS) {
            libraryRepository.observeRecentlyPlayed(limit = 9).first()
        } ?: emptyList()
        val pinned = withTimeoutOrNull(HISTORY_TIMEOUT_MS) {
            libraryRepository.observePinned().first()
        } ?: emptyList()
        val speedDialArtwork = (pinned + recentlyPlayed).take(9).mapNotNull { it.artworkUri }

        // Quick Picks: real personalized queries (same "top artists from
        // history" logic HomeViewModel uses), searched now so a fresh
        // network round-trip is already underway (and its result cached)
        // well before the user opens Home.
        val queries = personalizedQueries(recentlyPlayed)

        val artworkPreloads = speedDialArtwork.map { url -> async { preloadImage(url) } }
        val searchWarmups = queries.map { query ->
            async {
                val results = runCatching { youtubeSearchRepository.search(query) }.getOrDefault(emptyList())
                // Warm Quick Picks' own artwork too, not just the
                // metadata — otherwise the search cache is warm but
                // the images themselves would still be a cold fetch
                // the moment Home renders.
                results.take(MAX_ARTWORK_PER_QUERY).mapNotNull { it.artworkUri }.forEach { preloadImage(it) }
            }
        }

        runCatching { (artworkPreloads + searchWarmups).awaitAll() }
            .onFailure { Log.w(TAG, "Startup preload did not fully complete", it) }
    }

    private suspend fun personalizedQueries(recentlyPlayed: List<PlayableItem>): List<String> {
        val topArtists = recentlyPlayed
            .groupingBy { it.artist }
            .eachCount()
            .entries
            .filter { it.key.isNotBlank() }
            .sortedWith(compareByDescending { it.value })
            .take(MAX_BLEND_ARTISTS)
            .map { it.key }
        return if (topArtists.isEmpty()) listOf(QUICK_PICKS_QUERY) else topArtists.map { "$it songs" }
    }

    private fun preloadImage(url: String) {
        val loader = Coil.imageLoader(context)
        val request = ImageRequest.Builder(context).data(url).build()
        loader.enqueue(request)
    }

    private companion object {
        const val TAG = "AppStartupPreloader"
        const val HISTORY_TIMEOUT_MS = 5_000L
        const val MAX_BLEND_ARTISTS = 5
        const val MAX_ARTWORK_PER_QUERY = 6
        const val QUICK_PICKS_QUERY = "popular music 2026"
    }
}
