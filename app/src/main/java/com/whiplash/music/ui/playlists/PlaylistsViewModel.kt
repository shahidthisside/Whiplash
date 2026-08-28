package com.whiplash.music.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whiplash.music.data.repository.LibraryRepository
import com.whiplash.music.domain.model.Playlist
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistsViewModel(private val libraryRepository: LibraryRepository) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = libraryRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createPlaylist(name: String) {
        viewModelScope.launch { libraryRepository.createPlaylist(name) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { libraryRepository.deletePlaylist(id) }
    }
}
