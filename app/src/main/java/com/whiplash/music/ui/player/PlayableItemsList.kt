package com.whiplash.music.ui.player

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.ui.draw.clip
import com.whiplash.music.ui.theme.glassShadow
import com.whiplash.music.ui.theme.glassMaterial
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whiplash.music.WhiplashApplication
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.ui.theme.GlassArtworkThumbnail
import com.whiplash.music.ui.theme.GlassIconButton
import com.whiplash.music.ui.theme.GlassListItem
import com.whiplash.music.ui.theme.GlassSheet
import com.whiplash.music.ui.theme.GlassTextInputDialog
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.PlainIconButton
import com.whiplash.music.ui.theme.WhiplashColors

/**
 * Identifies the playlist a [PlayableItemsList] instance is showing the
 * tracks *of* — passed only from [com.whiplash.music.ui.playlists.PlaylistDetailScreen],
 * null everywhere else (Search/Home/Local Library/Favorites), where a
 * track's membership in a specific playlist isn't a meaningful concept
 * for the list to expose. Drives the sheet's "Remove from playlist"
 * (replacing the generic "Add to playlist") and "Move to other playlist"
 * rows — see [SongActionsContent]'s own doc on why those are mutually
 * exclusive with the generic add-to-playlist flow.
 */
data class PlaylistContext(val playlistId: Long, val playlistName: String)

