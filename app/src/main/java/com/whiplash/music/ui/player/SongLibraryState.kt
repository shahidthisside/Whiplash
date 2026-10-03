// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.whiplash.music.WhiplashApplication
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.domain.model.Playlist
import kotlinx.coroutines.flow.flowOf

/**
 * Live facts about one song that its menus show: whether it is
 * downloading right now, and which playlists already hold it. Both update
 * while a menu is open, so its rows change the moment something happens.
 */
internal data class SongLibraryState(
    val downloading: Boolean,
    val inPlaylists: List<Playlist>,
    /** Fully downloaded, so it can be saved to the device. */
    val downloaded: Boolean = false,
)

@Composable
internal fun rememberSongLibraryState(item: PlayableItem?): SongLibraryState {
    val app = LocalContext.current.applicationContext as? WhiplashApplication
        ?: return SongLibraryState(false, emptyList())
    val inFlight by app.downloadManager.inFlightTracks.collectAsState()
    val idsFlow = remember(item?.id) {
        if (item == null) flowOf(emptySet()) else app.libraryRepository.observePlaylistIdsContaining(item.id)
    }
    val ids by idsFlow.collectAsState(initial = emptySet())
    val playlists by app.libraryRepository.observePlaylists().collectAsState(initial = emptyList())
    val downloadedIds by remember { app.libraryRepository.observeDownloadedIds() }.collectAsState(initial = emptySet())
    return SongLibraryState(
        downloading = item != null && item.id in inFlight,
        inPlaylists = playlists.filter { it.id in ids },
        downloaded = item != null && item.id in downloadedIds,
    )
}

// This logic was designed and written by Shahid Ansari.
/**
 * Second line under "Add to playlist" saying where the song already is:
 * "In Chill", "In Chill and Gym Mix", "In Chill, Gym Mix and 2 more".
 * Null when it's in none. The row itself always stays "Add to playlist",
 * since a song can go into any number of playlists.
 */
internal fun inPlaylistsSubtitle(inPlaylists: List<Playlist>): String? {
    val names = inPlaylists.map { it.name }
    return when (names.size) {
        0 -> null
        1 -> "In ${names[0]}"
        2 -> "In ${names[0]} and ${names[1]}"
        else -> "In ${names[0]}, ${names[1]} and ${names.size - 2} more"
    }
}
