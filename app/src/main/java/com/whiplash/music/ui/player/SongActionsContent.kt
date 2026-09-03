package com.whiplash.music.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.ui.theme.GlassArtworkThumbnail
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.WhiplashColors

/**
 * Long-press song actions sheet (section 51: "song actions"). Shown from
 * long-pressing any track row in Search, Local Library, Home, or Favorites.
 */
@Composable
fun SongActionsContent(
    item: PlayableItem,
    isFavorite: Boolean,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit = {},
    onToggleFavorite: (() -> Unit)? = null,
    onStartRadio: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    isPinned: Boolean = false,
    onTogglePinned: (() -> Unit)? = null,
    onRemoveFromSpeedDial: (() -> Unit)? = null,
    onRemoveFromQuickPicks: (() -> Unit)? = null,
    onRemoveFromHistory: (() -> Unit)? = null,
    // Playlist-context actions (section: Playlist detail screen) — both
    // null everywhere the sheet isn't opened from inside a specific
    // playlist's own track list (Search/Home/Local Library/Favorites),
    // where "this song's membership in playlist X" isn't a meaningful
    // concept for the sheet to expose. When non-null, onRemoveFromPlaylist
    // replaces the generic onAddToPlaylist row below (a song already
    // known to be inside the playlist the sheet was opened from doesn't
    // need "Add to playlist" — it needs to leave that specific playlist).
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onMoveToOtherPlaylist: (() -> Unit)? = null,
    onCopyToOtherPlaylist: (() -> Unit)? = null,
    // Offline downloads (section: Library > Downloads, YouTube-Music-style):
    // isDownloaded reflects real persisted state (see LibraryRepository.observeDownloadedIds),
    // onDownload/onRemoveDownload are null wherever downloading doesn't make
    // sense (LocalTrack — already on-device; DownloadedTrack shown from the
    // Downloads tab itself gets onRemoveDownload only).
    isDownloaded: Boolean = false,
    onDownload: (() -> Unit)? = null,
    onRemoveDownload: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = GlassTokens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassArtworkThumbnail(artworkUri = item.artworkUri, size = 48.dp)
            Column(modifier = Modifier.padding(horizontal = GlassTokens.spaceSm)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = WhiplashColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = WhiplashColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        SongActionRow(
            icon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = WhiplashColors.textPrimary) },
            label = "Play next",
            onClick = onPlayNext,
        )
        SongActionRow(
            icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = WhiplashColors.textPrimary) },
            label = "Add to queue",
            onClick = onAddToQueue,
        )
        if (onStartRadio != null) {
            SongActionRow(
                icon = { Icon(Icons.Filled.Radio, contentDescription = null, tint = WhiplashColors.textPrimary) },
                label = "Start radio",
                onClick = onStartRadio,
            )
        }
        SongActionRow(
            icon = { Icon(Icons.Filled.LibraryAdd, contentDescription = null, tint = WhiplashColors.textPrimary) },
            label = if (onRemoveFromPlaylist != null) "Remove from playlist" else "Add to playlist",
            onClick = onRemoveFromPlaylist ?: onAddToPlaylist,
        )
        if (onMoveToOtherPlaylist != null) {
            SongActionRow(
                icon = { Icon(Icons.Filled.DriveFileMove, contentDescription = null, tint = WhiplashColors.textPrimary) },
                label = "Move to other playlist",
                onClick = onMoveToOtherPlaylist,
            )
        }
        if (onCopyToOtherPlaylist != null) {
            SongActionRow(
                icon = { Icon(Icons.Filled.FileCopy, contentDescription = null, tint = WhiplashColors.textPrimary) },
                label = "Copy to other playlist",
                onClick = onCopyToOtherPlaylist,
            )
        }
        if (onDownload != null) {
            SongActionRow(
                icon = {
                    Icon(
                        imageVector = if (isDownloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                        contentDescription = null,
                        tint = if (isDownloaded) WhiplashColors.accent else WhiplashColors.textPrimary,
                    )
                },
                label = if (isDownloaded) "Downloaded" else "Download",
                onClick = onDownload,
            )
        }
        if (onRemoveDownload != null) {
            SongActionRow(
                icon = { Icon(Icons.Filled.RemoveCircleOutline, contentDescription = null, tint = WhiplashColors.error) },
                label = "Remove download",
                onClick = onRemoveDownload,
            )
        }
        if (onToggleFavorite != null) {
            SongActionRow(
                icon = {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) WhiplashColors.accent else WhiplashColors.textPrimary,
                    )
                },
                label = if (isFavorite) "Remove from favorites" else "Add to favorites",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleFavorite()
                },
            )
        }
        if (onShare != null) {
            SongActionRow(
                icon = { Icon(Icons.Filled.Share, contentDescription = null, tint = WhiplashColors.textPrimary) },
                label = "Share",
                onClick = onShare,
            )
        }
        if (onTogglePinned != null) {
            SongActionRow(
                icon = {
                    Icon(
                        imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = null,
                        tint = if (isPinned) WhiplashColors.accent else WhiplashColors.textPrimary,
                    )
                },
                label = if (isPinned) "Unpin from Speed dial" else "Pin to Speed dial",
                onClick = onTogglePinned,
            )
        }
        if (onRemoveFromSpeedDial != null) {
            SongActionRow(
                icon = { Icon(Icons.Filled.RemoveCircleOutline, contentDescription = null, tint = WhiplashColors.error) },
                label = "Remove from Speed dial",
                onClick = onRemoveFromSpeedDial,
            )
        }
        if (onRemoveFromQuickPicks != null) {
            SongActionRow(
                icon = { Icon(Icons.Filled.RemoveCircleOutline, contentDescription = null, tint = WhiplashColors.error) },
                label = "Remove from Quick Picks",
                onClick = onRemoveFromQuickPicks,
            )
        }
        if (onRemoveFromHistory != null) {
            SongActionRow(
                icon = { Icon(Icons.Filled.RemoveCircleOutline, contentDescription = null, tint = WhiplashColors.error) },
                label = "Remove from history",
                onClick = onRemoveFromHistory,
            )
        }

        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceSm))
    }
}

@Composable
private fun SongActionRow(icon: @Composable () -> Unit, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = GlassTokens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = WhiplashColors.textPrimary,
            modifier = Modifier.padding(start = GlassTokens.spaceMd),
        )
    }
}
