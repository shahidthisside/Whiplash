package com.whiplash.music.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.whiplash.music.playback.controller.PlaybackController
import com.whiplash.music.playback.provider.lrclib.LrcLibProvider

class LyricsViewModelFactory(
    private val controller: PlaybackController,
    private val lrcLibProvider: LrcLibProvider,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return LyricsViewModel(controller, lrcLibProvider) as T
    }
}
