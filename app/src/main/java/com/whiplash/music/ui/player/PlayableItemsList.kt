package com.whiplash.music.ui.player

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
) {
    val context = LocalContext.current
    val app = context.applicationContext as WhiplashApplication
    val haptic = LocalHapticFeedback.current
    val songActionsViewModel: SongActionsViewModel = viewModel(
        factory = SongActionsViewModelFactory(app.libraryRepository),
    )
    var actionsSheetItem by remember { mutableStateOf<PlayableItem?>(null) }
    var addToPlaylistItem by remember { mutableStateOf<PlayableItem?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceXs),
        contentPadding = contentPadding,
    ) {
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
    }

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
                onAddToPlaylist = {
                    addToPlaylistItem = sheetItem
                    actionsSheetItem = null
                },
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
                onShare = if (sheetItem is PlayableItem.YoutubeTrack) {
                    {
                        shareYoutubeTrack(context, sheetItem)
                        actionsSheetItem = null
                    }
                } else null,
                isPinned = isPinned,
                onTogglePinned = {
                    songActionsViewModel.togglePinned(sheetItem, isCurrentlyPinned = isPinned)
                    actionsSheetItem = null
                },
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
                    songActionsViewModel.addToPlaylist(playlistTargetItem, playlist.id)
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
}

/**
 * Shares a real, working YouTube watch URL for [track] via Android's
 * native share sheet — only offered for YouTube tracks (section 73: don't
 * add a fake action) since a [PlayableItem.LocalTrack] has no meaningful
 * external link to share.
 */
fun shareYoutubeTrack(context: android.content.Context, track: PlayableItem.YoutubeTrack) {
    val url = "https://youtube.com/watch?v=${track.id}"
    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, "${track.title} — $url")
    }
    context.startActivity(android.content.Intent.createChooser(sendIntent, "Share song"))
}
