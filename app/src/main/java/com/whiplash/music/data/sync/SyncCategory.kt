package com.whiplash.music.data.sync

/** Parts of the library the user can choose to sync (Settings > Account & sync). */
enum class SyncCategory(val label: String) {
    PLAYLISTS("Playlists"),
    FAVORITES("Favourites"),
    HISTORY("History & Replay"),
    SPEED_DIAL("Speed dial"),
    SETTINGS("Settings"),
    ;

    /** [into] with this category's data replaced by [from]'s. */
    fun copy(from: SyncSnapshot, into: SyncSnapshot): SyncSnapshot = when (this) {
        PLAYLISTS -> into.copy(playlists = from.playlists)
        FAVORITES -> into.copy(favorites = from.favorites)
        HISTORY -> into.copy(history = from.history, replay = from.replay)
        SPEED_DIAL -> into.copy(pinned = from.pinned)
        SETTINGS -> into.copy(settings = from.settings, lyricOffsets = from.lyricOffsets)
    }
}
