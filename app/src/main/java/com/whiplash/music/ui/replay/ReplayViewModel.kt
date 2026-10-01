package com.whiplash.music.ui.replay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.whiplash.music.data.repository.LibraryRepository
import com.whiplash.music.data.repository.SettingsRepository
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.domain.model.ReplaySummary
import com.whiplash.music.domain.model.ReplayTrackStat
import com.whiplash.music.domain.model.buildReplaySummary
import com.whiplash.music.domain.model.defaultReplayMonth
import com.whiplash.music.domain.model.replayMonthKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * 4.7 Monthly Replay. Activity-scoped: Home's Replay card and the Replay
 * story read the same state, so opening the story shows exactly the month
 * the card described.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplayViewModel(
    private val library: LibraryRepository,
    settings: SettingsRepository,
) : ViewModel() {

    /** Null until the setting has loaded. */
    val enabled: StateFlow<Boolean?> = settings.replayEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Re-read on every open/resume so a new month starts without a restart.
    private val currentMonth = MutableStateFlow(replayMonthKey(System.currentTimeMillis()))

    val months: StateFlow<List<String>> = library.observeReplayMonths()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val currentMonthPlays = currentMonth
        .flatMapLatest { library.observeReplayMonth(it) }
        .map { rows -> rows.sumOf { it.plays } }

    /** The month Home's card describes and the story opens on. */
    private val defaultMonth: StateFlow<String?> =
        combine(currentMonth, currentMonthPlays, months) { current, plays, all ->
            defaultReplayMonth(current, plays, all)
        }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Recap for Home's card (null when there's nothing to show yet). */
    val teaser: StateFlow<ReplaySummary?> = defaultMonth
        .flatMapLatest { month -> summaryOf(month) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val pickedMonth = MutableStateFlow<String?>(null)

    val selectedMonth: StateFlow<String?> = combine(pickedMonth, defaultMonth) { picked, default -> picked ?: default }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Recap the story shows. */
    val summary: StateFlow<ReplaySummary?> = selectedMonth
        .flatMapLatest { month -> summaryOf(month) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currentMonthKey: StateFlow<String> = currentMonth

    private fun summaryOf(month: String?) =
        if (month == null) {
            flowOf(null)
        } else {
            // Built off the main thread: Home asks for it while it's starting up.
            library.observeReplayMonth(month).map { buildReplaySummary(month, it) }
                .flowOn(kotlinx.coroutines.Dispatchers.Default)
        }

    fun refreshMonth() {
        currentMonth.value = replayMonthKey(System.currentTimeMillis())
    }

    fun selectMonth(month: String) {
        pickedMonth.value = month
    }

    /** Back to the default month (called when the story closes). */
    fun resetMonth() {
        pickedMonth.value = null
    }

    suspend fun playable(tracks: List<ReplayTrackStat>): List<PlayableItem> = library.resolveReplayTracks(tracks)
}

class ReplayViewModelFactory(
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return ReplayViewModel(library, settings) as T
    }
}
