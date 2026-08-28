package com.whiplash.music.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whiplash.music.data.repository.SettingsRepository
import com.whiplash.music.domain.model.AudioQuality
import com.whiplash.music.ui.theme.ThemeVariant
import com.whiplash.music.ui.theme.WhiplashColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val audioQuality: StateFlow<AudioQuality> = repository.audioQuality
        .stateIn(viewModelScope, SharingStarted.Eagerly, AudioQuality.AUTO)

    val autoplayEnabled: StateFlow<Boolean> = repository.autoplayEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val gaplessEnabled: StateFlow<Boolean> = repository.gaplessEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val crossfadeDurationMs: StateFlow<Int> = repository.crossfadeDurationMs
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val playbackSpeed: StateFlow<Float> = repository.playbackSpeed
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)

    val themeVariant: StateFlow<ThemeVariant> = repository.themeVariant
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeVariant.CLASSIC)

    // Theme application to the live WhiplashColors state now happens
    // eagerly at true app startup (see WhiplashApplication.onCreate) so
    // the correct theme is live before this screen — or any screen — is
    // ever opened, rather than only once this ViewModel happens to be
    // created.

    fun setAudioQuality(quality: AudioQuality) {
        viewModelScope.launch { repository.setAudioQuality(quality) }
    }

    fun setAutoplayEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoplayEnabled(enabled) }
    }

    fun setGaplessEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setGaplessEnabled(enabled) }
    }

    fun setCrossfadeDurationMs(ms: Int) {
        viewModelScope.launch { repository.setCrossfadeDurationMs(ms) }
    }

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch { repository.setPlaybackSpeed(speed) }
    }

    fun setThemeVariant(variant: ThemeVariant) {
        WhiplashColors.applyVariant(variant) // instant visual feedback, before the DataStore write completes
        viewModelScope.launch { repository.setThemeVariant(variant) }
    }
}
