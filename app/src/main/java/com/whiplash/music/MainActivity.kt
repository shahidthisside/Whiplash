package com.whiplash.music
// Developed by Shahid Ansari — github.com/shahidthisside (-SA)

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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.launch
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
    @androidx.compose.foundation.layout.ExperimentalLayoutApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighestRefreshRate()
        setContent {
            WhiplashTheme {
                WhiplashApp()
            }
        }
    }

    /**
     * Requests the display's highest available refresh rate for this
     * window (e.g. 90Hz/120Hz on devices that support it), rather than
     * silently running at whatever the OS's conservative power-saving
     * default is. Without this, some OEM skins keep an app's window at
     * 60Hz even on a 120Hz-capable device/display, which reads as visible
     * choppiness compared to apps that explicitly opt in — this was a
     * real, user-reported issue ("app feels choppy... not adapting to my
     * phone's 120Hz refresh rate"), not a misperception: Compose's own
     * animations only ever run as smoothly as the surface they're
     * composited onto is actually being refreshed.
     *
     * Only compares modes at the CURRENT resolution (never requests a
     * mode that would also change resolution) and picks whichever has the
     * highest refreshRate among those — the standard, documented pattern
     * for this API. The system is still free to override this at its own
     * discretion (e.g. low battery, thermal throttling), per Android's
     * own refresh-rate documentation.
     */
    private fun requestHighestRefreshRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val currentMode = window.windowManager.defaultDisplay.mode
        val bestMode = window.windowManager.defaultDisplay.supportedModes
            .filter { it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight }
            .maxByOrNull { it.refreshRate }
        if (bestMode != null && bestMode.refreshRate > currentMode.refreshRate) {
            window.attributes = window.attributes.apply { preferredDisplayModeId = bestMode.modeId }
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

/**
 * Hosts [GlassMiniPlayer] with its own independent [PlaybackController]
 * state collection, rather than reading that state in [WhiplashApp]'s own
 * body. [com.whiplash.music.playback.controller.PlaybackState] updates
 * every ~500ms while a track is playing (the position ticker, for a
 * smoothly advancing progress bar) — reading it directly in a composable
 * as broad as [WhiplashApp] put its entire tab-switching content and
 * bottom nav in the same recomposition scope as that tick, a real,
 * measurable contributor to reported UI choppiness during playback.
 * Scoping the collection to this small leaf composable keeps that
 * recomposition work contained to just the mini-player itself.
 */
@Composable
private fun MiniPlayerHost(
    playerViewModel: PlayerViewModel,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by playerViewModel.state.collectAsState()
    val currentItem = state.currentItem ?: return
    GlassMiniPlayer(
        title = currentItem.title,
        artist = currentItem.artist,
        artworkUri = currentItem.artworkUri,
        isPlaying = state.isPlaying,
        isBuffering = state.isBuffering || state.isResolvingStream,
        progressFraction = if (state.durationMs > 0) {
            state.positionMs.toFloat() / state.durationMs.toFloat()
        } else 0f,
        onTogglePlayPause = playerViewModel::togglePlayPause,
        onExpand = onExpand,
        onPrevious = playerViewModel::seekToPrevious,
        onNext = playerViewModel::seekToNext,
        modifier = modifier,
    )
}

@androidx.compose.material3.ExperimentalMaterial3Api
@androidx.compose.foundation.ExperimentalFoundationApi
@androidx.compose.foundation.layout.ExperimentalLayoutApi
@Composable
private fun WhiplashApp() {
    val context = LocalContext.current
    val mainScope = androidx.compose.runtime.rememberCoroutineScope()
    val app = context.applicationContext as WhiplashApplication
    val playerViewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModelFactory(app.playbackController, app.libraryRepository, app.settingsRepository),
    )
    val playbackState by playerViewModel.state.collectAsState()
    val lyricsViewModel: com.whiplash.music.ui.player.LyricsViewModel = viewModel(
        factory = com.whiplash.music.ui.player.LyricsViewModelFactory(app.playbackController, app.lrcLibProvider),
    )
    val lyrics by lyricsViewModel.lyrics.collectAsState()

    var isPlayerExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var openPlaylist by remember { mutableStateOf<com.whiplash.music.domain.model.Playlist?>(null) }
    // Same collapse-not-exit back pattern as openPlaylist, for the Home
    // tab's "see full History" screen (reached via Speed dial's History
    // button — see HomeScreen/SectionHeader).
    var showHistory by rememberSaveable { mutableStateOf(false) }
    // Simple back-stack for Search tab detail navigation (album/artist),
    // since an artist page can itself open an album (section 40 "albums"
    // tab), needing more than one level of "open detail" state.
    var searchDetailStack by remember { mutableStateOf<List<SearchDestination>>(emptyList()) }
    // Hoisted up from SearchScreen itself (real, reported bug: SearchScreen
    // is removed from composition entirely while an album/artist detail
    // screen is open — see the AppTab.SEARCH branch below — so a plain
    // rememberSaveable INSIDE SearchScreen for which Songs/Albums/Artists/
    // Playlists sub-tab was selected did not reliably survive that
    // removal/reinsertion, and going back from a detail screen always
    // reset the selection to Songs regardless of what the user had
    // actually selected before opening that album/artist). Living here
    // instead means it survives exactly as long as searchDetailStack
    // itself already correctly does.
    var selectedSearchResultTab by rememberSaveable {
        mutableStateOf(com.whiplash.music.ui.search.SearchResultTab.SONGS)
    }

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

    // A plain, brief in-app toast on a failed play attempt — matching the
    // standard, simple "no internet connection" popup every mainstream
    // music app (Spotify, YouTube Music) shows for a few seconds, rather
    // than surfacing the raw underlying failure (e.g. a DNS resolution
    // exception message) which is meaningless to a regular user. The
    // message stays generic and short on purpose either way — the user
    // only needs to know "this didn't work, check your connection" (for a
    // real connectivity problem) or "this didn't work" (anything else),
    // never the technical reason.
    LaunchedEffect(playbackState.playbackError) {
        val error = playbackState.playbackError ?: return@LaunchedEffect
        val message = if (error.isNetworkFailure) "No internet connection" else "Couldn't play this song"
        com.whiplash.music.ui.common.ToastController.show(message)
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

    // Same pattern for the Home tab's History screen.
    BackHandler(enabled = !isPlayerExpanded && showHistory) {
        showHistory = false
    }

    // Same pattern for Search tab's album/artist detail navigation — pops
    // one level off the stack rather than exiting the app or the Search tab.
    BackHandler(enabled = !isPlayerExpanded && searchDetailStack.isNotEmpty()) {
        searchDetailStack = searchDetailStack.dropLast(1)
    }

    // Back from any non-Home tab returns to Home instead of leaving the app,
    // which is the platform's recommended bottom-navigation behavior — only
    // Home is a genuine exit point. Without this, back from Library, Search,
    // Favorites, Playlists or Settings fell through to the Activity default
    // and closed the app outright.
    //
    // The condition deliberately repeats every state the handlers above
    // already intercept rather than relying on being declared last. Compose
    // adds these callbacks to the dispatcher in composition order and the
    // most recently added *enabled* one wins, so leaving them overlapping
    // would make this handler quietly outrank them and swallow a detail
    // screen's own back. Keeping them mutually exclusive means exactly one
    // handler is ever enabled and the declaration order can't change the
    // outcome.
    BackHandler(
        enabled = !isPlayerExpanded &&
            selectedTab != AppTab.HOME &&
            !showHistory &&
            openPlaylist == null &&
            searchDetailStack.isEmpty(),
    ) {
        selectedTab = AppTab.HOME
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
                    // Tab-level crossfade (Home/Search/Library/Favorites/
                    // Playlists/Settings) — a plain fade rather than a
                    // directional slide, since bottom-nav tabs have no
                    // spatial "forward/back" relationship to each other
                    // (unlike drilling into a detail screen within a tab,
                    // handled by the nested AnimatedContents below). This
                    // only wraps the *rendering* transition — selectedTab
                    // itself, and everything each branch does, is exactly
                    // what already existed; no navigation/state logic
                    // changed here.
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(GlassTokens.animRegular))
                                .togetherWith(fadeOut(animationSpec = tween(GlassTokens.animFast)))
                        },
                        label = "tabContent",
                    ) { tab ->
                        when (tab) {
                            AppTab.HOME -> {
                                // Home's own two states (Speed dial/Quick
                                // Picks vs. the full History screen) get a
                                // horizontal slide — "opening a detail
                                // view" reads as forward motion, matching
                                // the same directional language used for
                                // Search/Playlists' own detail navigation
                                // below, for a consistent feel across the
                                // whole app rather than a plain fade here
                                // and a slide there for conceptually the
                                // same kind of navigation.
                                AnimatedContent(
                                    targetState = showHistory,
                                    transitionSpec = {
                                        val forward = targetState && !initialState
                                        val enter = slideInHorizontally(animationSpec = tween(GlassTokens.animRegular)) { w -> if (forward) w / 3 else -w / 3 } +
                                            fadeIn(animationSpec = tween(GlassTokens.animRegular))
                                        val exit = slideOutHorizontally(animationSpec = tween(GlassTokens.animFast)) { w -> if (forward) -w / 3 else w / 3 } +
                                            fadeOut(animationSpec = tween(GlassTokens.animFast))
                                        enter.togetherWith(exit)
                                    },
                                    label = "homeHistoryContent",
                                ) { isHistory ->
                                    if (!isHistory) {
                                        HomeScreen(
                                            onPlayTrack = { track -> app.playbackController.playNow(track) },
                                            onOpenHistory = { showHistory = true },
                                        )
                                    } else {
                                        com.whiplash.music.ui.home.HistoryScreen(
                                            onBack = { showHistory = false },
                                            onPlayQueue = { queue, index -> app.playbackController.playQueue(queue, index) },
                                        )
                                    }
                                }
                            }
                            AppTab.SEARCH -> {
                                val topDestination = searchDetailStack.lastOrNull()
                                // Keyed on stack depth + which destination
                                // is on top, so pushing a second-level
                                // destination (artist -> album) animates
                                // too, not just the null <-> first-level
                                // transition. Direction (slide left when
                                // pushing deeper, right when popping back)
                                // follows the stack depth actually
                                // growing/shrinking, not just "which
                                // destination" — going from Album back to
                                // null and from null to a *different*
                                // Album should read as backward/forward
                                // respectively regardless of the specific
                                // destination values involved.
                                AnimatedContent(
                                    targetState = searchDetailStack.size,
                                    transitionSpec = {
                                        val forward = targetState >= initialState
                                        val enter = slideInHorizontally(animationSpec = tween(GlassTokens.animRegular)) { w -> if (forward) w / 3 else -w / 3 } +
                                            fadeIn(animationSpec = tween(GlassTokens.animRegular))
                                        val exit = slideOutHorizontally(animationSpec = tween(GlassTokens.animFast)) { w -> if (forward) -w / 3 else w / 3 } +
                                            fadeOut(animationSpec = tween(GlassTokens.animFast))
                                        enter.togetherWith(exit)
                                    },
                                    label = "searchDetailContent",
                                ) { _ ->
                                    when (topDestination) {
                                        null -> SearchScreen(
                                            onPlayTrack = { track -> app.playbackController.playNow(track) },
                                            onOpenAlbum = { album ->
                                                searchDetailStack = searchDetailStack + SearchDestination.Album(album.url)
                                            },
                                            onOpenArtist = { artist ->
                                                searchDetailStack = searchDetailStack + SearchDestination.Artist(artist.channelUrl)
                                            },
                                            selectedTab = selectedSearchResultTab,
                                            onSelectedTabChange = { selectedSearchResultTab = it },
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
                            }
                            AppTab.LOCAL -> LocalLibraryScreen(
                                onPlayQueue = { queue, index -> app.playbackController.playQueue(queue, index) },
                            )
                            AppTab.FAVORITES -> FavoritesScreen(
                                onPlayQueue = { queue, index -> app.playbackController.playQueue(queue, index) },
                            )
                            AppTab.PLAYLISTS -> {
                                val currentPlaylist = openPlaylist
                                AnimatedContent(
                                    targetState = currentPlaylist != null,
                                    transitionSpec = {
                                        val forward = targetState && !initialState
                                        val enter = slideInHorizontally(animationSpec = tween(GlassTokens.animRegular)) { w -> if (forward) w / 3 else -w / 3 } +
                                            fadeIn(animationSpec = tween(GlassTokens.animRegular))
                                        val exit = slideOutHorizontally(animationSpec = tween(GlassTokens.animFast)) { w -> if (forward) -w / 3 else w / 3 } +
                                            fadeOut(animationSpec = tween(GlassTokens.animFast))
                                        enter.togetherWith(exit)
                                    },
                                    label = "playlistDetailContent",
                                ) { _ ->
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
                            }
                            AppTab.SETTINGS -> SettingsScreen()
                        }
                    }

                    // Mini-player pinned to the bottom of the content area,
                    // above the bottom nav bar, visible whenever there is a
                    // current item. Extracted into its own composable that
                    // collects playback state independently (see
                    // MiniPlayerHost's doc) so the 500ms position-tick
                    // recomposition this state produces during playback
                    // stays scoped to this one leaf, instead of being read
                    // directly in WhiplashApp()'s own body — where it would
                    // put the entire tab-switching Box (Home/Search/Library/
                    // etc., whichever is currently selected) in the same
                    // recomposition scope, a real, measurable contributor to
                    // "choppy" scrolling/interaction while a song is
                    // playing, reported by a user on a 120Hz device.
                    MiniPlayerHost(
                        playerViewModel = playerViewModel,
                        onExpand = { isPlayerExpanded = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(GlassTokens.spaceMd),
                    )
                }

                GlassBottomBar(
                    items = AppTab.entries,
                    selected = selectedTab,
                    onSelect = { tab ->
                        // Real, reported navigation bug (UAT audit
                        // finding): re-tapping the *already-selected*
                        // bottom-nav tab while a nested sub-screen was
                        // open (History under Home, a playlist's detail
                        // view under Playlists, an album/artist detail
                        // under Search) silently did nothing — Compose
                        // never recomposes from `selectedTab = it` when
                        // `it` already equals the current value, and none
                        // of those nested-state variables were ever reset
                        // anywhere except their own screen-local `onBack`.
                        // Every other major app treats "tap the tab
                        // you're already on" as "return to that tab's
                        // root", so this now explicitly collapses the
                        // matching nested state when the tap target is
                        // the tab already selected, in addition to the
                        // always-correct plain tab switch.
                        if (tab == selectedTab) {
                            when (tab) {
                                AppTab.HOME -> showHistory = false
                                AppTab.SEARCH -> searchDetailStack = emptyList()
                                AppTab.PLAYLISTS -> openPlaylist = null
                                else -> {}
                            }
                        }
                        selectedTab = tab
                    },
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
                    val autoplayEnabled by playerViewModel.autoplayEnabled.collectAsState()
                    val playbackSpeed by playerViewModel.playbackSpeed.collectAsState()
                    val playlistsForPlayer by playerViewModel.playlists.collectAsState()
                    val downloadedIds by app.libraryRepository.observeDownloadedIds().collectAsState(initial = emptySet())
                    val currentItemForDownload = playbackState.currentItem
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
                        autoplayEnabled = autoplayEnabled,
                        onToggleAutoplay = playerViewModel::setAutoplayEnabled,
                        onSetSleepTimer = playerViewModel::setSleepTimer,
                        lyrics = lyrics,
                        playbackSpeed = playbackSpeed,
                        onSetPlaybackSpeed = playerViewModel::setPlaybackSpeed,
                        playlists = playlistsForPlayer,
                        onAddToPlaylist = playerViewModel::addCurrentToPlaylist,
                        onCreatePlaylistAndAdd = playerViewModel::createPlaylistAndAddCurrent,
                        isCurrentDownloaded = currentItemForDownload is com.whiplash.music.domain.model.PlayableItem.DownloadedTrack ||
                            (currentItemForDownload != null && currentItemForDownload.id in downloadedIds),
                        onDownloadCurrent = (currentItemForDownload as? com.whiplash.music.domain.model.PlayableItem.YoutubeTrack)?.let { track ->
                            { app.downloadManager.startDownload(track) }
                        },
                        onRemoveDownloadCurrent = if (currentItemForDownload != null) {
                            {
                                val id = currentItemForDownload.id
                                com.whiplash.music.ui.common.ToastController.show("Download removed")
                                mainScope.launch { app.downloadManager.removeDownload(id) }
                            }
                        } else null,
                    )
                }
            }

            // App-wide toast host (section: feedback for silent actions —
            // favoriting, pinning, playlist/queue changes, etc.), drawn
            // last so it renders above even the expanded full player.
            com.whiplash.music.ui.theme.GlassToastHost(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = GlassTokens.spaceXl + GlassTokens.miniPlayerReservedHeight),
            )
        }
    }
}
