package com.whiplash.music.ui.playlists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import com.whiplash.music.ui.common.selectedHighlight
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.PlayArrow
import kotlinx.coroutines.flow.first
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
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
    // Custom cover: the options sheet, then the song picker or the gallery.
    var coverTarget by remember { mutableStateOf<Playlist?>(null) }
    // "Add all to Favorites" from a playlist's menu (asks first).
    var favoritesTarget by remember { mutableStateOf<Playlist?>(null) }
    var coverSongsTarget by remember { mutableStateOf<Playlist?>(null) }
    var galleryTarget by remember { mutableStateOf<Playlist?>(null) }
    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val target = galleryTarget
        galleryTarget = null
        if (uri != null && target != null) viewModel.setGalleryCover(context, target, uri)
    }
    // 4.4: cover grid (default) or compact list; persisted and in backup.
    val listView by app.settingsRepository.playlistsListView.collectAsState(initial = false)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // ---- Multi-select (started from a playlist's menu → Select) ----------
    val selection = com.whiplash.music.ui.common.rememberItemSelection(playlists) { it.id.toString() }
    var bulkDeleteConfirm by remember { mutableStateOf<List<Playlist>?>(null) }
    // All songs of the selected playlists, in order (a song in two playlists plays twice, like "Play all" would).
    suspend fun selectedTracks(): List<com.whiplash.music.domain.model.PlayableItem> =
        selection.selected().flatMap { app.libraryRepository.observePlaylistTracks(it.id).first() }
    fun withTracks(block: (List<com.whiplash.music.domain.model.PlayableItem>) -> Unit) {
        scope.launch {
            val tracks = selectedTracks()
            if (tracks.isEmpty()) com.whiplash.music.ui.common.ToastController.show("These playlists are empty")
            else block(tracks)
            selection.clear()
        }
    }
    run {
        val chosen = selection.selected()
        val allPinned = chosen.isNotEmpty() && chosen.all { it.pinned }
        com.whiplash.music.ui.common.SelectionChrome(
            selection = selection,
            noun = "playlist",
            primary = listOf(
                com.whiplash.music.ui.common.action("Play", Icons.Filled.PlayArrow) { withTracks { app.playbackController.playQueue(it, 0) } },
                com.whiplash.music.ui.common.action("Add to queue", Icons.AutoMirrored.Filled.QueueMusic) { withTracks { app.playbackController.addAllToQueue(it) } },
                com.whiplash.music.ui.common.action("Download", Icons.Filled.Download) {
                    withTracks { tracks ->
                        val todo = tracks.filterIsInstance<com.whiplash.music.domain.model.PlayableItem.YoutubeTrack>().distinctBy { it.id }
                        if (todo.isEmpty()) com.whiplash.music.ui.common.ToastController.show("Nothing to download")
                        else app.downloadManager.downloadAll(todo)
                    }
                },
            ),
            more = listOf(
                com.whiplash.music.ui.common.action("Shuffle play", Icons.Filled.Shuffle) { withTracks { app.playbackController.playQueue(it.shuffled(), 0) } },
                com.whiplash.music.ui.common.action("Add all to Favorites", Icons.Filled.FavoriteBorder) {
                    withTracks { tracks ->
                        scope.launch {
                            val n = app.libraryRepository.addAllToFavorites(tracks)
                            com.whiplash.music.ui.common.ToastController.show(if (n == 0) "Already in favorites" else if (n == 1) "1 song added to favorites" else "$n songs added to favorites")
                        }
                    }
                },
                com.whiplash.music.ui.common.action(if (allPinned) "Unpin from top" else "Pin to top", if (allPinned) Icons.Outlined.PushPin else Icons.Filled.PushPin) {
                    viewModel.setPinnedAll(chosen, !allPinned); selection.clear()
                },
                com.whiplash.music.ui.common.action("Delete playlists", Icons.Filled.Delete, destructive = true) { bulkDeleteConfirm = chosen },
            ),
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ModernPlaylistsActions(
            isImporting = isImporting,
            showLayoutToggle = playlists.isNotEmpty(),
            listView = listView,
            onNew = { showCreateDialog = true },
            onImport = { showImportDialog = true },
            onToggleLayout = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scope.launch { app.settingsRepository.setPlaylistsListView(!listView) }
            },
        )

        if (playlists.isEmpty()) {
            com.whiplash.music.ui.theme.CollectionEmptyState(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                title = "No playlists yet",
                message = "Make one for any mood, or bring one over from YouTube with its link.",
                tint = WhiplashColors.accent,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.whiplash.music.ui.theme.CollectionPillButton("New playlist", Icons.Filled.Add, { showCreateDialog = true }, primary = true)
                    com.whiplash.music.ui.theme.CollectionPillButton("Import", Icons.Filled.Link, { showImportDialog = true }, primary = false, enabled = !isImporting)
                }
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
                        selected = selection.isSelected(playlist),
                        onClick = { if (selection.selecting) selection.toggle(playlist) else onOpenPlaylist(playlist) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (selection.selecting) selection.toggle(playlist) else playlistPendingDelete = playlist
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
                    val tracks by remember(playlist.id) { app.libraryRepository.observePlaylistTracks(playlist.id) }.collectAsState(initial = null)
                    GlassListItem(
                        title = playlist.name,
                        subtitle = playlistSubtitle(playlist, tracks?.size),
                        onClick = { if (selection.selecting) selection.toggle(playlist) else onOpenPlaylist(playlist) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (selection.selecting) selection.toggle(playlist) else playlistPendingDelete = playlist
                        },
                        leading = {
                          Box(contentAlignment = Alignment.Center) {
                            PlaylistArtwork(
                                playlist = playlist,
                                tracks = tracks,
                                modifier = Modifier.size(56.dp),
                                cornerRadius = WhiplashRadius.small,
                                emptyTile = { m ->
                                    com.whiplash.music.ui.theme.GradientIconCover(
                                        Icons.AutoMirrored.Filled.QueueMusic,
                                        com.whiplash.music.ui.theme.tintForName(playlist.name),
                                        m,
                                    )
                                },
                            )
                            com.whiplash.music.ui.common.SelectionCheck(visible = selection.isSelected(playlist))
                          }
                        },
                        trailing = {
                            if (selection.selecting) {
                                androidx.compose.foundation.layout.Spacer(Modifier.size(48.dp))
                            } else {
                                PlainIconButton(
                                    contentDescription = "More options for ${playlist.name}",
                                    onClick = { playlistPendingDelete = playlist },
                                    size = 48.dp,
                                ) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = null, tint = WhiplashColors.textSecondary)
                                }
                            }
                        },
                        modifier = Modifier.animateItem().selectedHighlight(selection.isSelected(playlist)),
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
        val tracksForSheet by remember(toDelete.id) { app.libraryRepository.observePlaylistTracks(toDelete.id) }.collectAsState(initial = null)
        val hasTracks = tracksForSheet?.isNotEmpty() ?: true // null = still loading; assume non-empty so the row doesn't flash in/out
        val menuTracks by remember(toDelete.id) { app.libraryRepository.observePlaylistTracks(toDelete.id) }.collectAsState(initial = null)
        val menuAllFavorited = com.whiplash.music.ui.common.rememberAllFavorited(menuTracks) == true
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = GlassTokens.spaceSm)
                        .clickable(
                            onClick = {
                                selection.start(toDelete)
                                playlistPendingDelete = null
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CheckCircleOutline, contentDescription = null, tint = WhiplashColors.textPrimary)
                    Text(
                        text = "Select",
                        style = MaterialTheme.typography.bodyLarge,
                        color = WhiplashColors.textPrimary,
                        modifier = Modifier.padding(start = GlassTokens.spaceMd),
                    )
                }
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
                                coverTarget = toDelete
                                playlistPendingDelete = null
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, tint = WhiplashColors.textPrimary)
                    Text(
                        text = "Change cover",
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
                                favoritesTarget = toDelete
                                playlistPendingDelete = null
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (menuAllFavorited) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = if (menuAllFavorited) WhiplashColors.accent else WhiplashColors.textPrimary,
                    )
                    Text(
                        text = if (menuAllFavorited) "Remove all from Favorites" else "Add all to Favorites",
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

    val bulkDelete = bulkDeleteConfirm
    if (bulkDelete != null) {
        val n = bulkDelete.size
        com.whiplash.music.ui.theme.GlassConfirmDialog(
            title = if (n == 1) "Delete 1 playlist?" else "Delete $n playlists?",
            message = "The songs stay in your library. This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.deletePlaylists(bulkDelete)
                bulkDeleteConfirm = null
                selection.clear()
            },
            onDismiss = { bulkDeleteConfirm = null },
        )
    }

    val favFor = favoritesTarget
    if (favFor != null) {
        val favTracks by remember(favFor.id) { app.libraryRepository.observePlaylistTracks(favFor.id) }.collectAsState(initial = null)
        val ready = favTracks
        // Decided once when the dialog opens, so it doesn't flip mid-dialog.
        val removing = remember(favFor.id) { mutableStateOf<Boolean?>(null) }
        val allFav = com.whiplash.music.ui.common.rememberAllFavorited(ready)
        if (removing.value == null && allFav != null) removing.value = allFav
        when {
            ready == null -> Unit // loading (a few ms)
            ready.isEmpty() -> {
                androidx.compose.runtime.LaunchedEffect(favFor.id) {
                    com.whiplash.music.ui.common.ToastController.show("This playlist is empty")
                    favoritesTarget = null
                }
            }
            removing.value == null -> Unit
            else -> com.whiplash.music.ui.common.FavoriteAllDialogs(
                name = favFor.name,
                tracks = ready,
                confirmAdd = removing.value == false,
                confirmRemove = removing.value == true,
                onDismiss = { favoritesTarget = null },
            )
        }
    }

    // Look the playlist up again so the sheets show a cover change straight away.
    val coverFor = coverTarget?.let { t -> playlists.firstOrNull { it.id == t.id } ?: t }
    if (coverFor != null) {
        val coverTracks by remember(coverFor.id) { app.libraryRepository.observePlaylistTracks(coverFor.id) }.collectAsState(initial = null)
        PlaylistCoverOptionsSheet(
            playlist = coverFor,
            tracks = coverTracks,
            onAutomatic = {
                viewModel.setCover(coverFor, com.whiplash.music.domain.model.PlaylistArt.Auto)
                coverTarget = null
            },
            onChooseSongs = {
                coverSongsTarget = coverFor
                coverTarget = null
            },
            onChooseGallery = {
                galleryTarget = coverFor
                coverTarget = null
                galleryLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ),
                )
            },
            onDismiss = { coverTarget = null },
        )
    }

    val songsFor = coverSongsTarget?.let { t -> playlists.firstOrNull { it.id == t.id } ?: t }
    if (songsFor != null) {
        val pickTracks by remember(songsFor.id) { app.libraryRepository.observePlaylistTracks(songsFor.id) }.collectAsState(initial = null)
        pickTracks?.let { list ->
            PlaylistCoverSongPicker(
                playlist = songsFor,
                tracks = list,
                onSave = { uris ->
                    viewModel.setCover(songsFor, com.whiplash.music.domain.model.PlaylistArt.Songs(uris))
                    coverSongsTarget = null
                },
                onDismiss = { coverSongsTarget = null },
            )
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
        val tracks by remember(toDownload.id) { app.libraryRepository.observePlaylistTracks(toDownload.id) }.collectAsState(initial = null)
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
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as WhiplashApplication
    val tracks by remember(playlist.id) { app.libraryRepository.observePlaylistTracks(playlist.id) }.collectAsState(initial = null)
    val subtitle = playlistSubtitle(playlist, tracks?.size)
    Column(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Playlist options")
            .semantics(mergeDescendants = true) {},
    ) {
        Box {
            PlaylistArtwork(
                playlist = playlist,
                tracks = tracks,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                cornerRadius = WhiplashRadius.medium,
                // An empty playlist gets its own colour and initial rather than a grey tile.
                emptyTile = { m -> NamedPlaylistTile(playlist.name, m) },
            )
            if (selected) {
                // Dim the cover and lay the checkmark in its centre.
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(WhiplashRadius.medium))
                        .background(WhiplashColors.background.copy(alpha = 0.45f)),
                )
            }
            com.whiplash.music.ui.common.SelectionCheck(
                visible = selected,
                modifier = Modifier.align(Alignment.Center),
                size = 44.dp,
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
internal fun PlaylistCover(
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


/** Modern Playlists header: New and Import pills, plus the grid/list switch. */
@Composable
private fun ModernPlaylistsActions(
    isImporting: Boolean,
    showLayoutToggle: Boolean,
    listView: Boolean,
    onNew: () -> Unit,
    onImport: () -> Unit,
    onToggleLayout: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = GlassTokens.spaceMd, end = GlassTokens.spaceXs, top = GlassTokens.spaceXs, bottom = GlassTokens.spaceMd),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.whiplash.music.ui.theme.CollectionPillButton("New", Icons.Filled.Add, onNew, primary = true)
        Box {
            com.whiplash.music.ui.theme.CollectionPillButton(
                text = if (isImporting) "Importing…" else "Import",
                icon = Icons.Filled.Link,
                onClick = onImport,
                primary = false,
                enabled = !isImporting,
                modifier = Modifier.semantics { contentDescription = "Import playlist from YouTube" },
            )
        }
        if (isImporting) {
            CircularProgressIndicator(color = WhiplashColors.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        }
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        if (showLayoutToggle) {
            PlainIconButton(
                contentDescription = if (listView) "Show playlists as grid" else "Show playlists as list",
                onClick = onToggleLayout,
                size = 48.dp,
            ) {
                Icon(
                    if (listView) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                    contentDescription = null,
                    tint = WhiplashColors.textSecondary,
                )
            }
        }
    }
}

/** Colour tile with the playlist's initial, for a playlist with no songs yet. */
@Composable
private fun NamedPlaylistTile(name: String, modifier: Modifier) {
    val tint = com.whiplash.music.ui.theme.tintForName(name)
    Box(
        modifier = modifier.background(
            androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(
                    androidx.compose.ui.graphics.lerp(tint, androidx.compose.ui.graphics.Color.White, 0.10f),
                    androidx.compose.ui.graphics.lerp(tint, androidx.compose.ui.graphics.Color.Black, 0.6f),
                ),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase() ?: "♪",
            style = MaterialTheme.typography.displayMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f),
        )
    }
}

/**
 * A playlist's cover, honouring a custom choice ([PlaylistArt]):
 * a gallery picture, a mosaic of chosen song covers, or — by default — the
 * cover built from its songs. [emptyTile] replaces the grey tile for a
 * playlist with no artwork. A gallery picture that can't load (say, after
 * restoring a backup on another phone) falls back to the automatic cover.
 */
@Composable
internal fun PlaylistArtwork(
    playlist: Playlist,
    tracks: List<com.whiplash.music.domain.model.PlayableItem>?,
    modifier: Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp,
    emptyTile: (@Composable (Modifier) -> Unit)? = null,
) {
    val art = remember(playlist.artworkUrl) { com.whiplash.music.domain.model.PlaylistArt.parse(playlist.artworkUrl) }
    var imageFailed by remember(art) { mutableStateOf(false) }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)
    when {
        art is com.whiplash.music.domain.model.PlaylistArt.Image && !imageFailed -> Box(
            modifier = modifier.clip(shape).background(WhiplashColors.surfaceElevated),
        ) {
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current).data(art.uri).crossfade(true).build(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                onError = { imageFailed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
        art is com.whiplash.music.domain.model.PlaylistArt.Songs -> MosaicCover(art.artworks, modifier, cornerRadius)
        else -> {
            val arts = tracks.orEmpty().mapNotNull { it.artworkUri }.take(4)
            if (emptyTile != null && tracks != null && arts.isEmpty()) {
                emptyTile(modifier.clip(shape))
            } else {
                PlaylistCover(artworks = arts, modifier = modifier, cornerRadius = cornerRadius)
            }
        }
    }
}

/**
 * Chosen song covers: one fills the tile, two sit side by side, three put
 * one tall cover beside two stacked, four make a 2×2 grid.
 */
@Composable
internal fun MosaicCover(artworks: List<String>, modifier: Modifier, cornerRadius: androidx.compose.ui.unit.Dp) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)
    val a = artworks.take(4)
    Box(modifier = modifier.clip(shape).background(WhiplashColors.surfaceElevated)) {
        when (a.size) {
            0 -> Unit
            1 -> CoverImage(a[0], Modifier.fillMaxSize())
            2 -> Row(Modifier.fillMaxSize()) {
                CoverImage(a[0], Modifier.weight(1f).fillMaxSize())
                CoverImage(a[1], Modifier.weight(1f).fillMaxSize())
            }
            3 -> Row(Modifier.fillMaxSize()) {
                CoverImage(a[0], Modifier.weight(1f).fillMaxSize())
                Column(Modifier.weight(1f).fillMaxSize()) {
                    CoverImage(a[1], Modifier.weight(1f).fillMaxWidth())
                    CoverImage(a[2], Modifier.weight(1f).fillMaxWidth())
                }
            }
            else -> Column(Modifier.fillMaxSize()) {
                for (row in 0 until 2) {
                    Row(Modifier.weight(1f)) {
                        for (col in 0 until 2) CoverImage(a[row * 2 + col], Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
        }
    }
}
