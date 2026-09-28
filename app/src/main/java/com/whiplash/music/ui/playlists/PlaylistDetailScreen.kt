package com.whiplash.music.ui.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whiplash.music.WhiplashApplication
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.domain.model.Playlist
import com.whiplash.music.ui.player.PlayableItemsList
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.PlainIconButton
import com.whiplash.music.ui.theme.WhiplashColors

/** Playlist detail screen (section 38): all tracks in the playlist, play/shuffle, remove. */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    onBack: () -> Unit,
    onPlayQueue: (queue: List<PlayableItem>, startIndex: Int) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as WhiplashApplication
    val viewModel: PlaylistDetailViewModel = viewModel(
        // Real, reported bug: this screen's viewModel() call site never
        // changes when the open playlist changes — only the `playlist`
        // parameter's value does — so without an explicit key, Compose
        // reused the SAME PlaylistDetailViewModel instance (and therefore
        // its already-collecting `tracks` StateFlow, still bound to
        // whichever playlist.id it was FIRST constructed with) across
        // completely different playlists. That showed up as one playlist's
        // songs appearing inside another right after switching, until the
        // app was force-stopped and the ViewModel was finally torn down.
        // Keying explicitly by playlist.id forces a fresh ViewModel (and a
        // fresh tracks query) per distinct playlist.
        key = "playlist_detail_${playlist.id}",
        factory = PlaylistDetailViewModelFactory(app.libraryRepository, playlist.id),
    )
    val tracks by viewModel.tracks.collectAsState()
    ModernPlaylistDetail(playlist, tracks, onBack, onPlayQueue)
}


/**
 * Playlist page as a collection: back row, then a hero with the cover
 * mosaic (or a tinted tile for an empty playlist), song count and running
 * time, Play / Shuffle / Download, filter and sort, then the songs.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun ModernPlaylistDetail(
    playlist: Playlist,
    tracks: List<PlayableItem>,
    onBack: () -> Unit,
    onPlayQueue: (List<PlayableItem>, Int) -> Unit,
) {
    val tint = com.whiplash.music.ui.theme.tintForName(playlist.name)
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = GlassTokens.spaceMd)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = GlassTokens.spaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlainIconButton(contentDescription = "Back", onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = WhiplashColors.textPrimary)
            }
        }
        com.whiplash.music.ui.common.TrackCollectionPage(
            items = tracks,
            eyebrow = if (playlist.pinned) "Pinned playlist" else "Playlist",
            title = playlist.name,
            tint = tint,
            cover = { m ->
                PlaylistArtwork(
                    playlist = playlist,
                    tracks = tracks,
                    modifier = m,
                    cornerRadius = com.whiplash.music.ui.theme.WhiplashRadius.medium,
                    emptyTile = { em -> com.whiplash.music.ui.theme.GradientIconCover(Icons.AutoMirrored.Filled.QueueMusic, tint, em) },
                )
            },
            onPlayQueue = onPlayQueue,
            sortKey = "playlist_${playlist.id}",
            defaultSortLabel = "Playlist order",
            playlistContext = com.whiplash.music.ui.player.PlaylistContext(playlist.id, playlist.name),
            heroActions = {
                if (tracks.isNotEmpty()) {
                    com.whiplash.music.ui.common.FavoriteAllIconButton(name = playlist.name, tracks = tracks)
                    com.whiplash.music.ui.common.BatchDownloadIconButton(batchName = playlist.name, tracks = tracks)
                }
            },
            emptyContent = {
                com.whiplash.music.ui.theme.CollectionEmptyState(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    title = "This playlist is empty",
                    message = "Long-press any song, or open its \u22ee menu, and choose Add to playlist.",
                    tint = tint,
                )
            },
        )
    }
}
