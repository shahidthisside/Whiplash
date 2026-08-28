package com.whiplash.music.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.whiplash.music.playback.provider.newpipe.YoutubeDetailProvider

class AlbumDetailViewModelFactory(
    private val detailProvider: YoutubeDetailProvider,
    private val url: String,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return AlbumDetailViewModel(detailProvider, url) as T
    }
}
