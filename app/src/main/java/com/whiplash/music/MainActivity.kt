package com.whiplash.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whiplash.music.ui.home.HomeScreen
import com.whiplash.music.ui.library.FavoritesScreen
import com.whiplash.music.ui.localmusic.LocalLibraryScreen
import com.whiplash.music.ui.player.FullPlayerScreen
import com.whiplash.music.ui.player.PlayerViewModel
import com.whiplash.music.ui.player.PlayerViewModelFactory
import com.whiplash.music.ui.album.AlbumDetailScreen
import com.whiplash.music.ui.artist.ArtistDetailScreen
import com.whiplash.music.ui.playlists.PlaylistDetailScreen
import com.whiplash.music.ui.playlists.PlaylistsScreen
import com.whiplash.music.ui.search.SearchScreen
import com.whiplash.music.ui.settings.SettingsScreen
import com.whiplash.music.ui.theme.GlassBottomBar
import com.whiplash.music.ui.theme.GlassMiniPlayer
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.WhiplashTheme

/**
 * Single activity host for the Compose UI.
 *
 * Per the Media3 playback architecture (CLAUDE.md section 12), this Activity
 * must not own the long-lived player lifecycle. Playback is driven by a
 * MediaSessionService; this activity only hosts the Compose navigation
 * graph and UI state.
 */
class MainActivity : ComponentActivity() {

    @androidx.compose.material3.ExperimentalMaterial3Api
    @androidx.compose.foundation.ExperimentalFoundationApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WhiplashTheme {
                WhiplashApp()
            }
        }
    }
}

private enum class AppTab(val label: String) {
    HOME("Home"),
    SEARCH("Search"),
    LOCAL("Library"),
    FAVORITES("Favorites"),
    PLAYLISTS("Playlists"),
    SETTINGS("Settings"),
}

/** Search tab detail-navigation targets (section 39/40: album/artist pages, opened from search or from an artist's albums tab). */
private sealed interface SearchDestination {
    data class Album(val url: String) : SearchDestination
    data class Artist(val channelUrl: String) : SearchDestination
}

