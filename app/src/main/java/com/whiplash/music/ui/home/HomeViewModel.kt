package com.whiplash.music.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whiplash.music.data.repository.LibraryRepository
import com.whiplash.music.data.repository.YoutubeSearchRepository
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.ui.common.ToastController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Home screen data (section 31). Only shows sections backed by real data:
 * Recently Played (from actual playback history) and Quick Picks (a real
 * YouTube Music search for popular/trending-style content — NewPipeExtractor
 * does not currently expose an actual YouTube Trending/Charts kiosk, per
 * section 73 "never claim a feature is supported until the current
 * provider actually implements it": YouTube removed the general Trending
 * page from its own interface in July 2025, and NewPipeExtractor's kiosk
 * support for it is documented as deprecated/unreliable as a result — a
 * dedicated "Charts" section using that API would be a fake feature).
 */
class HomeViewModel(
    private val libraryRepository: LibraryRepository,
    private val youtubeSearchRepository: YoutubeSearchRepository,
) : ViewModel() {

    val recentlyPlayed: StateFlow<List<PlayableItem>> = libraryRepository.observeRecentlyPlayed(limit = 25)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * YouTube-Music-style "Speed dial" (a 3x3 grid of artwork, section 31).
     * Pinned tracks (explicitly pinned via the 3-dot menu, section 51) are
     * shown first and stay until unpinned — real persisted state, not a
     * fake toggle — filling any remaining slots with the most recently
     * played tracks that aren't already pinned.
     */
    val speedDial: StateFlow<List<PlayableItem>> = kotlinx.coroutines.flow.combine(
        libraryRepository.observePinned(),
        recentlyPlayed,
    ) { pinned, recent ->
        val pinnedIds = pinned.map { it.source to it.id }.toSet()
        (pinned + recent.filter { (it.source to it.id) !in pinnedIds }).take(9)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _quickPicks = MutableStateFlow<List<PlayableItem.YoutubeTrack>>(emptyList())
    val quickPicks: StateFlow<List<PlayableItem.YoutubeTrack>> = _quickPicks

    private val _isLoadingQuickPicks = MutableStateFlow(false)
    val isLoadingQuickPicks: StateFlow<Boolean> = _isLoadingQuickPicks

    init {
        loadQuickPicks()
    }

    fun loadQuickPicks() {
        viewModelScope.launch {
            val cached = youtubeSearchRepository.cachedResults(QUICK_PICKS_QUERY)
            if (cached != null) _quickPicks.value = cached

            _isLoadingQuickPicks.value = true
            try {
                _quickPicks.value = youtubeSearchRepository.search(QUICK_PICKS_QUERY)
            } catch (_: Exception) {
                // Leave whatever cached/previous results were already showing.
            } finally {
                _isLoadingQuickPicks.value = false
            }
        }
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
    }
}
