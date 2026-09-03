package com.whiplash.music.data.backup

/**
 * One selectable category in the Advanced Backup sheet (Settings > Backup
 * & Restore > Advanced backup). Each maps to a genuinely distinct,
 * user-facing concept — not a raw table name — matching how a user
 * actually thinks about "the things I might want to back up."
 *
 * [DOWNLOADS] backs up only the *records* of what's downloaded (title,
 * artist, artwork reference, file path) — not the audio files themselves,
 * which are large binary blobs unsuitable for a small JSON-based
 * selective backup. [displayName]/[description] make this scope explicit
 * in the UI rather than silently claiming more than what's actually
 * backed up (the same "never claim a feature/behavior that isn't real"
 * principle already followed throughout this codebase, e.g. Quick Picks'
 * own honest-personalization doc).
 */
enum class BackupCategory(val displayName: String, val description: String) {
    PLAYLISTS("Playlists", "Your created and imported playlists, and their tracks"),
    FAVORITES("Favorites", "Songs you've marked as favorites"),
    HISTORY("History", "Your recently played history"),
    PINNED("Pinned", "Songs pinned to your Home screen's Speed dial"),
    DOWNLOADS("Downloads", "Records of what you've downloaded (not the audio files themselves)"),
    SETTINGS("Settings", "Theme, playback, and other app preferences"),
}
