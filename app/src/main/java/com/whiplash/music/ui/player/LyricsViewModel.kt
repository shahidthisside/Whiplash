package com.whiplash.music.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whiplash.music.domain.model.LyricsResult
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.playback.controller.PlaybackController
import com.whiplash.music.playback.provider.lrclib.LrcLibProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Drives the lyrics sheet (CLAUDE.md section 20). Reactively re-fetches
 * whenever the currently playing track changes (keyed on source+id, not
 * the whole [PlayableItem], so an artwork-only metadata refresh doesn't
 * needlessly re-hit the network) and caches per-track results in memory
 * for the lifetime of this ViewModel so reopening the sheet for the same
 * track during one playback session doesn't refetch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LyricsViewModel(
    controller: PlaybackController,
    private val lrcLibProvider: LrcLibProvider,
) : ViewModel() {

    private val cache = mutableMapOf<String, LyricsResult>()

    val lyrics: StateFlow<LyricsResult?> = controller.state
        .map { it.currentItem }
        .distinctUntilChanged { old, new -> trackKey(old) == trackKey(new) }
        .flatMapLatest { item ->
            if (item == null) {
                flowOf(null)
            } else {
                val key = "${item.source}:${item.id}"
                val cached = cache[key]
                if (cached != null) {
                    flowOf(cached)
                } else {
                    kotlinx.coroutines.flow.flow {
                        emit(null) // Loading — represented as null, distinct from a real Unavailable result.
                        val result = lrcLibProvider.getLyrics(item.title, item.artist, item.durationMs)
                        cache[key] = result
                        emit(result)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun trackKey(item: PlayableItem?): String? = item?.let { "${it.source}:${it.id}" }
}
