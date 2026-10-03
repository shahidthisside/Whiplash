// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.whiplash.music.domain.model.AudioQuality
import com.whiplash.music.ui.theme.ThemeVariant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore is created with a [ReplaceFileCorruptionHandler]: without one, a
 * corrupted preferences file makes every `dataStore.data` read throw
 * CorruptionException, which crashes every settings collector app-wide with
 * no recovery path. This is a genuinely reachable state here — [BackupManager.restore]
 * copies raw zip-entry bytes straight into `whiplash_settings.preferences_pb`,
 * so a truncated or foreign backup file, or an unclean shutdown mid-write, can
 * leave an unparseable file. Replacing it with empty preferences degrades to
 * built-in defaults instead of a permanent launch-crash loop.
 */
private val Context.settingsDataStore by preferencesDataStore(
    name = "whiplash_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

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

    /** App-wide theme (Dark, Light, Liquid Glass, ...). Defaults to Dark, the original look. */
    val appTheme: Flow<com.whiplash.music.ui.theme.AppTheme> = dataStore.data.map { prefs ->
        prefs[APP_THEME_KEY]?.let { stored ->
            runCatching { com.whiplash.music.ui.theme.AppTheme.valueOf(stored) }.getOrNull()
        } ?: com.whiplash.music.ui.theme.AppTheme.DARK
    }

    suspend fun setAppTheme(theme: com.whiplash.music.ui.theme.AppTheme) {
        dataStore.edit { prefs -> prefs[APP_THEME_KEY] = theme.name }
    }

    /** The Custom theme's background and accent, stored as ARGB ints. */
    val customThemeColors: Flow<com.whiplash.music.ui.theme.CustomThemeColors> = dataStore.data.map { prefs ->
        val d = com.whiplash.music.ui.theme.CustomThemeColors()
        com.whiplash.music.ui.theme.CustomThemeColors(
            background = prefs[CUSTOM_BG_KEY]?.let { androidx.compose.ui.graphics.Color(it) } ?: d.background,
            accent = prefs[CUSTOM_ACCENT_KEY]?.let { androidx.compose.ui.graphics.Color(it) } ?: d.accent,
            glassBackground = prefs[GLASS_BG_KEY]?.let { stored ->
                runCatching { com.whiplash.music.ui.theme.GlassBackground.valueOf(stored) }.getOrNull()
            } ?: d.glassBackground,
            glassColor = prefs[GLASS_BG_COLOR_KEY]?.let { androidx.compose.ui.graphics.Color(it) } ?: d.glassColor,
            accentColor = prefs[ACCENT_CUSTOM_COLOR_KEY]?.let { androidx.compose.ui.graphics.Color(it) } ?: d.accentColor,
        )
    }

    suspend fun setCustomThemeColors(colors: com.whiplash.music.ui.theme.CustomThemeColors) {
        dataStore.edit { prefs ->
            prefs[CUSTOM_BG_KEY] = colors.background.toArgbInt()
            prefs[CUSTOM_ACCENT_KEY] = colors.accent.toArgbInt()
            prefs[GLASS_BG_KEY] = colors.glassBackground.name
            prefs[GLASS_BG_COLOR_KEY] = colors.glassColor.toArgbInt()
            prefs[ACCENT_CUSTOM_COLOR_KEY] = colors.accentColor.toArgbInt()
        }
    }

    /** Liquid Glass tint strength, 0..1. */
    val glassOpacity: Flow<Float> = dataStore.data.map { prefs ->
        (prefs[GLASS_OPACITY_KEY] ?: com.whiplash.music.ui.theme.WhiplashColors.DEFAULT_GLASS_OPACITY).coerceIn(0f, 1f)
    }

    /** Liquid Glass lens bending, 0..1. */
    val glassLens: Flow<Float> = dataStore.data.map { prefs ->
        (prefs[GLASS_LENS_KEY] ?: com.whiplash.music.ui.theme.WhiplashColors.DEFAULT_GLASS_LENS).coerceIn(0f, 1f)
    }

    suspend fun setGlassLens(value: Float) {
        dataStore.edit { prefs -> prefs[GLASS_LENS_KEY] = value.coerceIn(0f, 1f) }
    }

    suspend fun setGlassOpacity(value: Float) {
        dataStore.edit { prefs -> prefs[GLASS_OPACITY_KEY] = value.coerceIn(0f, 1f) }
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
    /**
     * Fade length between tracks, 0 (off) to 12s (see PlaybackTuning). Clamped on
     * read as well as write so a value from an older or hand-edited backup
     * can never drive the fade logic outside the range the UI offers.
     */
    val crossfadeDurationMs: Flow<Int> = dataStore.data.map { prefs ->
        com.whiplash.music.domain.model.PlaybackTuning.normalizeCrossfadeMs(prefs[CROSSFADE_KEY] ?: 0)
    }

    suspend fun setCrossfadeDurationMs(ms: Int) {
        dataStore.edit { prefs -> prefs[CROSSFADE_KEY] = com.whiplash.music.domain.model.PlaybackTuning.normalizeCrossfadeMs(ms) }
    }

    /** Gapless playback between consecutive tracks (section 18). Defaults on. */
    val gaplessEnabled: Flow<Boolean> = dataStore.data.map { prefs -> prefs[GAPLESS_KEY] ?: true }

    suspend fun setGaplessEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[GAPLESS_KEY] = enabled }
    }

    /** Persisted playback speed multiplier (section 18). Defaults 1.0x (normal speed). */
    /** Playback speed, 0.5x..2.0x (see PlaybackTuning); clamped on read and write (see [crossfadeDurationMs]). */
    val playbackSpeed: Flow<Float> = dataStore.data.map { prefs ->
        com.whiplash.music.domain.model.PlaybackTuning.normalizeSpeed(prefs[SPEED_KEY] ?: 1.0f)
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        dataStore.edit { prefs -> prefs[SPEED_KEY] = com.whiplash.music.domain.model.PlaybackTuning.normalizeSpeed(speed) }
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
     * Only start downloads on an unmetered connection (Wi-Fi/Ethernet),
     * so a bulk "Download album" on mobile data can't quietly burn through
     * a data plan. Off by default so existing behaviour is unchanged.
     * Streaming is not affected: that is what per-network quality is for.
     */
    val downloadWifiOnly: Flow<Boolean> = dataStore.data.map { prefs -> prefs[DOWNLOAD_WIFI_ONLY_KEY] ?: false }

    suspend fun setDownloadWifiOnly(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[DOWNLOAD_WIFI_ONLY_KEY] = enabled }
    }

    /**
     * True when the active network is metered (mobile data, a metered
     * hotspot) or there is no network at all. Uses the system's own
     * metered flag rather than checking for the Wi-Fi transport, so a
     * phone tethered to another phone's hotspot counts as metered, which
     * is what the listener actually cares about.
     */
    fun isActiveNetworkMetered(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            ?: return false
        return cm.isActiveNetworkMetered
    }

    /** Shows the codec / sample rate / bitrate line under the full player's artwork. Off by default. */
    /**
     * In-app "Reduce animations". Combined with the system's own
     * animator-duration setting in isReducedMotionEnabled(), so either one
     * switches off the app's nonessential motion. Off by default.
     */
    val reduceAnimations: Flow<Boolean> = dataStore.data.map { prefs -> prefs[REDUCE_ANIMATIONS_KEY] ?: false }

    suspend fun setReduceAnimations(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[REDUCE_ANIMATIONS_KEY] = enabled }
    }

    /**
     * Colours the full player from the current cover: a mesh-gradient
     * backdrop sampled from the artwork and an accent for the play button
     * and active toggles. On by default as part of the Now Playing
     * redesign; turning it off restores the plain theme-coloured player.
     */
    val playerArtworkColors: Flow<Boolean> = dataStore.data.map { prefs -> prefs[PLAYER_ARTWORK_COLORS_KEY] ?: true }

    suspend fun setPlayerArtworkColors(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[PLAYER_ARTWORK_COLORS_KEY] = enabled }
    }

    /** Shows the current lyric line above the full player's seek bar. On by default. */
    val playerLyricStrip: Flow<Boolean> = dataStore.data.map { prefs -> prefs[PLAYER_LYRIC_STRIP_KEY] ?: true }

    suspend fun setPlayerLyricStrip(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[PLAYER_LYRIC_STRIP_KEY] = enabled }
    }

    /** Swipe up on the full player to open the lyrics. On by default. */
    val swipeUpForLyrics: Flow<Boolean> = dataStore.data.map { prefs -> prefs[SWIPE_UP_LYRICS_KEY] ?: true }

    suspend fun setSwipeUpForLyrics(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[SWIPE_UP_LYRICS_KEY] = enabled }
    }

    /**
     * 4.7 Monthly Replay: the recap card on Home, and counting plays and
     * listening time for it. Off stops counting; nothing already counted is
     * deleted. Default on.
     */
    val replayEnabled: Flow<Boolean> = dataStore.data.map { prefs -> prefs[REPLAY_KEY] ?: true }

    suspend fun setReplayEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[REPLAY_KEY] = enabled }
    }

    /** Explore (new releases, charts, moods & genres) on Search's start screen. Default on. */
    /**
     * Settings > Account & sync master switch. Off hides the account card and
     * stops every Google/Drive request; on (the default) only shows the
     * optional sign-in — nothing syncs until the user signs in.
     */
    val cloudSyncEnabled: Flow<Boolean> = dataStore.data.map { prefs -> prefs[CLOUD_SYNC_KEY] ?: true }

    suspend fun setCloudSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[CLOUD_SYNC_KEY] = enabled }
    }

    /** Account & sync: show the signed-in email on the profile in Settings (this device only). */
    val showAccountEmail: Flow<Boolean> = dataStore.data.map { prefs -> prefs[SHOW_ACCOUNT_EMAIL_KEY] ?: true }

    suspend fun setShowAccountEmail(show: Boolean) {
        dataStore.edit { prefs -> prefs[SHOW_ACCOUNT_EMAIL_KEY] = show }
    }

    // ---- First-run onboarding + music taste -------------------------------

    /** Null until decided: fresh installs see onboarding, existing users are marked done. */
    val onboardingDone: Flow<Boolean?> = dataStore.data.map { prefs -> prefs[ONBOARDING_DONE_KEY] }

    suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { prefs -> prefs[ONBOARDING_DONE_KEY] = done }
    }

    /** Taste picked during onboarding (or Settings › Music taste); empty when skipped. */
    val tasteLanguages: Flow<List<String>> = dataStore.data.map { prefs -> decodeTaste(prefs[TASTE_LANGUAGES_KEY]) }
    val tasteGenres: Flow<List<String>> = dataStore.data.map { prefs -> decodeTaste(prefs[TASTE_GENRES_KEY]) }
    val tasteArtists: Flow<List<String>> = dataStore.data.map { prefs -> decodeTaste(prefs[TASTE_ARTISTS_KEY]) }

    suspend fun setTaste(languages: List<String>, genres: List<String>, artists: List<String>) {
        dataStore.edit { prefs ->
            prefs[TASTE_LANGUAGES_KEY] = encodeTaste(languages)
            prefs[TASTE_GENRES_KEY] = encodeTaste(genres)
            prefs[TASTE_ARTISTS_KEY] = encodeTaste(artists)
        }
    }

    // Stored as one string in pick order (a string set would lose the order).
    private fun encodeTaste(values: List<String>): String =
        values.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(TASTE_SEPARATOR)

    private fun decodeTaste(raw: String?): List<String> =
        raw?.split(TASTE_SEPARATOR)?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    /** Account & sync: show the "Synced just now"-style status on the Settings profile card (this device only). */
    val showSyncStatus: Flow<Boolean> = dataStore.data.map { prefs -> prefs[SHOW_SYNC_STATUS_KEY] ?: true }

    suspend fun setShowSyncStatus(show: Boolean) {
        dataStore.edit { prefs -> prefs[SHOW_SYNC_STATUS_KEY] = show }
    }

    /** Account & sync: show the profile photo in Settings; off shows your initial instead (this device only). */
    val showAccountPhoto: Flow<Boolean> = dataStore.data.map { prefs -> prefs[SHOW_ACCOUNT_PHOTO_KEY] ?: true }

    suspend fun setShowAccountPhoto(show: Boolean) {
        dataStore.edit { prefs -> prefs[SHOW_ACCOUNT_PHOTO_KEY] = show }
    }

    /** Emits on every settings write — cloud sync uses it to notice changes. */
    val rawChanges: Flow<Preferences> = dataStore.data

    val exploreEnabled: Flow<Boolean> = dataStore.data.map { prefs -> prefs[EXPLORE_KEY] ?: true }

    suspend fun setExploreEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[EXPLORE_KEY] = enabled }
    }

    /** Home recommendation shelves (albums/playlists from artists you play). Default on. */
    val homeShelvesEnabled: Flow<Boolean> = dataStore.data.map { prefs -> prefs[HOME_SHELVES_KEY] ?: true }

    suspend fun setHomeShelvesEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[HOME_SHELVES_KEY] = enabled }
    }

    /** Playlists screen shown as a list instead of the cover grid (default: grid). */
    val playlistsListView: Flow<Boolean> = dataStore.data.map { prefs -> prefs[PLAYLISTS_LIST_KEY] ?: false }

    suspend fun setPlaylistsListView(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[PLAYLISTS_LIST_KEY] = enabled }
    }

    /** Home "Quick Picks" shown as an artwork grid instead of a list (default: grid). */
    val quickPicksGridView: Flow<Boolean> = dataStore.data.map { prefs -> prefs[QUICK_PICKS_GRID_KEY] ?: true }

    suspend fun setQuickPicksGridView(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[QUICK_PICKS_GRID_KEY] = enabled }
    }

    /**
     * Quick Picks grid: songs per swipeable page (3, 6, 9 or 12 = one to four
     * rows of three), or 0 for "All" (one tall grid, no paging). Default 9.
     */
    val quickPicksGridCount: Flow<Int> = dataStore.data.map { prefs ->
        prefs[QUICK_PICKS_GRID_COUNT_KEY]?.takeIf { it in QUICK_PICKS_GRID_COUNTS } ?: 9
    }

    suspend fun setQuickPicksGridCount(count: Int) {
        if (count !in QUICK_PICKS_GRID_COUNTS) return
        dataStore.edit { prefs -> prefs[QUICK_PICKS_GRID_COUNT_KEY] = count }
    }

    /** Home "Speed dial" shown as a list instead of the 3x3 artwork grid. */
    val speedDialListView: Flow<Boolean> = dataStore.data.map { prefs -> prefs[SPEED_DIAL_LIST_KEY] ?: false }

    suspend fun setSpeedDialListView(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[SPEED_DIAL_LIST_KEY] = enabled }
    }

    /**
     * Speed dial grid swipes sideways through up to [SPEED_DIAL_MAX_PAGES]
     * pages of nine; off keeps the single 3x3 grid. Default on.
     */
    val speedDialPaging: Flow<Boolean> = dataStore.data.map { prefs -> prefs[SPEED_DIAL_PAGING_KEY] ?: true }

    suspend fun setSpeedDialPaging(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[SPEED_DIAL_PAGING_KEY] = enabled }
    }

    /** Speed dial songs per page (grid size): one of [SPEED_DIAL_GRID_COUNTS]. Default 9 (3x3). */
    val speedDialGridCount: Flow<Int> = dataStore.data.map { prefs ->
        prefs[SPEED_DIAL_GRID_COUNT_KEY]?.takeIf { it in SPEED_DIAL_GRID_COUNTS } ?: SPEED_DIAL_PAGE_SIZE
    }

    suspend fun setSpeedDialGridCount(count: Int) {
        if (count !in SPEED_DIAL_GRID_COUNTS) return
        dataStore.edit { prefs -> prefs[SPEED_DIAL_GRID_COUNT_KEY] = count }
    }

    /** How many Speed dial pages (of nine) when paging is on: one of [SPEED_DIAL_PAGE_COUNTS]. Default 3. */
    val speedDialPageCount: Flow<Int> = dataStore.data.map { prefs ->
        prefs[SPEED_DIAL_PAGE_COUNT_KEY]?.takeIf { it in SPEED_DIAL_PAGE_COUNTS } ?: SPEED_DIAL_MAX_PAGES
    }

    suspend fun setSpeedDialPageCount(count: Int) {
        if (count !in SPEED_DIAL_PAGE_COUNTS) return
        dataStore.edit { prefs -> prefs[SPEED_DIAL_PAGE_COUNT_KEY] = count }
    }

    /** Speed dial pages: show a sliver of the next page at the edge. Default on. */
    val speedDialPeek: Flow<Boolean> = dataStore.data.map { prefs -> prefs[SPEED_DIAL_PEEK_KEY] ?: true }

    suspend fun setSpeedDialPeek(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[SPEED_DIAL_PEEK_KEY] = enabled }
    }

    /** Quick Picks pages: show a sliver of the next page at the edge. Default on. */
    val quickPicksPeek: Flow<Boolean> = dataStore.data.map { prefs -> prefs[QUICK_PICKS_PEEK_KEY] ?: true }

    suspend fun setQuickPicksPeek(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[QUICK_PICKS_PEEK_KEY] = enabled }
    }

    /** Soft blur on lyric lines away from the one being sung (API 31+; ignored on older Android). */
    val lyricsBlurUnfocused: Flow<Boolean> = dataStore.data.map { prefs -> prefs[LYRICS_BLUR_KEY] ?: false }

    suspend fun setLyricsBlurUnfocused(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[LYRICS_BLUR_KEY] = enabled }
    }

    /** Where lyrics come from: Automatic (LRCLIB, then lyrics.ovh) or one provider only. */
    val lyricsSource: Flow<com.whiplash.music.data.lyrics.LyricsSourcePreference> = dataStore.data.map { prefs ->
        prefs[LYRICS_SOURCE_KEY]?.let { runCatching { com.whiplash.music.data.lyrics.LyricsSourcePreference.valueOf(it) }.getOrNull() }
            ?: com.whiplash.music.data.lyrics.LyricsSourcePreference.AUTO
    }

    suspend fun setLyricsSource(source: com.whiplash.music.data.lyrics.LyricsSourcePreference) {
        dataStore.edit { prefs -> prefs[LYRICS_SOURCE_KEY] = source.name }
    }

    /** Where song streams are looked up; see [com.whiplash.music.playback.provider.StreamSourcePreference]. Automatic by default. */
    val streamSource: Flow<com.whiplash.music.playback.provider.StreamSourcePreference> = dataStore.data.map { prefs ->
        prefs[STREAM_SOURCE_KEY]?.let { runCatching { com.whiplash.music.playback.provider.StreamSourcePreference.valueOf(it) }.getOrNull() }
            ?: com.whiplash.music.playback.provider.StreamSourcePreference.AUTO
    }

    suspend fun setStreamSource(source: com.whiplash.music.playback.provider.StreamSourcePreference) {
        dataStore.edit { prefs -> prefs[STREAM_SOURCE_KEY] = source.name }
    }

    /** Full-bleed "hero" artwork across the top of the full player. Off by default. */
    val playerHeroArtwork: Flow<Boolean> = dataStore.data.map { prefs -> prefs[PLAYER_HERO_ARTWORK_KEY] ?: false }

    suspend fun setPlayerHeroArtwork(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[PLAYER_HERO_ARTWORK_KEY] = enabled }
    }

    val statsForNerdsEnabled: Flow<Boolean> = dataStore.data.map { prefs -> prefs[STATS_FOR_NERDS_KEY] ?: false }

    suspend fun setStatsForNerdsEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[STATS_FOR_NERDS_KEY] = enabled }
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
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val capabilities = runCatching { cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) } }.getOrNull()
        val chosen = if (!perNetworkQualityEnabled.first()) {
            audioQuality.first()
        } else {
            when {
                capabilities == null -> audioQuality.first()
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> audioQualityWifi.first()
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> audioQualityCellular.first()
                else -> audioQuality.first()
            }
        }
        // Auto picks the best stream, except on a slow connection, where a
        // lighter one starts sooner and doesn't stall. A quality the listener
        // picked themselves is always kept.
        return if (chosen == AudioQuality.AUTO) slowConnectionQuality(capabilities) ?: chosen else chosen
    }

    /**
     * A lighter quality for a slow connection, or null when it's fast enough.
     * Speed is the player's own measured download rate of songs, which
     * starts from Media3's estimate for the network type and country and
     * learns from every song played. On mobile data the system's link
     * estimate counts too, if lower (on Wi-Fi that figure is the speed to
     * the router, not to the internet).
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun slowConnectionQuality(capabilities: android.net.NetworkCapabilities?): AudioQuality? {
        if (capabilities == null) return null
        val measured = runCatching {
            androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.getSingletonInstance(appContext).bitrateEstimate
        }.getOrNull()?.takeIf { it > 0 }
        val link = if (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) {
            capabilities.linkDownstreamBandwidthKbps.toLong().takeIf { it > 0 }?.times(1000)
        } else {
            null
        }
        val bps = listOfNotNull(measured, link).minOrNull() ?: return null
        return when {
            bps < SLOW_LINK_LOW_BPS -> AudioQuality.LOW
            bps < SLOW_LINK_MEDIUM_BPS -> AudioQuality.MEDIUM
            else -> null
        }
    }

    private companion object {
        /** Below these speeds Auto streams at Low / Medium. */
        const val SLOW_LINK_LOW_BPS = 300_000L
        const val SLOW_LINK_MEDIUM_BPS = 700_000L
        val AUDIO_QUALITY_KEY: Preferences.Key<String> = stringPreferencesKey("audio_quality")
        val DOWNLOAD_QUALITY_KEY: Preferences.Key<String> = stringPreferencesKey("download_quality")
        val AUTOPLAY_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("autoplay_enabled")
        val THEME_KEY: Preferences.Key<String> = stringPreferencesKey("theme_variant")
        val APP_THEME_KEY: Preferences.Key<String> = stringPreferencesKey("app_theme")
        val CUSTOM_BG_KEY: Preferences.Key<Int> = intPreferencesKey("custom_theme_background")
        val CUSTOM_ACCENT_KEY: Preferences.Key<Int> = intPreferencesKey("custom_theme_accent")
        val GLASS_OPACITY_KEY: Preferences.Key<Float> = floatPreferencesKey("glass_opacity")
        val GLASS_LENS_KEY: Preferences.Key<Float> = floatPreferencesKey("glass_lens")
        val GLASS_BG_KEY: Preferences.Key<String> = stringPreferencesKey("glass_background")
        val GLASS_BG_COLOR_KEY: Preferences.Key<Int> = intPreferencesKey("glass_background_color")
        val ACCENT_CUSTOM_COLOR_KEY: Preferences.Key<Int> = intPreferencesKey("accent_custom_color")
        val SEEK_BAR_STYLE_KEY: Preferences.Key<String> = stringPreferencesKey("seek_bar_style")
        val CROSSFADE_KEY: Preferences.Key<Int> = intPreferencesKey("crossfade_duration_ms")
        val GAPLESS_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("gapless_enabled")
        val SPEED_KEY: Preferences.Key<Float> = floatPreferencesKey("playback_speed")
        val AUDIO_CACHE_ENABLED_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("audio_cache_enabled")
        val LAST_BACKUP_TIME_KEY: Preferences.Key<Long> = androidx.datastore.preferences.core.longPreferencesKey("last_backup_time_ms")
        val SKIP_SILENCE_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("skip_silence_enabled")
        val DOWNLOAD_WIFI_ONLY_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("download_wifi_only")
        val REDUCE_ANIMATIONS_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("reduce_animations")
        val PLAYER_ARTWORK_COLORS_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("player_artwork_colors")
        val PLAYER_LYRIC_STRIP_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("player_lyric_strip")
        val SWIPE_UP_LYRICS_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("player_swipe_up_lyrics")
        val EXPLORE_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("explore_enabled")
        val CLOUD_SYNC_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("cloud_sync_enabled")
        val SHOW_ACCOUNT_EMAIL_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("show_account_email")
        val SHOW_SYNC_STATUS_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("show_sync_status")
        val ONBOARDING_DONE_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("onboarding_done")
        val TASTE_LANGUAGES_KEY: Preferences.Key<String> = stringPreferencesKey("taste_languages")
        val TASTE_GENRES_KEY: Preferences.Key<String> = stringPreferencesKey("taste_genres")
        val TASTE_ARTISTS_KEY: Preferences.Key<String> = stringPreferencesKey("taste_artists")
        private const val TASTE_SEPARATOR = "\u001F"
        val SHOW_ACCOUNT_PHOTO_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("show_account_photo")
        val REPLAY_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("replay_enabled")
        val HOME_SHELVES_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("home_shelves_enabled")
        val PLAYLISTS_LIST_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("playlists_list_view")
        val QUICK_PICKS_GRID_COUNT_KEY: Preferences.Key<Int> = androidx.datastore.preferences.core.intPreferencesKey("quick_picks_grid_count")
        val QUICK_PICKS_GRID_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("quick_picks_grid_view")
        val SPEED_DIAL_LIST_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("speed_dial_list_view")
        val SPEED_DIAL_PAGING_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("speed_dial_paging")
        val SPEED_DIAL_PEEK_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("speed_dial_peek")
        val SPEED_DIAL_GRID_COUNT_KEY: Preferences.Key<Int> = androidx.datastore.preferences.core.intPreferencesKey("speed_dial_grid_count")
        val SPEED_DIAL_PAGE_COUNT_KEY: Preferences.Key<Int> = androidx.datastore.preferences.core.intPreferencesKey("speed_dial_page_count")
        val QUICK_PICKS_PEEK_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("quick_picks_peek")
        val LYRICS_BLUR_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("lyrics_blur_unfocused")
        val LYRICS_SOURCE_KEY: Preferences.Key<String> = stringPreferencesKey("lyrics_source")
        val STREAM_SOURCE_KEY: Preferences.Key<String> = stringPreferencesKey("stream_source")
        val PLAYER_HERO_ARTWORK_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("player_hero_artwork")
        val STATS_FOR_NERDS_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("stats_for_nerds_enabled")
        val AUDIO_QUALITY_WIFI_KEY: Preferences.Key<String> = stringPreferencesKey("audio_quality_wifi")
        val AUDIO_QUALITY_CELLULAR_KEY: Preferences.Key<String> = stringPreferencesKey("audio_quality_cellular")
        val PER_NETWORK_QUALITY_ENABLED_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("per_network_quality_enabled")
    }
}

/** Allowed Quick Picks grid page sizes; 0 = All. */
val QUICK_PICKS_GRID_COUNTS = listOf(3, 6, 9, 12, 0)

/** ARGB int without depending on the Compose toArgb extension import here. */
private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int =
    android.graphics.Color.argb(
        (alpha * 255f + 0.5f).toInt(), (red * 255f + 0.5f).toInt(),
        (green * 255f + 0.5f).toInt(), (blue * 255f + 0.5f).toInt(),
    )

/** Default number of Speed dial pages when paging is on (nine songs each). */
const val SPEED_DIAL_MAX_PAGES = 3

/** Page counts offered in Settings (2 to 5). */
val SPEED_DIAL_PAGE_COUNTS = listOf(2, 3, 4, 5)
/** Default Speed dial songs per page. */
const val SPEED_DIAL_PAGE_SIZE = 9

/** Speed dial grid sizes offered in Settings (one to four rows of three). */
val SPEED_DIAL_GRID_COUNTS = listOf(3, 6, 9, 12)
