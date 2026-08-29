package com.whiplash.music.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whiplash.music.data.repository.SettingsRepository
import com.whiplash.music.domain.model.AudioQuality
import com.whiplash.music.playback.cache.AudioCacheManager
import com.whiplash.music.ui.theme.ThemeVariant
import com.whiplash.music.ui.theme.WhiplashColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val cacheManager: AudioCacheManager,
) : ViewModel() {

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

    /** Whether resolved YouTube streams are cached to disk (see SettingsRepository.audioCacheEnabled doc). */
    val audioCacheEnabled: StateFlow<Boolean> = repository.audioCacheEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Real current on-disk cache size, refreshed on load and after Clear cache — not an estimate. */
    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes

    init {
        refreshCacheSize()
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            _cacheSizeBytes.value = withContext(Dispatchers.IO) { cacheManager.currentCacheSizeBytes() }
        }
    }

    fun setAudioCacheEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setAudioCacheEnabled(enabled) }
    }

    /** Real "Clear cache" action (same as Spotify's Storage settings) — deletes every cached byte on disk. */
    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { cacheManager.clearCache() }
            refreshCacheSize()
        }
    }

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
