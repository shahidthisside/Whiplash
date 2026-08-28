package com.whiplash.music.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.whiplash.music.data.repository.LibraryRepository
import com.whiplash.music.playback.controller.PlaybackController

class PlayerViewModelFactory(
    private val controller: PlaybackController,
    private val libraryRepository: LibraryRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return PlayerViewModel(controller, libraryRepository) as T
    }
}
