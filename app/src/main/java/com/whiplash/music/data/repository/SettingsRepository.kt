package com.whiplash.music.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.whiplash.music.domain.model.AudioQuality
import com.whiplash.music.ui.theme.ThemeVariant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "whiplash_settings")

/**
 * Persists real, implemented user settings (CLAUDE.md section 59: "never
 * show a setting that is not implemented"). Backed by DataStore Preferences
 * (a Phase 1 dependency, unused until now).
 *
 * Starts with [audioQuality] (section 61); extended with [autoplayEnabled]
 * (section 22: "autoplay must be user-controllable"), [themeVariant]
 * (section 59 Appearance), [crossfadeDurationMs]/[gaplessEnabled]/
 * [playbackSpeed] (section 18) as those features are actually built —
 * settings are added here only once their underlying capability exists,
 * never speculatively.
 */
class SettingsRepository(context: Context) {

    private val dataStore = context.settingsDataStore

    val audioQuality: Flow<AudioQuality> = dataStore.data.map { prefs ->
        prefs[AUDIO_QUALITY_KEY]?.let { stored ->
            runCatching { AudioQuality.valueOf(stored) }.getOrNull()
        } ?: AudioQuality.AUTO
    }

    suspend fun setAudioQuality(quality: AudioQuality) {
        dataStore.edit { prefs -> prefs[AUDIO_QUALITY_KEY] = quality.name }
    }

    /** Whether to auto-extend the queue with related tracks when it runs low (section 13/22). Defaults on. */
    val autoplayEnabled: Flow<Boolean> = dataStore.data.map { prefs -> prefs[AUTOPLAY_KEY] ?: true }

    suspend fun setAutoplayEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[AUTOPLAY_KEY] = enabled }
    }

    /** Selected Appearance theme (section 59). Defaults to the original Classic Graphite palette. */
    val themeVariant: Flow<ThemeVariant> = dataStore.data.map { prefs ->
        prefs[THEME_KEY]?.let { stored ->
            runCatching { ThemeVariant.valueOf(stored) }.getOrNull()
        } ?: ThemeVariant.CLASSIC
    }

    suspend fun setThemeVariant(variant: ThemeVariant) {
        dataStore.edit { prefs -> prefs[THEME_KEY] = variant.name }
    }

    /** Selected full-player seek bar visual style (section: Appearance). Defaults to the original Classic style. */
    val seekBarStyle: Flow<com.whiplash.music.ui.theme.SeekBarStyle> = dataStore.data.map { prefs ->
        prefs[SEEK_BAR_STYLE_KEY]?.let { stored ->
            runCatching { com.whiplash.music.ui.theme.SeekBarStyle.valueOf(stored) }.getOrNull()
        } ?: com.whiplash.music.ui.theme.SeekBarStyle.CLASSIC
    }

    suspend fun setSeekBarStyle(style: com.whiplash.music.ui.theme.SeekBarStyle) {
        dataStore.edit { prefs -> prefs[SEEK_BAR_STYLE_KEY] = style.name }
    }

    /** Crossfade duration between tracks, 0 = off (section 18). Defaults off. */
    val crossfadeDurationMs: Flow<Int> = dataStore.data.map { prefs -> prefs[CROSSFADE_KEY] ?: 0 }

    suspend fun setCrossfadeDurationMs(ms: Int) {
        dataStore.edit { prefs -> prefs[CROSSFADE_KEY] = ms }
    }

    /** Gapless playback between consecutive tracks (section 18). Defaults on. */
    val gaplessEnabled: Flow<Boolean> = dataStore.data.map { prefs -> prefs[GAPLESS_KEY] ?: true }

    suspend fun setGaplessEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[GAPLESS_KEY] = enabled }
    }

    /** Persisted playback speed multiplier (section 18). Defaults 1.0x (normal speed). */
    val playbackSpeed: Flow<Float> = dataStore.data.map { prefs -> prefs[SPEED_KEY] ?: 1.0f }

    suspend fun setPlaybackSpeed(speed: Float) {
        dataStore.edit { prefs -> prefs[SPEED_KEY] = speed }
    }

    /**
     * Whether resolved YouTube audio streams are cached to disk so a
     * replayed track starts instantly without a fresh network fetch —
     * the same behavior Spotify/YouTube Music's own streaming cache
     * provides (bounded size, oldest-unused-first eviction; never
     * presented as an offline "download"). Defaults on, matching how
     * every comparable app ships this by default; the user can turn it
     * off entirely if they'd rather avoid the disk usage.
     */
    val audioCacheEnabled: Flow<Boolean> = dataStore.data.map { prefs -> prefs[AUDIO_CACHE_ENABLED_KEY] ?: true }

    suspend fun setAudioCacheEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[AUDIO_CACHE_ENABLED_KEY] = enabled }
    }

    /** Epoch millis of the last successful manual backup (section: Backup & Restore), null if never backed up. */
    val lastBackupTimeMs: Flow<Long?> = dataStore.data.map { prefs -> prefs[LAST_BACKUP_TIME_KEY] }

    suspend fun setLastBackupTimeMs(timeMs: Long) {
        dataStore.edit { prefs -> prefs[LAST_BACKUP_TIME_KEY] = timeMs }
    }

    private companion object {
        val AUDIO_QUALITY_KEY: Preferences.Key<String> = stringPreferencesKey("audio_quality")
        val AUTOPLAY_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("autoplay_enabled")
        val THEME_KEY: Preferences.Key<String> = stringPreferencesKey("theme_variant")
        val SEEK_BAR_STYLE_KEY: Preferences.Key<String> = stringPreferencesKey("seek_bar_style")
        val CROSSFADE_KEY: Preferences.Key<Int> = intPreferencesKey("crossfade_duration_ms")
        val GAPLESS_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("gapless_enabled")
        val SPEED_KEY: Preferences.Key<Float> = floatPreferencesKey("playback_speed")
        val AUDIO_CACHE_ENABLED_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("audio_cache_enabled")
        val LAST_BACKUP_TIME_KEY: Preferences.Key<Long> = androidx.datastore.preferences.core.longPreferencesKey("last_backup_time_ms")
    }
}
