package com.whiplash.music.ui.home
// Developed by Shahid Ansari — github.com/shahidthisside (-SA)

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whiplash.music.WhiplashApplication
import com.whiplash.music.domain.model.PlayableItem
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
private enum class SheetOrigin { SPEED_DIAL, QUICK_PICKS }

/** Number of skeleton rows shown while Quick Picks' first real results are loading — roughly matches how many rows fit before scrolling. */
private const val QUICK_PICKS_SKELETON_ROW_COUNT = 5

@androidx.compose.material3.ExperimentalMaterial3Api
@ExperimentalFoundationApi
@Composable
fun HomeScreen(onPlayTrack: (PlayableItem) -> Unit, onOpenHistory: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as WhiplashApplication
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(app.libraryRepository, app.youtubeSearchRepository),
    )
    val songActionsViewModel: SongActionsViewModel = viewModel(
        factory = SongActionsViewModelFactory(app.libraryRepository, app.downloadManager),
    )
    val downloadedIds by app.libraryRepository.observeDownloadedIds().collectAsState(initial = emptySet())
    val downloadProgress by app.downloadManager.progress.collectAsState()
    var cancelDownloadTarget by remember { mutableStateOf<PlayableItem?>(null) }
    val speedDial by viewModel.speedDial.collectAsState()
    val isSpeedDialLoaded by viewModel.isSpeedDialLoaded.collectAsState()
    val quickPicks by viewModel.quickPicks.collectAsState()
    val isLoadingQuickPicks by viewModel.isLoadingQuickPicks.collectAsState()
    val haptic = LocalHapticFeedback.current
    var actionsSheetItem by remember { mutableStateOf<PlayableItem?>(null) }
    var actionsSheetOrigin by remember { mutableStateOf(SheetOrigin.SPEED_DIAL) }
    var addToPlaylistItem by remember { mutableStateOf<PlayableItem?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showClearSpeedDialConfirm by remember { mutableStateOf(false) }

    // The truly-empty state ("no history, no Quick Picks, nothing loading")
    // must wait for isSpeedDialLoaded — otherwise this renders on every
    // single cold start for the one frame before Room's Speed dial flow
    // has emitted anything yet, replacing what should be a loading
    // skeleton with a flash of "Play something to see it here." that
    // then immediately gets replaced by real content once data arrives.
    if (isSpeedDialLoaded && speedDial.isEmpty() && quickPicks.isEmpty() && !isLoadingQuickPicks) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Play something to see it here.",
                style = MaterialTheme.typography.bodyMedium,
                color = WhiplashColors.textSecondary,
            )
        }
        return
    }

    LazyColumn(
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
                SectionHeader(title = "Speed dial", onHistory = onOpenHistory, onClear = { showClearSpeedDialConfirm = true })
            }
            item {
                SpeedDialGrid(
                    items = speedDial,
                    onPlayTrack = onPlayTrack,
                    onLongPressTrack = { track ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        actionsSheetOrigin = SheetOrigin.SPEED_DIAL
                        actionsSheetItem = track
                    },
                )
            }
        }

        if (quickPicks.isNotEmpty() || isLoadingQuickPicks) {
            item {
                SectionHeader(
                    title = "Quick Picks",
                    onRefresh = { viewModel.loadQuickPicks() },
                    isRefreshing = isLoadingQuickPicks,
                )
            }
            if (isLoadingQuickPicks && quickPicks.isEmpty()) {
                items(QUICK_PICKS_SKELETON_ROW_COUNT) {
                    com.whiplash.music.ui.theme.ShimmerSkeletonRow()
                }
            }
            items(quickPicks, key = { "quickpick:${it.id}" }) { track ->
                GlassListItem(
                    title = track.title,
                    subtitle = track.artist,
                    onClick = { onPlayTrack(track) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        actionsSheetOrigin = SheetOrigin.QUICK_PICKS
                        actionsSheetItem = track
                    },
                    leading = { GlassArtworkThumbnail(artworkUri = track.artworkUri) },
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
                        com.whiplash.music.ui.theme.PlainIconButton(
                            contentDescription = "More options for ${track.title}",
                            onClick = {
                                actionsSheetOrigin = SheetOrigin.QUICK_PICKS
                                actionsSheetItem = track
                            },
                            size = 40.dp,
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
    }

    // 3-dot / long-press song actions sheet (section 51), including
    // Pin/Unpin to Speed dial — reachable via long-press on either a Speed
    // dial tile or a Quick Picks row, since Speed dial tiles have no room
    // for an inline 3-dot button of their own at that tile size.
    val sheetItem = actionsSheetItem
    if (sheetItem != null) {
        val isFavorite by app.libraryRepository.observeIsFavorite(sheetItem).collectAsState(initial = false)
        val isPinned by app.libraryRepository.observeIsPinned(sheetItem).collectAsState(initial = false)
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
                onRemoveDownload = if (sheetItem is PlayableItem.DownloadedTrack) {
                    {
                        songActionsViewModel.removeDownload(sheetItem)
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
}

/**
 * 3x3 grid of square, rounded artwork tiles — matches YouTube Music's
 * "Speed dial" redesign of its former "Listen again" carousel (real
 * design reference researched before building this). Uses a fixed
 * 3-column grid sized to the available width rather than LazyVerticalGrid's
 * own scrolling (the grid never needs to scroll internally — it's capped
 * at 9 items and lives inside the outer LazyColumn).
 */
@ExperimentalFoundationApi
@Composable
private fun SpeedDialGrid(
    items: List<PlayableItem>,
    onPlayTrack: (PlayableItem) -> Unit,
    onLongPressTrack: (PlayableItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm)) {
        items.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm),
            ) {
                row.forEach { track ->
                    SpeedDialTile(
                        track = track,
                        onClick = { onPlayTrack(track) },
                        onLongClick = { onLongPressTrack(track) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Pad the last row with empty spacers so a partial row (e.g. 7 items -> 3+3+1)
                // still aligns to the same 3-column grid instead of stretching the lone tile wide.
                repeat(3 - row.size) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                }
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

@Composable
private fun SectionHeader(
    title: String,
    onHistory: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    isRefreshing: Boolean = false,
    onClear: (() -> Unit)? = null,
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
            if (onHistory != null) {
                PlainIconButton(contentDescription = "See full history", onClick = onHistory, size = 40.dp) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.History,
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
                    size = 40.dp,
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
                PlainIconButton(contentDescription = "Clear $title", onClick = onClear, size = 40.dp) {
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
