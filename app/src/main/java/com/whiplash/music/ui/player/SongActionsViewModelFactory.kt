// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.whiplash.music.data.download.DownloadManager
import com.whiplash.music.data.repository.LibraryRepository

// Authored by S. Ansari for Whiplash; all rights reserved.
class SongActionsViewModelFactory(
    private val libraryRepository: LibraryRepository,
    private val downloadManager: DownloadManager? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return SongActionsViewModel(libraryRepository, downloadManager) as T
    }
}
