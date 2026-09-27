package com.whiplash.music.ui.settings

/**
 * 4.6: the searchable index behind the grouped Settings screen. Every row on
 * the screen has an entry here (its id, the section it lives in, and extra
 * words people might type for it), so search and section visibility come from
 * one place. Pure Kotlin, unit-tested.
 */
enum class SettingsSection(val label: String) {
    AUDIO_QUALITY("Audio quality"),
    PLAYBACK("Playback"),
    DOWNLOADS("Downloads"),
    NOW_PLAYING("Now Playing"),
    LYRICS("Lyrics"),
    APPEARANCE("Appearance"),
    STORAGE("Storage"),
    BACKUP("Backup & Restore"),
}

/**
 * How the section folders are grouped on the Settings start page, in order.
 * Every section appears in exactly one group.
 */
enum class SettingsGroup(val label: String, val sections: List<SettingsSection>) {
    LISTENING("Listening", listOf(SettingsSection.AUDIO_QUALITY, SettingsSection.PLAYBACK, SettingsSection.DOWNLOADS)),
    LOOK_AND_FEEL("Look & feel", listOf(SettingsSection.NOW_PLAYING, SettingsSection.LYRICS, SettingsSection.APPEARANCE)),
    YOUR_DATA("Your data", listOf(SettingsSection.STORAGE, SettingsSection.BACKUP)),
}

enum class SettingEntry(val section: SettingsSection, val title: String, val keywords: String) {
    AUDIO_QUALITY(SettingsSection.AUDIO_QUALITY, "Audio Quality", "streaming bitrate sound"),
    PER_NETWORK(SettingsSection.AUDIO_QUALITY, "Per-Network Audio Quality", "wifi wi-fi cellular mobile data plan"),
    AUTOPLAY(SettingsSection.PLAYBACK, "Autoplay", "radio related queue continue"),
    GAPLESS(SettingsSection.PLAYBACK, "Gapless Playback", "pause between songs preload"),
    SKIP_SILENCE(SettingsSection.PLAYBACK, "Skip Silence", "quiet"),
    CROSSFADE(SettingsSection.PLAYBACK, "Crossfade", "fade transition mix"),
    SPEED(SettingsSection.PLAYBACK, "Playback Speed", "tempo faster slower"),
    EQUALIZER(SettingsSection.PLAYBACK, "Equalizer", "eq bass treble sound"),
    DOWNLOAD_QUALITY(SettingsSection.DOWNLOADS, "Download Quality", "offline storage"),
    DOWNLOAD_WIFI(SettingsSection.DOWNLOADS, "Download on Wi-Fi only", "wifi mobile data metered offline"),
    SEEK_BAR(SettingsSection.NOW_PLAYING, "Progress Bar Style", "seek bar scrubber waveform wavy hairline minimal"),
    ARTWORK_COLOURS(SettingsSection.NOW_PLAYING, "Artwork colours in player", "color colour tint dynamic background"),
    FULL_BLEED(SettingsSection.NOW_PLAYING, "Full-bleed artwork", "hero edge to edge album art"),
    STATS(SettingsSection.NOW_PLAYING, "Stats for Nerds", "codec bitrate sample rate channels"),
    LYRIC_LINE(SettingsSection.LYRICS, "Lyric line in player", "current lyric strip"),
    LYRICS_BLUR(SettingsSection.LYRICS, "Blur other lyric lines", "focus"),
    LYRICS_SOURCE(SettingsSection.LYRICS, "Lyrics source", "lrclib lyrics.ovh provider"),
    QUICK_PICKS_GRID(SettingsSection.APPEARANCE, "Quick Picks grid size", "songs per page swipe home grid"),
    EXPLORE(SettingsSection.APPEARANCE, "Explore in Search", "new releases charts moods genres browse"),
    REPLAY(SettingsSection.APPEARANCE, "Monthly Replay", "recap stats wrapped top songs artists minutes listened month"),
    HOME_SHELVES(SettingsSection.APPEARANCE, "Home shelves", "recommendations feed albums playlists for you"),
    THEME(SettingsSection.APPEARANCE, "Theme", "color colour dark palette accent"),
    REDUCE_ANIMATIONS(SettingsSection.APPEARANCE, "Reduce animations", "motion accessibility transitions"),
    CACHE_SONGS(SettingsSection.STORAGE, "Cache Songs", "offline storage space"),
    DOWNLOADED_DATA(SettingsSection.STORAGE, "Downloaded songs", "offline downloads delete clear remove space storage size"),
    CACHED_DATA(SettingsSection.STORAGE, "Cached data", "clear cache storage size space"),
    BACKUP(SettingsSection.BACKUP, "Local backup", "restore export import file"),
}

/**
 * True when [entry] matches [query]: every word typed must appear in the
 * entry's title, keywords or section name (case-insensitive, any order).
 * A blank query matches everything.
 */
fun settingMatches(entry: SettingEntry, query: String): Boolean {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val haystack = "${entry.title} ${entry.keywords} ${entry.section.label}".lowercase()
    return words.all { haystack.contains(it) }
}

/** Sections that have at least one matching entry, in screen order. */
fun visibleSections(query: String): List<SettingsSection> =
    SettingsSection.entries.filter { section ->
        SettingEntry.entries.any { it.section == section && settingMatches(it, query) }
    }
