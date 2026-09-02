package com.whiplash.music.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.whiplash.music.data.download.DownloadManager
import com.whiplash.music.data.repository.LibraryRepository

class SongActionsViewModelFactory(
    private val libraryRepository: LibraryRepository,
    private val downloadManager: DownloadManager? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return SongActionsViewModel(libraryRepository, downloadManager) as T
    }
}
