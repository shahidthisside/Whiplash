// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.whiplash.music.data.repository.LibraryRepository
import com.whiplash.music.data.repository.YoutubeSearchRepository

class PlaylistsViewModelFactory(
    private val libraryRepository: LibraryRepository,
    private val youtubeSearchRepository: YoutubeSearchRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return PlaylistsViewModel(libraryRepository, youtubeSearchRepository) as T
    }
}
