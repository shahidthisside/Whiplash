package com.whiplash.music.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.whiplash.music.data.repository.YoutubeSearchRepository

class SearchViewModelFactory(private val repository: YoutubeSearchRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return SearchViewModel(repository) as T
    }
}
