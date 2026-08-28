package com.whiplash.music.ui.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whiplash.music.domain.model.YoutubeArtistDetail
import com.whiplash.music.playback.provider.newpipe.YoutubeDetailProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ArtistDetailUiState {
    data object Loading : ArtistDetailUiState
    data class Loaded(val detail: YoutubeArtistDetail) : ArtistDetailUiState
    data class Error(val message: String) : ArtistDetailUiState
}

class ArtistDetailViewModel(
    private val detailProvider: YoutubeDetailProvider,
    private val channelUrl: String,
) : ViewModel() {

    private val _state = MutableStateFlow<ArtistDetailUiState>(ArtistDetailUiState.Loading)
    val state: StateFlow<ArtistDetailUiState> = _state

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = ArtistDetailUiState.Loading
            try {
                _state.value = ArtistDetailUiState.Loaded(detailProvider.getArtistDetail(channelUrl))
            } catch (e: Exception) {
                _state.value = ArtistDetailUiState.Error(e.message ?: "Couldn't load this artist")
            }
        }
    }
}
