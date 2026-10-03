// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whiplash.music.domain.model.YoutubeArtistDetail
import com.whiplash.music.domain.model.toUserFacingMessage
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
    private val searchRepository: com.whiplash.music.data.repository.YoutubeSearchRepository? = null,
) : ViewModel() {

    private val _state = MutableStateFlow<ArtistDetailUiState>(ArtistDetailUiState.Loading)
    val state: StateFlow<ArtistDetailUiState> = _state

    /**
     * Albums found by searching the artist's name, for channels that expose
     * no albums tab (YouTube Music's "Artist - Topic" channels expose no
     * tabs at all). Only albums credited to this artist are kept. Loaded
     * after the page is up, so it never delays it.
     */
    private val _foundAlbums = MutableStateFlow<List<com.whiplash.music.domain.model.YoutubePlaylistResult>>(emptyList())
    val foundAlbums: StateFlow<List<com.whiplash.music.domain.model.YoutubePlaylistResult>> = _foundAlbums

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = ArtistDetailUiState.Loading
            try {
                val detail = detailProvider.getArtistDetail(channelUrl)
                _state.value = ArtistDetailUiState.Loaded(detail)
                if (detail.albums.isEmpty()) findAlbums(detail.name)
            } catch (e: Exception) {
                _state.value = ArtistDetailUiState.Error(e.toUserFacingMessage("Couldn't load this artist"))
            }
        }
    }

    private fun findAlbums(channelName: String) {
        val repo = searchRepository ?: return
        val name = channelName.removeSuffix(" - Topic").trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            val results = runCatching { repo.searchAlbums(name) }.getOrNull().orEmpty()
            _foundAlbums.value = results.filter { it.uploaderName?.contains(name, ignoreCase = true) == true }
        }
    }
}
