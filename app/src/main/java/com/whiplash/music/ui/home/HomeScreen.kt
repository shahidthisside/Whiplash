package com.whiplash.music.ui.home
// Developed by Shahid Ansari — github.com/shahidthisside (-SA)

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.RemoveCircleOutline
import com.whiplash.music.ui.common.selectedHighlight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whiplash.music.WhiplashApplication
import com.whiplash.music.ui.common.ToastController
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.domain.model.speedDialIdentity
import com.whiplash.music.ui.player.SongActionsContent
import com.whiplash.music.ui.player.SongActionsViewModel
import com.whiplash.music.ui.player.SongActionsViewModelFactory
import com.whiplash.music.ui.theme.GlassArtworkThumbnail
import com.whiplash.music.ui.theme.PlainIconButton
import com.whiplash.music.ui.theme.GlassListItem
import com.whiplash.music.ui.theme.GlassSheet
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.WhiplashRadius

/**
 * Home screen (section 31). "Speed dial" (YouTube-Music-style 3x3 grid of
 * recently played artwork — real listening history, not fabricated) and
 * Quick Picks (real search-backed suggestions). Sections only render when
 * they have real backing data — no empty/fake placeholder sections.
 * (built by -SA · github.com/shahidthisside)
 */
private enum class SheetOrigin { SPEED_DIAL, QUICK_PICKS, SHELF }

/** Number of skeleton rows shown while Quick Picks' first real results are loading — roughly matches how many rows fit before scrolling. */
private const val QUICK_PICKS_SKELETON_ROW_COUNT = 5

