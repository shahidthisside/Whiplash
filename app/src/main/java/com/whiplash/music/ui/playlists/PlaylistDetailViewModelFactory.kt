package com.whiplash.music.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.whiplash.music.data.repository.LibraryRepository

class PlaylistDetailViewModelFactory(
    private val libraryRepository: LibraryRepository,
    private val playlistId: Long,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return PlaylistDetailViewModel(libraryRepository, playlistId) as T
    }
}
