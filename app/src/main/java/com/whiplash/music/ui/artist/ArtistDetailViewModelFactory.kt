package com.whiplash.music.ui.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.whiplash.music.playback.provider.newpipe.YoutubeDetailProvider

class ArtistDetailViewModelFactory(
    private val detailProvider: YoutubeDetailProvider,
    private val channelUrl: String,
    private val searchRepository: com.whiplash.music.data.repository.YoutubeSearchRepository? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return ArtistDetailViewModel(detailProvider, channelUrl, searchRepository) as T
    }
}
