package com.whiplash.music.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whiplash.music.data.repository.LibraryRepository
import com.whiplash.music.domain.model.PlayableItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Full play history (section 31), reached from Speed dial's new "History"
 * button. Speed dial itself only ever shows a curated 3x3 grid (pinned
 * tracks + the most recent 9), so this is the same underlying history data
 * — just with a much higher limit — rather than a separate data source.
 */
class HistoryViewModel(private val libraryRepository: LibraryRepository) : ViewModel() {

    val history: StateFlow<List<PlayableItem>> = libraryRepository.observeRecentlyPlayed(limit = 200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Clears the same underlying `history` table Speed dial's own "Clear"
     * button clears (see HomeScreen's showClearSpeedDialConfirm) — Speed
     * dial is just a windowed view of this same history, not a separate
     * store, so there is deliberately only one clear action, not two
     * independent ones that could drift out of sync with each other.
     */
    fun clearHistory() {
        viewModelScope.launch { libraryRepository.clearHistory() }
    }
}
