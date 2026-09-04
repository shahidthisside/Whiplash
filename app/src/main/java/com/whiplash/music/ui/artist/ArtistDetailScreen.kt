package com.whiplash.music.ui.artist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whiplash.music.WhiplashApplication
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.domain.model.YoutubeArtistDetail
import com.whiplash.music.domain.model.YoutubePlaylistResult
import com.whiplash.music.ui.player.SongActionsContent
import com.whiplash.music.ui.player.SongActionsViewModel
import com.whiplash.music.ui.player.SongActionsViewModelFactory
import com.whiplash.music.ui.player.shareYoutubeTrack
import com.whiplash.music.ui.theme.GlassArtworkThumbnail
import com.whiplash.music.ui.theme.GlassButton
import com.whiplash.music.ui.theme.GlassListItem
import com.whiplash.music.ui.theme.GlassSheet
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.PlainIconButton
import com.whiplash.music.ui.theme.WhiplashColors

/**
 * Artist/channel detail screen (section 40): artwork, popular songs, real
 * albums when the channel exposes that tab, radio (reuses the existing,
 * already-verified autoplay/recommendation system — starting radio from an
 * artist just plays their first popular song, and the real autoplay
 * mechanism takes over from there, same honest reuse pattern as
 * "Start radio" in [com.whiplash.music.ui.player.SongActionsContent]).
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun ArtistDetailScreen(
    channelUrl: String,
    onBack: () -> Unit,
    onPlayQueue: (queue: List<PlayableItem>, startIndex: Int) -> Unit,
    onOpenAlbum: (YoutubePlaylistResult) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as WhiplashApplication
    val viewModel: ArtistDetailViewModel = viewModel(
        key = "artist:$channelUrl",
        factory = ArtistDetailViewModelFactory(app.youtubeDetailProvider, channelUrl),
    )
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = GlassTokens.spaceMd)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = GlassTokens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlainIconButton(contentDescription = "Back", onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = WhiplashColors.textPrimary)
            }
        }

        // Loading -> Error/Loaded crossfade — same rationale as
        // AlbumDetailScreen's own identical fix: a plain fade so the
        // loading spinner doesn't just vanish/appear abruptly once the
        // real network fetch resolves.
        AnimatedContent(
            targetState = when (state) {
                is ArtistDetailUiState.Loading -> "loading"
                is ArtistDetailUiState.Error -> "error"
                is ArtistDetailUiState.Loaded -> "loaded"
            },
            transitionSpec = {
                fadeIn(animationSpec = tween(GlassTokens.animRegular))
                    .togetherWith(fadeOut(animationSpec = tween(GlassTokens.animFast)))
            },
            label = "artistDetailState",
        ) { _ ->
            when (val s = state) {
                is ArtistDetailUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WhiplashColors.accent)
                }
                is ArtistDetailUiState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Couldn't load this artist",
                            style = MaterialTheme.typography.titleMedium,
                            color = WhiplashColors.textPrimary,
                        )
                        Text(text = s.message, style = MaterialTheme.typography.bodySmall, color = WhiplashColors.textSecondary)
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceMd))
                        GlassButton(text = "Retry", onClick = viewModel::load)
                    }
                }
                is ArtistDetailUiState.Loaded -> ArtistDetailContent(s.detail, onPlayQueue, onOpenAlbum)
            }
        }
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun ArtistDetailContent(
    detail: YoutubeArtistDetail,
    onPlayQueue: (List<PlayableItem>, Int) -> Unit,
    onOpenAlbum: (YoutubePlaylistResult) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as WhiplashApplication
    val haptic = LocalHapticFeedback.current
    val songActionsViewModel: SongActionsViewModel = viewModel(
        factory = SongActionsViewModelFactory(app.libraryRepository, app.downloadManager),
    )

    // Same live download-status subscriptions PlayableItemsList/HomeScreen
    // already use for their own rows — hoisted once here rather than
    // per-row, matching that existing pattern (see PlayableItemsList's
    // own doc comment on why this needs to be a single shared
    // subscription rather than one per row).
    val downloadedIds by app.libraryRepository.observeDownloadedIds().collectAsState(initial = emptySet())
    val downloadProgress by app.downloadManager.progress.collectAsState()
    var cancelDownloadTarget by remember { mutableStateOf<PlayableItem?>(null) }
    var removeDownloadTarget by remember { mutableStateOf<PlayableItem?>(null) }
    var actionsSheetItem by remember { mutableStateOf<PlayableItem?>(null) }
    var addToPlaylistItem by remember { mutableStateOf<PlayableItem?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = GlassTokens.miniPlayerReservedHeight),
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(WhiplashColors.surfaceElevated),
                ) {
                    if (detail.artworkUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(detail.artworkUrl).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceSm))
                Text(text = detail.name, style = MaterialTheme.typography.headlineSmall, color = WhiplashColors.textPrimary)
                detail.subscriberCount?.let {
                    Text(
                        text = "${com.whiplash.music.ui.common.formatCompactCount(it)} subscribers",
                        style = MaterialTheme.typography.bodySmall,
                        color = WhiplashColors.textSecondary,
                    )
                }
                if (detail.popularSongs.isNotEmpty()) {
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceMd))
                    Row(horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm), verticalAlignment = Alignment.CenterVertically) {
                        GlassButton(
                            text = "Radio",
                            onClick = { onPlayQueue(listOf(detail.popularSongs.first()), 0) },
                        )
                        com.whiplash.music.ui.common.BatchDownloadButton(batchName = detail.name, tracks = detail.popularSongs)
                    }
                }
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceLg))
            }
        }

        if (detail.popularSongs.isNotEmpty()) {
            item {
                Text(
                    text = "Popular songs",
                    style = MaterialTheme.typography.titleMedium,
                    color = WhiplashColors.textPrimary,
                    modifier = Modifier.padding(bottom = GlassTokens.spaceSm),
                )
            }
            items(detail.popularSongs, key = { "song:${it.id}" }) { track ->
                GlassListItem(
                    title = track.title,
                    subtitle = track.artist,
                    onClick = { onPlayQueue(detail.popularSongs, detail.popularSongs.indexOf(track)) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        actionsSheetItem = track
                    },
                    leading = { GlassArtworkThumbnail(artworkUri = track.artworkUri) },
                    trailing = {
                        // Same animated progress-ring/checkmark/failed
                        // badge + 3-dot "more options" button every other
                        // track list in the app already shows (Search,
                        // Local Library, Home's Quick Picks, Downloads
                        // tab) — a real, reported gap: Popular songs rows
                        // here had neither, so a song downloaded from an
                        // artist's page showed no progress/checkmark at
                        // all, and there was no way to reach the
                        // long-press-only actions sheet without knowing
                        // long-press was even possible.
                        val inFlightProgress = downloadProgress[track.id]
                        val downloaded = track.id in downloadedIds
                        androidx.compose.animation.AnimatedContent(
                            targetState = when {
                                inFlightProgress?.failed == true -> "failed"
                                inFlightProgress != null -> "downloading"
                                downloaded -> "downloaded"
                                else -> "none"
                            },
                            label = "artistPopularSongDownloadStatusBadge",
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
                                            onClick = { cancelDownloadTarget = track },
                                        )
                                        .semantics { contentDescription = "Cancel download of ${track.title}" },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        progress = { inFlightProgress?.fraction ?: 0f },
                                        color = WhiplashColors.accent,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                "failed" -> Icon(
                                    Icons.Filled.ErrorOutline,
                                    contentDescription = "Download failed for ${track.title}",
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
                            contentDescription = "More options for ${track.title}",
                            onClick = { actionsSheetItem = track },
                            size = 48.dp,
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null, tint = WhiplashColors.textSecondary)
                        }
                    },
                )
            }
        }

        if (detail.albums.isNotEmpty()) {
            item {
                Text(
                    text = "Albums",
                    style = MaterialTheme.typography.titleMedium,
                    color = WhiplashColors.textPrimary,
                    modifier = Modifier.padding(top = GlassTokens.spaceMd, bottom = GlassTokens.spaceSm),
                )
            }
            items(detail.albums, key = { "album:${it.url}" }) { album ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAlbum(album) }
                        .padding(vertical = GlassTokens.spaceSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassArtworkThumbnail(artworkUri = album.artworkUrl)
                    Column(modifier = Modifier.padding(start = GlassTokens.spaceSm)) {
                        Text(
                            text = album.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = WhiplashColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (detail.popularSongs.isEmpty() && detail.albums.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(GlassTokens.spaceLg), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No songs or albums found for this artist.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WhiplashColors.textSecondary,
                    )
                }
            }
        }
    }

    // Cancel-download confirmation — tapping the in-flight progress ring
    // opens this rather than cancelling immediately on a single
    // accidental tap, matching PlayableItemsList/HomeScreen's own
    // cancel-download confirmation.
    val cancelTarget = cancelDownloadTarget
    if (cancelTarget != null) {
        com.whiplash.music.ui.theme.GlassConfirmDialog(
            title = "Cancel download?",
            message = "Downloading \"${cancelTarget.title}\" will be cancelled.",
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

    // Long-press/3-dot song-actions sheet — same actions PlayableItemsList
    // offers for a YoutubeTrack row (Search, Home, Local Library).
    val sheetItem = actionsSheetItem
    if (sheetItem != null) {
        val isFavorite by app.libraryRepository.observeIsFavorite(sheetItem).collectAsState(initial = false)
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
                    { onPlayQueue(listOf(sheetItem), 0); actionsSheetItem = null }
                } else null,
                onShare = if (sheetItem is PlayableItem.YoutubeTrack) {
                    { shareYoutubeTrack(context, sheetItem); actionsSheetItem = null }
                } else null,
                onAddToPlaylist = {
                    addToPlaylistItem = sheetItem
                    actionsSheetItem = null
                },
                isDownloaded = sheetItem.id in downloadedIds,
                onDownload = if (sheetItem is PlayableItem.YoutubeTrack && sheetItem.id !in downloadedIds) {
                    {
                        app.downloadManager.startDownload(sheetItem)
                        actionsSheetItem = null
                    }
                } else null,
                onRemoveDownload = if (sheetItem.id in downloadedIds) {
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
                onCreateNew = {
                    // Real, reported bug (UAT audit finding): this used
                    // to just close the sheet and discard the action —
                    // tapping "New playlist" from an artist page's Add-
                    // to-playlist sheet silently did nothing. Now wired
                    // to the same GlassTextInputDialog + createPlaylistAndAdd
                    // flow PlayableItemsList.kt's own AddToPlaylistContent
                    // usage already uses correctly.
                    showCreatePlaylistDialog = true
                },
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
}
