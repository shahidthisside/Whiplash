package com.whiplash.music.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whiplash.music.data.repository.LibraryRepository
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.ui.common.ToastController
import kotlinx.coroutines.launch

/**
 * Small shared ViewModel for song-action operations (favorite toggling,
 * queue insertion) triggered from long-press sheets across multiple
 * screens (Search/Local Library/Home/Favorites), so those call sites don't
 * need to launch coroutines directly against a raw repository reference.
 *
 * Each action posts a brief confirmation via [ToastController] (section:
 * user feedback for actions that were previously silent) — every one of
 * these is a background-only state change with no other visible UI
 * effect, so without this a user has no way to tell an action actually
 * registered.
 */
class SongActionsViewModel(private val libraryRepository: LibraryRepository) : ViewModel() {

    fun toggleFavorite(item: PlayableItem, isCurrentlyFavorite: Boolean) {
        viewModelScope.launch {
            libraryRepository.toggleFavorite(item, isCurrentlyFavorite)
            ToastController.show(if (isCurrentlyFavorite) "Removed from favorites" else "Added to favorites")
        }
    }

    fun addToPlaylist(item: PlayableItem, playlistId: Long, playlistName: String) {
        viewModelScope.launch {
            libraryRepository.addToPlaylist(playlistId, item)
            ToastController.show("Added to $playlistName")
        }
    }

    fun createPlaylistAndAdd(name: String, item: PlayableItem) {
        viewModelScope.launch {
            val id = libraryRepository.createPlaylist(name)
            libraryRepository.addToPlaylist(id, item)
            ToastController.show("Added to $name")
        }
    }

    fun togglePinned(item: PlayableItem, isCurrentlyPinned: Boolean) {
        viewModelScope.launch {
            libraryRepository.togglePinned(item, isCurrentlyPinned)
            ToastController.show(if (isCurrentlyPinned) "Unpinned from Speed dial" else "Pinned to Speed dial")
        }
    }

    fun removeFromSpeedDial(item: PlayableItem) {
        viewModelScope.launch {
            libraryRepository.removeFromSpeedDial(item)
            ToastController.show("Removed from Speed dial")
        }
    }
}