@androidx.compose.material3.ExperimentalMaterial3Api
@ExperimentalFoundationApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPlayTrack: (PlayableItem) -> Unit,
    onOpenHistory: () -> Unit,
    onPlayQueue: (queue: List<PlayableItem>, startIndex: Int) -> Unit = { _, _ -> },
    onOpenCollection: (com.whiplash.music.domain.model.YoutubePlaylistResult) -> Unit = {},
    // Hoisted by the caller so Home keeps its scroll position while an album
    // or History is open on top of it (this screen leaves composition then).
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    // 4.7: Monthly Replay card, drawn after Quick Picks when non-null.
    replayCard: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext as WhiplashApplication
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(
            app.libraryRepository, app.youtubeSearchRepository, app.settingsRepository, app.cloudSyncManager.onlineChanges(),
            QuickPicksRadio(app.database.playEventDao()) { id ->
                // YT Music's "You might also like" for the seed, then its radio.
                kotlinx.coroutines.coroutineScope {
                    val related = async { runCatching { app.musicSources.related(id) }.getOrDefault(emptyList()) }
                    val radio = async { runCatching { app.musicSources.radioPage(id, null).items }.getOrDefault(emptyList()) }
                    (related.await().take(12) + radio.await()).distinctBy { it.id }
                }
            },
        ),
    )
    val songActionsViewModel: SongActionsViewModel = viewModel(
        factory = SongActionsViewModelFactory(app.libraryRepository, app.downloadManager),
    )
    val downloadedIds by app.libraryRepository.observeDownloadedIds().collectAsState(initial = emptySet())
    val downloadProgress by app.downloadManager.progress.collectAsState()
    var cancelDownloadTarget by remember { mutableStateOf<PlayableItem?>(null) }
    var removeDownloadTarget by remember { mutableStateOf<PlayableItem?>(null) }
    val speedDial by viewModel.speedDial.collectAsState()
    val isSpeedDialLoaded by viewModel.isSpeedDialLoaded.collectAsState()
    val quickPicks by viewModel.quickPicks.collectAsState()
    val isLoadingQuickPicks by viewModel.isLoadingQuickPicks.collectAsState()
    val quickPicksSettled by viewModel.quickPicksSettled.collectAsState()
    val online by remember { app.cloudSyncManager.onlineChanges() }.collectAsState(initial = true)
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullState = rememberPullToRefreshState()
    val haptic = LocalHapticFeedback.current
    var actionsSheetItem by remember { mutableStateOf<PlayableItem?>(null) }
    var actionsSheetOrigin by remember { mutableStateOf(SheetOrigin.SPEED_DIAL) }

    // ---- Quick Picks multi-select (song menu → Select) --------------------
    val qpSelection = com.whiplash.music.ui.common.rememberItemSelection<PlayableItem>(quickPicks) { "${it.source}:${it.id}" }
    var qpBulkPlaylist by remember { mutableStateOf<List<PlayableItem>?>(null) }
    var qpBulkCreate by remember { mutableStateOf(false) }
    var qpUnlikeConfirm by remember { mutableStateOf<List<PlayableItem>?>(null) }
    val qpFavoriteKeys by app.libraryRepository.observeFavoriteKeys().collectAsState(initial = emptySet())
    run {
        val chosen = qpSelection.selected()
        val all = com.whiplash.music.ui.player.buildSelectionActions(
            selected = chosen,
            downloadedIds = downloadedIds,
            favoriteKeys = qpFavoriteKeys,
            playlistContext = null,
            inHistory = false,
            onPlay = { app.playbackController.playQueue(chosen, 0); qpSelection.clear() },
            onShuffle = { app.playbackController.playQueue(chosen.shuffled(), 0); qpSelection.clear() },
            onPlayNext = { app.playbackController.playAllNext(chosen); qpSelection.clear() },
            onAddToQueue = { app.playbackController.addAllToQueue(chosen); qpSelection.clear() },
            onAddToPlaylist = { qpBulkPlaylist = chosen },
            onFavorite = { songActionsViewModel.addAllToFavorites(it); qpSelection.clear() },
            onUnfavorite = { qpUnlikeConfirm = it },
            onDownload = { app.downloadManager.downloadAll(it); qpSelection.clear() },
            onRemoveFromPlaylist = {},
            onRemoveFromHistory = {},
            onRemoveDownloads = {},
            onSaveToDevice = {},
        ).filter { it.label != "Save to device" && it.label != "Delete download" } +
            // Quick Picks' own removal (the song menu offers it too); no confirm, it's only a suggestion.
            listOfNotNull(
                chosen.filterIsInstance<PlayableItem.YoutubeTrack>().takeIf { it.isNotEmpty() }?.let { yt ->
                    com.whiplash.music.ui.common.action("Remove from Quick Picks", Icons.Filled.RemoveCircleOutline, destructive = true) {
                        yt.forEach { viewModel.removeFromQuickPicks(it) }
                        com.whiplash.music.ui.common.ToastController.show(if (yt.size == 1) "Removed from Quick Picks" else "${yt.size} songs removed from Quick Picks")
                        qpSelection.clear()
                    }
                },
            )
        val third = all.firstOrNull { it.label == "Like" || it.label == "Unlike" } ?: all.firstOrNull { it.label == "Add to queue" }
        val primary = all.filter { it.label == "Play next" || it.label == "Add to playlist" } + listOfNotNull(third)
        com.whiplash.music.ui.common.SelectionChrome(
            selection = qpSelection,
            noun = "song",
            primary = primary,
            more = all.filter { it !in primary },
        )
    }
    val qpClick: (PlayableItem) -> Unit = { track -> if (qpSelection.selecting) qpSelection.toggle(track) else onPlayTrack(track) }
    var addToPlaylistItem by remember { mutableStateOf<PlayableItem?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showClearSpeedDialConfirm by remember { mutableStateOf(false) }
    // 4.2: grid ⇄ list, persisted (and in backup) so it sticks across launches.
    val speedDialListView = viewModel.speedDialListView.collectAsState().value ?: false
    // Grid is the default, so assume it until the saved choice loads (no list-then-grid flash on first launch).
    val quickPicksGridView = viewModel.quickPicksGridView.collectAsState().value ?: true
    val quickPicksGridCount = viewModel.quickPicksGridCount.collectAsState().value ?: 9
    val speedDialPeek by viewModel.speedDialPeek.collectAsState()
    val speedDialPerPage by viewModel.speedDialGridCount.collectAsState()
    val quickPicksPeek by viewModel.quickPicksPeek.collectAsState()
    val layoutScope = androidx.compose.runtime.rememberCoroutineScope()
    // 4.1: shelves feed (can be turned off in Settings → Home shelves).
    val shelvesEnabled = viewModel.homeShelvesEnabled.collectAsState().value
    androidx.compose.runtime.LaunchedEffect(shelvesEnabled) {
        shelvesEnabled?.let { viewModel.setShelvesEnabled(it) }
    }
    val shelves by viewModel.shelves.collectAsState()
    val isLoadingShelves by viewModel.isLoadingShelves.collectAsState()
    val hasMoreShelves by viewModel.hasMoreShelves.collectAsState()
    var collectionSheetItem by remember { mutableStateOf<com.whiplash.music.domain.model.YoutubePlaylistResult?>(null) }
    val onShelfClick: (HomeViewModel.ShelfItem) -> Unit = { item ->
        when (item) {
            is HomeViewModel.ShelfItem.Track -> onPlayTrack(item.track)
            is HomeViewModel.ShelfItem.Collection -> onOpenCollection(item.collection)
        }
    }
    val onShelfLongClick: (HomeViewModel.ShelfItem) -> Unit = { item ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        when (item) {
            is HomeViewModel.ShelfItem.Track -> {
                actionsSheetOrigin = SheetOrigin.SHELF
                actionsSheetItem = item.track
            }
            is HomeViewModel.ShelfItem.Collection -> collectionSheetItem = item.collection
        }
    }
    // Infinite feed: fetch the next shelves once the last loaded one scrolls into view.
    val nearShelvesEnd by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo.map { it.key }
            visible.any { it == "shelves-end" }
        }
    }
    androidx.compose.runtime.LaunchedEffect(nearShelvesEnd, shelves.size, hasMoreShelves) {
        if (nearShelvesEnd && shelvesEnabled == true && hasMoreShelves) viewModel.loadMoreShelves()
    }

    // The truly-empty state ("no history, no Quick Picks, nothing loading")
    // must wait for isSpeedDialLoaded — otherwise this renders on every
    // single cold start for the one frame before Room's Speed dial flow
    // has emitted anything yet, replacing what should be a loading
    // skeleton with a flash of "Play something to see it here." that
    // then immediately gets replaced by real content once data arrives.
    if (isSpeedDialLoaded && speedDial.isEmpty() && quickPicks.isEmpty() && !isLoadingQuickPicks && quickPicksSettled && online) {
        // Pullable too, and deliberately so: an empty Home is the single
        // most likely place someone reaches for a pull-to-refresh, and a
        // screen that says "nothing here" while ignoring the gesture reads
        // as broken. Needs verticalScroll because pull-to-refresh is driven
        // by nested-scroll events — a plain Box emits none, so the gesture
        // would be silently dead here even though the indicator existed.
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshHome() },
            state = pullState,
            indicator = { HomeRefreshIndicator(pullState, isRefreshing, Modifier.align(Alignment.TopCenter)) },
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Play something to see it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WhiplashColors.textSecondary,
                )
            }
        }
        return
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshHome() },
        state = pullState,
        indicator = { HomeRefreshIndicator(pullState, isRefreshing, Modifier.align(Alignment.TopCenter)) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = GlassTokens.spaceMd),
            verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = GlassTokens.miniPlayerReservedHeight),
        ) {
            // Show a real skeleton — not a blank gap, not the earlier
            // "Play something to see it here." flash — for the brief window
            // between the screen first composing and Speed dial's Room flow
            // emitting its first real snapshot, since that snapshot could
            // turn out to have items (needing the section to have already
            // been "expecting" content) or turn out empty (in which case the
            // skeleton simply disappears once isSpeedDialLoaded flips true).
            if (!isSpeedDialLoaded) {
                item {
                    SectionHeader(title = "Speed dial")
                }
                item { SpeedDialSkeletonGrid() }
            } else if (speedDial.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Speed dial",
                        onHistory = onOpenHistory,
                        onClear = { showClearSpeedDialConfirm = true },
                        listView = speedDialListView,
                        onToggleLayout = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.setSpeedDialListView(!speedDialListView)
                        },
                    )
                }
                val onLongPressSpeedDial: (PlayableItem) -> Unit = { track ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    actionsSheetOrigin = SheetOrigin.SPEED_DIAL
                    actionsSheetItem = track
                }
                if (speedDialListView) {
                    // The list stays one screen's worth; paging is a grid thing.
                    items(speedDial.take(speedDialPerPage), key = { it.speedDialIdentity().let { (id, source) -> "sdl:$source:$id" } }) { track ->
                        GlassListItem(
                            title = track.title,
                            subtitle = track.artist,
                            onClick = { onPlayTrack(track) },
                            onLongClick = { onLongPressSpeedDial(track) },
                            leading = { GlassArtworkThumbnail(artworkUri = track.artworkUri) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                } else {
                    item {
                        if (speedDial.size > speedDialPerPage) {
                            SpeedDialPager(speedDial, onPlayTrack, onLongPressSpeedDial, peek = speedDialPeek, perPage = speedDialPerPage)
                        } else {
                            SpeedDialGrid(
                                items = speedDial,
                                onPlayTrack = onPlayTrack,
                                onLongPressTrack = onLongPressSpeedDial,
                            )
                        }
                    }
                }
            }

            // Shown from the very first frame (skeleton) until the first load
            // settles, instead of the whole section popping in when it arrives.
            // Offline with nothing yet: keep the skeleton up (it loads by itself on reconnect).
            val qpPending = quickPicks.isEmpty() && (isLoadingQuickPicks || !quickPicksSettled || !online)
            if (quickPicks.isNotEmpty() || qpPending) {
                item {
                    SectionHeader(
                        title = "Quick Picks",
                        // Only offered once there is something to play. The header
                        // itself also renders during the initial load, when
                        // quickPicks is still empty, and a Play all that silently
                        // did nothing would be worse than no button.
                        onPlayAll = if (quickPicks.isNotEmpty()) {
                            { onPlayQueue(quickPicks, 0) }
                        } else {
                            null
                        },
                        // Scoped to this section only: it lives in the Quick
                        // Picks header, so it refreshes Quick Picks and leaves
                        // Speed dial alone. Pulling the whole screen down is
                        // the gesture that refreshes everything.
                        onRefresh = { viewModel.refreshQuickPicks() },
                        isRefreshing = isLoadingQuickPicks,
                        listView = !quickPicksGridView,
                        onToggleLayout = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.setQuickPicksGridView(!quickPicksGridView)
                        },
                    )
                }
                val onLongPressQuickPick: (PlayableItem) -> Unit = { track ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (qpSelection.selecting) {
                        qpSelection.toggle(track)
                    } else {
                        actionsSheetOrigin = SheetOrigin.QUICK_PICKS
                        actionsSheetItem = track
                    }
                }
                val qpSelected: (PlayableItem) -> Boolean = { qpSelection.isSelected(it) }
                if (qpPending && !online) {
                    item(key = "qp-offline") {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = GlassTokens.spaceXs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Icon(
                                Icons.Filled.CloudOff,
                                contentDescription = null,
                                tint = WhiplashColors.textTertiary,
                                modifier = Modifier.size(16.dp),
                            )
                            androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 6.dp))
                            Text(
                                "You're offline. Quick Picks will load when you reconnect.",
                                style = MaterialTheme.typography.bodySmall,
                                color = WhiplashColors.textSecondary,
                            )
                        }
                    }
                }
                if (qpPending) {
                    item(key = "qp-skeleton") {
                        if (quickPicksGridView) QuickPicksSkeletonGrid(rows = ((quickPicksGridCount.takeIf { it > 0 } ?: 9) + 2) / 3)
                        else Column(verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceXs)) {
                            repeat(6) { com.whiplash.music.ui.theme.ShimmerSkeletonRow() }
                        }
                    }
                }
                if (quickPicksGridView && quickPicks.isNotEmpty()) {
                    val perPage = quickPicksGridCount
                    if (perPage == 0) {
                        // "All": one tall grid, rows of three.
                        val rows = quickPicks.chunked(3)
                        items(rows.size, key = { "qpgrid:${rows[it].first().id}" }) { rowIndex ->
                            QuickPicksTileRow(rows[rowIndex], qpClick, onLongPressQuickPick, Modifier.animateItem(), qpSelected)
                        }
                    } else {
                        // Pages of [perPage] songs; swipe sideways for the rest,
                        // with the next page peeking in at the edge.
                        item(key = "qpgrid-pager") {
                            QuickPicksPager(quickPicks, perPage, qpClick, onLongPressQuickPick, qpSelected, peek = quickPicksPeek)
                        }
                    }
                }
                if (!quickPicksGridView) items(quickPicks, key = { "quickpick:${it.id}" }) { track ->
                    val qpIsSelected = qpSelection.isSelected(track)
                    GlassListItem(
                        title = track.title,
                        subtitle = track.artist,
                        modifier = Modifier.selectedHighlight(qpIsSelected),
                        onClick = { qpClick(track) },
                        onLongClick = { onLongPressQuickPick(track) },
                        leading = {
                            Box(contentAlignment = Alignment.Center) {
                                GlassArtworkThumbnail(artworkUri = track.artworkUri)
                                com.whiplash.music.ui.common.SelectionCheck(visible = qpIsSelected)
                            }
                        },
                        trailing = {
                            // Same animated progress-ring/checkmark/failed
                            // badge PlayableItemsList shows elsewhere (Search,
                            // Local Library, Downloads tab) — a real, reported
                            // gap: Quick Picks rows only ever checked the
                            // completed-downloads set, never the in-flight
                            // progress map, so a track downloaded from Quick
                            // Picks itself showed no progress indicator and no
                            // checkmark until the next full recomposition
                            // (e.g. navigating away and back). Also adds the
                            // missing 3-dot "more options" button — Quick
                            // Picks rows previously only opened the actions
                            // sheet via long-press, with no visible affordance
                            // for it at all, unlike every other track list in
                            // the app.
                            val inFlightProgress = downloadProgress[track.id]
                            val downloaded = track.id in downloadedIds
                            androidx.compose.animation.AnimatedContent(
                                targetState = when {
                                    inFlightProgress?.failed == true -> "failed"
                                    inFlightProgress != null -> "downloading"
                                    downloaded -> "downloaded"
                                    else -> "none"
                                },
                                label = "quickPicksDownloadStatusBadge",
                            ) { state ->
                                when (state) {
                                    "downloading" -> Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .padding(end = GlassTokens.spaceXs)
                                            .clickable(
                                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                indication = null,
                                                role = androidx.compose.ui.semantics.Role.Button,
                                                onClick = { cancelDownloadTarget = track },
                                            )
                                            .semantics { contentDescription = "Cancel download of ${track.title}" },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            progress = { inFlightProgress?.fraction ?: 0f },
                                            color = com.whiplash.music.ui.theme.WhiplashColors.accent,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    "failed" -> androidx.compose.material3.Icon(
                                        Icons.Filled.ErrorOutline,
                                        contentDescription = "Download failed for ${track.title}",
                                        tint = com.whiplash.music.ui.theme.WhiplashColors.error,
                                        modifier = Modifier.size(18.dp).padding(end = GlassTokens.spaceXs),
                                    )
                                    "downloaded" -> androidx.compose.material3.Icon(
                                        Icons.Filled.DownloadDone,
                                        contentDescription = "Downloaded",
                                        tint = com.whiplash.music.ui.theme.WhiplashColors.accent,
                                        modifier = Modifier.size(18.dp).padding(end = GlassTokens.spaceXs),
                                    )
                                    else -> androidx.compose.foundation.layout.Spacer(Modifier.size(0.dp))
                                }
                            }
                            if (qpSelection.selecting) androidx.compose.foundation.layout.Spacer(Modifier.size(48.dp))
                            else com.whiplash.music.ui.theme.PlainIconButton(
                                contentDescription = "More options for ${track.title}",
                                onClick = {
                                    actionsSheetOrigin = SheetOrigin.QUICK_PICKS
                                    actionsSheetItem = track
                                },
                                size = 48.dp,
                            ) {
                                androidx.compose.material3.Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = null,
                                    tint = com.whiplash.music.ui.theme.WhiplashColors.textSecondary,
                                )
                            }
                        },
                    )
                }
            }

            if (replayCard != null) {
                item(key = "replay-card") {
                    Box(Modifier.padding(top = GlassTokens.spaceMd).animateItem()) { replayCard() }
                }
            }

            // 4.1: hero + shelves after Quick Picks; the feed keeps loading at the end.
            if (shelvesEnabled == true) {
                val heroShelf = shelves.firstOrNull()
                val hero = heroShelf?.items?.firstOrNull { it is HomeViewModel.ShelfItem.Collection }
                if (heroShelf == null && isLoadingShelves) {
                    item(key = "hero-skeleton") { Box(Modifier.padding(top = GlassTokens.spaceMd)) { ShelfHeroSkeleton() } }
                    item(key = "shelf-skeleton-0") { ShelfSkeleton() }
                } else if (heroShelf != null) {
                    if (hero != null) {
                        item(key = "hero") {
                            Box(Modifier.padding(top = GlassTokens.spaceMd)) {
                                ShelfHeroCard(
                                    label = heroLabel(heroShelf.spec.kind),
                                    item = hero,
                                    onClick = { onShelfClick(hero) },
                                    onLongClick = { onShelfLongClick(hero) },
                                )
                            }
                        }
                    }
                    shelves.forEachIndexed { index, shelf ->
                        // The hero is not repeated on its own shelf.
                        val shelfItems = if (index == 0 && hero != null) shelf.items - hero else shelf.items
                        if (shelfItems.isNotEmpty()) {
                            item(key = "shelf:${shelf.spec.key}") {
                                HomeShelfRow(shelf, shelfItems, onShelfClick, onShelfLongClick)
                            }
                        }
                    }
                    if (isLoadingShelves) item(key = "shelf-skeleton-more") { ShelfSkeleton() }
                    // Sentinel for paging (zero height).
                    item(key = "shelves-end") { Box(Modifier.size(1.dp)) }
                }
            }

        }
    }

    // 3-dot / long-press song actions sheet (section 51), including
    // Pin/Unpin to Speed dial — reachable via long-press on either a Speed
    // dial tile or a Quick Picks row, since Speed dial tiles have no room
    // for an inline 3-dot button of their own at that tile size.
    val collectionItem = collectionSheetItem
    if (collectionItem != null) {
        GlassSheet(onDismissRequest = { collectionSheetItem = null }) {
            CollectionActionsContent(
                collection = collectionItem,
                onOpen = {
                    collectionSheetItem = null
                    onOpenCollection(collectionItem)
                },
                onPlay = {
                    collectionSheetItem = null
                    layoutScope.launch { playCollection(app, collectionItem, shuffle = false, onPlayQueue) }
                },
                onShuffle = {
                    collectionSheetItem = null
                    layoutScope.launch { playCollection(app, collectionItem, shuffle = true, onPlayQueue) }
                },
                onShare = {
                    collectionSheetItem = null
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, collectionItem.url)
                    }
                    runCatching { context.startActivity(android.content.Intent.createChooser(send, "Share ${collectionItem.title}")) }
                        .onFailure { ToastController.show("Couldn't open share") }
                },
            )
        }
    }

    val sheetItem = actionsSheetItem
    if (sheetItem != null) {
        val isFavorite by app.libraryRepository.observeIsFavorite(sheetItem).collectAsState(initial = false)
        val isPinned by app.libraryRepository.observeIsPinned(sheetItem).collectAsState(initial = false)
        GlassSheet(onDismissRequest = { actionsSheetItem = null }) {
            SongActionsContent(
                item = sheetItem,
                isFavorite = isFavorite,
                onSelect = if (actionsSheetOrigin == SheetOrigin.QUICK_PICKS && quickPicks.any { it.id == sheetItem.id }) {
                    { qpSelection.start(sheetItem); actionsSheetItem = null }
                } else null,
                onPlayNext = {
                    app.playbackController.playNext(sheetItem)
                    actionsSheetItem = null
                },
                onAddToQueue = {
                    app.playbackController.addToQueue(sheetItem)
                    actionsSheetItem = null
                },
                onToggleFavorite = {
                    songActionsViewModel.toggleFavorite(sheetItem, isCurrentlyFavorite = isFavorite)
                    actionsSheetItem = null
                },
                onStartRadio = if (sheetItem is PlayableItem.YoutubeTrack) {
                    { app.playbackController.playNow(sheetItem); actionsSheetItem = null }
                } else null,
                onShare = if (sheetItem is PlayableItem.YoutubeTrack) {
                    { com.whiplash.music.ui.player.shareYoutubeTrack(context, sheetItem); actionsSheetItem = null }
                } else null,
                onAddToPlaylist = {
                    addToPlaylistItem = sheetItem
                    actionsSheetItem = null
                },
                isPinned = isPinned,
                onTogglePinned = {
                    songActionsViewModel.togglePinned(sheetItem, isCurrentlyPinned = isPinned)
                    actionsSheetItem = null
                },
                onRemoveFromSpeedDial = if (actionsSheetOrigin == SheetOrigin.SPEED_DIAL) {
                    {
                        songActionsViewModel.removeFromSpeedDial(sheetItem)
                        actionsSheetItem = null
                    }
                } else null,
                onRemoveFromQuickPicks = if (actionsSheetOrigin == SheetOrigin.QUICK_PICKS && sheetItem is PlayableItem.YoutubeTrack) {
                    {
                        viewModel.removeFromQuickPicks(sheetItem)
                        actionsSheetItem = null
                    }
                } else null,
                isDownloaded = sheetItem is PlayableItem.DownloadedTrack || sheetItem.id in downloadedIds,
                onDownload = if (sheetItem is PlayableItem.YoutubeTrack && sheetItem.id !in downloadedIds) {
                    {
                        app.downloadManager.startDownload(sheetItem)
                        actionsSheetItem = null
                    }
                } else null,
                onRemoveDownload = if (sheetItem.id in downloadedIds || sheetItem is PlayableItem.DownloadedTrack) {
                    {
                        // Same UAT-audit fix as PlayableItemsList.kt —
                        // route through a confirm dialog rather than
                        // deleting instantly, matching every other
                        // download-destructive action.
                        removeDownloadTarget = sheetItem
                        actionsSheetItem = null
                    }
                } else null,
            )
        }
    }

    val qpBulk = qpBulkPlaylist
    if (qpBulk != null) {
        val playlists by app.libraryRepository.observePlaylists().collectAsState(initial = emptyList())
        GlassSheet(onDismissRequest = { qpBulkPlaylist = null }) {
            com.whiplash.music.ui.player.AddToPlaylistContent(
                playlists = playlists,
                title = if (qpBulk.size == 1) "Add 1 song to playlist" else "Add ${qpBulk.size} songs to playlist",
                onSelectPlaylist = { p ->
                    songActionsViewModel.addAllToPlaylist(qpBulk, p.id, p.name)
                    qpBulkPlaylist = null
                    qpSelection.clear()
                },
                onCreateNew = { qpBulkCreate = true },
            )
        }
    }
    if (qpBulkCreate) {
        com.whiplash.music.ui.theme.GlassTextInputDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onConfirm = { name ->
                qpBulkPlaylist?.let { songActionsViewModel.createPlaylistAndAddAll(name, it) }
                qpBulkCreate = false
                qpBulkPlaylist = null
                qpSelection.clear()
            },
            onDismiss = { qpBulkCreate = false },
        )
    }
    val qpUnlike = qpUnlikeConfirm
    if (qpUnlike != null) {
        com.whiplash.music.ui.theme.GlassConfirmDialog(
            title = if (qpUnlike.size == 1) "Remove 1 song from favorites?" else "Remove ${qpUnlike.size} songs from favorites?",
            message = "You can like them again any time.",
            confirmLabel = "Remove",
            onConfirm = {
                songActionsViewModel.removeAllFromFavorites(qpUnlike)
                qpUnlikeConfirm = null
                qpSelection.clear()
            },
            onDismiss = { qpUnlikeConfirm = null },
        )
    }

    val playlistTargetItem = addToPlaylistItem
    if (playlistTargetItem != null) {
        val playlists by app.libraryRepository.observePlaylists().collectAsState(initial = emptyList())
        GlassSheet(onDismissRequest = { addToPlaylistItem = null }) {
            com.whiplash.music.ui.player.AddToPlaylistContent(
                item = playlistTargetItem,
                playlists = playlists,
                onSelectPlaylist = { playlist ->
                    songActionsViewModel.addToPlaylist(playlistTargetItem, playlist.id, playlist.name)
                    addToPlaylistItem = null
                },
                onCreateNew = { showCreatePlaylistDialog = true },
            )
        }
    }

    if (showCreatePlaylistDialog) {
        com.whiplash.music.ui.theme.GlassTextInputDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onConfirm = { name ->
                val item = playlistTargetItem
                if (item != null) songActionsViewModel.createPlaylistAndAdd(name, item)
                showCreatePlaylistDialog = false
                addToPlaylistItem = null
            },
            onDismiss = { showCreatePlaylistDialog = false },
        )
    }

    if (showClearSpeedDialConfirm) {
        com.whiplash.music.ui.theme.GlassConfirmDialog(
            title = "Clear Speed dial?",
            message = "This clears your recently played history. Pinned songs will stay. This can't be undone.",
            onConfirm = {
                viewModel.clearHistory()
                showClearSpeedDialConfirm = false
            },
            onDismiss = { showClearSpeedDialConfirm = false },
        )
    }

    val cancelTarget = cancelDownloadTarget
    if (cancelTarget != null) {
        com.whiplash.music.ui.theme.GlassConfirmDialog(
            title = "Cancel download?",
            message = "\"${cancelTarget.title}\" is still downloading. Canceling will delete the partial download.",
            confirmLabel = "Cancel download",
            dismissLabel = "Keep downloading",
            onConfirm = {
                app.downloadManager.cancelDownload(cancelTarget.id)
                cancelDownloadTarget = null
            },
            onDismiss = { cancelDownloadTarget = null },
        )
    }

    val removeTarget = removeDownloadTarget
    if (removeTarget != null) {
        com.whiplash.music.ui.theme.GlassConfirmDialog(
            title = "Remove download?",
            message = "\"${removeTarget.title}\" will be deleted from this device. You can download it again later.",
            confirmLabel = "Remove",
            dismissLabel = "Cancel",
            onConfirm = {
                songActionsViewModel.removeDownload(removeTarget.id)
                removeDownloadTarget = null
            },
            onDismiss = { removeDownloadTarget = null },
        )
    }
}

