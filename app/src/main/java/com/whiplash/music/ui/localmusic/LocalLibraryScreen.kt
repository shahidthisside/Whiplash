package com.whiplash.music.ui.localmusic

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whiplash.music.domain.model.LocalAlbum
import com.whiplash.music.domain.model.LocalArtist
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.localmedia.LocalMediaPermission
import com.whiplash.music.ui.theme.GlassButton
import com.whiplash.music.ui.theme.GlassListItem
import com.whiplash.music.ui.theme.GlassSearchField
import com.whiplash.music.ui.theme.GlassTabRow
import com.whiplash.music.ui.theme.GlassTokens

private enum class LibraryTab(val label: String) {
    DOWNLOADS("Downloads"),
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun LocalLibraryScreen(
    onPlayQueue: (queue: List<PlayableItem>, startIndex: Int) -> Unit = { _, _ -> },
    onAlbumClick: (LocalAlbum) -> Unit = {},
    onArtistClick: (LocalArtist) -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel: LocalLibraryViewModel = viewModel(factory = LocalLibraryViewModelFactory(context))

    var hasPermission by rememberSaveable { mutableStateOf(LocalMediaPermission.isGranted(context)) }
    var permissionPermanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        viewModel.onPermissionResult(granted)
        if (!granted) permissionPermanentlyDenied = true
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.onPermissionResult(true)
    }

    // Real, reported bug: the MediaStore permission prompt used to replace
    // this entire screen (search bar, tab row, and every tab including
    // Downloads) whenever local media permission hadn't been granted —
    // but Downloads lives in app-private storage and never needed that
    // permission at all, so a user who'd never granted (or had denied)
    // device-media access had no way to reach their downloaded songs,
    // even though nothing about Downloads actually required that
    // permission. The search bar and tab row (Songs/Albums/Artists/
    // Downloads) now always render; only the SONGS/ALBUMS/ARTISTS tabs'
    // own content is replaced by the permission prompt when needed (see
    // LibraryContent) — Downloads works regardless of this permission's
    // state, exactly as it should.
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = GlassTokens.spaceMd)) {
        LibraryContent(
            viewModel = viewModel,
            onPlayQueue = onPlayQueue,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            hasMediaPermission = hasPermission,
            permissionPermanentlyDenied = permissionPermanentlyDenied,
            onRequestPermission = { permissionLauncher.launch(LocalMediaPermission.permission) },
            onOpenSettings = {
                // Guarded for the same reason as the GitHub footer link in
                // SettingsScreen (which was a confirmed on-device crash):
                // an unhandled intent throws ActivityNotFoundException and
                // kills the process. This particular action exists on
                // essentially every real device, but a bare startActivity is
                // never worth a potential hard crash.
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                    )
                }.onFailure {
                    com.whiplash.music.ui.common.ToastController.show("Couldn't open app settings")
                }
            },
        )
    }
}


