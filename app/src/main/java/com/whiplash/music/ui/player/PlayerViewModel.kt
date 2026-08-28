package com.whiplash.music.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whiplash.music.data.repository.LibraryRepository
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.playback.controller.PlaybackController
import com.whiplash.music.playback.controller.PlaybackState
import com.whiplash.music.playback.controller.RepeatMode
import com.whiplash.music.playback.controller.SleepTimerMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Thin UI-facing wrapper around [PlaybackController] (section 12: ViewModel
 * depends on PlaybackController, never on MediaController/ExoPlayer).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(
    private val controller: PlaybackController,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    val state: StateFlow<PlaybackState> = controller.state

    /** Whether the currently playing track is favorited, reactive to both track changes and favorite toggles. */
    val isCurrentFavorite: StateFlow<Boolean> = state
        .flatMapLatest { s ->
            s.currentItem?.let { libraryRepository.observeIsFavorite(it) }
                ?: kotlinx.coroutines.flow.flowOf(false)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun togglePlayPause() = controller.togglePlayPause()

    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)

    fun seekToNext() = controller.seekToNext()

    fun seekToPrevious() = controller.seekToPrevious()

    fun toggleShuffle() = controller.setShuffleEnabled(!controller.state.value.shuffleEnabled)

    fun cycleRepeatMode() {
        val next = when (controller.state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        controller.setRepeatMode(next)
    }

    fun toggleFavoriteCurrent() {
        val item = state.value.currentItem ?: return
        viewModelScope.launch {
            libraryRepository.toggleFavorite(item, isCurrentlyFavorite = isCurrentFavorite.value)
        }
    }

    fun playQueueItem(index: Int) {
        val queue = state.value.queue
        if (index !in queue.indices) return
        controller.playQueue(queue, index)
    }

    fun removeFromQueue(index: Int) = controller.removeFromQueue(index)

    fun moveInQueue(from: Int, to: Int) = controller.moveInQueue(from, to)

    fun clearQueueExceptCurrent() = controller.clearQueueExceptCurrent()

    fun addToQueue(item: PlayableItem) = controller.addToQueue(item)

    fun playNext(item: PlayableItem) = controller.playNext(item)

    fun setSleepTimer(mode: SleepTimerMode?) = controller.setSleepTimer(mode)
}
