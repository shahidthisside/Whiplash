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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.background
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.History
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
    onOpenHistory: (() -> Unit)? = null,
    backEnabled: Boolean = true,
    resetKey: Int = 0,
) {
    val context = LocalContext.current
    val viewModel: LocalLibraryViewModel = viewModel(factory = LocalLibraryViewModelFactory(context))

    val appForToggle = context.applicationContext as com.whiplash.music.WhiplashApplication
    val modern by appForToggle.settingsRepository.modernLibraryPages.collectAsState(initial = true)
    // Modern start page: which section page is open (null = the folder list).
    var sectionName by rememberSaveable { mutableStateOf<String?>(null) }
    val section = sectionName?.let { runCatching { LibrarySection.valueOf(it) }.getOrNull() }
    LaunchedEffect(resetKey) { if (resetKey != 0) sectionName = null }
    // Registered before the album/artist handler so that one wins while a page is open on top.
    androidx.activity.compose.BackHandler(enabled = backEnabled && modern && section != null) { sectionName = null }

    // Local album / artist page (modern pages only). Held as kind + id + name so it survives rotation.
    var detailKind by rememberSaveable { mutableStateOf<String?>(null) }
    var detailId by rememberSaveable { mutableStateOf(0L) }
    var detailTitle by rememberSaveable { mutableStateOf("") }
    var detailSubtitle by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(resetKey) { if (resetKey != 0) detailKind = null }
    // Off while the full player covers Library, so Back closes the player first.
    androidx.activity.compose.BackHandler(enabled = backEnabled && detailKind != null) { detailKind = null }

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
    val openAlbum: (LocalAlbum) -> Unit = { album ->
        onAlbumClick(album)
        detailKind = "album"; detailId = album.id; detailTitle = album.title
        detailSubtitle = listOfNotNull(album.artist, album.year?.toString()).joinToString(" · ")
    }
    val openArtist: (LocalArtist) -> Unit = { artist ->
        onArtistClick(artist)
        detailKind = "artist"; detailId = artist.id; detailTitle = artist.name
        detailSubtitle = if (artist.albumCount == 1) "1 album" else "${artist.albumCount} albums"
    }
    val requestPermission = { permissionLauncher.launch(LocalMediaPermission.permission) }
    val openAppSettings = {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                }
            )
        }.onFailure {
            com.whiplash.music.ui.common.ToastController.show("Couldn't open app settings")
        }
        Unit
    }

    Box(modifier = Modifier.fillMaxSize()) {
    if (modern) {
        // Start page with folders; each section slides in over it and the list keeps its place underneath.
        ModernLibraryHome(
            viewModel = viewModel,
            hasMediaPermission = hasPermission,
            onOpenSection = { sectionName = it.name },
            onOpenHistory = onOpenHistory,
            onPlayQueue = onPlayQueue,
            modifier = if (section != null || detailKind != null) Modifier.clearAndSetSemantics {} else Modifier,
        )
        var shownSection by remember { mutableStateOf(section) }
        if (section != null) shownSection = section
        LibrarySlideOverlay(visible = section != null) {
            shownSection?.let { s ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(com.whiplash.music.ui.theme.WhiplashColors.background)
                        .then(if (detailKind != null) Modifier.clearAndSetSemantics {} else Modifier),
                ) {
                    LibrarySectionPage(
                        section = s,
                        viewModel = viewModel,
                        hasMediaPermission = hasPermission,
                        permissionPermanentlyDenied = permissionPermanentlyDenied,
                        onRequestPermission = requestPermission,
                        onOpenSettings = openAppSettings,
                        onBack = { sectionName = null },
                        onPlayQueue = onPlayQueue,
                        onAlbumClick = openAlbum,
                        onArtistClick = openArtist,
                    )
                }
            }
        }
    } else Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = GlassTokens.spaceMd)
            .then(if (detailKind != null) Modifier.clearAndSetSemantics {} else Modifier),
    ) {
        LibraryContent(
            viewModel = viewModel,
            onPlayQueue = onPlayQueue,
            onAlbumClick = { album ->
                onAlbumClick(album)
                detailKind = "album"; detailId = album.id; detailTitle = album.title
                detailSubtitle = listOfNotNull(album.artist, album.year?.toString()).joinToString(" · ")
            },
            onArtistClick = { artist ->
                onArtistClick(artist)
                detailKind = "artist"; detailId = artist.id; detailTitle = artist.name
                detailSubtitle = if (artist.albumCount == 1) "1 album" else "${artist.albumCount} albums"
            },
            hasMediaPermission = hasPermission,
            onOpenHistory = onOpenHistory,
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
    // The list stays composed (and scrolled) underneath; the page slides over it.
    var shownKind by remember { mutableStateOf(detailKind) }
    if (detailKind != null) shownKind = detailKind
    LibrarySlideOverlay(visible = detailKind != null) {
        val kind = shownKind
        if (kind != null) {
            Box(modifier = Modifier.fillMaxSize().background(com.whiplash.music.ui.theme.WhiplashColors.background)) {
                LocalCollectionPage(
                    viewModel = viewModel,
                    isAlbum = kind == "album",
                    id = detailId,
                    title = detailTitle,
                    subtitle = detailSubtitle,
                    onBack = { detailKind = null },
                    onPlayQueue = onPlayQueue,
                )
            }
        }
    }
    }
}