/**
 * 3x3 grid of square, rounded artwork tiles — matches YouTube Music's
 * "Speed dial" redesign of its former "Listen again" carousel (real
 * design reference researched before building this).
 *
 * Uses [LazyVerticalGrid] with `userScrollEnabled = false` rather than a
 * plain Column/Row of Rows — not for scrolling (this never scrolls
 * internally: it's capped at 9 items and lives inside the outer
 * LazyColumn as a single item, exactly as before), but specifically to
 * get [androidx.compose.foundation.lazy.grid.LazyGridItemScope.animateItem]'s
 * built-in reorder animation on each tile, which only exists on lazy
 * layouts. A real, reported UX gap: playing (or long-pressing on for
 * pinning) a tile updates the underlying history/pinned data live, which
 * reorders this exact list (most-recently-played moves toward the front
 * of the non-pinned tiles, a newly-pinned track jumps to right after the
 * other pinned ones) — with a plain Row/Column that reorder was an
 * instant, jarring snap with no visual continuity at all connecting a
 * tile's old position to its new one. animateItem() gives every tile a
 * smooth slide to its new grid cell instead, keyed by
 * [PlayableItem.speedDialIdentity] (the same normalized YOUTUBE/DOWNLOAD
 * identity Speed dial's own dedup already uses) so Compose can actually
 * tell "this is the same tile, just moved" rather than treating the
 * reordered list as all-new content with no animatable identity.
 */
