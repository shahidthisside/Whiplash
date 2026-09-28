package com.whiplash.music
// Developed by Shahid Ansari — github.com/shahidthisside (-SA)

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import com.whiplash.music.ui.player.playerDragToDismiss
import com.whiplash.music.ui.player.playerDismissTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home as OutlinedHome
import androidx.compose.material.icons.outlined.LibraryMusic as OutlinedLibraryMusic
import androidx.compose.material.icons.outlined.Search as OutlinedSearch
import androidx.compose.material.icons.outlined.Settings as OutlinedSettings
import androidx.compose.material.icons.automirrored.outlined.QueueMusic as OutlinedQueueMusic
import androidx.compose.foundation.layout.size
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
import com.whiplash.music.ui.theme.GlassMiniPlayer
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.WhiplashTheme
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.appBackground
import com.whiplash.music.ui.theme.glassSource

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
            // In-app "Reduce animations", provided at the root so
            // isReducedMotionEnabled() sees it everywhere.
            val reduceAnimations by (application as WhiplashApplication).settingsRepository
                .reduceAnimations.collectAsState(initial = false)
            WhiplashTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.whiplash.music.ui.common.LocalAppReduceMotion provides reduceAnimations,
                ) {
                    WhiplashApp()
                }
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
    /** 4.3: an Explore mood/genre page. */
    data class Genre(val id: String) : SearchDestination
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
    // A flat card in the app's own near-black surface, so the mini player
    // belongs to the page instead of sitting on it as a grey slab.
    val uiSurface = com.whiplash.music.ui.theme.WhiplashColors.tone(0.07f)
    GlassMiniPlayer(
        containerColor = uiSurface,
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
    // Reduce animations (in-app setting or system "Remove animations"):
    // navigation swaps instantly instead of sliding/fading.
    val reduceMotion = com.whiplash.music.ui.common.isReducedMotionEnabled()
    val playerViewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModelFactory(app.playbackController, app.libraryRepository, app.settingsRepository),
    )
    val playbackState by playerViewModel.state.collectAsState()
    val lyricsViewModel: com.whiplash.music.ui.player.LyricsViewModel = viewModel(
        factory = com.whiplash.music.ui.player.LyricsViewModelFactory(app.playbackController, app.lyricsProviderChain, app.settingsRepository, app.lyricOffsetStore, app.lyricsCache),
    )
    val lyrics by lyricsViewModel.lyrics.collectAsState()
    val lyricOffsetMs by lyricsViewModel.lyricOffsetMs.collectAsState()
    val lyricsSourceName by lyricsViewModel.lyricsProviderName.collectAsState()

    var isPlayerExpanded by rememberSaveable { mutableStateOf(false) }
    // Bumped when the Library tab is tapped again, to close an album/artist page.
    var libraryResetKey by remember { mutableStateOf(0) }
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var openPlaylist by remember { mutableStateOf<com.whiplash.music.domain.model.Playlist?>(null) }
    // Same collapse-not-exit back pattern as openPlaylist, for the Home
    // tab's "see full History" screen (reached via Speed dial's History
    // button — see HomeScreen/SectionHeader).
    // 4.5: History is its own destination, opened from Home or Library.
    // Remembers which tab opened it, so it stays inside that tab (switching
    // tabs and back keeps it open there, like the other nested screens).
    var historyTab by rememberSaveable { mutableStateOf<AppTab?>(null) }
    val showHistory = historyTab != null && historyTab == selectedTab
    // 4.1: album/playlist opened from a Home shelf (its YouTube URL).
    var homeCollectionUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val homeListState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Simple back-stack for Search tab detail navigation (album/artist),
    // since an artist page can itself open an album (section 40 "albums"
    // tab), needing more than one level of "open detail" state.
    var searchDetailStack by remember { mutableStateOf<List<SearchDestination>>(emptyList()) }
    // Search detail pages currently drawn (includes one still sliding out after Back).
    var searchLayers by remember { mutableStateOf<List<SearchDestination>>(emptyList()) }
    // 4.7 Monthly Replay: full-screen story over everything but the full player.
    val replayViewModel: com.whiplash.music.ui.replay.ReplayViewModel = viewModel(
        factory = com.whiplash.music.ui.replay.ReplayViewModelFactory(app.libraryRepository, app.settingsRepository),
    )
    val replayEnabled by replayViewModel.enabled.collectAsState()
    val replayTeaser by replayViewModel.teaser.collectAsState()
    val replayCurrentMonth by replayViewModel.currentMonthKey.collectAsState()
    var showReplay by rememberSaveable { mutableStateOf(false) }
    // Bumped when the Settings tab is re-tapped, to return it to its start page.
    var settingsResetKey by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // A new month can begin while the app sits in the background.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        replayViewModel.refreshMonth()
        onPauseOrDispose { }
    }
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
    // 2.11: predictive back previews the player's exit (shrink) and only
    // collapses on commit; a plain back press still collapses immediately.
    val playerDismiss = com.whiplash.music.ui.player.rememberPlayerDismissState()
    var playerBackGestureActive by remember { mutableStateOf(false) }
    com.whiplash.music.ui.player.PlayerPredictiveBack(
        enabled = isPlayerExpanded,
        state = playerDismiss,
        onBackGestureActive = { playerBackGestureActive = it },
        onDismiss = { isPlayerExpanded = false },
    )
    // Reopening the player always starts from fully open, never half-dragged.
    LaunchedEffect(isPlayerExpanded) {
        if (isPlayerExpanded) playerDismiss.reset()
    }

    // Same collapse-not-exit pattern for the Playlists tab's detail view:
    // back should return to the playlist list, not exit the app, while a
    // playlist is open.
    BackHandler(enabled = !isPlayerExpanded && openPlaylist != null) {
        openPlaylist = null
    }

    // Same pattern for the Home tab's History screen.
    BackHandler(enabled = !isPlayerExpanded && showHistory) {
        historyTab = null
    }

    // Same pattern for an album/playlist opened from a Home shelf.
    BackHandler(enabled = !isPlayerExpanded && !showHistory && selectedTab == AppTab.HOME && homeCollectionUrl != null) {
        homeCollectionUrl = null
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

    // Declared after the handlers above so Replay's own Back wins while it's open.
    BackHandler(enabled = !isPlayerExpanded && showReplay) {
        showReplay = false
    }

                val onSelectTab: (AppTab) -> Unit = { tab ->
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
                            AppTab.HOME, AppTab.LOCAL -> {
                                if (historyTab == tab) historyTab = null
                                if (tab == AppTab.HOME) homeCollectionUrl = null
                                if (tab == AppTab.LOCAL) libraryResetKey++
                            }
                            AppTab.SEARCH -> searchDetailStack = emptyList()
                            AppTab.PLAYLISTS -> openPlaylist = null
                            AppTab.SETTINGS -> settingsResetKey++
                            else -> {}
                        }
                    }
                    selectedTab = tab
                }
    val bottomBar: @Composable () -> Unit = {
                com.whiplash.music.ui.theme.FadeBottomBar(
                    items = AppTab.entries,
                    selected = selectedTab,
                    onSelect = onSelectTab,
                    label = { it.label },
                    icon = { tab, sel ->
                        Icon(
                            imageVector = when (tab) {
                                AppTab.HOME -> if (sel) Icons.Filled.Home else Icons.Outlined.OutlinedHome
                                AppTab.SEARCH -> if (sel) Icons.Filled.Search else Icons.Outlined.OutlinedSearch
                                AppTab.LOCAL -> if (sel) Icons.Filled.LibraryMusic else Icons.Outlined.OutlinedLibraryMusic
                                AppTab.FAVORITES -> if (sel) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
                                AppTab.PLAYLISTS -> if (sel) Icons.AutoMirrored.Filled.QueueMusic else Icons.AutoMirrored.Outlined.OutlinedQueueMusic
                                AppTab.SETTINGS -> if (sel) Icons.Filled.Settings else Icons.Outlined.OutlinedSettings
                            },
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                        )
                    },
                )
    }

    // Themes: Liquid Glass records the page as a backdrop that the floating
    // tab bar and mini player refract. The full player and Replay keep their
    // dark, artwork-lit look in light themes (dark palette just for them).
    val glass = WhiplashColors.isGlass
    val glassBackdrop = com.whiplash.music.ui.theme.rememberGlassBackdrop()
    val darkOnlyPalette = if (WhiplashColors.isLight) {
        com.whiplash.music.ui.theme.resolvePalette(
            com.whiplash.music.ui.theme.AppTheme.DARK, WhiplashColors.accentVariant, WhiplashColors.customColors,
        )
    } else null
    val lightSystemBars = WhiplashColors.isLight && !isPlayerExpanded && !showReplay
    val rootView = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        val window = (rootView.context as? android.app.Activity)?.window ?: return@SideEffect
        androidx.core.view.WindowCompat.getInsetsController(window, rootView).apply {
            isAppearanceLightStatusBars = lightSystemBars
            isAppearanceLightNavigationBars = lightSystemBars
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        // Single Box hosting every layer of the screen (tab content, mini
        // player, bottom nav, full player) so Compose's z-order-based hit
        // testing works correctly between them — the full player, drawn
        // last, is on top and (via the scrim below) explicitly consumes all
        // touch input over its full bounds rather than only over its
        // individual buttons, which is what let taps reach the content
        // underneath before this fix.
        //
        // The full player is drawn edge to edge (under the status and
        // navigation bars, with its own insets), so only the tab content,
        // mini player and bottom nav get the Scaffold's system-bar padding.
        Box(modifier = Modifier.fillMaxSize()) {
          Box(
              modifier = Modifier
                  .fillMaxSize()
                  .then(if (glass) Modifier.glassSource(glassBackdrop).appBackground() else Modifier),
          ) {
          Box(
              modifier = Modifier
                  .padding(innerPadding)
                  .fillMaxSize()
                  // Hidden from TalkBack while the Replay story covers it.
                  .then(if (showReplay) Modifier.clearAndSetSemantics {} else Modifier),
          ) {
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
                            if (reduceMotion) {
                                instantContentTransform()
                            } else {
                                fadeIn(animationSpec = tween(GlassTokens.animRegular))
                                    .togetherWith(fadeOut(animationSpec = tween(GlassTokens.animFast)))
                            }
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
                                // Home and History swap like before. An album/playlist opened
                                // from a shelf is drawn *over* Home instead, so Home stays
                                // composed (and scrolled) underneath and Back only has to slide
                                // the album away — nothing is rebuilt mid-animation. (The old
                                // version swapped the outgoing album for a second Home halfway
                                // through the back animation, which is what stuttered.)
                                val homeFirstFrame = remember { mutableStateOf(true) }
                                androidx.compose.runtime.SideEffect { homeFirstFrame.value = false }
                                Box(modifier = Modifier.fillMaxSize()) {
                                    val albumOpen = homeCollectionUrl != null
                                    AnimatedContent(
                                        targetState = historyTab == AppTab.HOME,
                                        transitionSpec = {
                                            val forward = targetState && !initialState
                                            if (reduceMotion) instantContentTransform() else detailNavTransform(forward)
                                        },
                                        label = "homeHistoryContent",
                                        modifier = Modifier.then(
                                            // Hidden from TalkBack while an album covers it.
                                            if (albumOpen) Modifier.clearAndSetSemantics {} else Modifier,
                                        ),
                                    ) { isHistory ->
                                        if (!isHistory) {
                                            HomeScreen(
                                                onPlayTrack = { track -> app.playbackController.playNow(track) },
                                                onOpenHistory = { historyTab = AppTab.HOME },
                                                onPlayQueue = { queue, index -> app.playbackController.playQueue(queue, index) },
                                                onOpenCollection = { homeCollectionUrl = it.url },
                                                listState = homeListState,
                                                replayCard = replayTeaser
                                                    ?.takeIf { replayEnabled == true && !it.isEmpty }
                                                    ?.let { teaser ->
                                                        {
                                                            com.whiplash.music.ui.replay.ReplayHomeCard(
                                                                summary = teaser,
                                                                isCurrentMonth = teaser.monthKey == replayCurrentMonth,
                                                                onOpen = {
                                                                    replayViewModel.refreshMonth()
                                                                    showReplay = true
                                                                },
                                                            )
                                                        }
                                                    },
                                            )
                                        } else {
                                            com.whiplash.music.ui.home.HistoryScreen(
                                                onBack = { historyTab = null },
                                                onPlayQueue = { queue, index -> app.playbackController.playQueue(queue, index) },
                                            )
                                        }
                                    }
                                    // Keeps the last URL so the album stays on screen while it
                                    // slides out after Back has cleared homeCollectionUrl.
                                    var shownCollectionUrl by remember { mutableStateOf(homeCollectionUrl) }
                                    if (homeCollectionUrl != null) shownCollectionUrl = homeCollectionUrl
                                    val url = shownCollectionUrl
                                    if (url != null) {
                                        androidx.compose.runtime.key(url) {
                                            DetailOverlay(
                                                visible = albumOpen,
                                                reduceMotion = reduceMotion,
                                                coveredAbove = false,
                                                startVisible = homeFirstFrame.value,
                                                onExitFinished = { if (homeCollectionUrl == null) shownCollectionUrl = null },
                                            ) {
                                                AlbumDetailScreen(
                                                    url = url,
                                                    onBack = { homeCollectionUrl = null },
                                                    onPlayQueue = { queue, index -> app.playbackController.playQueue(queue, index) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            AppTab.SEARCH -> {
                                // Same approach as albums opened from Home: Search results
                                // stay composed (and scrolled) underneath, and every album /
                                // artist page is its own layer sliding over them. Back only
                                // slides the top layer away — nothing underneath is rebuilt
                                // mid-animation, which is what made the old swap stutter.
                                // A popped layer stays in [searchLayers] until its exit
                                // animation finishes, then is dropped.
                                if (!(searchLayers.size >= searchDetailStack.size &&
                                        searchLayers.take(searchDetailStack.size) == searchDetailStack)
                                ) {
                                    searchLayers = searchDetailStack
                                }
                                // True only on the frame the Search tab (re)appears.
                                val searchFirstFrame = remember { mutableStateOf(true) }
                                androidx.compose.runtime.SideEffect { searchFirstFrame.value = false }
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then(if (searchDetailStack.isNotEmpty()) Modifier.clearAndSetSemantics {} else Modifier),
                                    ) {
                                        SearchScreen(
                                            onPlayTrack = { track -> app.playbackController.playNow(track) },
                                            onOpenAlbum = { album ->
                                                searchDetailStack = searchDetailStack + SearchDestination.Album(album.url)
                                            },
                                            onOpenArtist = { artist ->
                                                searchDetailStack = searchDetailStack + SearchDestination.Artist(artist.channelUrl)
                                            },
                                            selectedTab = selectedSearchResultTab,
                                            onSelectedTabChange = { selectedSearchResultTab = it },
                                            onOpenGenre = { genre ->
                                                searchDetailStack = searchDetailStack + SearchDestination.Genre(genre.id)
                                            },
                                        )
                                    }
                                    searchLayers.forEachIndexed { index, destination ->
                                        androidx.compose.runtime.key(index, destination) {
                                            val visible = index < searchDetailStack.size
                                            DetailOverlay(
                                                visible = visible,
                                                reduceMotion = reduceMotion,
                                                // Layers under the top one are hidden from TalkBack.
                                                coveredAbove = index < searchDetailStack.size - 1,
                                                startVisible = searchFirstFrame.value,
                                                onExitFinished = {
                                                    if (index >= searchDetailStack.size) searchLayers = searchLayers.take(index)
                                                },
                                            ) {
                                                when (destination) {
                                                    is SearchDestination.Album -> AlbumDetailScreen(
                                                        url = destination.url,
                                                        onBack = { searchDetailStack = searchDetailStack.take(index) },
                                                        onPlayQueue = { queue, i -> app.playbackController.playQueue(queue, i) },
                                                    )
                                                    is SearchDestination.Genre -> {
                                                        val genre = com.whiplash.music.domain.model.exploreGenre(destination.id)
                                                        if (genre != null) {
                                                            com.whiplash.music.ui.explore.GenreScreen(
                                                                genre = genre,
                                                                onBack = { searchDetailStack = searchDetailStack.take(index) },
                                                                onOpenCollection = { album ->
                                                                    searchDetailStack = searchDetailStack.take(index + 1) + SearchDestination.Album(album.url)
                                                                },
                                                                onPlayQueue = { queue, i -> app.playbackController.playQueue(queue, i) },
                                                            )
                                                        }
                                                    }
                                                    is SearchDestination.Artist -> ArtistDetailScreen(
                                                        channelUrl = destination.channelUrl,
                                                        onBack = { searchDetailStack = searchDetailStack.take(index) },
                                                        onPlayQueue = { queue, i -> app.playbackController.playQueue(queue, i) },
                                                        onOpenAlbum = { album ->
                                                            searchDetailStack = searchDetailStack.take(index + 1) + SearchDestination.Album(album.url)
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            AppTab.LOCAL -> AnimatedContent(
                                targetState = historyTab == AppTab.LOCAL,
                                transitionSpec = {
                                    val forward = targetState && !initialState
                                    if (reduceMotion) instantContentTransform() else detailNavTransform(forward)
                                },
                                label = "libraryHistoryContent",
                            ) { isHistory ->
                                if (!isHistory) {
                                    LocalLibraryScreen(
                                        onPlayQueue = { queue, index -> app.playbackController.playQueue(queue, index) },
                                        onOpenHistory = { historyTab = AppTab.LOCAL },
                                        backEnabled = !isPlayerExpanded,
                                        resetKey = libraryResetKey,
                                    )
                                } else {
                                    com.whiplash.music.ui.home.HistoryScreen(
                                        onBack = { historyTab = null },
                                        onPlayQueue = { queue, index -> app.playbackController.playQueue(queue, index) },
                                    )
                                }
                            }
                            AppTab.FAVORITES -> FavoritesScreen(
                                onPlayQueue = { queue, index -> app.playbackController.playQueue(queue, index) },
                                onBack = { selectedTab = AppTab.HOME },
                            )
                            AppTab.PLAYLISTS -> {
                                // The playlist page slides over the list (see DetailOverlay):
                                // the list stays composed and scrolled underneath, and the
                                // page is built before its slide starts, so even a big
                                // imported playlist's heavy first frame can't make it jump.
                                // (This used to be an instant swap for exactly that reason.)
                                var shownPlaylist by remember { mutableStateOf(openPlaylist) }
                                if (openPlaylist != null) shownPlaylist = openPlaylist
                                val playlistsFirstFrame = remember { mutableStateOf(true) }
                                androidx.compose.runtime.SideEffect { playlistsFirstFrame.value = false }
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then(if (openPlaylist != null) Modifier.clearAndSetSemantics {} else Modifier),
                                    ) {
                                        PlaylistsScreen(onOpenPlaylist = { openPlaylist = it })
                                    }
                                    val playlist = shownPlaylist
                                    if (playlist != null) {
                                        androidx.compose.runtime.key(playlist.id) {
                                            DetailOverlay(
                                                visible = openPlaylist?.id == playlist.id,
                                                reduceMotion = reduceMotion,
                                                coveredAbove = false,
                                                startVisible = playlistsFirstFrame.value,
                                                onExitFinished = { if (openPlaylist == null) shownPlaylist = null },
                                            ) {
                                                PlaylistDetailScreen(
                                                    playlist = playlist,
                                                    onBack = { openPlaylist = null },
                                                    onPlayQueue = { queue, index -> app.playbackController.playQueue(queue, index) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            AppTab.SETTINGS -> SettingsScreen(resetKey = settingsResetKey, backEnabled = !isPlayerExpanded)
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
                    // The page fades out into the bottom bar (drawn under the mini player).
                    if (!glass) {
                        com.whiplash.music.ui.theme.BottomBarFade(modifier = Modifier.align(Alignment.BottomCenter))
                        MiniPlayerHost(
                            playerViewModel = playerViewModel,
                            onExpand = { isPlayerExpanded = true },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = GlassTokens.spaceSm)
                                .padding(bottom = GlassTokens.spaceXs),
                        )
                    }
                }
                if (!glass) bottomBar()

            }

          }
          }

            // Liquid Glass chrome: outside the recorded page (glass must never
            // sample itself), floating over its bottom edge.
            if (glass) {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.whiplash.music.ui.theme.LocalGlassBackdrop provides glassBackdrop,
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .then(if (showReplay) Modifier.clearAndSetSemantics {} else Modifier),
                    ) {
                        MiniPlayerHost(
                            playerViewModel = playerViewModel,
                            onExpand = { isPlayerExpanded = true },
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 8.dp),
                        )
                        bottomBar()
                    }
                }
            }

            // 4.7 Replay story: fades and settles in from slightly larger, like
            // opening a story; closing reverses it. Same 380 ms as detail pages.
            AnimatedVisibility(
                visible = showReplay,
                enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(DETAIL_NAV_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                    androidx.compose.animation.scaleIn(tween(DETAIL_NAV_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing), initialScale = 1.06f),
                exit = if (reduceMotion) ExitTransition.None else fadeOut(tween(DETAIL_NAV_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                    androidx.compose.animation.scaleOut(tween(DETAIL_NAV_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing), targetScale = 1.06f),
            ) {
                androidx.compose.runtime.CompositionLocalProvider(com.whiplash.music.ui.theme.LocalPaletteOverride provides darkOnlyPalette) {
                WhiplashTheme {
                com.whiplash.music.ui.replay.ReplayScreen(
                    viewModel = replayViewModel,
                    onClose = { showReplay = false },
                    onPlayQueue = { queue, index ->
                        app.playbackController.playQueue(queue, index)
                        com.whiplash.music.ui.common.ToastController.show("Playing your Replay songs")
                    },
                )
                }
                }
                // Next open starts on the default month again.
                androidx.compose.runtime.DisposableEffect(Unit) { onDispose { replayViewModel.resetMonth() } }
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
                enter = if (reduceMotion) EnterTransition.None else slideInVertically(animationSpec = tween(GlassTokens.animSlow)) { it },
                exit = if (reduceMotion) ExitTransition.None else slideOutVertically(animationSpec = tween(GlassTokens.animSlow)) { it },
            ) {
                val scrimInteractionSource = remember { MutableInteractionSource() }
                var playerHeightPx by remember { mutableStateOf(0f) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { playerHeightPx = it.height.toFloat() }
                        // 2.11: drag down anywhere on the player to dismiss;
                        // it follows the finger and springs back if released early.
                        .playerDismissTransform(
                            state = playerDismiss,
                            heightPx = { playerHeightPx },
                            isBackGesture = { playerBackGestureActive },
                        )
                        .playerDragToDismiss(
                            state = playerDismiss,
                            heightPx = { playerHeightPx },
                            enabled = !playerBackGestureActive,
                            onDismiss = { isPlayerExpanded = false },
                        )
                        .background(darkOnlyPalette?.background ?: MaterialTheme.colorScheme.background)
                        .clickable(
                            interactionSource = scrimInteractionSource,
                            indication = null,
                            onClick = {}, // absorb taps; does not close the player (only the collapse button/back does)
                        ),
                ) {
                    val isFavorite by playerViewModel.isCurrentFavorite.collectAsState()
                    val autoplayEnabled by playerViewModel.autoplayEnabled.collectAsState()
                    val playbackSpeed by playerViewModel.playbackSpeed.collectAsState()
                    val statsForNerdsEnabled by playerViewModel.statsForNerdsEnabled.collectAsState()
                    val playerArtworkColors by playerViewModel.playerArtworkColors.collectAsState()
                    val playerLyricStrip by playerViewModel.playerLyricStrip.collectAsState()
                    val playerHeroArtwork by playerViewModel.playerHeroArtwork.collectAsState()
                    val lyricsBlurUnfocused by playerViewModel.lyricsBlurUnfocused.collectAsState()
                    val playlistsForPlayer by playerViewModel.playlists.collectAsState()
                    val downloadedIds by app.libraryRepository.observeDownloadedIds().collectAsState(initial = emptySet())
                    val currentItemForDownload = playbackState.currentItem
                    androidx.compose.runtime.CompositionLocalProvider(com.whiplash.music.ui.theme.LocalPaletteOverride provides darkOnlyPalette) {
                    WhiplashTheme {
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
                        lyricOffsetMs = lyricOffsetMs,
                        lyricsSourceName = lyricsSourceName,
                        onAdjustLyricOffset = lyricsViewModel::adjustLyricOffset,
                        onResetLyricOffset = lyricsViewModel::resetLyricOffset,
                        playbackSpeed = playbackSpeed,
                        onSetPlaybackSpeed = playerViewModel::setPlaybackSpeed,
                        showStatsForNerds = statsForNerdsEnabled,
                        artworkColorsEnabled = playerArtworkColors,
                        showLyricStrip = playerLyricStrip,
                        heroArtwork = playerHeroArtwork,
                        lyricsBlurUnfocused = lyricsBlurUnfocused,
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
                }
            }

            // App-wide toast host (section: feedback for silent actions —
            // favoriting, pinning, playlist/queue changes, etc.), drawn
            // last so it renders above even the expanded full player.
            com.whiplash.music.ui.theme.GlassToastHost(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(innerPadding)
                    .padding(bottom = GlassTokens.spaceXl + GlassTokens.miniPlayerReservedHeight),
            )
        }
    }
}

/**
 * An AnimatedContent transition with no motion at all, used when reduced
 * motion is on. sizeTransform is null for the same reason as the playlist
 * swap above: the default would still animate the container's height.
 */
private fun instantContentTransform(): ContentTransform = ContentTransform(
    targetContentEnter = EnterTransition.None,
    initialContentExit = ExitTransition.None,
    sizeTransform = null,
)

/**
 * Push/pop transition for detail screens (album, playlist from Home, History,
 * search detail). Same length both ways, with Material's standard easing: the
 * new screen slides a quarter of the width while crossfading, and the old one
 * drifts the other way and fades out. The exit used to be twice as fast as the
 * entry, which made going back look abrupt.
 */
private fun detailNavTransform(forward: Boolean): androidx.compose.animation.ContentTransform {
    val slide = tween<androidx.compose.ui.unit.IntOffset>(DETAIL_NAV_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    val enter = slideInHorizontally(slide) { w -> if (forward) w / 4 else -w / 4 } +
        fadeIn(tween(DETAIL_NAV_MS, delayMillis = DETAIL_NAV_MS / 6, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
    val exit = slideOutHorizontally(slide) { w -> if (forward) -w / 6 else w / 6 } +
        fadeOut(tween(DETAIL_NAV_MS / 2, easing = androidx.compose.animation.core.FastOutLinearInEasing))
    return enter.togetherWith(exit).apply { targetContentZIndex = if (forward) 1f else -1f }
}

private const val DETAIL_NAV_MS = 380

/** Album-over-Home push: slides in a quarter width while fading in. */
private fun detailOverlayEnter(): androidx.compose.animation.EnterTransition =
    slideInHorizontally(tween(DETAIL_NAV_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { w -> w / 4 } +
        fadeIn(tween(DETAIL_NAV_MS, easing = androidx.compose.animation.core.LinearOutSlowInEasing))

/** The exact reverse of [detailOverlayEnter], same length and easing, so Back feels like entry played backwards. */
private fun detailOverlayExit(): androidx.compose.animation.ExitTransition =
    slideOutHorizontally(tween(DETAIL_NAV_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { w -> w / 4 } +
        fadeOut(tween(DETAIL_NAV_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing))

/**
 * One detail page drawn over the screen beneath it: slides a quarter of the
 * width and fades, in when [visible] turns true and back out when it turns
 * false, same length and easing both ways. Opaque and touch-blocking, so
 * nothing underneath reacts. [onExitFinished] runs once it has fully left.
 *
 * The page is composed (invisibly) before the slide starts, and the slide
 * only begins two frames later. Building a page for the first time can take
 * one long frame (a big playlist measured 95-165 ms); an animation already
 * running would skip ahead through that frame and visibly jump, so the work
 * is done first and the motion afterwards.
 */
@Composable
private fun DetailOverlay(
    visible: Boolean,
    reduceMotion: Boolean,
    coveredAbove: Boolean,
    onExitFinished: () -> Unit,
    startVisible: Boolean = false,
    content: @Composable () -> Unit,
) {
    val progress = remember { androidx.compose.animation.core.Animatable(if (startVisible && visible) 1f else 0f) }
    val latestOnExit by androidx.compose.runtime.rememberUpdatedState(onExitFinished)
    androidx.compose.runtime.LaunchedEffect(visible, reduceMotion) {
        if (visible) {
            if (progress.value < 1f) {
                androidx.compose.runtime.withFrameNanos { }
                androidx.compose.runtime.withFrameNanos { }
                if (reduceMotion) progress.snapTo(1f) else progress.animateTo(1f, detailNavSpec())
            }
        } else {
            if (reduceMotion) progress.snapTo(0f) else progress.animateTo(0f, detailNavSpec())
            latestOnExit()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = progress.value
                translationX = (1f - p) * size.width / 4f
                alpha = p
            }
            .appBackground()
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .then(if (coveredAbove) Modifier.clearAndSetSemantics {} else Modifier),
    ) {
        content()
    }
}

private fun detailNavSpec() =
    tween<Float>(DETAIL_NAV_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)
