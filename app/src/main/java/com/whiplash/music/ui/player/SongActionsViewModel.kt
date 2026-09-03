package com.whiplash.music.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whiplash.music.data.download.DownloadManager
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
class SongActionsViewModel(
    private val libraryRepository: LibraryRepository,
    private val downloadManager: DownloadManager? = null,
) : ViewModel() {

    fun toggleFavorite(item: PlayableItem, isCurrentlyFavorite: Boolean) {
        viewModelScope.launch {
            libraryRepository.toggleFavorite(item, isCurrentlyFavorite)
            ToastController.show(if (isCurrentlyFavorite) "Removed from favorites" else "Added to favorites")
        }
    }

    fun addToPlaylist(item: PlayableItem, playlistId: Long, playlistName: String) {
        viewModelScope.launch {
            val added = libraryRepository.addToPlaylist(playlistId, item)
            ToastController.show(if (added) "Added to $playlistName" else "Already in $playlistName")
        }
    }

    fun createPlaylistAndAdd(name: String, item: PlayableItem) {
        viewModelScope.launch {
            val id = libraryRepository.createPlaylist(name)
            libraryRepository.addToPlaylist(id, item)
            ToastController.show("Added to $name")
        }
    }

    /**
     * Removes [item] from the playlist currently being viewed —
     * [PlaylistDetailScreen]'s "Remove from playlist" action (shown in
     * place of "Add to playlist" for a song already known to be inside
     * the playlist the actions sheet was opened from).
     */
    fun removeFromPlaylist(playlistId: Long, playlistName: String, item: PlayableItem) {
        viewModelScope.launch {
            libraryRepository.removeFromPlaylist(playlistId, item)
            ToastController.show("Removed from $playlistName")
        }
    }

    /**
     * Moves [item] from the playlist currently being viewed to
     * [toPlaylistId] — [PlaylistDetailScreen]'s "Move to other playlist"
     * action. Removal from the source playlist always runs regardless of
     * whether the target add was a genuinely new row or a no-op (the
     * song was already there) — either way the postcondition ("item is
     * now in the target playlist, and not in the source one") holds.
     */
    fun moveToPlaylist(fromPlaylistId: Long, toPlaylistId: Long, toPlaylistName: String, item: PlayableItem) {
        viewModelScope.launch {
            val added = libraryRepository.moveToPlaylist(fromPlaylistId, toPlaylistId, item)
            ToastController.show(if (added) "Moved to $toPlaylistName" else "Already in $toPlaylistName — removed from here")
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

    /**
     * Removes [item] from history only (History screen's per-item action)
     * — distinct from [removeFromSpeedDial]: this never touches pinned
     * status, only the play-history record.
     */
    fun removeFromHistory(item: PlayableItem) {
        viewModelScope.launch {
            libraryRepository.removeFromHistory(item)
            ToastController.show("Removed from history")
        }
    }

    /**
     * Deletes a downloaded track's audio/artwork files and its Room row
     * (Library > Downloads). Takes a plain id rather than
     * [PlayableItem.DownloadedTrack] so this can be triggered from *any*
     * screen showing an already-downloaded song — Search, Home, Library,
     * Favorites, Playlists — not just the Downloads tab itself.
     */
    fun removeDownload(id: String) {
        viewModelScope.launch {
            downloadManager?.removeDownload(id)
            ToastController.show("Download removed")
        }
    }
}
