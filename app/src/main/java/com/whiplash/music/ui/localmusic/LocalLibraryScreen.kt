package com.whiplash.music.ui.localmusic

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
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
import com.whiplash.music.ui.theme.GlassCard
import com.whiplash.music.ui.theme.GlassListItem
import com.whiplash.music.ui.theme.GlassSearchField
import com.whiplash.music.ui.theme.GlassTabRow
import com.whiplash.music.ui.theme.GlassTokens

private enum class LibraryTab(val label: String) {
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun LocalLibraryScreen(
    onPlayQueue: (queue: List<PlayableItem.LocalTrack>, startIndex: Int) -> Unit = { _, _ -> },
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

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = GlassTokens.spaceMd)) {
        when {
            !hasPermission -> PermissionRequestState(
                permanentlyDenied = permissionPermanentlyDenied,
                onRequestPermission = { permissionLauncher.launch(LocalMediaPermission.permission) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                    )
                },
            )

            else -> LibraryContent(viewModel, onPlayQueue, onAlbumClick, onArtistClick)
        }
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun LibraryContent(
    viewModel: LocalLibraryViewModel,
    onPlayQueue: (List<PlayableItem.LocalTrack>, Int) -> Unit,
    onAlbumClick: (LocalAlbum) -> Unit,
    onArtistClick: (LocalArtist) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(LibraryTab.SONGS) }
    val isScanning by viewModel.isScanning.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    GlassSearchField(
        query = searchQuery,
        onQueryChange = viewModel::onSearchQueryChanged,
        placeholder = "Search your music...",
        modifier = Modifier.fillMaxWidth(),
    )

    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceMd))

    // While a search query is active, it takes over the whole content area
    // (tabs hidden) — matches the same "search overlays the current view"
    // pattern as the online SearchScreen, rather than trying to filter
    // three different tab contents (songs/albums/artists) simultaneously.
    if (searchQuery.isNotBlank()) {
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

    GlassTabRow(
        items = LibraryTab.entries,
        selected = selectedTab,
        onSelect = { selectedTab = it },
        label = { it.label },
    )

    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceMd))

    val isEmpty = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()

    when {
        isScanning && isEmpty -> LoadingState()
        isEmpty -> EmptyLibraryState(onRescan = viewModel::rescan)
        else -> when (selectedTab) {
            LibraryTab.SONGS -> SongList(songs, onPlayQueue)
            LibraryTab.ALBUMS -> AlbumList(albums, onAlbumClick)
            LibraryTab.ARTISTS -> ArtistList(artists, onArtistClick)
        }
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun LocalSearchResults(
    query: String,
    results: List<PlayableItem.LocalTrack>,
    onPlayQueue: (List<PlayableItem.LocalTrack>, Int) -> Unit,
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

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun SongList(songs: List<PlayableItem.LocalTrack>, onPlayQueue: (List<PlayableItem.LocalTrack>, Int) -> Unit) {
    com.whiplash.music.ui.player.PlayableItemsList(
        items = songs,
        onPlayQueue = { _, index -> onPlayQueue(songs, index) },
        modifier = Modifier.fillMaxSize(),
    )
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
        GlassCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No local music found",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "We couldn't find any songs on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceMd))
                GlassButton(text = "Rescan", onClick = onRescan)
            }
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
        GlassCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Music permission needed",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Whiplash needs access to your device's audio files to show your local music library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceMd))
                GlassButton(
                    text = if (permanentlyDenied) "Open Settings" else "Grant Access",
                    onClick = if (permanentlyDenied) onOpenSettings else onRequestPermission,
                )
            }
        }
    }
}
