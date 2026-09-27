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
    private val lyricsCache: com.whiplash.music.data.lyrics.LyricsCache,
    private val lyricsProviderChain: com.whiplash.music.data.lyrics.LyricsProviderChain,
    private val downloadManager: com.whiplash.music.data.download.DownloadManager,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return SettingsViewModel(repository, cacheManager, backupManager, lyricsCache, lyricsProviderChain, downloadManager) as T
    }
}