@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun LibraryContent(
    viewModel: LocalLibraryViewModel,
    onPlayQueue: (List<PlayableItem>, Int) -> Unit,
    onAlbumClick: (LocalAlbum) -> Unit,
    onArtistClick: (LocalArtist) -> Unit,
    hasMediaPermission: Boolean,
    permissionPermanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // Defaults to Downloads (not Songs) — Downloads is the only tab that
    // never needs the local-media permission (see LocalLibraryScreen's
    // own doc above), so opening the Library tab lands somewhere
    // immediately useful even for a user who hasn't granted (or has
    // denied) device-media access yet, rather than landing on a
    // permission prompt by default.
    var selectedTab by rememberSaveable { mutableStateOf(LibraryTab.DOWNLOADS) }
    val isScanning by viewModel.isScanning.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val inFlightTracks by viewModel.inFlightTracks.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val downloadSearchResults by viewModel.downloadSearchResults.collectAsState()
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    GlassSearchField(
        query = searchQuery,
        onQueryChange = viewModel::onSearchQueryChanged,
        placeholder = if (selectedTab == LibraryTab.DOWNLOADS) "Search downloads..." else "Search your music...",
        modifier = Modifier.fillMaxWidth(),
    )

    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceMd))

    // While a search query is active, it takes over the whole content area
    // (tabs hidden) — matches the same "search overlays the current view"
    // pattern as the online SearchScreen, rather than trying to filter
    // three different tab contents (songs/albums/artists) simultaneously.
    // Search is scope-aware (section: search should follow the selected
    // tab): the DOWNLOADS tab searches only downloaded tracks (in-memory
    // filter, no media permission needed — Downloads never required it);
    // every other tab searches the local MediaStore-backed library, which
    // does need the permission, same as the Songs/Albums/Artists tabs
    // below.
    if (searchQuery.isNotBlank()) {
        if (selectedTab == LibraryTab.DOWNLOADS) {
            DownloadSearchResults(
                query = searchQuery,
                results = downloadSearchResults,
                onPlayQueue = { queue, index ->
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onPlayQueue(queue, index)
                },
            )
            return
        }
        if (!hasMediaPermission) {
            PermissionRequestState(
                permanentlyDenied = permissionPermanentlyDenied,
                onRequestPermission = onRequestPermission,
                onOpenSettings = onOpenSettings,
            )
            return
        }
        LocalSearchResults(
            query = searchQuery,
            results = searchResults,
            onPlayQueue = { queue, index ->
                keyboardController?.hide()
                focusManager.clearFocus()
                onPlayQueue(queue, index)
            },
        )
        return
    }

    // Extra 8dp start inset so "Songs"/"Albums"/etc's own text (which sits
    // GlassTokens.spaceMd inside each GlassChip, on top of this Column's
    // own spaceMd outer padding — 32dp total) lines up with
    // GlassSearchField's placeholder/typed text above it (which has its
    // own spaceLg internal padding on top of the same spaceMd outer
    // padding — 40dp total). Without this, the two rows' text visibly
    // started at different x positions — a reported misalignment.
    GlassTabRow(
        items = LibraryTab.entries,
        selected = selectedTab,
        onSelect = { selectedTab = it },
        label = { tab ->
            val count = when (tab) {
                LibraryTab.SONGS -> songs.size
                LibraryTab.ALBUMS -> albums.size
                LibraryTab.ARTISTS -> artists.size
                LibraryTab.DOWNLOADS -> downloads.size
            }
            if (count > 0) "${tab.label} ($count)" else tab.label
        },
        modifier = Modifier.padding(start = GlassTokens.spaceLg - GlassTokens.spaceMd),
    )

    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceMd))

    val isEmpty = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()

    // Real, reported bug: the MediaStore permission prompt used to
    // replace this entire screen — including the Downloads tab, which
    // lives in app-private storage and never needed this permission at
    // all. Now it only replaces the SONGS/ALBUMS/ARTISTS tabs' own
    // content; DOWNLOADS is checked first and is completely unaffected
    // by hasMediaPermission's value either way.
    when {
        selectedTab == LibraryTab.DOWNLOADS -> DownloadList(
            downloads = downloads,
            downloadProgress = downloadProgress,
            inFlightTracks = inFlightTracks,
            onPlayQueue = onPlayQueue,
            onClearAll = viewModel::clearAllDownloads,
        )
        !hasMediaPermission -> PermissionRequestState(
            permanentlyDenied = permissionPermanentlyDenied,
            onRequestPermission = onRequestPermission,
            onOpenSettings = onOpenSettings,
        )
        isScanning && isEmpty -> LoadingState()
        isEmpty -> EmptyLibraryState(onRescan = viewModel::rescan)
        else -> when (selectedTab) {
            LibraryTab.SONGS -> SongList(songs, onPlayQueue)
            LibraryTab.ALBUMS -> AlbumList(albums, onAlbumClick)
            LibraryTab.ARTISTS -> ArtistList(artists, onArtistClick)
            LibraryTab.DOWNLOADS -> Unit // handled above
        }
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun LocalSearchResults(
    query: String,
    results: List<PlayableItem.LocalTrack>,
    onPlayQueue: (List<PlayableItem>, Int) -> Unit,
) {
    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No local songs match \"$query\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    com.whiplash.music.ui.player.PlayableItemsList(
        items = results,
        onPlayQueue = { _, index -> onPlayQueue(results, index) },
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * Downloads-scope search results (Library > Downloads tab, section:
 * search bar should follow the selected tab): mirrors [LocalSearchResults]
 * exactly, but over [PlayableItem.DownloadedTrack] filtered in-memory by
 * [LocalLibraryViewModel.downloadSearchResults] rather than a Room query —
 * Downloads never needed the media permission and this search doesn't
 * either.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun DownloadSearchResults(
    query: String,
    results: List<PlayableItem.DownloadedTrack>,
    onPlayQueue: (List<PlayableItem>, Int) -> Unit,
) {
    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No downloaded songs match \"$query\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    com.whiplash.music.ui.player.PlayableItemsList(
        items = results,
        onPlayQueue = { _, index -> onPlayQueue(results, index) },
        modifier = Modifier.fillMaxSize(),
    )
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun SongList(songs: List<PlayableItem.LocalTrack>, onPlayQueue: (List<PlayableItem>, Int) -> Unit) {
    com.whiplash.music.ui.player.PlayableItemsList(
        items = songs,
        onPlayQueue = { _, index -> onPlayQueue(songs, index) },
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * Downloads tab (Library > Downloads, YouTube-Music-style offline
 * downloads): reuses [com.whiplash.music.ui.player.PlayableItemsList] —
 * the same 3-dot / long-press song actions sheet every other track list
 * in the app uses (Play next / Add to queue / Save to playlist / Remove
 * download / Share for a downloaded track, see the isDownloadedTrack
 * scoping in PlayableItemsList), and the same animated progress-ring
 * badge for anything currently downloading.
 *
 * A currently-downloading track is shown as a full, real row (real
 * title/artist/artwork via [inFlightTracks], not a bare "Downloading…"
 * placeholder) at the top of the list, ahead of completed downloads —
 * [PlayableItemsList] itself renders the progress ring/tap-to-cancel for
 * it via the same [com.whiplash.music.data.download.DownloadManager]
 * state every other screen's badge reads.
 *
 * The Play all / Shuffle / Clear all row is a plain fixed [Row] ABOVE
 * [PlayableItemsList] (not inside its scrolling `header` slot) so it
 * stays pinned in place while the list scrolls — previously it scrolled
 * away with the first download, a reported bug.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun DownloadList(
    downloads: List<PlayableItem.DownloadedTrack>,
    downloadProgress: Map<String, com.whiplash.music.data.download.DownloadProgress>,
    inFlightTracks: Map<String, PlayableItem.YoutubeTrack>,
    onPlayQueue: (List<PlayableItem>, Int) -> Unit,
    onClearAll: () -> Unit,
) {
    var showClearAllConfirm by remember { mutableStateOf(false) }

    // In-flight tracks first (most-recently-started-feeling order,
    // matches YouTube Music's own "downloading at the top" placement),
    // then completed downloads. A track id can only ever be in one of
    // the two — inFlightTracks is cleared the moment a download finishes
    // or fails (see DownloadManager) — so there's no risk of the same
    // song appearing twice during the brief handoff between the two
    // states; the row's own key (source:id) would collide anyway.
    val rows: List<PlayableItem> = inFlightTracks.values.toList() + downloads

    if (rows.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(GlassTokens.spaceLg)) {
                Text(
                    text = "No downloads yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceSm))
                Text(
                    text = "Download a song from its \u22ee menu to listen offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            // Horizontal padding matches GlassListItem's own row padding
            // (GlassTokens.spaceMd) so Shuffle's left edge lines up with
            // the artwork thumbnail below it, and Clear all's right edge
            // lines up with the 3-dot "more options" button below it.
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GlassTokens.spaceMd, vertical = GlassTokens.spaceSm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm)) {
                com.whiplash.music.ui.theme.PlainIconButton(
                    contentDescription = "Shuffle play downloads",
                    onClick = { onPlayQueue(downloads.shuffled(), 0) },
                    enabled = downloads.isNotEmpty(),
                ) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = null,
                        tint = com.whiplash.music.ui.theme.WhiplashColors.textPrimary,
                    )
                }
                com.whiplash.music.ui.theme.PlainIconButton(
                    contentDescription = "Play all downloads",
                    onClick = { onPlayQueue(downloads, 0) },
                    enabled = downloads.isNotEmpty(),
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = com.whiplash.music.ui.theme.WhiplashColors.textPrimary,
                    )
                }
            }
            com.whiplash.music.ui.theme.PlainIconButton(
                contentDescription = "Clear all downloads",
                onClick = { showClearAllConfirm = true },
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = com.whiplash.music.ui.theme.WhiplashColors.textPrimary,
                )
            }
        }

        com.whiplash.music.ui.player.PlayableItemsList(
            items = rows,
            onPlayQueue = onPlayQueue,
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (showClearAllConfirm) {
        com.whiplash.music.ui.theme.GlassConfirmDialog(
            title = "Clear all downloads?",
            message = "This will cancel any in-progress downloads and permanently delete every downloaded song from this device. This can't be undone.",
            confirmLabel = "Clear all",
            onConfirm = {
                onClearAll()
                showClearAllConfirm = false
            },
            onDismiss = { showClearAllConfirm = false },
        )
    }
}

@Composable
private fun AlbumList(albums: List<LocalAlbum>, onAlbumClick: (LocalAlbum) -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceXs),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = GlassTokens.miniPlayerReservedHeight),
    ) {
        items(albums, key = { it.id }) { album ->
            GlassListItem(
                title = album.title,
                subtitle = "${album.artist} · ${album.songCount} songs",
                onClick = { onAlbumClick(album) },
            )
        }
    }
}

