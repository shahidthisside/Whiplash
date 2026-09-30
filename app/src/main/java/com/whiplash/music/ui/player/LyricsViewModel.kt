package com.whiplash.music.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whiplash.music.data.lyrics.LyricsCache
import com.whiplash.music.data.lyrics.LyricsLookup
import com.whiplash.music.data.lyrics.LyricsProviderChain
import com.whiplash.music.domain.model.LyricsResult
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.playback.controller.PlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * Drives the lyrics sheet (CLAUDE.md section 20). Reactively re-fetches
 * whenever the currently playing track changes (keyed on source+id, not
 * the whole [PlayableItem], so an artwork-only metadata refresh doesn't
 * needlessly re-hit the network). Results go through [LyricsCache] (memory
 * LRU + GZIP disk), so replays — even after a restart or offline — don't refetch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LyricsViewModel(
    private val controller: PlaybackController,
    private val providerChain: LyricsProviderChain,
    private val settingsRepository: com.whiplash.music.data.repository.SettingsRepository,
    private val lyricOffsetStore: com.whiplash.music.data.repository.LyricOffsetStore,
    private val lyricsCache: LyricsCache,
) : ViewModel() {

    /** Current lookup (result + which provider found it); null while loading or with nothing playing. */
    private val lookup: StateFlow<LyricsLookup?> = kotlinx.coroutines.flow.combine(
        controller.state
            .map { it.currentItem }
            .distinctUntilChanged { old, new -> trackKey(old) == trackKey(new) },
        settingsRepository.lyricsSource.distinctUntilChanged(),
    ) { item, source -> item to source }
        .flatMapLatest { (item, source) ->
            if (item == null) {
                flowOf<LyricsLookup?>(null)
            } else {
                // The source preference is part of the key, so switching providers
                // shows that provider's answer rather than a cached one from another.
                val key = "${source.name}:${item.source}:${item.id}"
                kotlinx.coroutines.flow.flow<LyricsLookup?> {
                    // Memory hit is instant; a disk hit is a small GZIP read, off the main thread.
                    val cached = withContext(Dispatchers.IO) { lyricsCache.get(key) }
                    if (cached != null) {
                        emit(cached)
                    } else {
                        emit(null) // Loading — represented as null, distinct from a real Unavailable result.
                        val result = providerChain.lookup(source, item.title, item.artist, item.durationMs)
                        withContext(Dispatchers.IO) { lyricsCache.put(key, result) }
                        emit(result)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val lyrics: StateFlow<LyricsResult?> = lookup
        .map { it?.result }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * The listener's timing offset for the current track's synced lyrics
     * (see [com.whiplash.music.domain.model.normalizeLyricOffsetMs] for the
     * sign convention). Keyed on the bare track id rather than source+id, so
     * a song keeps its correction after it is downloaded and plays from the
     * device instead of the stream: the audio, and so the timing, is the same.
     */
    val lyricOffsetMs: StateFlow<Long> = kotlinx.coroutines.flow.combine(
        controller.state.map { it.currentItem?.id }.distinctUntilChanged(),
        lyricOffsetStore.offsets,
    ) { id, offsets -> id?.let { offsets[it] } ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** Nudges the current track's offset by [deltaMs] (clamped and snapped). */
    fun adjustLyricOffset(deltaMs: Long) {
        val id = controller.state.value.currentItem?.id ?: return
        lyricOffsetStore.setOffset(id, lyricOffsetStore.offsetFor(id) + deltaMs)
    }

    /** Clears the current track's offset back to the LRC file's own timing. */
    fun resetLyricOffset() {
        val id = controller.state.value.currentItem?.id ?: return
        lyricOffsetStore.setOffset(id, 0L)
    }

    private fun trackKey(item: PlayableItem?): String? = item?.let { "${it.source}:${it.id}" }

}
