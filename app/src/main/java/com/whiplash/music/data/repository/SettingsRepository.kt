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
import kotlinx.coroutines.flow.first
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

    private val appContext = context.applicationContext
    private val dataStore = context.settingsDataStore

    val audioQuality: Flow<AudioQuality> = dataStore.data.map { prefs ->
        prefs[AUDIO_QUALITY_KEY]?.let { stored ->
            runCatching { AudioQuality.valueOf(stored) }.getOrNull()
        } ?: AudioQuality.AUTO
    }

    suspend fun setAudioQuality(quality: AudioQuality) {
        dataStore.edit { prefs -> prefs[AUDIO_QUALITY_KEY] = quality.name }
    }

    /**
     * Quality used when resolving the audio stream for an offline
     * download (Library > Downloads) — deliberately a separate setting
     * from [audioQuality] (which only applies to live streaming
     * playback): a user may want small, storage-friendly downloads for
     * offline listening while still streaming at a higher quality when
     * online, or vice versa. Reuses the same real [AudioQuality] enum
     * and the same [com.whiplash.music.playback.provider.PlaybackManager.resolveStream]
     * quality parameter [audioQuality] already drives — genuinely
     * changes which bitrate is fetched, never a cosmetic-only setting.
     * Defaults to AUTO (currently: highest available), matching
     * [audioQuality]'s own default.
     */
    val downloadQuality: Flow<AudioQuality> = dataStore.data.map { prefs ->
        prefs[DOWNLOAD_QUALITY_KEY]?.let { stored ->
            runCatching { AudioQuality.valueOf(stored) }.getOrNull()
        } ?: AudioQuality.AUTO
    }

    suspend fun setDownloadQuality(quality: AudioQuality) {
        dataStore.edit { prefs -> prefs[DOWNLOAD_QUALITY_KEY] = quality.name }
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

    /**
     * Whether silent passages are automatically sped through during playback
     * (Media3's own [androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor],
     * not a custom DSP). Defaults off — this measurably changes what's heard
     * (silence is shortened, not skipped instantly), so it must be an explicit
     * opt-in rather than a surprise default.
     */
    val skipSilenceEnabled: Flow<Boolean> = dataStore.data.map { prefs -> prefs[SKIP_SILENCE_KEY] ?: false }

    suspend fun setSkipSilenceEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[SKIP_SILENCE_KEY] = enabled }
    }

    /**
     * Audio quality ceiling used while on Wi-Fi (section 61, extended for
     * per-network control). Kept separate from [audioQuality] so a user's
     * generic "Audio Quality" preference can be split by network without a
     * migration: both [audioQualityWifi] and [audioQualityCellular] fall
     * back to [audioQuality]'s own currently-stored value the first time
     * they're read, so upgrading never silently resets a user's existing
     * choice back to AUTO.
     */
    val audioQualityWifi: Flow<AudioQuality> = dataStore.data.map { prefs ->
        prefs[AUDIO_QUALITY_WIFI_KEY]?.let { stored -> runCatching { AudioQuality.valueOf(stored) }.getOrNull() }
            ?: prefs[AUDIO_QUALITY_KEY]?.let { stored -> runCatching { AudioQuality.valueOf(stored) }.getOrNull() }
            ?: AudioQuality.AUTO
    }

    suspend fun setAudioQualityWifi(quality: AudioQuality) {
        dataStore.edit { prefs -> prefs[AUDIO_QUALITY_WIFI_KEY] = quality.name }
    }

    /**
     * Audio quality ceiling used while on cellular data (see
     * [audioQualityWifi]). Defaults to MEDIUM rather than AUTO/HIGH the very
     * first time it's ever read (i.e. no [AUDIO_QUALITY_CELLULAR_KEY] and no
     * prior generic [AUDIO_QUALITY_KEY] to inherit) — a fresh install
     * shouldn't be able to burn a user's mobile data at the highest bitrate
     * before they've ever opened Settings.
     */
    val audioQualityCellular: Flow<AudioQuality> = dataStore.data.map { prefs ->
        prefs[AUDIO_QUALITY_CELLULAR_KEY]?.let { stored -> runCatching { AudioQuality.valueOf(stored) }.getOrNull() }
            ?: prefs[AUDIO_QUALITY_KEY]?.let { stored -> runCatching { AudioQuality.valueOf(stored) }.getOrNull() }
            ?: AudioQuality.MEDIUM
    }

    suspend fun setAudioQualityCellular(quality: AudioQuality) {
        dataStore.edit { prefs -> prefs[AUDIO_QUALITY_CELLULAR_KEY] = quality.name }
    }

    /**
     * Whether per-network audio quality (separate Wi-Fi/cellular ceilings)
     * is active at all. Defaults off so [audioQuality] alone keeps
     * controlling playback for every existing user until they explicitly
     * turn this on — enabling it is what makes [audioQualityWifi]/
     * [audioQualityCellular] actually take effect instead of [audioQuality].
     */
    val perNetworkQualityEnabled: Flow<Boolean> = dataStore.data.map { prefs -> prefs[PER_NETWORK_QUALITY_ENABLED_KEY] ?: false }

    suspend fun setPerNetworkQualityEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[PER_NETWORK_QUALITY_ENABLED_KEY] = enabled }
    }

    /**
     * The quality to actually resolve a stream at right now (adapted from
     * BitChord's per-network quality ceilings): when [perNetworkQualityEnabled]
     * is on, checks the device's current active network transport via
     * [android.net.ConnectivityManager] and returns [audioQualityWifi] or
     * [audioQualityCellular] accordingly; any other/unknown transport (e.g.
     * Ethernet, VPN, or no active network at all) falls back to [audioQuality]
     * as a safe default rather than guessing which of the two ceilings should
     * apply. When the feature is off, this is simply [audioQuality] — every
     * existing call site that resolves a stream can call this one function
     * and automatically respect per-network quality without duplicating the
     * connectivity check.
     */
    suspend fun effectiveAudioQuality(): AudioQuality {
        if (!perNetworkQualityEnabled.first()) return audioQuality.first()
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val capabilities = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        return when {
            capabilities == null -> audioQuality.first()
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> audioQualityWifi.first()
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> audioQualityCellular.first()
            else -> audioQuality.first()
        }
    }

    private companion object {
        val AUDIO_QUALITY_KEY: Preferences.Key<String> = stringPreferencesKey("audio_quality")
        val DOWNLOAD_QUALITY_KEY: Preferences.Key<String> = stringPreferencesKey("download_quality")
        val AUTOPLAY_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("autoplay_enabled")
        val THEME_KEY: Preferences.Key<String> = stringPreferencesKey("theme_variant")
        val SEEK_BAR_STYLE_KEY: Preferences.Key<String> = stringPreferencesKey("seek_bar_style")
        val CROSSFADE_KEY: Preferences.Key<Int> = intPreferencesKey("crossfade_duration_ms")
        val GAPLESS_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("gapless_enabled")
        val SPEED_KEY: Preferences.Key<Float> = floatPreferencesKey("playback_speed")
        val AUDIO_CACHE_ENABLED_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("audio_cache_enabled")
        val LAST_BACKUP_TIME_KEY: Preferences.Key<Long> = androidx.datastore.preferences.core.longPreferencesKey("last_backup_time_ms")
        val SKIP_SILENCE_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("skip_silence_enabled")
        val AUDIO_QUALITY_WIFI_KEY: Preferences.Key<String> = stringPreferencesKey("audio_quality_wifi")
        val AUDIO_QUALITY_CELLULAR_KEY: Preferences.Key<String> = stringPreferencesKey("audio_quality_cellular")
        val PER_NETWORK_QUALITY_ENABLED_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("per_network_quality_enabled")
    }
}
