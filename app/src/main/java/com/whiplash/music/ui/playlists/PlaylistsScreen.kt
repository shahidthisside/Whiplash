package com.whiplash.music.ui.playlists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import kotlinx.coroutines.launch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whiplash.music.WhiplashApplication
import com.whiplash.music.domain.model.Playlist
import com.whiplash.music.ui.theme.GlassListItem
import com.whiplash.music.ui.theme.GlassSheet
import com.whiplash.music.ui.theme.GlassTextInputDialog
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.PlainIconButton
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.WhiplashRadius

/**
 * Playlists list screen (section 38). The repository layer for playlists
 * (create/rename/delete/add/remove/reorder tracks) was built earlier in the
 * project; this is the first UI surface for it.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun PlaylistsScreen(onOpenPlaylist: (Playlist) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as WhiplashApplication
    val viewModel: PlaylistsViewModel = viewModel(factory = PlaylistsViewModelFactory(app.libraryRepository, app.youtubeSearchRepository))
    val playlists by viewModel.playlists.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val haptic = LocalHapticFeedback.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var playlistPendingDelete by remember { mutableStateOf<Playlist?>(null) }
    var playlistPendingRename by remember { mutableStateOf<Playlist?>(null) }
    var playlistPendingDownload by remember { mutableStateOf<Playlist?>(null) }
    // Real, reported safety gap (UAT audit finding): unlike every other
    // destructive action in this app (removing a single download,
    // clearing all downloads, clearing history), deleting an entire
    // playlist — which can represent significant curation effort —
    // previously fired immediately on tap with zero confirmation. This
    // holds the playlist awaiting a second, explicit confirm tap via
    // GlassConfirmDialog below, matching every other destructive action's
    // existing convention.
    var playlistPendingDeleteConfirm by remember { mutableStateOf<Playlist?>(null) }
    // 4.4: cover grid (default) or compact list; persisted and in backup.
    val listView by app.settingsRepository.playlistsListView.collectAsState(initial = false)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = GlassTokens.spaceMd, vertical = GlassTokens.spaceSm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Playlists", style = MaterialTheme.typography.titleMedium, color = WhiplashColors.textPrimary)
            Row {
                if (playlists.isNotEmpty()) {
                    PlainIconButton(
                        contentDescription = if (listView) "Show playlists as grid" else "Show playlists as list",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { app.settingsRepository.setPlaylistsListView(!listView) }
                        },
                        size = 48.dp,
                    ) {
                        Icon(
                            if (listView) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                            contentDescription = null,
                            tint = WhiplashColors.textPrimary,
                        )
                    }
                }
                // Import from a YouTube/YouTube Music playlist link —
                // disabled (rather than hidden) while an import is
                // already running so a second tap can't stack another
                // import on top of the one in progress, matching the
                // same enabled=!isRefreshing pattern Home's Quick Picks
                // refresh button already uses.
                PlainIconButton(
                    contentDescription = "Import playlist from YouTube",
                    onClick = { showImportDialog = true },
                    size = 48.dp,
                    enabled = !isImporting,
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(color = WhiplashColors.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Filled.Link, contentDescription = null, tint = WhiplashColors.textPrimary)
                    }
                }
                PlainIconButton(contentDescription = "New playlist", onClick = { showCreateDialog = true }, size = 48.dp) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = WhiplashColors.textPrimary)
                }
            }
        }

        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(horizontal = GlassTokens.spaceMd), contentAlignment = Alignment.Center) {
                Text(
                    text = "No playlists yet. Tap + to create one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WhiplashColors.textSecondary,
                )
            }
        } else if (!listView) {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = GlassTokens.spaceMd),
                horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceMd),
                verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceLg),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = GlassTokens.spaceXs,
                    bottom = GlassTokens.miniPlayerReservedHeight,
                ),
            ) {
                gridItems(playlists, key = { it.id }) { playlist ->
                    PlaylistGridTile(
                        playlist = playlist,
                        onClick = { onOpenPlaylist(playlist) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            playlistPendingDelete = playlist
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceXs),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = GlassTokens.miniPlayerReservedHeight),
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    val tracks by app.libraryRepository.observePlaylistTracks(playlist.id).collectAsState(initial = null)
                    GlassListItem(
                        title = playlist.name,
                        subtitle = playlistSubtitle(playlist, tracks?.size),
                        onClick = { onOpenPlaylist(playlist) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            playlistPendingDelete = playlist
                        },
                        leading = {
                            PlaylistCover(
                                artworks = tracks.orEmpty().mapNotNull { it.artworkUri }.take(4),
                                modifier = Modifier.size(56.dp),
                                cornerRadius = WhiplashRadius.small,
                            )
                        },
                        trailing = {
                            PlainIconButton(
                                contentDescription = "More options for ${playlist.name}",
                                onClick = { playlistPendingDelete = playlist },
                                size = 48.dp,
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = null, tint = WhiplashColors.textSecondary)
                            }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        GlassTextInputDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    if (showImportDialog) {
        GlassTextInputDialog(
            title = "Import playlist from YouTube",
            placeholder = "Paste a YouTube or YouTube Music playlist link",
            confirmLabel = "Import",
            onConfirm = { url ->
                viewModel.importPlaylist(url)
                showImportDialog = false
            },
            onDismiss = { showImportDialog = false },
        )
    }

    val toDelete = playlistPendingDelete
    if (toDelete != null) {
        // Real, reported bug: "Download playlist" showed unconditionally
        // here even for a playlist with zero tracks, offering an action
        // that can never do anything (PlaylistDetailScreen's own
        // download button already correctly hides itself when
        // tracks.isEmpty() — this sheet is a second, separate entry
        // point to the same action that didn't have the same guard).
        // observePlaylistTracks is the same call this sheet's own
        // download-confirm dialog below already makes for the same
        // playlist — cheap, and only evaluated for the single playlist
        // currently long-pressed, not for every row in the list.
        val tracksForSheet by app.libraryRepository.observePlaylistTracks(toDelete.id).collectAsState(initial = null)
        val hasTracks = tracksForSheet?.isNotEmpty() ?: true // null = still loading; assume non-empty so the row doesn't flash in/out
        GlassSheet(onDismissRequest = { playlistPendingDelete = null }) {
            Column {
                Text(
                    text = toDelete.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = WhiplashColors.textPrimary,
                    // Two lines rather than one: this is the sheet's own title
                    // with the full width to itself, so there is room to show
                    // more of a long imported-playlist name than the detail
                    // header can. It still has to stop somewhere — unbounded, a
                    // sentence-length name pushed the actions below it down the
                    // screen.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = GlassTokens.spaceSm),
                )
                if (hasTracks) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = GlassTokens.spaceSm)
                            .clickable(
                                onClick = {
                                    playlistPendingDownload = toDelete
                                    playlistPendingDelete = null
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, tint = WhiplashColors.textPrimary)
                        Text(
                            text = "Download playlist",
                            style = MaterialTheme.typography.bodyLarge,
                            color = WhiplashColors.textPrimary,
                            modifier = Modifier.padding(start = GlassTokens.spaceMd),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = GlassTokens.spaceSm)
                        .clickable(
                            onClick = {
                                viewModel.setPinned(toDelete, !toDelete.pinned)
                                playlistPendingDelete = null
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (toDelete.pinned) Icons.Outlined.PushPin else Icons.Filled.PushPin,
                        contentDescription = null,
                        tint = WhiplashColors.textPrimary,
                    )
                    Text(
                        text = if (toDelete.pinned) "Unpin from top" else "Pin to top",
                        style = MaterialTheme.typography.bodyLarge,
                        color = WhiplashColors.textPrimary,
                        modifier = Modifier.padding(start = GlassTokens.spaceMd),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = GlassTokens.spaceSm)
                        .clickable(
                            onClick = {
                                playlistPendingRename = toDelete
                                playlistPendingDelete = null
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = WhiplashColors.textPrimary)
                    Text(
                        text = "Rename playlist",
                        style = MaterialTheme.typography.bodyLarge,
                        color = WhiplashColors.textPrimary,
                        modifier = Modifier.padding(start = GlassTokens.spaceMd),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = GlassTokens.spaceSm)
                        .clickable(
                            onClick = {
                                playlistPendingDeleteConfirm = toDelete
                                playlistPendingDelete = null
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = WhiplashColors.error)
                    Text(
                        text = "Delete playlist",
                        style = MaterialTheme.typography.bodyLarge,
                        color = WhiplashColors.error,
                        modifier = Modifier.padding(start = GlassTokens.spaceMd),
                    )
                }
            }
        }
    }

    val toConfirmDelete = playlistPendingDeleteConfirm
    if (toConfirmDelete != null) {
        com.whiplash.music.ui.theme.GlassConfirmDialog(
            title = "Delete playlist?",
            message = "\"${toConfirmDelete.name}\" will be permanently deleted. This can't be undone.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            onConfirm = {
                viewModel.deletePlaylist(toConfirmDelete.id, toConfirmDelete.name)
                playlistPendingDeleteConfirm = null
            },
            onDismiss = { playlistPendingDeleteConfirm = null },
        )
    }

    val toRename = playlistPendingRename
    if (toRename != null) {
        GlassTextInputDialog(
            title = "Rename playlist",
            initialValue = toRename.name,
            confirmLabel = "Save",
            onConfirm = { newName ->
                viewModel.renamePlaylist(toRename.id, newName, toRename.description)
                playlistPendingRename = null
            },
            onDismiss = { playlistPendingRename = null },
        )
    }

    val toDownload = playlistPendingDownload
    if (toDownload != null) {
        // Only YoutubeTrack entries can actually be downloaded — a
        // LocalTrack is already on-device and a DownloadedTrack already
        // in this playlist is already downloaded (same reasoning as
        // PlaylistDetailScreen's own "Download playlist" button).
        val tracks by app.libraryRepository.observePlaylistTracks(toDownload.id).collectAsState(initial = null)
        when (val current = tracks) {
            null -> Unit // still loading; avoid showing a confirm dialog with a wrong/empty count
            else -> {
                val downloadable = current.filterIsInstance<com.whiplash.music.domain.model.PlayableItem.YoutubeTrack>()
                com.whiplash.music.ui.theme.GlassConfirmDialog(
                    title = "Download playlist?",
                    message = if (downloadable.isEmpty()) {
                        "None of the songs in \"${toDownload.name}\" can be downloaded (already local or already downloaded)."
                    } else {
                        "All ${downloadable.size} songs in \"${toDownload.name}\" will be downloaded for offline playback."
                    },
                    confirmLabel = "Download",
                    onConfirm = {
                        app.downloadManager.downloadAll(downloadable)
                        playlistPendingDownload = null
                    },
                    onDismiss = { playlistPendingDownload = null },
                )
            }
        }
    }
}

private fun playlistSubtitle(playlist: Playlist, count: Int?): String? {
    val songs = when (count) {
        null -> null
        1 -> "1 song"
        else -> "$count songs"
    }
    return when {
        playlist.pinned && songs != null -> "Pinned · $songs"
        playlist.pinned -> "Pinned"
        else -> songs
    }
}

/** 4.4: cover-first grid card — mosaic artwork, name, song count, pin badge. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistGridTile(
    playlist: Playlist,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as WhiplashApplication
    val tracks by app.libraryRepository.observePlaylistTracks(playlist.id).collectAsState(initial = null)
    val subtitle = playlistSubtitle(playlist, tracks?.size)
    Column(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Playlist options")
            .semantics(mergeDescendants = true) {},
    ) {
        Box {
            PlaylistCover(
                artworks = tracks.orEmpty().mapNotNull { it.artworkUri }.take(4),
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                cornerRadius = WhiplashRadius.medium,
            )
            if (playlist.pinned) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(GlassTokens.spaceSm)
                        .size(28.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PushPin, contentDescription = "Pinned", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleSmall,
            color = WhiplashColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = GlassTokens.spaceSm),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = WhiplashColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Playlist artwork: a 2×2 mosaic when there are four covers, one cover when
 * there are fewer, and a tinted music-note tile for an empty playlist.
 */
@Composable
private fun PlaylistCover(
    artworks: List<String>,
    modifier: Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(WhiplashColors.surfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        when {
            artworks.size >= 4 -> Column {
                for (row in 0 until 2) {
                    Row(modifier = Modifier.weight(1f)) {
                        for (col in 0 until 2) {
                            CoverImage(artworks[row * 2 + col], Modifier.weight(1f).fillMaxSize())
                        }
                    }
                }
            }
            artworks.isNotEmpty() -> CoverImage(artworks.first(), Modifier.fillMaxSize())
            else -> Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                tint = WhiplashColors.textSecondary,
                modifier = Modifier.fillMaxSize(0.4f),
            )
        }
    }
}

@Composable
private fun CoverImage(uri: String, modifier: Modifier) {
    coil.compose.AsyncImage(
        model = coil.request.ImageRequest.Builder(LocalContext.current).data(uri).crossfade(true).build(),
        contentDescription = null,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = modifier,
    )
}
