package com.whiplash.music.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whiplash.music.data.repository.LibraryRepository
import com.whiplash.music.domain.model.PlayableItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistDetailViewModel(
    private val libraryRepository: LibraryRepository,
    private val playlistId: Long,
) : ViewModel() {

    val tracks: StateFlow<List<PlayableItem>> = libraryRepository.observePlaylistTracks(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun removeAt(position: Int) {
        viewModelScope.launch { libraryRepository.removeFromPlaylistAt(playlistId, position) }
    }
}
