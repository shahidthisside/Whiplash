package com.whiplash.music.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.whiplash.music.data.backup.BackupManager
import com.whiplash.music.data.repository.SettingsRepository
import com.whiplash.music.playback.cache.AudioCacheManager

class SettingsViewModelFactory(
    private val repository: SettingsRepository,
    private val cacheManager: AudioCacheManager,
    private val backupManager: BackupManager,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return SettingsViewModel(repository, cacheManager, backupManager) as T
    }
}
