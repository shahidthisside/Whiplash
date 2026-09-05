package com.whiplash.music.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whiplash.music.data.repository.LibraryRepository
import com.whiplash.music.data.repository.YoutubeSearchRepository
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.domain.model.speedDialIdentity
import com.whiplash.music.ui.common.ToastController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Home screen data (section 31). Only shows sections backed by real data:
 * Recently Played (from actual playback history) and Quick Picks (a real
 * YouTube search blended across the user's own top few listened-to
 * artists when available — see [personalizedQuickPicksQueries] — falling
 * back to a generic popular-music search for new users with no history
 * yet). This is deliberately NOT a claim of YouTube Music's own
 * personalized "Quick picks" feed, which is a server-side, account-based
 * recommendation system NewPipeExtractor has no access to (per section 73
 * "never claim a feature is supported until the current provider actually
 * implements it") — nor is it YouTube's general Trending/Charts kiosk,
 * which YouTube itself removed from its interface in July 2025 and which
 * NewPipeExtractor's kiosk support for is documented as
 * deprecated/unreliable as a result. What this app can honestly do
 * instead: search for more music from artists the user actually played,
 * blended together rather than dominated by a single artist.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val libraryRepository: LibraryRepository,
    private val youtubeSearchRepository: YoutubeSearchRepository,
) : ViewModel() {

    val recentlyPlayed: StateFlow<List<PlayableItem>> = libraryRepository.observeRecentlyPlayed(limit = 25)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * True once Speed dial's underlying Room flow has emitted its first
     * real snapshot (whether that snapshot is empty or has items) — the
     * genuine "have we heard back from the database yet" signal, distinct
     * from [speedDial] simply being an empty list. [speedDial] itself
     * starts as `emptyList()` before Room's Flow has emitted anything
     * (see its `stateIn` initial value below), so checking
     * `speedDial.isEmpty()` alone cannot tell "definitely no history yet"
     * apart from "haven't heard back yet" — exactly the ambiguity that
     * made Home's Speed dial section render nothing at all (rather than a
     * loading skeleton) for the brief window on a cold app start before
     * this first real emission arrives.
     */
    private val _isSpeedDialLoaded = MutableStateFlow(false)
    val isSpeedDialLoaded: StateFlow<Boolean> = _isSpeedDialLoaded

    /**
     * Bumped by [refreshHome] to force [speedDial] to genuinely re-subscribe
     * to its two Room queries, which makes Room re-execute them. This is the
     * difference between a real re-read and pretending: incrementing a
     * counter that only re-ran the mapping block would recompute the same
     * list from the same cached emissions and change nothing.
     */
    private val speedDialRefreshTrigger = MutableStateFlow(0)

    /**
     * YouTube-Music-style "Speed dial" (a 3x3 grid of artwork, section 31).
     * Pinned tracks (explicitly pinned via the 3-dot menu, section 51) are
     * shown first and stay until unpinned — real persisted state, not a
     * fake toggle — filling any remaining slots with the most recently
     * played tracks that aren't already pinned.
     *
     * Subscribes to the repository Flows directly rather than to this
     * ViewModel's own [recentlyPlayed] StateFlow so that a refresh actually
     * re-queries the database: re-subscribing to a StateFlow just replays
     * its cached value, whereas re-subscribing to a Room Flow re-runs the
     * SQL.
     */
    val speedDial: StateFlow<List<PlayableItem>> = speedDialRefreshTrigger
        .flatMapLatest {
            kotlinx.coroutines.flow.combine(
                libraryRepository.observePinned(),
                libraryRepository.observeRecentlyPlayed(limit = 25),
            ) { pinned, recent ->
                // Real, reported bug: this used to dedup by the raw
                // (item.source, item.id) pair — the exact same YOUTUBE/DOWNLOAD
                // identity gap HistoryDao.observeRecentlyPlayed's own doc
                // explains (a DownloadedTrack's id IS the YouTube video id it
                // was downloaded from), so a song played once as a live YouTube
                // stream and once as a downloaded file could appear as two
                // separate Speed dial tiles for what a user experiences as one
                // song. speedDialIdentity() normalizes YOUTUBE/DOWNLOAD together
                // (never LOCAL, a genuinely different id namespace) so both
                // halves of this composition — the pinned set used to exclude
                // already-pinned tracks from the "recent" fallback, and the
                // dedup itself — agree on what counts as the same track.
                val pinnedIds = pinned.map { it.speedDialIdentity() }.toSet()
                (pinned + recent.filter { it.speedDialIdentity() !in pinnedIds }).take(9)
            }
        }
        .onEach { _isSpeedDialLoaded.value = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _quickPicks = MutableStateFlow<List<PlayableItem.YoutubeTrack>>(emptyList())
    val quickPicks: StateFlow<List<PlayableItem.YoutubeTrack>> = _quickPicks

    private val _isLoadingQuickPicks = MutableStateFlow(false)
    val isLoadingQuickPicks: StateFlow<Boolean> = _isLoadingQuickPicks

    /**
     * True only while a whole-screen refresh the user asked for by pulling
     * Home down is in flight. Deliberately separate from
     * [isLoadingQuickPicks], which is also true during the automatic load
     * from `init{}`: binding the pull-to-refresh indicator to that would
     * drop a spinner onto the top of Home on every single cold start,
     * unprompted, competing with the shimmer skeleton that already
     * communicates the initial load.
     *
     * Only the pull gesture sets this. The Quick Picks header button does
     * not, because it is scoped to its own section (see [refreshQuickPicks])
     * and a section-scoped action should not animate a screen-wide control.
     */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    init {
        loadQuickPicks()
    }

    /**
     * Background/automatic load (app start). Fire-and-forget: nothing is
     * waiting on it and no user-visible refresh affordance is tied to it.
     */
    fun loadQuickPicks() {
        viewModelScope.launch { fetchQuickPicks() }
    }

    /**
     * Refreshes **only** Quick Picks — what the refresh button in the Quick
     * Picks section header means. It sits in that section's header, so it
     * refreshes that section and deliberately leaves Speed dial alone.
     *
     * Reports progress through [isLoadingQuickPicks], which the button
     * already spins on, and pointedly not through [isRefreshing]: a
     * section-scoped button should not drive the screen-wide pull indicator.
     */
    fun refreshQuickPicks() {
        viewModelScope.launch { fetchQuickPicks() }
    }

    /**
     * Refreshes the **whole** Home screen — both Speed dial and Quick Picks —
     * which is what pulling the screen down means. Holds [isRefreshing] true
     * until the work genuinely finishes rather than releasing the indicator
     * on a timer, because the whole point of the spinner is that it means
     * "still working".
     *
     * The two halves run concurrently and the pull is held until both are
     * done. Speed dial's half is a local database re-read and will normally
     * finish long before the network search; being honest about scope, that
     * re-read rarely produces different tiles, because Speed dial is fed by
     * Room Flows that already emit on every write to the pinned and history
     * tables — it cannot go stale the way a cached network response can. It
     * is re-run anyway so the gesture genuinely covers everything on screen
     * rather than quietly ignoring half of it.
     *
     * Re-entrant pulls are ignored while one is already running, so
     * repeatedly yanking the list can't stack up concurrent searches.
     */
    fun refreshHome() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                coroutineScope {
                    launch { refreshSpeedDial() }
                    launch { fetchQuickPicks() }
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Forces Room to re-execute Speed dial's two queries and waits for the
     * fresh emission, so the pull indicator's lifetime honestly covers the
     * database work instead of the gesture claiming to refresh something it
     * never touched.
     *
     * Bounded by a timeout for the same reason
     * [personalizedQuickPicksQueries] is: a genuinely stuck database must
     * not leave the refresh indicator spinning forever.
     */
    private suspend fun refreshSpeedDial() {
        speedDialRefreshTrigger.value += 1
        withTimeoutOrNull(5_000L) {
            kotlinx.coroutines.flow.combine(
                libraryRepository.observePinned(),
                libraryRepository.observeRecentlyPlayed(limit = 25),
            ) { pinned, recent -> pinned.size + recent.size }.first()
        }
    }

    private suspend fun fetchQuickPicks() {
        val queries = personalizedQuickPicksQueries()

        // Show cached results immediately (from any query that already
        // has a fresh cache entry) while a real network refresh runs,
        // same "cache -> display immediately -> background refresh"
        // pattern YoutubeSearchRepository already documents.
        val cachedBlend = blend(queries.map { youtubeSearchRepository.cachedResults(it) ?: emptyList() })
        if (cachedBlend.isNotEmpty()) _quickPicks.value = cachedBlend

        _isLoadingQuickPicks.value = true
        try {
            // Run all artist searches in parallel rather than one
            // sequential search per artist — same total latency as
            // the old single-query version, just fanned out.
            // coroutineScope because this is a plain suspend function
            // rather than a viewModelScope.launch block: it supplies the
            // scope async needs, and also means a caller that cancels
            // (refreshHome's job being cancelled with the ViewModel)
            // cancels the in-flight searches with it rather than leaking
            // them.
            val blended = coroutineScope {
                val resultSets = queries.map { query ->
                    async { runCatching { youtubeSearchRepository.search(query) }.getOrDefault(emptyList()) }
                }.awaitAll()
                blend(resultSets)
            }
            // Only replace what's showing if the blend actually
            // produced something — an all-queries-failed network
            // blip should leave the previous/cached results visible
            // rather than clearing them, same as the old catch-all
            // behavior.
            if (blended.isNotEmpty()) _quickPicks.value = blended
        } finally {
            _isLoadingQuickPicks.value = false
        }
    }

    /**
     * Interleaves multiple artists' result sets round-robin (first song
     * from each artist, then second song from each, ...) instead of
     * concatenating them, so Quick Picks reads as a genuine mix rather
     * than one artist's whole block followed by the next. Deduplicates
     * by track id along the way (the same video can legitimately surface
     * in more than one artist's search, e.g. a feature/collab track).
     */
    private fun blend(resultSets: List<List<PlayableItem.YoutubeTrack>>): List<PlayableItem.YoutubeTrack> {
        val seenIds = HashSet<String>()
        val blended = mutableListOf<PlayableItem.YoutubeTrack>()
        val maxLen = resultSets.maxOfOrNull { it.size } ?: 0
        for (i in 0 until maxLen) {
            for (set in resultSets) {
                val track = set.getOrNull(i) ?: continue
                if (seenIds.add(track.id)) blended += track
            }
        }
        return blended
    }

    /**
     * Builds Quick Picks search queries from the user's own real listening
     * history rather than a single hardcoded string for everyone — an
     * honest, low-cost personalization: no account, no ML model, no
     * backend recommendation API (NewPipeExtractor gives no access to
     * YouTube Music's own personalized "Quick picks" feed, which is
     * server-side only), just "search for more from artists you actually
     * played recently."
     *
     * Returns up to [MAX_BLEND_ARTISTS] queries (one per distinct top
     * artist by play frequency, most-played first, ties favoring whoever
     * was played more recently) so the resulting Quick Picks list is a
     * genuine blend across the user's actual listening spread rather than
     * a wall of a single artist's tracks — the earlier single-artist
     * version of this made Quick Picks feel narrower than intended when a
     * user had listened to several different artists.
     *
     * Falls back to a single generic query when there's no history yet
     * (a fresh install/new user) — an explicit, honest fallback rather
     * than pretending to personalize with no data to draw from.
     */
    private suspend fun personalizedQuickPicksQueries(): List<String> {
        // Reads a fresh, independent collection of the repository's own
        // Flow rather than this ViewModel's derived `recentlyPlayed`
        // StateFlow: that StateFlow is `SharingStarted.WhileSubscribed`,
        // meaning its upstream Room query only starts once the Home
        // screen's Compose UI actually subscribes to it — which hasn't
        // happened yet when this runs from init{}'s first loadQuickPicks()
        // call. Collecting the repository Flow directly here always
        // starts fresh and emits its first real snapshot immediately
        // (Room Flows emit on collection, they don't wait for a shared
        // subscriber), so this reliably has real data on a cold start
        // whenever real history exists. Still bounded by a generous
        // timeout as a safety net against a genuinely stuck/broken DB —
        // a genuinely new user's Flow also legitimately emits emptyList()
        // right away, so this never hangs either way. This was
        // previously 500ms, which turned out to be too tight: on a slow
        // cold app start (process creation + Room DB open competing with
        // everything else happening at launch) this could race and lose,
        // silently falling back to the generic query even with real
        // history sitting in the database — confirmed on-device via a
        // cold-start test where the app took 7+ seconds just to render
        // its first frame. 5 seconds gives Room a realistic window
        // without meaningfully delaying Quick Picks for the rare case
        // where it's actually needed.
        val history = withTimeoutOrNull(5_000L) {
            libraryRepository.observeRecentlyPlayed(limit = 25).first()
        } ?: emptyList()
        val topArtists = history
            .groupingBy { it.artist }
            .eachCount()
            .entries
            .filter { it.key.isNotBlank() }
            .sortedWith(compareByDescending { it.value })
            .take(MAX_BLEND_ARTISTS)
            .map { it.key }
        return if (topArtists.isEmpty()) listOf(QUICK_PICKS_QUERY) else topArtists.map { "$it songs" }
    }

    /**
     * Removes [item] from the currently displayed Quick Picks list. This is
     * deliberately session-only (not persisted): Quick Picks is a live
     * search result list re-fetched on [loadQuickPicks] (e.g. after a
     * fresh app start), not a stored collection with per-item state like
     * Speed dial's history/pin data — there's no real "permanently hidden
     * search result" concept to persist here without a further-scoped
     * feature (a hidden-ids table), so this only hides it until the next
     * reload rather than claiming permanence it doesn't have.
     */
    fun removeFromQuickPicks(item: PlayableItem.YoutubeTrack) {
        _quickPicks.value = _quickPicks.value.filter { it.id != item.id }
        ToastController.show("Removed from Quick Picks")
    }

    fun clearHistory() {
        viewModelScope.launch {
            libraryRepository.clearHistory()
            ToastController.show("History cleared")
        }
    }

    private companion object {
        const val QUICK_PICKS_QUERY = "popular music 2026"
        const val MAX_BLEND_ARTISTS = 5
    }
}