@androidx.compose.material3.ExperimentalMaterial3Api
@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
private fun WhiplashApp() {
    val context = LocalContext.current
    val app = context.applicationContext as WhiplashApplication
    val playerViewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModelFactory(app.playbackController, app.libraryRepository),
    )
    val playbackState by playerViewModel.state.collectAsState()
    val lyricsViewModel: com.whiplash.music.ui.player.LyricsViewModel = viewModel(
        factory = com.whiplash.music.ui.player.LyricsViewModelFactory(app.playbackController, app.lrcLibProvider),
    )
    val lyrics by lyricsViewModel.lyrics.collectAsState()

    var isPlayerExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var openPlaylist by remember { mutableStateOf<com.whiplash.music.domain.model.Playlist?>(null) }
    // Simple back-stack for Search tab detail navigation (album/artist),
    // since an artist page can itself open an album (section 40 "albums"
    // tab), needing more than one level of "open detail" state.
    var searchDetailStack by remember { mutableStateOf<List<SearchDestination>>(emptyList()) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )

    // Request the notification permission once playback becomes visually
    // prominent (mini-player appears), rather than at app launch, so the
    // request is contextual (section 14: proper media notification while
    // playing). No-op below API 33 where the permission doesn't exist.
    LaunchedEffect(playbackState.currentItem != null) {
        if (playbackState.currentItem == null) return@LaunchedEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Collapse the full player on system back instead of the default
    // Activity behavior (exiting the app). Only intercepts back while the
    // full player is actually open, so normal back navigation elsewhere is
    // unaffected.
    BackHandler(enabled = isPlayerExpanded) {
        isPlayerExpanded = false
    }

    // Same collapse-not-exit pattern for the Playlists tab's detail view:
    // back should return to the playlist list, not exit the app, while a
    // playlist is open.
    BackHandler(enabled = !isPlayerExpanded && openPlaylist != null) {
        openPlaylist = null
    }

    // Same pattern for Search tab's album/artist detail navigation — pops
    // one level off the stack rather than exiting the app or the Search tab.
    BackHandler(enabled = !isPlayerExpanded && searchDetailStack.isNotEmpty()) {
        searchDetailStack = searchDetailStack.dropLast(1)
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        // Single Box hosting every layer of the screen (tab content, mini
        // player, bottom nav, full player) so Compose's z-order-based hit
        // testing works correctly between them — the full player, drawn
        // last, is on top and (via the scrim below) explicitly consumes all
        // touch input over its full bounds rather than only over its
        // individual buttons, which is what let taps reach the content
        // underneath before this fix.
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                com.whiplash.music.ui.theme.WhiplashAppHeader(
                    title = if (selectedTab == AppTab.HOME) "Whiplash" else selectedTab.label,
                )

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        AppTab.HOME -> HomeScreen(
                            onPlayTrack = { track -> app.playbackController.playNow(track) },
                        )
                        AppTab.SEARCH -> {
                            val topDestination = searchDetailStack.lastOrNull()
                            when (topDestination) {
                                null -> SearchScreen(
                                    onPlayTrack = { track -> app.playbackController.playNow(track) },
                                    onOpenAlbum = { album ->
                                        searchDetailStack = searchDetailStack + SearchDestination.Album(album.url)
                                    },
                                    onOpenArtist = { artist ->
                                        searchDetailStack = searchDetailStack + SearchDestination.Artist(artist.channelUrl)
                                    },
                                )
                                is SearchDestination.Album -> AlbumDetailScreen(
                                    url = topDestination.url,
                                    onBack = { searchDetailStack = searchDetailStack.dropLast(1) },
                                    onPlayQueue = { queue, index -> app.playbackController.playQueue(queue, index) },
                                )
                                is SearchDestination.Artist -> ArtistDetailScreen(
                                    channelUrl = topDestination.channelUrl,
                                    onBack = { searchDetailStack = searchDetailStack.dropLast(1) },
                                    onPlayQueue = { queue, index -> app.playbackController.playQueue(queue, index) },
                                    onOpenAlbum = { album ->
                                        searchDetailStack = searchDetailStack + SearchDestination.Album(album.url)
                                    },
                                )
                            }
                        }
                        AppTab.LOCAL -> LocalLibraryScreen(
                            onPlayQueue = { queue, index -> app.playbackController.playQueue(queue, index) },
                        )
                        AppTab.FAVORITES -> FavoritesScreen(
                            onPlayQueue = { queue, index -> app.playbackController.playQueue(queue, index) },
                        )
                        AppTab.PLAYLISTS -> {
                            val currentPlaylist = openPlaylist
                            if (currentPlaylist == null) {
                                PlaylistsScreen(onOpenPlaylist = { openPlaylist = it })
                            } else {
                                PlaylistDetailScreen(
                                    playlist = currentPlaylist,
                                    onBack = { openPlaylist = null },
                                    onPlayQueue = { queue, index -> app.playbackController.playQueue(queue, index) },
                                )
                            }
                        }
                        AppTab.SETTINGS -> SettingsScreen()
                    }

                    // Mini-player pinned to the bottom of the content area,
                    // above the bottom nav bar, visible whenever there is a
                    // current item.
                    if (playbackState.currentItem != null) {
                        GlassMiniPlayer(
                            title = playbackState.currentItem?.title.orEmpty(),
                            artist = playbackState.currentItem?.artist.orEmpty(),
                            artworkUri = playbackState.currentItem?.artworkUri,
                            isPlaying = playbackState.isPlaying,
                            isBuffering = playbackState.isBuffering || playbackState.isResolvingStream,
                            progressFraction = if (playbackState.durationMs > 0) {
                                playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()
                            } else 0f,
                            onTogglePlayPause = playerViewModel::togglePlayPause,
                            onExpand = { isPlayerExpanded = true },
                            onPrevious = playerViewModel::seekToPrevious,
                            onNext = playerViewModel::seekToNext,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(GlassTokens.spaceMd),
                        )
                    }
                }

                GlassBottomBar(
                    items = AppTab.entries,
                    selected = selectedTab,
                    onSelect = { selectedTab = it },
                    label = { it.label },
                    icon = { tab ->
                        Icon(
                            imageVector = when (tab) {
                                AppTab.HOME -> Icons.Filled.Home
                                AppTab.SEARCH -> Icons.Filled.Search
                                AppTab.LOCAL -> Icons.Filled.LibraryMusic
                                AppTab.FAVORITES -> Icons.Filled.Favorite
                                AppTab.PLAYLISTS -> Icons.AutoMirrored.Filled.QueueMusic
                                AppTab.SETTINGS -> Icons.Filled.Settings
                            },
                            contentDescription = null,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    },
                )
            }

            // Full player, animated in/out (section 47: smooth transformation,
            // no abrupt visual jump). Drawn last in this Box so it is on top
            // in both z-order and hit-testing. Slide-only (no fade) since a
            // fade animates alpha across the whole subtree, which made the
            // background/artwork genuinely semi-transparent mid-transition
            // and let content underneath show through — a real visual bug,
            // not just a perception issue. The scrim Box is explicitly
            // opaque and consumes every touch over its full bounds instead
            // of only over FullPlayerScreen's interactive children, which is
            // what previously let taps pass through to content underneath.
            AnimatedVisibility(
                visible = isPlayerExpanded && playbackState.currentItem != null,
                enter = slideInVertically(animationSpec = tween(GlassTokens.animSlow)) { it },
                exit = slideOutVertically(animationSpec = tween(GlassTokens.animSlow)) { it },
            ) {
                val scrimInteractionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .clickable(
                            interactionSource = scrimInteractionSource,
                            indication = null,
                            onClick = {}, // absorb taps; does not close the player (only the collapse button/back does)
                        ),
                ) {
                    val isFavorite by playerViewModel.isCurrentFavorite.collectAsState()
                    FullPlayerScreen(
                        state = playbackState,
                        onTogglePlayPause = playerViewModel::togglePlayPause,
                        onSeekTo = playerViewModel::seekTo,
                        onNext = playerViewModel::seekToNext,
                        onPrevious = playerViewModel::seekToPrevious,
                        onToggleShuffle = playerViewModel::toggleShuffle,
                        onCycleRepeat = playerViewModel::cycleRepeatMode,
                        onCollapse = { isPlayerExpanded = false },
                        isFavorite = isFavorite,
                        onToggleFavorite = playerViewModel::toggleFavoriteCurrent,
                        onPlayQueueIndex = playerViewModel::playQueueItem,
                        onRemoveFromQueue = playerViewModel::removeFromQueue,
                        onMoveInQueue = playerViewModel::moveInQueue,
                        onClearQueue = playerViewModel::clearQueueExceptCurrent,
                        onSetSleepTimer = playerViewModel::setSleepTimer,
                        lyrics = lyrics,
                    )
                }
            }
        }
    }
}
