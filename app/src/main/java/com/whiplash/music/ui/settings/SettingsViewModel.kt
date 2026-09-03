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
    private val backupManager: com.whiplash.music.data.backup.BackupManager,
) : ViewModel() {

    /**
     * Null until the real persisted value has been read from DataStore at
     * least once — [SettingsScreen] uses this (not a hardcoded default) to
     * decide whether to render the Audio Quality selector at all yet. Using
     * a hardcoded fallback like [AudioQuality.AUTO] as the initial value
     * here (the previous approach) meant every fresh app launch briefly
     * rendered "Auto" selected and then visibly jumped to the user's real
     * saved setting (e.g. "High") the instant DataStore's first real
     * emission arrived a frame or two later — a real, reported UI glitch,
     * not just a theoretical race. Deferring the selector's first render
     * until this is non-null eliminates the flash entirely rather than
     * just making the window narrower.
     */
    val audioQuality: StateFlow<AudioQuality?> = repository.audioQuality
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Same null-until-loaded pattern as [audioQuality] above, for the same reason (avoids a flash of the wrong selected option on first render). */
    val downloadQuality: StateFlow<AudioQuality?> = repository.downloadQuality
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

    /** Selected full-player seek bar visual style (see SettingsRepository.seekBarStyle doc). */
    val seekBarStyle: StateFlow<com.whiplash.music.ui.theme.SeekBarStyle> = repository.seekBarStyle
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.whiplash.music.ui.theme.SeekBarStyle.CLASSIC)

    fun setSeekBarStyle(style: com.whiplash.music.ui.theme.SeekBarStyle) {
        viewModelScope.launch { repository.setSeekBarStyle(style) }
    }

    /** Whether resolved YouTube streams are cached to disk (see SettingsRepository.audioCacheEnabled doc). */
    val audioCacheEnabled: StateFlow<Boolean> = repository.audioCacheEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Epoch millis of the last successful manual backup, null if never backed up (see SettingsRepository doc). */
    val lastBackupTimeMs: StateFlow<Long?> = repository.lastBackupTimeMs
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** One-shot result of the most recent backup/restore attempt, for a Toast/snackbar — never raw exception text (section: keep user-facing errors simple). */
    private val _backupResult = MutableStateFlow<BackupResult?>(null)
    val backupResult: StateFlow<BackupResult?> = _backupResult

    fun onBackupResultShown() {
        _backupResult.value = null
    }

    /** Writes a full backup zip to [destination] (a Uri from the system "Save as" picker). */
    fun backup(destination: android.net.Uri) {
        viewModelScope.launch {
            val success = backupManager.backup(destination)
            if (success) {
                repository.setLastBackupTimeMs(System.currentTimeMillis())
            }
            _backupResult.value = if (success) BackupResult.BackupSuccess else BackupResult.BackupFailed
        }
    }

    /**
     * Restores from [source] (a Uri from the system "Open" picker). The
     * caller (SettingsScreen) is responsible for fully restarting the app
     * process right after a successful result — see [BackupManager.restore]'s
     * doc for why a live Room connection can't just keep running against a
     * file that was swapped out from underneath it.
     */
    fun restore(source: android.net.Uri, onRestored: () -> Unit) {
        viewModelScope.launch {
            val success = backupManager.restore(source)
            if (success) {
                onRestored()
            } else {
                _backupResult.value = BackupResult.RestoreFailed
            }
        }
    }

    enum class BackupResult { BackupSuccess, BackupFailed, RestoreFailed }

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
            com.whiplash.music.ui.common.ToastController.show("Cache cleared")
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

    fun setDownloadQuality(quality: AudioQuality) {
        viewModelScope.launch { repository.setDownloadQuality(quality) }
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