@ExperimentalFoundationApi
@Composable
private fun SpeedDialGrid(
    items: List<PlayableItem>,
    onPlayTrack: (PlayableItem) -> Unit,
    onLongPressTrack: (PlayableItem) -> Unit,
    // Pages of a pager all keep the full height, so it doesn't jump mid-swipe.
    minRows: Int = 0,
) {
    val rows = maxOf((items.size + 2) / 3, minRows)
    val spacing = GlassTokens.spaceSm

    // Real, reported bug: row height used to be a hardcoded 132.dp guess.
    // Each tile's actual height is (square artwork, whose side length is
    // 1/3 of the *available* width — itself different across phones with
    // different screen widths/densities/font scales) + spaceXs + one
    // line of labelMedium text. 132.dp only happened to be tall enough
    // on some devices; on others the real content was taller than the
    // grid's fixed-height container clipped it to, silently cutting off
    // each tile's title text against whatever rendered directly below
    // (Quick Picks' own header) with no visible error — exactly the kind
    // of device-specific layout bug a single hardcoded dp value causes.
    // BoxWithConstraints measures the real available width this grid
    // will actually get on *this* device, and the artwork's height is
    // exactly that (aspectRatio(1f)) divided evenly across 3 columns
    // minus the two inter-column gaps — matching LazyVerticalGrid's own
    // real column math exactly, not a separate approximation of it. The
    // label's own height is read from the same TextStyle actually used
    // to render it (labelMedium) via LocalDensity, rather than guessed.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val tileWidth = (maxWidth - spacing * 2) / 3
        val labelHeight = with(density) {
            val lineHeightSp = MaterialTheme.typography.labelMedium.lineHeight
            if (lineHeightSp.isSp) lineHeightSp.toDp() else 16.dp
        }
        val rowHeight = tileWidth + GlassTokens.spaceXs + labelHeight

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight * rows + spacing * (rows - 1).coerceAtLeast(0)),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            userScrollEnabled = false,
        ) {
            items(items, key = { it.speedDialIdentity().let { (id, source) -> "$source:$id" } }) { track ->
                SpeedDialTile(
                    track = track,
                    onClick = { onPlayTrack(track) },
                    onLongClick = { onLongPressTrack(track) },
                    // Slow, eased glide when a played song moves to the front.
                    modifier = Modifier.animateItem(
                        fadeInSpec = androidx.compose.animation.core.tween(SPEED_DIAL_GLIDE_MS),
                        placementSpec = androidx.compose.animation.core.tween(
                            SPEED_DIAL_GLIDE_MS,
                            easing = androidx.compose.animation.core.FastOutSlowInEasing,
                        ),
                        fadeOutSpec = androidx.compose.animation.core.tween(SPEED_DIAL_GLIDE_MS / 2),
                    ),
                )
            }
        }
    }
}