/**
 * Reusable track list used across Search, Local Library, Home, and
 * Favorites: tap to play the whole visible list as a queue starting at
 * that index (section 21), long-press for the song-actions sheet (play
 * next / add to queue / favorite, section 51) with haptic feedback
 * (section 57 — subtle, on the meaningful long-press action only, not on
 * every tap).
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun PlayableItemsList(
    items: List<PlayableItem>,
    onPlayQueue: (queue: List<PlayableItem>, startIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(bottom = GlassTokens.miniPlayerReservedHeight),
    header: (@Composable () -> Unit)? = null,
    // Like [header], but also handed a way to open this list's song
    // actions sheet for any item (Search's top result card uses it for
    // its own long-press and ⋮ menu). Used instead of [header] when set.
    headerWithActions: (@Composable (openActions: (PlayableItem) -> Unit) -> Unit)? = null,
    // Songs drawn inside the header (Search's top result) that take part in
    // multi-select like the rows below. The header reads [LocalListSelection]
    // to show their selected state and route taps.
    headerItems: List<PlayableItem> = emptyList(),
    // Drawn after the last song, scrolling with the list (an artist page's
    // Albums shelf, for example).
    footer: (@Composable () -> Unit)? = null,
    // Optional real infinite-scroll hook (section: search pagination) —
    // both default to null/false so every existing caller (Local
    // Library, Home, Favorites) behaves exactly as before with zero
    // changes; only Search's Songs tab currently passes these.
    onLoadMore: (() -> Unit)? = null,
    isLoadingMore: Boolean = false,
    // Optional per-item "Remove from history" action (History screen
    // only) — null everywhere else (Search/Local Library/Favorites),
    // where a track has no history-specific removal concept.
    onRemoveFromHistory: ((PlayableItem) -> Unit)? = null,
    // See [PlaylistContext]'s own doc — null everywhere except
    // PlaylistDetailScreen.
    playlistContext: PlaylistContext? = null,
) {

    val context = LocalContext.current
    val app = context.applicationContext as WhiplashApplication
    val haptic = LocalHapticFeedback.current
    val songActionsViewModel: SongActionsViewModel = viewModel(
        factory = SongActionsViewModelFactory(app.libraryRepository, app.downloadManager),
    )
    var actionsSheetItem by remember { mutableStateOf<PlayableItem?>(null) }
    var addToPlaylistItem by remember { mutableStateOf<PlayableItem?>(null) }
    var moveToPlaylistItem by remember { mutableStateOf<PlayableItem?>(null) }
    var copyToPlaylistItem by remember { mutableStateOf<PlayableItem?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // ---- Multi-select ----------------------------------------------------
    // Long-press a song to start selecting; tap then toggles. Keys are
    // "SOURCE:id", so a song listed twice is selected as one.
    var selectedKeys by androidx.compose.runtime.saveable.rememberSaveable(
        stateSaver = androidx.compose.runtime.saveable.listSaver<Set<String>, String>(
            save = { it.toList() },
            restore = { it.toSet() },
        ),
    ) { mutableStateOf(emptySet()) }
    val selecting = selectedKeys.isNotEmpty()
    val selectable = if (headerItems.isEmpty()) items else headerItems + items
    fun keyOf(item: PlayableItem) = "${item.source}:${item.id}"
    fun toggle(item: PlayableItem) {
        val k = keyOf(item)
        selectedKeys = if (k in selectedKeys) selectedKeys - k else selectedKeys + k
    }
    // Songs removed by an action (or a list refresh) drop out of the selection.
    // Skipped while the list is empty: after rotation or a reload it's briefly
    // empty before the songs come back, and that must not wipe the selection.
    LaunchedEffect(selectable) {
        if (selectedKeys.isNotEmpty() && selectable.isNotEmpty()) {
            val present = selectable.mapTo(HashSet()) { keyOf(it) }
            selectedKeys = selectedKeys.filterTo(HashSet()) { it in present }
        }
    }
    androidx.activity.compose.BackHandler(enabled = selecting) { selectedKeys = emptySet() }
    var bulkPlaylistItems by remember { mutableStateOf<List<PlayableItem>?>(null) }
    var showBulkCreatePlaylist by remember { mutableStateOf(false) }
    var pendingBulk by remember { mutableStateOf<PendingBulkAction?>(null) }

    // Single shared subscription for the small offline-downloaded
    // checkmark badge (YouTube-Music-style) — hoisted here rather than
    // one Flow collection per row, which would otherwise scale with list
    // length for no benefit (every row needs the exact same set).
    val downloadedIds by app.libraryRepository.observeDownloadedIds().collectAsState(initial = emptySet())

    var cancelDownloadTarget by remember { mutableStateOf<PlayableItem?>(null) }
    var removeDownloadTarget by remember { mutableStateOf<PlayableItem?>(null) }

    // Real scroll-triggered "load more" detection — the standard, correct
    // Compose pattern (snapshotFlow over LazyListState.layoutInfo, not a
    // polling hack or a fake fixed-delay timer): fires onLoadMore() once
    // the last *visible* item index comes within a small threshold of the
    // last *loaded* item index, so the next page has a chance to arrive
    // slightly before the user actually scrolls past the end — this is
    // what makes it feel smooth/seamless rather than showing a visible
    // "hit the wall, wait, then more appears" stutter. Guarded by
    // onLoadMore != null so callers that don't opt into pagination (every
    // existing caller) never even install this effect.
    if (onLoadMore != null) {
        // Keyed on listState alone — NOT on items.size. Restarting this
        // LaunchedEffect every time a new page was appended (the
        // previous version keyed on items.size too) tore down and
        // recreated the snapshotFlow collector at exactly the moment new
        // items were being inserted — the single highest-risk instant
        // for a stutter during a fast fling, and part of a reported bug:
        // scrolling fast made the list appear to freeze at the last
        // loaded item even after more items had actually finished
        // loading underneath.
        LaunchedEffect(listState, isLoadingMore, items.size) {
            // The snapshotFlow block itself reads lastVisibleIndex,
            // isLoadingMore, AND items.size — not just lastVisibleIndex —
            // so it re-emits whenever any of the three changes, not only
            // on a fresh scroll delta. This closes the other half of the
            // same reported bug: if the last visible item's index happens
            // to stay the same while a fast fling is settled right at the
            // threshold (a common outcome of a fling that overshoots and
            // rests exactly there), the previous version — which only
            // read lastVisibleIndex inside the tracked block — would
            // never re-check once isLoadingMore cleared, since nothing
            // it was actually tracking as a snapshot read had changed.
            // The list only "unstuck" on the next slow scroll because
            // that produced a genuinely new lastVisibleIndex value. Now a
            // page finishing loading (isLoadingMore flipping true->false)
            // or new items landing (items.size changing) is itself enough
            // to re-run the check and fire onLoadMore() again if still
            // within range, with no scroll gesture required in between.
            snapshotFlow {
                Triple(listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index, isLoadingMore, items.size)
            }.collect { (lastVisibleIndex, currentlyLoadingMore, _) ->
                    if (lastVisibleIndex == null) return@collect
                    // Account for the optional header occupying index 0
                    // and the loading-footer item at the very end — both
                    // are real LazyColumn items but not part of [items],
                    // so the threshold check below is against [items]'
                    // own last index, offset by whether a header exists.
                    val headerOffset = if (header != null || headerWithActions != null) 1 else 0
                    val lastItemIndex = headerOffset + items.lastIndex
                    if (!currentlyLoadingMore && lastVisibleIndex >= lastItemIndex - LOAD_MORE_THRESHOLD) {
                        onLoadMore()
                    }
                }
        }
    }

    Box(modifier = modifier) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceXs),
        contentPadding = contentPadding,
    ) {
        // Rendered as the first lazy item (not a separate non-scrolling
        // Column above this LazyColumn) so screens like Album/Artist
        // detail — where the header includes large artwork — scroll as
        // one continuous list. Splitting the header into its own
        // non-scrolling container was the real cause of a reported bug:
        // tall artwork could push the header+track list below the
        // viewport with no way to scroll back up past it, since only the
        // inner list (not the header above it) was ever scrollable.
        if (headerWithActions != null) {
            item(key = "__header__") {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalListSelection provides ListSelectionAccess(
                        selecting = selecting,
                        isSelected = { keyOf(it) in selectedKeys },
                        toggle = { toggle(it) },
                    ),
                ) { headerWithActions { actionsSheetItem = it } }
            }
        } else if (header != null) {
            item(key = "__header__") { header() }
        }
        // Key includes the row index, not just source+id: an album,
        // playlist or artist page's track list comes straight from YouTube
        // extraction with no dedup, and can legitimately contain the same
        // video id twice (a track appearing on a compilation twice, a song
        // added to a playlist twice). A bare "source:id" key would then be
        // duplicated and crash LazyColumn ("key was already used"), taking
        // the whole screen down. Prefixing the index makes every key unique
        // (matching QueueContent/LyricsContent's own convention here).
        itemsIndexed(items, key = { index, item -> "$index:${item.source}:${item.id}" }) { index, item ->
            val isSelected = selecting && keyOf(item) in selectedKeys
            GlassListItem(
                title = item.title,
                subtitle = item.artist,
                modifier = Modifier
                    .then(if (isSelected) selectedRowModifier() else Modifier)
                    .semantics { if (selecting) stateDescription = if (isSelected) "Selected" else "Not selected" },
                onClick = { if (selecting) toggle(item) else onPlayQueue(items, index) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    // Selecting: long-press toggles like a tap. Otherwise it opens
                    // the song menu, whose first row is "Select".
                    if (selecting) toggle(item) else actionsSheetItem = item
                },
                leading = {
                    Box(contentAlignment = Alignment.Center) {
                        GlassArtworkThumbnail(artworkUri = item.artworkUri)
                        // Selected: the artwork turns into a round checkmark.
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isSelected,
                            enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut(),
                        ) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .background(WhiplashColors.background.copy(alpha = 0.55f), androidx.compose.foundation.shape.RoundedCornerShape(com.whiplash.music.ui.theme.WhiplashRadius.small)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier.size(30.dp).background(WhiplashColors.accent, androidx.compose.foundation.shape.CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = WhiplashColors.onAccent, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                },
                trailing = {
                    // Offline-download status badge (YouTube-Music-style):
                    // while downloading, an animated determinate progress
                    // ring in place of the checkmark — tapping it opens a
                    // Cancel/Keep-downloading confirmation (see
                    // cancelDownloadTarget below) rather than immediately
                    // canceling on a single accidental tap. On completion,
                    // AnimatedContent crossfades the ring into the
                    // checkmark rather than an abrupt swap.
                    // In-flight progress, read per row (see rememberDownloadProgress).
                    val inFlightProgress by com.whiplash.music.ui.common.rememberDownloadProgress(
                        (item as? PlayableItem.YoutubeTrack)?.id,
                    )
                    // Real, reported bug: this used to trust
                    // `item is PlayableItem.DownloadedTrack` outright as
                    // proof of being downloaded — but a playlist's track
                    // list is resolved from its own persisted
                    // (trackId, source) rows (see
                    // LibraryRepository.observePlaylistTracks), which only
                    // re-resolves when the playlist's own track list
                    // changes, not when an unrelated removeDownload() call
                    // deletes the download itself. That left a track
                    // removed from Downloads still rendering its
                    // "Downloaded" checkmark inside any playlist it was
                    // added to until the playlist's own track list
                    // happened to change for some other reason. Checking
                    // the live downloadedIds set (already reactive to
                    // exactly this) instead of the item's static type
                    // fixes it for every screen at once.
                    val downloaded = item.id in downloadedIds
                    androidx.compose.animation.AnimatedContent(
                        targetState = when {
                            inFlightProgress?.failed == true -> "failed"
                            inFlightProgress != null -> "downloading"
                            downloaded -> "downloaded"
                            else -> "none"
                        },
                        label = "downloadStatusBadge",
                    ) { state ->
                        when (state) {
                            "downloading" -> Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(end = GlassTokens.spaceXs)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        role = Role.Button,
                                        onClick = { cancelDownloadTarget = item },
                                    )
                                    .semantics { contentDescription = "Cancel download of ${item.title}" },
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    progress = { inFlightProgress?.fraction ?: 0f },
                                    color = WhiplashColors.accent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            // A download that failed after its automatic
                            // retry (see DownloadManager.runDownload) —
                            // shown distinctly rather than silently
                            // vanishing, which was the real reported bug
                            // ("getting invisible from downloading
                            // options, there shows no downloads").
                            "failed" -> Icon(
                                Icons.Filled.ErrorOutline,
                                contentDescription = "Download failed for ${item.title}",
                                tint = WhiplashColors.error,
                                modifier = Modifier.size(18.dp).padding(end = GlassTokens.spaceXs),
                            )
                            "downloaded" -> Icon(
                                Icons.Filled.DownloadDone,
                                contentDescription = "Downloaded",
                                tint = WhiplashColors.accent,
                                modifier = Modifier.size(18.dp).padding(end = GlassTokens.spaceXs),
                            )
                            else -> androidx.compose.foundation.layout.Spacer(Modifier.size(0.dp))
                        }
                    }
                    if (selecting) {
                        // Same width as ⋮ so titles don't jump while selecting.
                        androidx.compose.foundation.layout.Spacer(Modifier.size(48.dp))
                    } else {
                        PlainIconButton(
                            contentDescription = "More options for ${item.title}",
                            onClick = { actionsSheetItem = item },
                            size = 48.dp,
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null, tint = WhiplashColors.textSecondary)
                        }
                    }
                },
            )
        }
        if (footer != null) {
            item(key = "__footer__") { footer() }
        }
        if (isLoadingMore) {
            item(key = "__load_more_footer__") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(GlassTokens.spaceMd),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = WhiplashColors.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }

    } // Box

    // Publish the selection to the app chrome (top bar + action bar in the tab bar's place).
    val selectionOwner = remember { Any() }
    val saveToDevice = com.whiplash.music.ui.common.rememberSaveToDevice()
    var showMoreSheet by remember { mutableStateOf(false) }
    val favoriteKeys by app.libraryRepository.observeFavoriteKeys().collectAsState(initial = emptySet())
    val selected = selectable.filter { keyOf(it) in selectedKeys }.distinctBy { keyOf(it) }
    val done = { selectedKeys = emptySet(); showMoreSheet = false }
    val allActions = buildSelectionActions(
        selected = selected,
        downloadedIds = downloadedIds,
        favoriteKeys = favoriteKeys,
        playlistContext = playlistContext,
        inHistory = onRemoveFromHistory != null,
        onPlay = { app.playbackController.playQueue(selected, 0); done() },
        onShuffle = { app.playbackController.playQueue(selected.shuffled(), 0); done() },
        onPlayNext = { app.playbackController.playAllNext(selected); done() },
        onAddToQueue = { app.playbackController.addAllToQueue(selected); done() },
        onAddToPlaylist = { showMoreSheet = false; bulkPlaylistItems = selected },
        onFavorite = { songs -> songActionsViewModel.addAllToFavorites(songs); done() },
        onUnfavorite = { songs -> showMoreSheet = false; pendingBulk = PendingBulkAction.Unfavorite(songs) },
        onDownload = { tracks -> app.downloadManager.downloadAll(tracks); done() },
        onRemoveFromPlaylist = { showMoreSheet = false; pendingBulk = PendingBulkAction.RemoveFromPlaylist(selected) },
        onRemoveFromHistory = { showMoreSheet = false; pendingBulk = PendingBulkAction.RemoveFromHistory(selected) },
        onRemoveDownloads = { ids -> showMoreSheet = false; pendingBulk = PendingBulkAction.RemoveDownloads(ids) },
        onSaveToDevice = { ids -> saveToDevice(ids); done() },
    )
    // Bottom bar: Play next, Add to playlist, and Like/Unlike (or Add to queue); the rest go in More.
    val primaryLabels = listOf("Play next", "Add to playlist")
    val third = allActions.firstOrNull { it.label == "Like" || it.label == "Unlike" }
        ?: allActions.firstOrNull { it.label == "Add to queue" }
    // Empty when nothing is selected (buildSelectionActions returns no actions).
    val primary = allActions.filter { it.label in primaryLabels } + listOfNotNull(third)
    val more = allActions.filter { it !in primary }
    if (selecting && selected.isNotEmpty()) {
        val totalCount = selectable.distinctBy { keyOf(it) }.size
        androidx.compose.runtime.SideEffect {
            com.whiplash.music.ui.common.SelectionController.publish(
                com.whiplash.music.ui.common.SelectionSession(
                    owner = selectionOwner,
                    selectedCount = selected.size,
                    totalCount = totalCount,
                    primary = primary,
                    onMore = { showMoreSheet = true },
                    onClose = { done() },
                    onSelectAll = { selectedKeys = selectable.mapTo(HashSet()) { keyOf(it) } },
                    onDeselectAll = { done() },
                ),
            )
        }
    } else {
        androidx.compose.runtime.SideEffect { com.whiplash.music.ui.common.SelectionController.clear(selectionOwner) }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { com.whiplash.music.ui.common.SelectionController.clear(selectionOwner) }
    }
    if (showMoreSheet && selecting) {
        GlassSheet(onDismissRequest = { showMoreSheet = false }) {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text(
                    text = if (selected.size == 1) "1 song selected" else "${selected.size} songs selected",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = WhiplashColors.textPrimary,
                    modifier = Modifier.padding(vertical = GlassTokens.spaceSm),
                )
                more.forEach { com.whiplash.music.ui.common.SelectionSheetRow(it) }
            }
        }
    }

    val sheetItem = actionsSheetItem
    if (sheetItem != null) {
        val isFavorite by app.libraryRepository.observeIsFavorite(sheetItem).collectAsState(initial = false)
        val isPinned by app.libraryRepository.observeIsPinned(sheetItem).collectAsState(initial = false)
        // The Downloads tab's action sheet is intentionally a smaller set
        // (Play next / Add to queue / Save to playlist / Remove download /
        // Share) — Favorite/Pin/Start radio don't apply to a track being
        // managed as a saved-offline-file rather than browsed online.
        val isDownloadedTrack = sheetItem is PlayableItem.DownloadedTrack
        GlassSheet(onDismissRequest = { actionsSheetItem = null }) {
            SongActionsContent(
                item = sheetItem,
                // Only for songs in this list (not e.g. Search's top-result card).
                onSelect = if (selectable.any { keyOf(it) == keyOf(sheetItem) }) {
                    {
                        selectedKeys = setOf(keyOf(sheetItem))
                        actionsSheetItem = null
                    }
                } else null,
                isFavorite = isFavorite,
                onPlayNext = {
                    app.playbackController.playNext(sheetItem)
                    actionsSheetItem = null
                },
                onAddToQueue = {
                    app.playbackController.addToQueue(sheetItem)
                    actionsSheetItem = null
                },
                onToggleFavorite = if (!isDownloadedTrack) {
                    {
                        songActionsViewModel.toggleFavorite(sheetItem, isCurrentlyFavorite = isFavorite)
                        actionsSheetItem = null
                    }
                } else null,
                onAddToPlaylist = {
                    addToPlaylistItem = sheetItem
                    actionsSheetItem = null
                },
                onRemoveFromPlaylist = if (playlistContext != null) {
                    {
                        songActionsViewModel.removeFromPlaylist(playlistContext.playlistId, playlistContext.playlistName, sheetItem)
                        actionsSheetItem = null
                    }
                } else null,
                onMoveToOtherPlaylist = if (playlistContext != null) {
                    {
                        moveToPlaylistItem = sheetItem
                        actionsSheetItem = null
                    }
                } else null,
                onCopyToOtherPlaylist = if (playlistContext != null) {
                    {
                        copyToPlaylistItem = sheetItem
                        actionsSheetItem = null
                    }
                } else null,
                onStartRadio = if (sheetItem is PlayableItem.YoutubeTrack) {
                    {
                        // "Start radio" plays just this track — the existing
                        // autoplay/recommendation system (already verified
                        // working: it extends the queue with real related
                        // tracks once this becomes the last queue item) is
                        // what actually builds the radio-style queue, so no
                        // separate mechanism is needed here.
                        app.playbackController.playNow(sheetItem)
                        actionsSheetItem = null
                    }
                } else null,
                onShare = if (sheetItem is PlayableItem.YoutubeTrack || sheetItem is PlayableItem.DownloadedTrack) {
                    {
                        shareYoutubeTrack(context, sheetItem)
                        actionsSheetItem = null
                    }
                } else null,
                isPinned = isPinned,
                // Real, reported design gap: Speed dial is meant to be an
                // *online* listening history — a completely separate,
                // additional feature from the local on-device library
                // (its own Library tab). Offering "Pin to Speed dial" for
                // a LocalTrack let a user pin something that then never
                // actually appeared anywhere (HomeViewModel.speedDial's
                // own pinned+recentlyPlayed composition, and
                // HistoryDao.observeRecentlyPlayed, both now exclude
                // LOCAL) — a dead, silently-no-op action rather than a
                // real capability.
                onTogglePinned = if (!isDownloadedTrack && sheetItem !is PlayableItem.LocalTrack) {
                    {
                        songActionsViewModel.togglePinned(sheetItem, isCurrentlyPinned = isPinned)
                        actionsSheetItem = null
                    }
                } else null,
                onRemoveFromHistory = if (onRemoveFromHistory != null) {
                    {
                        onRemoveFromHistory(sheetItem)
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
                        // Real, reported UX gap (UAT audit finding):
                        // every other download-destructive action
                        // (cancel an in-flight download, "Clear all
                        // downloads") confirms first — removing a single
                        // completed download deleted the file instantly
                        // with no confirmation at all. removeDownloadTarget
                        // below now routes this through the same
                        // GlassConfirmDialog pattern as cancelDownloadTarget.
                        removeDownloadTarget = sheetItem
                        actionsSheetItem = null
                    }
                } else null,
            )
        }
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
        GlassTextInputDialog(
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

    // "Move to other playlist" target picker — same AddToPlaylistContent
    // sheet reused for the add flow above, minus the playlist currently
    // being viewed (moving a song to the playlist it's already in is a
    // no-op the picker shouldn't even offer) and with "New playlist"
    // omitted (moving into a brand new empty playlist is exactly the same
    // outcome as just adding it there and removing it from here, which
    // "New playlist" from the add flow already covers if that's really
    // what's wanted — this picker is specifically for moving between
    // *existing* playlists).
    val moveTargetItem = moveToPlaylistItem
    if (moveTargetItem != null && playlistContext != null) {
        val allPlaylists by app.libraryRepository.observePlaylists().collectAsState(initial = emptyList())
        val otherPlaylists = allPlaylists.filter { it.id != playlistContext.playlistId }
        GlassSheet(onDismissRequest = { moveToPlaylistItem = null }) {
            com.whiplash.music.ui.player.AddToPlaylistContent(
                playlists = otherPlaylists,
                title = "Move to playlist",
                showCreateNew = false,
                onSelectPlaylist = { targetPlaylist ->
                    songActionsViewModel.moveToPlaylist(
                        fromPlaylistId = playlistContext.playlistId,
                        toPlaylistId = targetPlaylist.id,
                        toPlaylistName = targetPlaylist.name,
                        item = moveTargetItem,
                    )
                    moveToPlaylistItem = null
                },
                onCreateNew = {},
            )
        }
    }

    // "Copy to other playlist" target picker — same picker/exclusion
    // rule as the move flow above (the playlist currently being viewed
    // is filtered out: copying a song to the playlist it's already in
    // is a no-op the picker shouldn't offer), but unlike move, the
    // source playlist is left completely untouched — see
    // SongActionsViewModel.copyToPlaylist's own doc for why this can
    // never create a duplicate row no matter how many times it's used.
    val copyTargetItem = copyToPlaylistItem
    if (copyTargetItem != null && playlistContext != null) {
        val allPlaylistsForCopy by app.libraryRepository.observePlaylists().collectAsState(initial = emptyList())
        val otherPlaylistsForCopy = allPlaylistsForCopy.filter { it.id != playlistContext.playlistId }
        GlassSheet(onDismissRequest = { copyToPlaylistItem = null }) {
            com.whiplash.music.ui.player.AddToPlaylistContent(
                playlists = otherPlaylistsForCopy,
                title = "Copy to playlist",
                showCreateNew = false,
                onSelectPlaylist = { targetPlaylist ->
                    songActionsViewModel.copyToPlaylist(
                        toPlaylistId = targetPlaylist.id,
                        toPlaylistName = targetPlaylist.name,
                        item = copyTargetItem,
                    )
                    copyToPlaylistItem = null
                },
                onCreateNew = {},
            )
        }
    }

    val cancelTarget = cancelDownloadTarget
    if (cancelTarget != null) {
        // Tapping the in-progress ring opens this rather than canceling
        // immediately — "Keep downloading" just dismisses (the download
        // was never actually touched), "Cancel download" calls the real
        // cancel path which deletes the partial file instantly.
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

    // ---- Multi-select sheets and confirmations ----------------------------

    val bulkItems = bulkPlaylistItems
    if (bulkItems != null) {
        val playlists by app.libraryRepository.observePlaylists().collectAsState(initial = emptyList())
        // Adding to the playlist being viewed would be a no-op, so it isn't offered.
        val targets = playlists.filter { it.id != playlistContext?.playlistId }
        GlassSheet(onDismissRequest = { bulkPlaylistItems = null }) {
            com.whiplash.music.ui.player.AddToPlaylistContent(
                playlists = targets,
                title = if (bulkItems.size == 1) "Add 1 song to playlist" else "Add ${bulkItems.size} songs to playlist",
                onSelectPlaylist = { playlist ->
                    songActionsViewModel.addAllToPlaylist(bulkItems, playlist.id, playlist.name)
                    bulkPlaylistItems = null
                    selectedKeys = emptySet()
                },
                onCreateNew = { showBulkCreatePlaylist = true },
            )
        }
    }
    if (showBulkCreatePlaylist) {
        GlassTextInputDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onConfirm = { name ->
                bulkPlaylistItems?.let { songActionsViewModel.createPlaylistAndAddAll(name, it) }
                showBulkCreatePlaylist = false
                bulkPlaylistItems = null
                selectedKeys = emptySet()
            },
            onDismiss = { showBulkCreatePlaylist = false },
        )
    }

    val bulk = pendingBulk
    if (bulk != null) {
        val n = bulk.count
        val what = if (n == 1) "1 song" else "$n songs"
        val (title, message) = when (bulk) {
            is PendingBulkAction.Unfavorite -> "Remove $what from favorites?" to "You can like them again any time."
            is PendingBulkAction.RemoveFromPlaylist ->
                "Remove $what from ${playlistContext?.playlistName ?: "playlist"}?" to "The songs stay in your library."
            is PendingBulkAction.RemoveFromHistory -> "Remove $what from history?" to "They'll also leave Speed dial and Replay."
            is PendingBulkAction.RemoveDownloads ->
                (if (n == 1) "Remove 1 download?" else "Remove $n downloads?") to "The files are deleted from this device."
        }
        com.whiplash.music.ui.theme.GlassConfirmDialog(
            title = title,
            message = message,
            confirmLabel = "Remove",
            onConfirm = {
                when (bulk) {
                    is PendingBulkAction.Unfavorite -> songActionsViewModel.removeAllFromFavorites(bulk.items)
                    is PendingBulkAction.RemoveFromPlaylist -> playlistContext?.let {
                        songActionsViewModel.removeAllFromPlaylist(it.playlistId, it.playlistName, bulk.items)
                    }
                    is PendingBulkAction.RemoveFromHistory -> songActionsViewModel.removeAllFromHistory(bulk.items)
                    is PendingBulkAction.RemoveDownloads -> songActionsViewModel.removeDownloads(bulk.ids)
                }
                pendingBulk = null
                selectedKeys = emptySet()
            },
            onDismiss = { pendingBulk = null },
        )
    }
}

/**
 * Highlight for a selected row. Liquid Glass gets the same raised 3D glass
 * puck as the open tab in the tab bar (rim, bevel, gloss, soft lift); the
 * other themes a soft accent tint.
 */