/** 380 ms slide-and-fade used for every page that opens inside Library. */
@Composable
private fun LibrarySlideOverlay(visible: Boolean, content: @Composable () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 4 } +
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(380)),
        exit = androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 4 } +
            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(260)),
    ) { content() }
}

/** A local album's or artist's page: back row, hero with cover, then its songs. */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun LocalCollectionPage(
    viewModel: LocalLibraryViewModel,
    isAlbum: Boolean,
    id: Long,
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onPlayQueue: (List<PlayableItem>, Int) -> Unit,
) {
    val songsFlow = remember(isAlbum, id) { if (isAlbum) viewModel.songsOnAlbum(id) else viewModel.songsByArtist(id) }
    val songs by songsFlow.collectAsState(initial = null)
    val list = songs.orEmpty()
    val tint = com.whiplash.music.ui.theme.tintForName(title)
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = GlassTokens.spaceMd)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = GlassTokens.spaceXs), verticalAlignment = Alignment.CenterVertically) {
            com.whiplash.music.ui.theme.PlainIconButton(contentDescription = "Back", onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = com.whiplash.music.ui.theme.WhiplashColors.textPrimary)
            }
        }
        if (songs == null) return@Column
        com.whiplash.music.ui.common.TrackCollectionPage(
            items = list,
            eyebrow = if (isAlbum) "Album" else "Artist",
            title = title,
            tint = tint,
            subtitlePrefix = subtitle.ifBlank { null },
            cover = { m ->
                LocalArtwork(
                    uri = list.firstOrNull { it.artworkUri != null }?.artworkUri,
                    name = title,
                    fallbackIcon = if (isAlbum) Icons.Filled.Album else Icons.Filled.Person,
                    modifier = if (isAlbum) m else m.clip(androidx.compose.foundation.shape.CircleShape),
                )
            },
            onPlayQueue = onPlayQueue,
            sortKey = "local_${if (isAlbum) "album" else "artist"}_$id",
            defaultSortLabel = if (isAlbum) "Track order" else "Library order",
            emptyContent = {
                com.whiplash.music.ui.theme.CollectionEmptyState(
                    icon = Icons.Filled.Album,
                    title = "No songs found",
                    message = "These songs may have been moved or deleted from this device.",
                    tint = tint,
                )
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
    onOpenHistory: (() -> Unit)? = null,
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
    val appForSettings = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.whiplash.music.WhiplashApplication
    val modern by appForSettings.settingsRepository.modernLibraryPages.collectAsState(initial = true)

    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        GlassSearchField(
            query = searchQuery,
            onQueryChange = viewModel::onSearchQueryChanged,
            placeholder = if (selectedTab == LibraryTab.DOWNLOADS) "Search downloads..." else "Search your music...",
            modifier = Modifier.weight(1f),
        )
        // 4.5: History is its own destination, reachable from Library too.
        if (onOpenHistory != null) {
            com.whiplash.music.ui.theme.PlainIconButton(
                contentDescription = "Listening history",
                onClick = onOpenHistory,
                size = 48.dp,
            ) {
                androidx.compose.material3.Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = com.whiplash.music.ui.theme.WhiplashColors.textSecondary,
                )
            }
        }
    }

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
            modern = modern,
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
            LibraryTab.SONGS -> SongList(songs, onPlayQueue, modern)
            LibraryTab.ALBUMS -> if (modern) ModernAlbumGrid(albums, songs, onAlbumClick) else AlbumList(albums, onAlbumClick)
            LibraryTab.ARTISTS -> if (modern) ModernArtistList(artists, songs, onArtistClick) else ArtistList(artists, onArtistClick)
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
private fun SongList(songs: List<PlayableItem.LocalTrack>, onPlayQueue: (List<PlayableItem>, Int) -> Unit, modern: Boolean = false) {
    if (modern) {
        Column(modifier = Modifier.fillMaxSize()) {
            LibraryActionBar(
                summary = com.whiplash.music.ui.theme.collectionSummary(songs),
                onPlay = { onPlayQueue(songs, 0) },
                onShuffle = { onPlayQueue(songs.shuffled(), 0) },
            )
            com.whiplash.music.ui.player.PlayableItemsList(
                items = songs,
                onPlayQueue = { _, index -> onPlayQueue(songs, index) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }
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
    modern: Boolean,
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

    if (rows.isEmpty() && modern) {
        com.whiplash.music.ui.theme.CollectionEmptyState(
            icon = Icons.Filled.DownloadForOffline,
            title = "No downloads yet",
            message = "Download a song, album or playlist from its \u22ee menu to listen with no connection.",
            tint = WhiplashLibraryTint,
        )
        return
    }
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
        if (modern) LibraryActionBar(
            summary = listOfNotNull(
                com.whiplash.music.ui.theme.collectionSummary(downloads),
                if (inFlightTracks.isNotEmpty()) "${inFlightTracks.size} downloading" else null,
            ).joinToString(" · "),
            onPlay = if (downloads.isNotEmpty()) ({ onPlayQueue(downloads, 0) }) else null,
            onShuffle = if (downloads.isNotEmpty()) ({ onPlayQueue(downloads.shuffled(), 0) }) else null,
        ) {
            com.whiplash.music.ui.theme.PlainIconButton(
                contentDescription = "Clear all downloads",
                onClick = { showClearAllConfirm = true },
                size = 48.dp,
            ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = com.whiplash.music.ui.theme.WhiplashColors.textSecondary)
            }
        } else Row(
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
internal fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun EmptyLibraryState(onRescan: () -> Unit) {
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
internal fun PermissionRequestState(
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


/** Accent for Library empty states and fallback covers. */
private val WhiplashLibraryTint = androidx.compose.ui.graphics.Color(0xFF5B8DEF)

/**
 * Modern Library action bar: Play and Shuffle pills, a one-line summary
 * ("12 songs · 48 min") that shrinks first on narrow screens, then any
 * [trailing] icon.
 */
@Composable
private fun LibraryActionBar(
    summary: String,
    onPlay: (() -> Unit)?,
    onShuffle: (() -> Unit)?,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = GlassTokens.spaceXs, top = GlassTokens.spaceXs, bottom = GlassTokens.spaceSm),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.whiplash.music.ui.theme.CollectionPillButton(
            text = "Play",
            icon = Icons.Filled.PlayArrow,
            onClick = { onPlay?.invoke() },
            primary = true,
            enabled = onPlay != null,
        )
        com.whiplash.music.ui.theme.CollectionPillButton(
            text = "Shuffle",
            icon = Icons.Filled.Shuffle,
            onClick = { onShuffle?.invoke() },
            primary = false,
            enabled = onShuffle != null,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = com.whiplash.music.ui.theme.WhiplashColors.textSecondary,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}


/** First artwork found for each album title (case-insensitive), from the song list. */
internal fun albumArtworks(songs: List<PlayableItem.LocalTrack>): Map<String, String> =
    songs.asSequence()
        .filter { it.album != null && it.artworkUri != null }
        .groupBy { it.album!!.lowercase() }
        .mapValues { (_, list) -> list.first().artworkUri!! }

/** First artwork found for each artist name (case-insensitive). */
internal fun artistArtworks(songs: List<PlayableItem.LocalTrack>): Map<String, String> =
    songs.asSequence()
        .filter { it.artworkUri != null }
        .groupBy { it.artist.lowercase() }
        .mapValues { (_, list) -> list.first().artworkUri!! }

/** Artwork, or a tinted tile with [fallbackIcon] if there's none or it fails to load. */
@Composable
internal fun LocalArtwork(
    uri: String?,
    name: String,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
) {
    var failed by remember(uri) { mutableStateOf(false) }
    Box(modifier = modifier) {
        com.whiplash.music.ui.theme.GradientIconCover(fallbackIcon, com.whiplash.music.ui.theme.tintForName(name), Modifier.fillMaxSize())
        if (uri != null && !failed) {
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current).data(uri).crossfade(true).build(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Albums as a cover grid. */
@Composable
internal fun ModernAlbumGrid(
    albums: List<LocalAlbum>,
    songs: List<PlayableItem.LocalTrack>,
    onAlbumClick: (LocalAlbum) -> Unit,
) {
    val art = remember(songs) { albumArtworks(songs) }
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = GlassTokens.spaceXs),
        horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceMd),
        verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceLg),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = GlassTokens.spaceXs, bottom = GlassTokens.miniPlayerReservedHeight),
    ) {
        items(albums.size, key = { albums[it].id }) { i ->
            val album = albums[i]
            Column(
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(com.whiplash.music.ui.theme.WhiplashRadius.medium))
                    .clickable(onClickLabel = "Open album") { onAlbumClick(album) }
                    .semantics(mergeDescendants = true) {},
            ) {
                LocalArtwork(
                    uri = art[album.title.lowercase()],
                    name = album.title,
                    fallbackIcon = Icons.Filled.Album,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(com.whiplash.music.ui.theme.WhiplashRadius.medium)),
                )
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = com.whiplash.music.ui.theme.WhiplashColors.textPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = GlassTokens.spaceSm),
                )
                Text(
                    text = "${album.artist} · ${com.whiplash.music.ui.theme.songCountLabel(album.songCount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = com.whiplash.music.ui.theme.WhiplashColors.textSecondary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Artists with round photos (their first song's artwork). */
@Composable
internal fun ModernArtistList(
    artists: List<LocalArtist>,
    songs: List<PlayableItem.LocalTrack>,
    onArtistClick: (LocalArtist) -> Unit,
) {
    val art = remember(songs) { artistArtworks(songs) }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceXs),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = GlassTokens.miniPlayerReservedHeight),
    ) {
        items(artists, key = { it.id }) { artist ->
            GlassListItem(
                title = artist.name,
                subtitle = listOf(
                    com.whiplash.music.ui.theme.songCountLabel(artist.trackCount),
                    if (artist.albumCount == 1) "1 album" else "${artist.albumCount} albums",
                ).joinToString(" · "),
                onClick = { onArtistClick(artist) },
                leading = {
                    LocalArtwork(
                        uri = art[artist.name.lowercase()],
                        name = artist.name,
                        fallbackIcon = Icons.Filled.Person,
                        modifier = Modifier.size(56.dp).clip(androidx.compose.foundation.shape.CircleShape),
                    )
                },
                trailing = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = com.whiplash.music.ui.theme.WhiplashColors.textTertiary,
                        modifier = Modifier.padding(end = GlassTokens.spaceSm),
                    )
                },
            )
        }
    }
}
