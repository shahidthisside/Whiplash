package com.whiplash.music.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whiplash.music.data.repository.LibraryRepository
import com.whiplash.music.domain.model.PlayableItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class PlaylistDetailViewModel(
    libraryRepository: LibraryRepository,
    playlistId: Long,
) : ViewModel() {

    val tracks: StateFlow<List<PlayableItem>> = libraryRepository.observePlaylistTracks(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // A removeAt(position) helper used to live here and had zero call sites —
    // playlist track removal really happens through
    // SongActionsViewModel.removeFromPlaylist (the long-press sheet's
    // PlaylistContext path). It was removed rather than left as dead code that
    // implies a second, unused removal route.
}
