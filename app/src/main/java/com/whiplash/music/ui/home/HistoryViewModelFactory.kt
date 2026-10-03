// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.whiplash.music.data.repository.LibraryRepository

class HistoryViewModelFactory(
    private val libraryRepository: LibraryRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return HistoryViewModel(libraryRepository) as T
    }
}
