package com.whiplash.music.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whiplash.music.data.repository.LibraryRepository
import com.whiplash.music.domain.model.PlayableItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class FavoritesViewModel(libraryRepository: LibraryRepository) : ViewModel() {
    val favorites: StateFlow<List<PlayableItem>> = libraryRepository.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
