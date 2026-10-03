// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.home

import com.whiplash.music.recommend.withoutNearDuplicates
import kotlinx.coroutines.flow.flowOn
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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val settingsRepository: com.whiplash.music.data.repository.SettingsRepository,
    // Emits online/offline; when it turns online with Quick Picks still empty, they reload.
    private val onlineChanges: kotlinx.coroutines.flow.Flow<Boolean>? = null,
    /** Song radios of what the listener finishes: Quick Picks' first source. */
    private val radioSource: QuickPicksRadio? = null,
    /** The last full Quick Picks, shown at once while a fresh list loads. */
    private val snapshot: com.whiplash.music.data.repository.QuickPicksSnapshot? = null,
    /** The last Speed dial, shown at once while history is read. */
    private val speedDialSnapshot: com.whiplash.music.data.repository.SpeedDialSnapshot? = null,
    /** The connection right now, for deciding whether a launch refresh is worth it. */
    private val network: () -> QuickPicksRefreshPolicy.Network = { QuickPicksRefreshPolicy.Network.UNMETERED },
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    // The saved Speed dial, until the live one from history arrives.
    private val savedSpeedDial = MutableStateFlow(speedDialSnapshot?.peek()?.takeIf { it.isNotEmpty() })
    private var liveSpeedDialSeen = false


    // Layout settings held here (not collected fresh in the screen) so their
    // real values are already known when Home comes back from an album or
    // History. Collecting them in the screen started each at a default for
    // the first frame, which changed the list's length and made Home lose its
    // scroll position (landing on Quick Picks instead of the shelf you left).
    // null = not read from disk yet.
    val speedDialListView: StateFlow<Boolean?> = settingsRepository.speedDialListView
        // Remembered next to the saved dial, so it isn't drawn as a grid first and then a list.
        .onEach { list -> if (list != speedDialSnapshot?.peekListView()) speedDialSnapshot?.writeListView(list) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, speedDialSnapshot?.peekListView())
    val quickPicksGridView: StateFlow<Boolean?> = settingsRepository.quickPicksGridView
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val quickPicksGridCount: StateFlow<Int?> = settingsRepository.quickPicksGridCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val speedDialGridCount: StateFlow<Int> = settingsRepository.speedDialGridCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.whiplash.music.data.repository.SPEED_DIAL_PAGE_SIZE)
    val speedDialPeek: StateFlow<Boolean> = settingsRepository.speedDialPeek
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val quickPicksPeek: StateFlow<Boolean> = settingsRepository.quickPicksPeek
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val homeShelvesEnabled: StateFlow<Boolean?> = settingsRepository.homeShelvesEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setSpeedDialListView(list: Boolean) {
        viewModelScope.launch { settingsRepository.setSpeedDialListView(list) }
    }

    fun setQuickPicksGridView(grid: Boolean) {
        viewModelScope.launch { settingsRepository.setQuickPicksGridView(grid) }
    }

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
    private val liveSpeedDial: kotlinx.coroutines.flow.Flow<List<PlayableItem>> = speedDialRefreshTrigger
        .flatMapLatest {
            kotlinx.coroutines.flow.combine(
                libraryRepository.observePinned(),
                libraryRepository.observeRecentlyPlayed(limit = SPEED_DIAL_FETCH),
                kotlinx.coroutines.flow.combine(
                    settingsRepository.speedDialPaging,
                    settingsRepository.speedDialPageCount,
                    settingsRepository.speedDialGridCount,
                ) { on, pages, perPage -> perPage * (if (on) pages else 1) },
            ) { pinned, recent, max ->
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
                // Pins are the listener's own choice and always stay; recent
                // plays skip another upload of a song already on the dial
                // (lyric video one day, official audio the next).
                val dupes = com.whiplash.music.recommend.NearDuplicateFilter().apply { pinned.forEach { add(it) } }
                (pinned + recent.filter { it.speedDialIdentity() !in pinnedIds && dupes.accept(it) }).take(max)
            }
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .onEach { live ->
            liveSpeedDialSeen = true
            savedSpeedDial.value = null
            _isSpeedDialLoaded.value = true
            // Kept for the next launch (only when it changed, so plays don't rewrite it needlessly).
            if (live != lastSavedSpeedDial) {
                lastSavedSpeedDial = live
                speedDialSnapshot?.let { store -> viewModelScope.launch { store.write(live) } }
            }
        }

    private var lastSavedSpeedDial: List<PlayableItem>? = null

    /** The live Speed dial, or the saved one from the last launch until the live one is read. */
    val speedDial: StateFlow<List<PlayableItem>> = kotlinx.coroutines.flow.combine(
        liveSpeedDial.map<List<PlayableItem>, List<PlayableItem>?> { it }.onStart { emit(null) },
        savedSpeedDial,
    ) { live, saved -> live ?: saved ?: emptyList() }
        .distinctUntilChanged()
        // Eagerly (a cheap local query), so Speed dial is already filled in when
        // Home returns after more than a few seconds away — otherwise it came back
        // empty for a frame and shifted everything below it.
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Declared after everything it touches, so they exist when it runs.
    init {
        // Already read at app start: shown from the first frame, no placeholder.
        if (savedSpeedDial.value != null && !liveSpeedDialSeen) _isSpeedDialLoaded.value = true
        if (savedSpeedDial.value == null) speedDialSnapshot?.let { store ->
            viewModelScope.launch {
                val saved = store.read()
                if (!liveSpeedDialSeen && saved.isNotEmpty()) {
                    savedSpeedDial.value = saved
                    _isSpeedDialLoaded.value = true
                }
            }
        }
    }

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

    // False until the first Quick Picks load finishes (or fails), so Home
    // shows the skeleton from the first frame instead of an empty gap.
    private val _quickPicksSettled = MutableStateFlow(false)
    val quickPicksSettled: StateFlow<Boolean> = _quickPicksSettled

    private var autoJob: kotlinx.coroutines.Job? = null

    /** Build info of the list on disk (the showing one, or [pendingQuickPicks]). */
    private var savedMeta: com.whiplash.music.data.repository.QuickPicksSnapshot.Meta? = null
    private var savedRead = false

    /** A refresh was due while offline; the reconnect runs it. */
    private var waitingForNetwork = false

    /**
     * Rule 5: a fresh list that arrived after the listener started using
     * Quick Picks. Held instead of swapped under their finger, and shown
     * the next time Home opens.
     */
    private var pendingQuickPicks: List<PlayableItem.YoutubeTrack>? = null
    private var quickPicksTouched = false

    // --- 4.1: shelves feed -------------------------------------------------

    /** One card on a shelf: a song (plays on tap) or an album/playlist (opens on tap). */
    sealed interface ShelfItem {
        val key: String
        data class Track(val track: PlayableItem.YoutubeTrack) : ShelfItem {
            override val key get() = "t:${track.id}"
        }
        data class Collection(val collection: com.whiplash.music.domain.model.YoutubePlaylistResult) : ShelfItem {
            override val key get() = "c:${collection.url}"
        }
    }

    data class HomeShelf(val spec: com.whiplash.music.domain.model.ShelfSpec, val items: List<ShelfItem>)

    private val _shelves = MutableStateFlow<List<HomeShelf>>(emptyList())
    val shelves: StateFlow<List<HomeShelf>> = _shelves

    private val _isLoadingShelves = MutableStateFlow(false)
    val isLoadingShelves: StateFlow<Boolean> = _isLoadingShelves

    private val _hasMoreShelves = MutableStateFlow(true)
    val hasMoreShelves: StateFlow<Boolean> = _hasMoreShelves

    private var shelfPlan: List<com.whiplash.music.domain.model.ShelfSpec>? = null
    private var nextShelfIndex = 0
    private var shelvesJob: kotlinx.coroutines.Job? = null

    /** Loads the next page of shelves when the end of the feed comes into view. */
    fun loadMoreShelves() {
        if (!shelvesEnabled || _isLoadingShelves.value || !_hasMoreShelves.value) return
        shelvesJob = viewModelScope.launch { fetchShelves(reset = false) }
    }

    private suspend fun fetchShelves(reset: Boolean) {
        _isLoadingShelves.value = true
        try {
            if (reset || shelfPlan == null) {
                val history = withTimeoutOrNull(5_000L) {
                    libraryRepository.observeRecentlyPlayed(limit = 50).first()
                } ?: emptyList()
                shelfPlan = com.whiplash.music.domain.model.planHomeShelves(
                    com.whiplash.music.domain.model.rankArtists(history.map { it.artist }, MAX_SHELF_ARTISTS),
                )
                nextShelfIndex = 0
                if (reset) _shelves.value = emptyList()
            }
            val plan = shelfPlan.orEmpty()
            val loaded = mutableListOf<HomeShelf>()
            // Keep going until a page yields something (empty searches are skipped)
            // or the plan runs out, so one empty shelf never stalls paging.
            while (loaded.isEmpty() && nextShelfIndex < plan.size) {
                val page = plan.subList(nextShelfIndex, minOf(nextShelfIndex + com.whiplash.music.domain.model.SHELVES_PER_PAGE, plan.size))
                nextShelfIndex += page.size
                loaded += coroutineScope {
                    page.map { spec -> async { loadShelf(spec) } }.awaitAll()
                }.filterNotNull()
            }
            if (loaded.isNotEmpty()) {
                val seen = _shelves.value.map { it.spec.key }.toSet()
                _shelves.value = _shelves.value + loaded.filter { it.spec.key !in seen }
            }
            _hasMoreShelves.value = nextShelfIndex < plan.size
        } finally {
            _isLoadingShelves.value = false
        }
    }

    /** One shelf's items, or null when the search failed or found nothing. */
    private suspend fun loadShelf(spec: com.whiplash.music.domain.model.ShelfSpec): HomeShelf? {
        val items: List<ShelfItem> = runCatching {
            when (spec.kind) {
                com.whiplash.music.domain.model.ShelfKind.ALBUMS ->
                    youtubeSearchRepository.searchAlbums(spec.query).map { ShelfItem.Collection(it) }
                com.whiplash.music.domain.model.ShelfKind.PLAYLISTS ->
                    youtubeSearchRepository.searchPlaylists(spec.query).map { ShelfItem.Collection(it) }
                com.whiplash.music.domain.model.ShelfKind.SONGS ->
                    youtubeSearchRepository.search(spec.query).let { found ->
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { found.withoutNearDuplicates() }
                    }.map { ShelfItem.Track(it) }
            }
        }.getOrDefault(emptyList())
            .distinctBy { it.key }
            .take(MAX_SHELF_ITEMS)
        return if (items.isEmpty()) null else HomeShelf(spec, items)
    }

    /**
     * Set from the "Home shelves" setting. The feed is only fetched while
     * enabled, so turning it off also stops its network searches.
     */
    private var shelvesEnabled = false

    fun setShelvesEnabled(enabled: Boolean) {
        if (enabled == shelvesEnabled) return
        shelvesEnabled = enabled
        if (enabled && _shelves.value.isEmpty() && !_isLoadingShelves.value) {
            shelvesJob = viewModelScope.launch { fetchShelves(reset = true) }
        }
    }

    init {
        loadQuickPicks()
        // Opened offline (e.g. finishing onboarding without a connection), or a
        // refresh was due while offline: do it as soon as the phone is back
        // online, with no pull needed.
        onlineChanges?.let { changes ->
            viewModelScope.launch {
                changes.drop(1).collect { online ->
                    if (online && (_quickPicks.value.isEmpty() || waitingForNetwork) && !_isLoadingQuickPicks.value) {
                        loadQuickPicks()
                        if (shelvesEnabled && _shelves.value.isEmpty() && !_isLoadingShelves.value) {
                            shelvesJob = viewModelScope.launch { fetchShelves(reset = true) }
                        }
                    }
                }
            }
        }
        // New picks from Settings › Your music taste refresh Quick Picks right away.
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                settingsRepository.tasteLanguages,
                settingsRepository.tasteGenres,
                settingsRepository.tasteArtists,
            ) { l, g, a -> Triple(l, g, a) }
                .distinctUntilChanged()
                .drop(1)
                .collect { viewModelScope.launch { fetchQuickPicks(automatic = false) } }
        }
    }

    /**
     * Background/automatic load (app start, back online, Home shown again).
     * Shows the saved list, then rebuilds only when [QuickPicksRefreshPolicy]
     * says it's due. Fire-and-forget: nothing is waiting on it.
     */
    fun loadQuickPicks() {
        if (autoJob?.isActive == true) return
        autoJob = viewModelScope.launch { autoRefreshQuickPicks() }
    }

    /** The listener scrolled or tapped Quick Picks; an automatic refresh now waits. */
    fun onQuickPicksTouched() {
        if (_quickPicks.value.isNotEmpty()) quickPicksTouched = true
    }

    /** Home came back on screen: show a held list, and check whether the saved one went stale. */
    fun onHomeShown() {
        pendingQuickPicks?.let { _quickPicks.value = it }
        pendingQuickPicks = null
        quickPicksTouched = false
        if (savedRead) loadQuickPicks()
    }

    private suspend fun showSavedQuickPicks() {
        if (savedRead) return
        val saved = snapshot?.readSaved()
        savedRead = true
        if (saved == null) return
        if (_quickPicks.value.isEmpty() && saved.tracks.isNotEmpty()) _quickPicks.value = saved.tracks
        if (savedMeta == null) savedMeta = saved.meta
    }

    private suspend fun autoRefreshQuickPicks() {
        showSavedQuickPicks()
        val meta = savedMeta
        val decision = QuickPicksRefreshPolicy.decide(
            QuickPicksRefreshPolicy.Inputs(
                nowMs = clock(),
                builtAtMs = meta?.builtAtMs,
                hasList = _quickPicks.value.isNotEmpty(),
                finishedSinceBuild = meta?.let { radioSource?.finishedSince(it.builtAtMs) } ?: 0,
                topArtistsAtBuild = meta?.topArtists.orEmpty(),
                topArtistsNow = if (meta != null) currentTopArtists() else emptyList(),
                network = network(),
            ),
        )
        waitingForNetwork = decision == QuickPicksRefreshPolicy.Decision.WAIT_FOR_NETWORK
        when (decision) {
            QuickPicksRefreshPolicy.Decision.REFRESH -> fetchQuickPicks(automatic = true)
            // Nothing to fetch: the saved list is what Home shows.
            else -> _quickPicksSettled.value = true
        }
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
        viewModelScope.launch { fetchQuickPicks(automatic = false) }
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
                    launch { fetchQuickPicks(automatic = false) }
                    if (shelvesEnabled) launch {
                        shelvesJob?.cancelAndJoin()
                        _isLoadingShelves.value = false
                        fetchShelves(reset = true)
                    }
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


    /**
     * Builds a fresh Quick Picks list. [automatic] refreshes (launch, back
     * online) don't swap a list the listener is already using; they hold
     * it for the next time Home opens. Manual ones always show it.
     */
    private suspend fun fetchQuickPicks(automatic: Boolean) {
        // Opening Home: the last full list (radios included) shows at once,
        // like YouTube Music's own feed, and is replaced when the fresh one
        // is ready.
        showSavedQuickPicks()
        val topArtists = currentTopArtists()
        val queries = personalizedQuickPicksQueries(topArtists)

        // Nothing saved yet: searches cached in the last few minutes stand in
        // while the real refresh runs. Never over a list already showing, so
        // a refresh swaps the list once instead of twice.
        if (_quickPicks.value.isEmpty()) {
            val cachedSets = queries.map { youtubeSearchRepository.cachedResults(it) ?: emptyList() }
            val cachedBlend = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { blend(cachedSets) }
            if (cachedBlend.isNotEmpty()) _quickPicks.value = cachedBlend
        }

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
                val radios = async {
                    val recent = withTimeoutOrNull(5_000L) { libraryRepository.observeRecentlyPlayed(limit = 25).first() }.orEmpty()
                    runCatching { radioSource?.load(recent) }.getOrNull().orEmpty()
                }
                val resultSets = queries.map { query ->
                    async { runCatching { youtubeSearchRepository.search(query) }.getOrDefault(emptyList()) }
                }.awaitAll()
                // Still nothing on screen (first launch): the searches land in
                // a second or two, the radios take longer. Show the searches
                // now; the full blend below replaces them as usual.
                if (_quickPicks.value.isEmpty()) {
                    val early = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { blend(resultSets) }
                    if (early.isNotEmpty()) {
                        _quickPicks.value = early
                        com.whiplash.music.ui.onboarding.OnboardingController.homeReady.value = true
                    }
                }
                // Radios of songs you finish lead; artist/taste searches fill in
                // and keep it fresh.
                val sets = radios.await() + resultSets
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { blend(sets) }
            }
            // Only replace what's showing if the blend actually
            // produced something — an all-queries-failed network
            // blip should leave the previous/cached results visible
            // rather than clearing them, same as the old catch-all
            // behavior.
            if (blended.isNotEmpty()) {
                val full = fillPages(blended)
                val meta = com.whiplash.music.data.repository.QuickPicksSnapshot.Meta(clock(), topArtists)
                if (automatic && quickPicksTouched && _quickPicks.value.isNotEmpty()) {
                    pendingQuickPicks = full
                } else {
                    _quickPicks.value = full
                    pendingQuickPicks = null
                }
                savedMeta = meta
                waitingForNetwork = false
                snapshot?.write(full, meta)
                com.whiplash.music.ui.onboarding.OnboardingController.homeReady.value = true
            }
        } finally {
            _isLoadingQuickPicks.value = false
            _quickPicksSettled.value = true
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
    /**
     * Tops [list] up with popular songs so the grid's pages are all full for
     * every page size offered (3, 6, 9, 12 all divide [QUICK_PICKS_FILL_STEP]).
     * Best effort: if the extra search fails, the list is returned as is.
     */
    private suspend fun fillPages(list: List<PlayableItem.YoutubeTrack>): List<PlayableItem.YoutubeTrack> {
        val target = ((list.size + QUICK_PICKS_FILL_STEP - 1) / QUICK_PICKS_FILL_STEP) * QUICK_PICKS_FILL_STEP
        if (list.size == target) return list
        val seen = list.mapTo(HashSet()) { it.id }
        val dupes = com.whiplash.music.recommend.NearDuplicateFilter().apply { list.forEach { add(it) } }
        val extra = runCatching { youtubeSearchRepository.search(QUICK_PICKS_QUERY) }.getOrDefault(emptyList())
            .filter { seen.add(it.id) && dupes.accept(it) }
        return list + extra.take(target - list.size)
    }

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
        // The same song found by two searches (or as video + lyric + slowed
        // uploads) shows once, as its best upload, where it first appeared.
        // Long-form jukeboxes/compilations don't belong in Quick Picks.
        return blended
            .filter { com.whiplash.music.playback.controller.classifySongLength(it.title) != com.whiplash.music.playback.controller.SongLengthClass.LONG_FORM }
            .withoutNearDuplicates()
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
    private suspend fun personalizedQuickPicksQueries(topArtists: List<String>): List<String> {
        // Onboarding picks fill whatever listening history doesn't cover yet, so
        // Home is personal from the first launch and gradually becomes all
        // history. Nothing picked and nothing played: the generic default.
        val fromHistory = topArtists.map { "$it songs" }
        val fromTaste = tasteQueries()
        if (fromHistory.isEmpty() && fromTaste.isEmpty()) return listOf(QUICK_PICKS_QUERY)
        return (fromHistory + fromTaste).distinct().take(MAX_BLEND_ARTISTS)
    }

    /** The listener's top artists from recent plays, most played first. */
    private suspend fun currentTopArtists(): List<String> {
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
        // Counted by main artist, so "A" and "A & B" are one artist and the
        // search is "A songs" rather than the whole credit line.
        return com.whiplash.music.domain.model.rankArtists(history.map { it.artist }, MAX_BLEND_ARTISTS)
    }

    /**
     * Searches built from the onboarding picks, mixed so each kind is
     * represented: artists ("Arijit Singh songs"), genres in the picked
     * languages ("hindi bollywood hits"), and languages alone ("latest tamil songs").
     * Artists are shuffled so a refresh brings a different mix.
     */
    private suspend fun tasteQueries(): List<String> {
        val languages = settingsRepository.tasteLanguages.first()
        val genres = settingsRepository.tasteGenres.first()
        val artists = settingsRepository.tasteArtists.first()
        val artistQ = artists.shuffled().map { "$it songs" }
        val genreQ = genres.mapIndexedNotNull { i, name ->
            val q = com.whiplash.music.ui.onboarding.OnboardingCatalog.genre(name)?.query ?: return@mapIndexedNotNull null
            val lang = languages.getOrNull(i % languages.size.coerceAtLeast(1))
            if (lang != null && lang != "English") "${lang.lowercase()} $q" else q
        }
        val languageQ = languages.map { "latest ${it.lowercase()} songs" }
        val lists = listOf(artistQ, genreQ, languageQ)
        val out = mutableListOf<String>()
        for (i in 0 until (lists.maxOfOrNull { it.size } ?: 0)) for (l in lists) l.getOrNull(i)?.let { out += it }
        return out
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
        pendingQuickPicks = pendingQuickPicks?.filter { it.id != item.id }
        // So it doesn't come back with the saved list on the next launch.
        val onDisk = pendingQuickPicks ?: _quickPicks.value
        viewModelScope.launch { snapshot?.write(onDisk, savedMeta) }
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

        /** Least common multiple of the grid's page sizes (3, 6, 9, 12). */
        private const val QUICK_PICKS_FILL_STEP = 36
        const val MAX_BLEND_ARTISTS = 5
        const val MAX_SHELF_ARTISTS = 6
        const val MAX_SHELF_ITEMS = 12
    }
}

/** Recent plays read for Speed dial: enough to fill every page after pinned songs. */
private const val SPEED_DIAL_FETCH = 60