@Composable
private fun selectedRowModifier(): Modifier {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(com.whiplash.music.ui.theme.WhiplashRadius.medium)
    if (!WhiplashColors.isGlass) {
        return Modifier.background(WhiplashColors.accent.copy(alpha = 0.14f), shape)
    }
    // Same recipe as FadeBottomBar's tab puck.
    return Modifier
        .glassShadow(shape, elevation = 6.dp, strength = if (WhiplashColors.isLight) 0.16f else 0.28f)
        .clip(shape)
        .background(
            if (WhiplashColors.isLight) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.55f)
            else WhiplashColors.textPrimary.copy(alpha = 0.14f),
        )
        .glassMaterial(shape)
}

/** Lets a list header (Search's top result) join the list's multi-select. */
class ListSelectionAccess(
    val selecting: Boolean,
    val isSelected: (PlayableItem) -> Boolean,
    val toggle: (PlayableItem) -> Unit,
)

val LocalListSelection = androidx.compose.runtime.compositionLocalOf<ListSelectionAccess?> { null }

/** A multi-select action that needs confirming first. */
private sealed interface PendingBulkAction {
    val count: Int
    data class Unfavorite(val items: List<PlayableItem>) : PendingBulkAction { override val count get() = items.size }
    data class RemoveFromPlaylist(val items: List<PlayableItem>) : PendingBulkAction { override val count get() = items.size }
    data class RemoveFromHistory(val items: List<PlayableItem>) : PendingBulkAction { override val count get() = items.size }
    data class RemoveDownloads(val ids: List<String>) : PendingBulkAction { override val count get() = ids.size }
}



