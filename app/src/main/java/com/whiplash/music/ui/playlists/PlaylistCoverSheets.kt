package com.whiplash.music.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.domain.model.Playlist
import com.whiplash.music.domain.model.PlaylistArt
import com.whiplash.music.ui.theme.CollectionPillButton
import com.whiplash.music.ui.theme.GlassArtworkThumbnail
import com.whiplash.music.ui.theme.GlassSheet
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.WhiplashRadius

/**
 * "Change cover" sheet: a preview of the current cover, then Automatic,
 * Choose from songs and Choose from gallery. The current choice has a check.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
internal fun PlaylistCoverOptionsSheet(
    playlist: Playlist,
    tracks: List<PlayableItem>?,
    onAutomatic: () -> Unit,
    onChooseSongs: () -> Unit,
    onChooseGallery: () -> Unit,
    onDismiss: () -> Unit,
) {
    val art = remember(playlist.artworkUrl) { PlaylistArt.parse(playlist.artworkUrl) }
    val hasSongArt = tracks.orEmpty().any { it.artworkUri != null }
    GlassSheet(onDismissRequest = onDismiss) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = GlassTokens.spaceMd)) {
                PlaylistArtwork(
                    playlist = playlist,
                    tracks = tracks,
                    modifier = Modifier.size(64.dp),
                    cornerRadius = WhiplashRadius.small,
                    emptyTile = { m -> NamedCoverFallback(playlist.name, m) },
                )
                Column(modifier = Modifier.padding(start = GlassTokens.spaceMd).weight(1f)) {
                    Text("Playlist cover", style = MaterialTheme.typography.titleMedium, color = WhiplashColors.textPrimary)
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = WhiplashColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            CoverOptionRow(
                icon = Icons.Filled.AutoAwesome,
                title = "Automatic",
                subtitle = "Built from the playlist's first songs",
                checked = art == PlaylistArt.Auto,
                onClick = onAutomatic,
            )
            CoverOptionRow(
                icon = Icons.Filled.GridView,
                title = "Choose from songs",
                subtitle = if (hasSongArt) "Pick 1 to 4 song covers" else "Add songs with artwork first",
                checked = art is PlaylistArt.Songs,
                enabled = hasSongArt,
                onClick = onChooseSongs,
            )
            CoverOptionRow(
                icon = Icons.Filled.PhotoLibrary,
                title = "Choose from gallery",
                subtitle = "Use any picture on this phone",
                checked = art is PlaylistArt.Image,
                onClick = onChooseGallery,
            )
        }
    }
}

@Composable
private fun CoverOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(WhiplashRadius.small))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = GlassTokens.spaceXs, vertical = GlassTokens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val alpha = if (enabled) 1f else GlassTokens.opacityDisabled
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(WhiplashRadius.small))
                .background(WhiplashColors.accent.copy(alpha = 0.14f * alpha)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = WhiplashColors.accent.copy(alpha = alpha), modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.padding(start = GlassTokens.spaceMd).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = WhiplashColors.textPrimary.copy(alpha = alpha))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = WhiplashColors.textSecondary.copy(alpha = alpha))
        }
        if (checked) Icon(Icons.Filled.Check, contentDescription = "Current", tint = WhiplashColors.accent)
    }
}

/**
 * Song-cover picker: tap up to four songs; their covers make the tile in
 * the order tapped (numbered badges), with a live preview at the top.
 * Songs sharing one cover are listed once.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
internal fun PlaylistCoverSongPicker(
    playlist: Playlist,
    tracks: List<PlayableItem>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val choices = remember(tracks) { tracks.filter { it.artworkUri != null }.distinctBy { it.artworkUri } }
    val initial = remember(playlist.artworkUrl) {
        (PlaylistArt.parse(playlist.artworkUrl) as? PlaylistArt.Songs)?.artworks.orEmpty()
            .filter { uri -> choices.any { it.artworkUri == uri } }
    }
    var picked by remember(initial) { mutableStateOf(initial) }

    GlassSheet(onDismissRequest = onDismiss) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(WhiplashRadius.medium))
                        .background(WhiplashColors.surfaceElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    if (picked.isEmpty()) {
                        Icon(Icons.Filled.GridView, contentDescription = null, tint = WhiplashColors.textTertiary)
                    } else {
                        MosaicCover(picked, Modifier.size(88.dp), WhiplashRadius.medium)
                    }
                }
                Column(modifier = Modifier.padding(start = GlassTokens.spaceMd).weight(1f)) {
                    Text("Choose covers", style = MaterialTheme.typography.titleMedium, color = WhiplashColors.textPrimary)
                    Text(
                        text = "${picked.size} of ${PlaylistArt.MAX_SONGS} picked · tap in the order you want",
                        style = MaterialTheme.typography.bodySmall,
                        color = WhiplashColors.textSecondary,
                    )
                }
            }
            Spacer(Modifier.padding(top = GlassTokens.spaceMd))
            LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                items(choices, key = { it.artworkUri!! }) { track ->
                    val uri = track.artworkUri!!
                    val index = picked.indexOf(uri)
                    val canAdd = index >= 0 || picked.size < PlaylistArt.MAX_SONGS
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(WhiplashRadius.small))
                            .clickable(enabled = canAdd, role = Role.Checkbox) {
                                picked = if (index >= 0) picked - uri else picked + uri
                            }
                            .semantics { selected = index >= 0 }
                            .padding(horizontal = GlassTokens.spaceXs, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassArtworkThumbnail(artworkUri = uri)
                        Column(modifier = Modifier.padding(start = GlassTokens.spaceMd).weight(1f)) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (canAdd) WhiplashColors.textPrimary else WhiplashColors.textDisabled,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = WhiplashColors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (index >= 0) WhiplashColors.accent else WhiplashColors.surfaceElevated),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (index >= 0) {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = WhiplashColors.onAccent,
                                )
                            }
                        }
                        Spacer(Modifier.width(GlassTokens.spaceSm))
                    }
                }
            }
            Spacer(Modifier.padding(top = GlassTokens.spaceMd))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                CollectionPillButton("Cancel", Icons.Filled.Close, onDismiss, primary = false, modifier = Modifier.weight(1f))
                CollectionPillButton(
                    text = "Save cover",
                    icon = Icons.Filled.Check,
                    onClick = { onSave(picked) },
                    primary = true,
                    enabled = picked.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Small colour tile with an initial, for the options-sheet preview of an empty playlist. */
@Composable
private fun NamedCoverFallback(name: String, modifier: Modifier) {
    com.whiplash.music.ui.theme.GradientIconCover(
        Icons.AutoMirrored.Filled.QueueMusic,
        com.whiplash.music.ui.theme.tintForName(name),
        modifier,
    )
}
