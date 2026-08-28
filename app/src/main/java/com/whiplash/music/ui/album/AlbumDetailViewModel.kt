package com.whiplash.music.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whiplash.music.domain.model.YoutubePlaylistDetail
import com.whiplash.music.playback.provider.newpipe.YoutubeDetailProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface AlbumDetailUiState {
    data object Loading : AlbumDetailUiState
    data class Loaded(val detail: YoutubePlaylistDetail) : AlbumDetailUiState
    data class Error(val message: String) : AlbumDetailUiState
}

class AlbumDetailViewModel(
    private val detailProvider: YoutubeDetailProvider,
    private val url: String,
) : ViewModel() {

    private val _state = MutableStateFlow<AlbumDetailUiState>(AlbumDetailUiState.Loading)
    val state: StateFlow<AlbumDetailUiState> = _state

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = AlbumDetailUiState.Loading
            try {
                _state.value = AlbumDetailUiState.Loaded(detailProvider.getPlaylistDetail(url))
            } catch (e: Exception) {
                _state.value = AlbumDetailUiState.Error(e.message ?: "Couldn't load this album")
            }
        }
    }
}
