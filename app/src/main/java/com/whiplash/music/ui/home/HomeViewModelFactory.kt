package com.whiplash.music.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.whiplash.music.data.repository.LibraryRepository

class HomeViewModelFactory(
    private val libraryRepository: LibraryRepository,
    private val youtubeSearchRepository: com.whiplash.music.data.repository.YoutubeSearchRepository,
    private val settingsRepository: com.whiplash.music.data.repository.SettingsRepository,
    private val onlineChanges: kotlinx.coroutines.flow.Flow<Boolean>? = null,
    private val radioSource: QuickPicksRadio? = null,
    private val snapshot: com.whiplash.music.data.repository.QuickPicksSnapshot? = null,
    private val speedDialSnapshot: com.whiplash.music.data.repository.SpeedDialSnapshot? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return HomeViewModel(libraryRepository, youtubeSearchRepository, settingsRepository, onlineChanges, radioSource, snapshot, speedDialSnapshot) as T
    }
}
