package com.whiplash.music.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.whiplash.music.data.repository.LibraryRepository

class HomeViewModelFactory(
    private val libraryRepository: LibraryRepository,
    private val youtubeSearchRepository: com.whiplash.music.data.repository.YoutubeSearchRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return HomeViewModel(libraryRepository, youtubeSearchRepository) as T
    }
}