/**
 * Speed dial as up to three swipeable pages of nine (Settings › Speed dial
 * pages): full-width pages of exactly nine, with dots showing the position.
 * Every page keeps three rows.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedDialPager(
    items: List<PlayableItem>,
    onPlayTrack: (PlayableItem) -> Unit,
    onLongPressTrack: (PlayableItem) -> Unit,
    // Settings › Speed dial: peek next page.
    peek: Boolean = true,
    // Settings › Speed dial grid size.
    perPage: Int = com.whiplash.music.data.repository.SPEED_DIAL_PAGE_SIZE,
) {
    // What's drawn. It trails [items] when a song from a later page is played:
    // the pager first slides back to page 1 still showing the old order, and
    // only then takes the new order, so page 1's own grid animates the song
    // into first place (tiles are separate grids per page, so a tile can't
    // glide across pages by itself).
    var shown by remember { mutableStateOf(items) }
    val pages = shown.chunked(perPage)
    val pagerState = androidx.compose.foundation.pager.rememberPagerState { pages.size }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // One slow, eased glide back to page 1, shared by the tap and the reorder
    // (two separate scrolls used to cut each other off, which looked like a jump).
    var slideBack by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun glideToFirstPage() {
        if (pagerState.currentPage == 0 && !pagerState.isScrollInProgress) return
        if (slideBack?.isActive == true) return
        slideBack = scope.launch {
            pagerState.animateScrollToPage(
                0,
                animationSpec = androidx.compose.animation.core.tween(
                    SPEED_DIAL_GLIDE_MS,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing,
                ),
            )
        }
    }
    androidx.compose.runtime.LaunchedEffect(items) {
        val firstChanged = items.firstOrNull()?.speedDialIdentity() != shown.firstOrNull()?.speedDialIdentity()
        if (firstChanged && (pagerState.currentPage != 0 || slideBack?.isActive == true)) {
            glideToFirstPage()
            slideBack?.join() // reorder only once page 1 has fully settled
            kotlinx.coroutines.delay(80)
        }
        shown = items
    }
    // Playing from a later page starts the glide right away; the play is
    // recorded a moment later, which is what reorders the list.
    val playFromPage: (PlayableItem) -> Unit = { track ->
        onPlayTrack(track)
        glideToFirstPage()
    }
    Column(verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm)) {
        // With peek, a sliver of the next page shows at the edge; without it,
        // exactly nine songs fill the width and the dots show there's more.
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(end = if (peek) PAGER_PEEK else 0.dp),
            pageSpacing = GlassTokens.spaceMd,
            // Keyed by position, not by the first song: when a played song moves
            // to the front, page 1 stays the same grid and its tiles glide into
            // place (animateItem) instead of the whole page being rebuilt.
            key = { page -> page },
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) { page ->
            SpeedDialGrid(pages[page], playFromPage, onLongPressTrack, minRows = (perPage + 2) / 3)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Speed dial page ${pagerState.currentPage + 1} of ${pages.size}" },
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(pages.size) { i ->
                val active = i == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = if (active) 16.dp else 6.dp, height = 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (active) WhiplashColors.textPrimary else WhiplashColors.textSecondary.copy(alpha = 0.4f)),
                )
            }
        }
    }
}

/**
 * A full 3x3 skeleton grid shaped exactly like [SpeedDialGrid]'s real
 * tiles (square artwork placeholder + a title-line placeholder beneath
 * each one) — shown for the brief window between Home first composing
 * and Speed dial's Room flow emitting its first real snapshot, rather
 * than leaving the section blank or missing entirely during that window.
 */