/**
 * The selection bar's actions for [selected], matching what the single-song
 * menu allows: play/queue/playlist everywhere, like/unlike only for songs
 * that can be liked, Download for streamable songs not yet saved, and the
 * removal that fits the screen (playlist, history, downloads).
 */
internal fun buildSelectionActions(
    selected: List<PlayableItem>,
    downloadedIds: Set<String>,
    favoriteKeys: Set<String>,
    playlistContext: PlaylistContext?,
    inHistory: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onFavorite: (List<PlayableItem>) -> Unit,
    onUnfavorite: (List<PlayableItem>) -> Unit,
    onDownload: (List<PlayableItem.YoutubeTrack>) -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onRemoveFromHistory: () -> Unit,
    onRemoveDownloads: (List<String>) -> Unit,
    onSaveToDevice: (List<String>) -> Unit,
): List<com.whiplash.music.ui.common.SelectionAction> {
    if (selected.isEmpty()) return emptyList()
    val actions = mutableListOf(
        com.whiplash.music.ui.common.SelectionAction("Play", Icons.Filled.PlayArrow, onClick = onPlay),
        com.whiplash.music.ui.common.SelectionAction("Shuffle play", Icons.Filled.Shuffle, onClick = onShuffle),
        com.whiplash.music.ui.common.SelectionAction("Play next", Icons.AutoMirrored.Filled.PlaylistPlay, onClick = onPlayNext),
        com.whiplash.music.ui.common.SelectionAction("Add to queue", Icons.AutoMirrored.Filled.QueueMusic, onClick = onAddToQueue),
        com.whiplash.music.ui.common.SelectionAction("Add to playlist", Icons.AutoMirrored.Filled.PlaylistAdd, onClick = onAddToPlaylist),
    )
    // Downloaded copies can't be liked (same rule as the song menu).
    val likeable = selected.filter { it !is PlayableItem.DownloadedTrack }
    if (likeable.isNotEmpty()) {
        val allLiked = likeable.all { com.whiplash.music.data.repository.favoriteKey(it.id, it.source) in favoriteKeys }
        actions += if (allLiked) {
            com.whiplash.music.ui.common.SelectionAction("Unlike", Icons.Filled.Favorite, onClick = { onUnfavorite(likeable) })
        } else {
            com.whiplash.music.ui.common.SelectionAction("Like", Icons.Filled.FavoriteBorder, onClick = { onFavorite(likeable) })
        }
    }
    val toDownload = selected.filterIsInstance<PlayableItem.YoutubeTrack>().filter { it.id !in downloadedIds }
    if (toDownload.isNotEmpty()) {
        actions += com.whiplash.music.ui.common.SelectionAction("Download", Icons.Filled.Download, onClick = { onDownload(toDownload) })
    }
    if (playlistContext != null) {
        actions += com.whiplash.music.ui.common.SelectionAction(
            "Remove", Icons.Filled.RemoveCircleOutline, destructive = true, onClick = onRemoveFromPlaylist,
        )
    }
    if (inHistory) {
        actions += com.whiplash.music.ui.common.SelectionAction(
            "Remove", Icons.Filled.RemoveCircleOutline, destructive = true, onClick = onRemoveFromHistory,
        )
    }
    val downloaded = selected.filter { it is PlayableItem.DownloadedTrack || it.id in downloadedIds }.map { it.id }.distinct()
    if (downloaded.isNotEmpty()) {
        // Copies the finished downloads out to Download/Whiplash (same as the song menu).
        actions += com.whiplash.music.ui.common.SelectionAction(
            "Save to device", Icons.Filled.SaveAlt, onClick = { onSaveToDevice(downloaded) },
        )
        actions += com.whiplash.music.ui.common.SelectionAction(
            "Delete download", Icons.Filled.Delete, destructive = true, onClick = { onRemoveDownloads(downloaded) },
        )
    }
    return actions
}

/**
 * How many items from the end of the list to start loading the next page
 * — high enough that the next page has a realistic chance of arriving
 * before the user actually scrolls to the current last item (avoiding a
 * visible "wait at the bottom" stall), low enough that it doesn't fire
 * a network request for a page the user may never scroll far enough to
 * see. 5 items is roughly one screen's worth of the typical list-item
 * height on this app's layout, matching the lookahead distance common
 * mainstream apps (YouTube Music, Spotify) visibly use.
 */
private const val LOAD_MORE_THRESHOLD = 5

/**
 * Shares a real, working YouTube watch URL for [track] via Android's
 * native share sheet — offered for [PlayableItem.YoutubeTrack] and
 * [PlayableItem.DownloadedTrack] (a downloaded track's [PlayableItem.id]
 * is the same YouTube video id it was downloaded from) since both have a
 * meaningful external link to share (section 73: don't add a fake
 * action) — a [PlayableItem.LocalTrack] does not, and is excluded.
 */
fun shareYoutubeTrack(context: android.content.Context, track: PlayableItem) {
    val url = "https://youtube.com/watch?v=${track.id}"
    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, "${track.title} — $url")
    }
    context.startActivity(android.content.Intent.createChooser(sendIntent, "Share song"))
}