@Composable
private fun ArtistList(artists: List<LocalArtist>, onArtistClick: (LocalArtist) -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceXs),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = GlassTokens.miniPlayerReservedHeight),
    ) {
        items(artists, key = { it.id }) { artist ->
            GlassListItem(
                title = artist.name,
                subtitle = "${artist.trackCount} songs · ${artist.albumCount} albums",
                onClick = { onArtistClick(artist) },
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyLibraryState(onRescan: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Plain, borderless layout matching PermissionRequestState's own
        // style exactly (a real, reported inconsistency: this state used
        // to wrap its text in a GlassCard, which has a visible border,
        // while the permission-prompt empty state right next to it in
        // the same tab flow had no border at all — the two empty states
        // visibly disagreed on style for what's otherwise the same kind
        // of "nothing to show here" message).
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(GlassTokens.spaceLg),
        ) {
            Text(
                text = "No local music found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceSm))
            Text(
                text = "We couldn't find any songs on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceMd))
            GlassButton(text = "Rescan", onClick = onRescan)
        }
    }
}

@Composable
private fun PermissionRequestState(
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(GlassTokens.spaceLg),
        ) {
            Text(
                text = "Music permission needed",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceSm))
            Text(
                text = "Whiplash needs access to your device's audio files to show your local music library.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceMd))
            GlassButton(
                text = if (permanentlyDenied) "Open Settings" else "Grant Access",
                onClick = if (permanentlyDenied) onOpenSettings else onRequestPermission,
            )
        }
    }
}