/** Quick Picks grid placeholder: [rows] rows of three tiles with two text lines, like QuickPickTile. */
@Composable
private fun QuickPicksSkeletonGrid(rows: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm)) {
        repeat(rows.coerceIn(1, 4)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm)) {
                repeat(3) {
                    Column(Modifier.weight(1f)) {
                        com.whiplash.music.ui.theme.ShimmerBox(Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(WhiplashRadius.medium))
                        com.whiplash.music.ui.theme.ShimmerBox(Modifier.padding(top = GlassTokens.spaceXs).fillMaxWidth(0.8f).height(12.dp))
                        com.whiplash.music.ui.theme.ShimmerBox(Modifier.padding(top = 4.dp).fillMaxWidth(0.5f).height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedDialSkeletonGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm)) {
        repeat(3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm),
            ) {
                repeat(3) {
                    Column(modifier = Modifier.weight(1f)) {
                        com.whiplash.music.ui.theme.ShimmerBox(
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            shape = RoundedCornerShape(WhiplashRadius.medium),
                        )
                        com.whiplash.music.ui.theme.ShimmerBox(
                            modifier = Modifier
                                .padding(top = GlassTokens.spaceXs)
                                .fillMaxWidth(0.7f)
                                .height(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@ExperimentalFoundationApi
@Composable
private fun SpeedDialTile(
    track: PlayableItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(WhiplashRadius.medium))
                .background(WhiplashColors.surfaceElevated)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            if (track.artworkUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(track.artworkUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = track.title,
            style = MaterialTheme.typography.labelMedium,
            color = WhiplashColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = GlassTokens.spaceXs),
        )
    }
}

/**
 * The pull-to-refresh spinner, themed to the app's own palette rather than
 * left on Material's defaults — the stock indicator renders on a light
 * container that reads as a bright blob against this app's dark frosted
 * surfaces.
 *
 * Takes an explicit [modifier] because overriding `PullToRefreshBox`'s
 * `indicator` parameter replaces its default lambda, and that default is
 * what supplies `Modifier.align(Alignment.TopCenter)`. Without passing the
 * alignment back in, the indicator inherits the Box's `TopStart` and draws
 * jammed against the left edge of the screen instead of centred — which is
 * exactly what it did until this was caught on-device.
 *
 * Shared by both the populated and empty Home states so the gesture looks
 * identical either way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeRefreshIndicator(
    state: androidx.compose.material3.pulltorefresh.PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    PullToRefreshDefaults.Indicator(
        state = state,
        isRefreshing = isRefreshing,
        containerColor = WhiplashColors.surfaceElevated,
        color = WhiplashColors.accent,
        modifier = modifier,
    )
}

@Composable
private fun SectionHeader(
    title: String,
    onHistory: (() -> Unit)? = null,
    onPlayAll: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    isRefreshing: Boolean = false,
    onClear: (() -> Unit)? = null,
    listView: Boolean = false,
    onToggleLayout: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = GlassTokens.spaceSm, bottom = GlassTokens.spaceXs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = WhiplashColors.textPrimary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onToggleLayout != null) {
                PlainIconButton(
                    contentDescription = if (listView) "Show $title as grid" else "Show $title as list",
                    onClick = onToggleLayout,
                    size = 48.dp,
                ) {
                    androidx.compose.material3.Icon(
                        if (listView) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                        contentDescription = null,
                        tint = WhiplashColors.textSecondary,
                    )
                }
            }
            if (onHistory != null) {
                PlainIconButton(contentDescription = "See full history", onClick = onHistory, size = 48.dp) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        tint = WhiplashColors.textSecondary,
                    )
                }
            }
            // Placed before refresh so the row reads play-then-reload, and
            // tinted like the other section actions rather than accented —
            // this is a convenience shortcut, not the screen's primary
            // action. Only supplied by a section that actually has items to
            // play (see the Quick Picks call site), so it never renders as a
            // button that would do nothing.
            if (onPlayAll != null) {
                PlainIconButton(contentDescription = "Play all $title", onClick = onPlayAll, size = 48.dp) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = WhiplashColors.textSecondary,
                    )
                }
            }
            if (onRefresh != null) {
                // Swaps the static refresh icon for a real spinner while
                // a refresh is actually in flight, so tapping it gives
                // visible feedback that something is happening rather
                // than looking like a no-op — and disables the button
                // meanwhile so a second tap can't stack another reload
                // on top of the one already running.
                PlainIconButton(
                    contentDescription = if (isRefreshing) "Refreshing $title" else "Refresh $title",
                    onClick = onRefresh,
                    size = 48.dp,
                    enabled = !isRefreshing,
                ) {
                    if (isRefreshing) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = WhiplashColors.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        androidx.compose.material3.Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = WhiplashColors.textSecondary,
                        )
                    }
                }
            }
            if (onClear != null) {
                PlainIconButton(contentDescription = "Clear $title", onClick = onClear, size = 48.dp) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        tint = WhiplashColors.textSecondary,
                    )
                }
            }
        }
    }
}

/** Fetches an album/playlist's tracks and plays them (shuffled if asked). */
private suspend fun playCollection(
    app: WhiplashApplication,
    collection: com.whiplash.music.domain.model.YoutubePlaylistResult,
    shuffle: Boolean,
    onPlayQueue: (List<PlayableItem>, Int) -> Unit,
) {
    ToastController.show("Loading ${collection.title}…")
    val tracks = runCatching { app.youtubeDetailProvider.getPlaylistDetail(collection.url).tracks }.getOrNull()
    if (tracks.isNullOrEmpty()) {
        ToastController.show("Couldn't load ${collection.title}")
        return
    }
    onPlayQueue(if (shuffle) tracks.shuffled() else tracks, 0)
}

/** Quick Picks grid tile: square artwork with title and artist beneath. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickPickTile(
    track: PlayableItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    Column(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onClickLabel = "Play", onLongClickLabel = "More options")
            .semantics(mergeDescendants = true) {},
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(WhiplashRadius.medium))
                .background(WhiplashColors.surfaceElevated),
        ) {
            if (track.artworkUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(track.artworkUri).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (selected) Box(Modifier.matchParentSize().background(WhiplashColors.background.copy(alpha = 0.45f)))
            com.whiplash.music.ui.common.SelectionCheck(visible = selected, modifier = Modifier.align(Alignment.Center), size = 36.dp)
        }
        Text(
            text = track.title,
            style = MaterialTheme.typography.labelMedium,
            color = WhiplashColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = GlassTokens.spaceXs),
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.labelSmall,
            color = WhiplashColors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One row of up to three Quick Picks tiles (empty slots keep the columns aligned). */
@Composable
private fun QuickPicksTileRow(
    tracks: List<PlayableItem>,
    onPlayTrack: (PlayableItem) -> Unit,
    onLongPress: (PlayableItem) -> Unit,
    modifier: Modifier = Modifier,
    isSelected: (PlayableItem) -> Boolean = { false },
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm),
    ) {
        tracks.forEach { track ->
            QuickPickTile(
                track = track,
                onClick = { onPlayTrack(track) },
                onLongClick = { onLongPress(track) },
                modifier = Modifier.weight(1f),
                selected = isSelected(track),
            )
        }
        repeat(3 - tracks.size) { Box(Modifier.weight(1f)) }
    }
}

/**
 * YouTube-Music-style Quick Picks grid: [perPage] songs per page (rows of
 * three), swiped sideways, one full page at a time. Every page is padded to the same number of rows so
 * the pager's height never changes mid-swipe, and a dot row shows where you are.
 */
@Composable
private fun QuickPicksPager(
    tracks: List<PlayableItem>,
    perPage: Int,
    onPlayTrack: (PlayableItem) -> Unit,
    onLongPress: (PlayableItem) -> Unit,
    isSelected: (PlayableItem) -> Boolean = { false },
    // Settings › Quick Picks: peek next page.
    peek: Boolean = true,
) {
    // A short last page (e.g. after removing songs) is dropped when there are
    // other pages, so every page is a full grid. A single page is kept as is.
    val pages = tracks.chunked(perPage).let { p ->
        if (p.size > 1 && p.last().size < perPage) p.dropLast(1) else p
    }
    val rowsPerPage = (perPage + 2) / 3
    val pagerState = androidx.compose.foundation.pager.rememberPagerState { pages.size }
    Column(verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm)) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            // Peek: a sliver of the next page at the edge (only when there is one).
            contentPadding = androidx.compose.foundation.layout.PaddingValues(end = if (peek && pages.size > 1) PAGER_PEEK else 0.dp),
            pageSpacing = GlassTokens.spaceMd,
            key = { pages[it].first().id },
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val rows = pages[page].chunked(3)
            Column(verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm)) {
                for (r in 0 until rowsPerPage) {
                    val row = rows.getOrNull(r)
                    if (row != null) {
                        QuickPicksTileRow(row, onPlayTrack, onLongPress, isSelected = isSelected)
                    } else {
                        // Invisible stand-in with a real tile's height.
                        QuickPicksTileRow(emptyList(), onPlayTrack, onLongPress, Modifier.graphicsLayer { alpha = 0f })
                    }
                }
            }
        }
        if (pages.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Page ${pagerState.currentPage + 1} of ${pages.size}" },
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pages.size) { i ->
                    val active = i == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(width = if (active) 16.dp else 6.dp, height = 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (active) WhiplashColors.textPrimary else WhiplashColors.textSecondary.copy(alpha = 0.4f)),
                    )
                }
            }
        }
    }
}

/** How much of the next page shows at a pager's edge when "peek next page" is on. */
private val PAGER_PEEK = 28.dp

/** Speed dial's glide back to page 1 after playing from a later page (slow on purpose). */
private const val SPEED_DIAL_GLIDE_MS = 700
