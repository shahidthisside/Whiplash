package com.whiplash.music.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whiplash.music.data.repository.LibraryRepository
import com.whiplash.music.domain.model.PlayableItem
import kotlinx.coroutines.launch

/**
 * Small shared ViewModel for song-action operations (favorite toggling,
 * queue insertion) triggered from long-press sheets across multiple
 * screens (Search/Local Library/Home/Favorites), so those call sites don't
 * need to launch coroutines directly against a raw repository reference.
 */
class SongActionsViewModel(private val libraryRepository: LibraryRepository) : ViewModel() {

    fun toggleFavorite(item: PlayableItem, isCurrentlyFavorite: Boolean) {
        viewModelScope.launch {
            libraryRepository.toggleFavorite(item, isCurrentlyFavorite)
        }
    }

    fun addToPlaylist(item: PlayableItem, playlistId: Long) {
        viewModelScope.launch {
            libraryRepository.addToPlaylist(playlistId, item)
        }
    }

    fun createPlaylistAndAdd(name: String, item: PlayableItem) {
        viewModelScope.launch {
            val id = libraryRepository.createPlaylist(name)
            libraryRepository.addToPlaylist(id, item)
        }
    }

    fun togglePinned(item: PlayableItem, isCurrentlyPinned: Boolean) {
        viewModelScope.launch {
            libraryRepository.togglePinned(item, isCurrentlyPinned)
        }
    }

    fun removeFromSpeedDial(item: PlayableItem) {
        viewModelScope.launch {
            libraryRepository.removeFromSpeedDial(item)
        }
    }
}
