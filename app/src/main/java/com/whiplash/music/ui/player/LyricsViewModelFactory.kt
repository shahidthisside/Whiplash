package com.whiplash.music.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.whiplash.music.playback.controller.PlaybackController

class LyricsViewModelFactory(
    private val controller: PlaybackController,
    private val providerChain: com.whiplash.music.data.lyrics.LyricsProviderChain,
    private val settingsRepository: com.whiplash.music.data.repository.SettingsRepository,
    private val lyricOffsetStore: com.whiplash.music.data.repository.LyricOffsetStore,
    private val lyricsCache: com.whiplash.music.data.lyrics.LyricsCache,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return LyricsViewModel(controller, providerChain, settingsRepository, lyricOffsetStore, lyricsCache) as T
    }
}
