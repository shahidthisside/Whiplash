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

    private companion object {
        val AUDIO_QUALITY_KEY: Preferences.Key<String> = stringPreferencesKey("audio_quality")
        val AUTOPLAY_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("autoplay_enabled")
        val THEME_KEY: Preferences.Key<String> = stringPreferencesKey("theme_variant")
        val CROSSFADE_KEY: Preferences.Key<Int> = intPreferencesKey("crossfade_duration_ms")
        val GAPLESS_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("gapless_enabled")
        val SPEED_KEY: Preferences.Key<Float> = floatPreferencesKey("playback_speed")
    }
}
