package com.whiplash.music.ui.player

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
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

    // Single shared subscription for the small offline-downloaded
    // checkmark badge (YouTube-Music-style) — hoisted here rather than
    // one Flow collection per row, which would otherwise scale with list
    // length for no benefit (every row needs the exact same set).
    val downloadedIds by app.libraryRepository.observeDownloadedIds().collectAsState(initial = emptySet())

    // In-flight download progress (section: Downloads tab redesign) —
    // drives the animated progress-ring badge for any YoutubeTrack row
    // currently downloading, in any list (Search, Home, Downloads tab
    // itself, etc), not just the Downloads tab.
    val downloadProgress by app.downloadManager.progress.collectAsState()
    var cancelDownloadTarget by remember { mutableStateOf<PlayableItem?>(null) }

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
        LaunchedEffect(listState) {
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
                    val headerOffset = if (header != null) 1 else 0
                    val lastItemIndex = headerOffset + items.lastIndex
                    if (!currentlyLoadingMore && lastVisibleIndex >= lastItemIndex - LOAD_MORE_THRESHOLD) {
                        onLoadMore()
                    }
                }
        }
    }

    LazyColumn(
        modifier = modifier,
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
        if (header != null) {
            item(key = "__header__") { header() }
        }
        itemsIndexed(items, key = { _, item -> "${item.source}:${item.id}" }) { index, item ->
            GlassListItem(
                title = item.title,
                subtitle = item.artist,
                onClick = { onPlayQueue(items, index) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    actionsSheetItem = item
                },
                leading = { GlassArtworkThumbnail(artworkUri = item.artworkUri) },
                trailing = {
                    // Offline-download status badge (YouTube-Music-style):
                    // while downloading, an animated determinate progress
                    // ring in place of the checkmark — tapping it opens a
                    // Cancel/Keep-downloading confirmation (see
                    // cancelDownloadTarget below) rather than immediately
                    // canceling on a single accidental tap. On completion,
                    // AnimatedContent crossfades the ring into the
                    // checkmark rather than an abrupt swap.
                    val inFlightProgress = if (item is PlayableItem.YoutubeTrack) downloadProgress[item.id] else null
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
                    PlainIconButton(
                        contentDescription = "More options for ${item.title}",
                        onClick = { actionsSheetItem = item },
                        size = 40.dp,
                    ) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null, tint = WhiplashColors.textSecondary)
                    }
                },
            )
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
                        songActionsViewModel.removeDownload(sheetItem.id)
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
