package com.whiplash.music.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whiplash.music.data.repository.LibraryRepository
import com.whiplash.music.data.repository.YoutubeSearchRepository
import com.whiplash.music.domain.model.Playlist
import com.whiplash.music.ui.common.ToastController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistsViewModel(
    private val libraryRepository: LibraryRepository,
    private val youtubeSearchRepository: YoutubeSearchRepository,
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = libraryRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            libraryRepository.createPlaylist(name)
            ToastController.show("Playlist \"$name\" created")
        }
    }

    fun deletePlaylist(id: Long, name: String) {
        viewModelScope.launch {
            libraryRepository.deletePlaylist(id)
            ToastController.show("Playlist \"$name\" deleted")
        }
    }

    fun renamePlaylist(id: Long, name: String, description: String?) {
        viewModelScope.launch {
            libraryRepository.renamePlaylist(id, name, description)
            ToastController.show("Playlist renamed to \"$name\"")
        }
    }

    /**
     * Imports a YouTube/YouTube Music playlist by URL: extracts its real
     * title and tracks via [YoutubeSearchRepository.importPlaylist], then
     * creates a genuine local playlist and adds every resolved track to
     * it — the same [LibraryRepository.createPlaylist]/[LibraryRepository.addToPlaylist]
     * path a manually-created playlist uses, so an imported playlist is
     * a completely normal playlist afterward (rename/delete/reorder/play
     * all work exactly the same, since there's no separate "imported
     * playlist" concept — just a playlist that happened to be seeded
     * from an import instead of one-at-a-time adds).
     *
     * Never silently produces an empty "playlist" for a genuinely
     * invalid link: a URL that NewPipeExtractor can't resolve at all
     * throws before any playlist is created, surfaced as a plain toast
     * rather than a raw exception message (matching the app's existing
     * "no internet" / "couldn't play this song" error-toast convention).
     * A URL that resolves but whose tracks can't be matched/resolved
     * (e.g. every video since removed) still creates the playlist with
     * its real name, since that reflects what genuinely happened, but
     * reports 0 tracks imported rather than claiming success either way.
     */
    fun importPlaylist(url: String) {
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val imported = youtubeSearchRepository.importPlaylist(url)
                val playlistName = imported.name.ifBlank { "Imported playlist" }
                val playlistId = libraryRepository.createPlaylist(playlistName)
                imported.tracks.forEach { track -> libraryRepository.addToPlaylist(playlistId, track) }
                ToastController.show(
                    if (imported.tracks.isEmpty()) {
                        "Imported \"$playlistName\" but couldn't match any tracks"
                    } else {
                        "Imported \"$playlistName\" (${imported.tracks.size} songs)"
                    },
                )
            } catch (e: Exception) {
                ToastController.show("Couldn't import that playlist link")
            } finally {
                _isImporting.value = false
            }
        }
    }
}
